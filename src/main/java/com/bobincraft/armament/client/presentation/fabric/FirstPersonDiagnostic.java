package com.bobincraft.armament.client.presentation.fabric;

import com.bobincraft.armament.client.mixin.GameRendererFovAccess;
import com.bobincraft.armament.client.presentation.*;
import com.bobincraft.armament.client.render.fabric.*;
import com.bobincraft.combat.InjuryCarrier;
import com.bobincraft.combat.InjurySeverity;
import com.bobincraft.drone.client.DroneClient;
import com.bobincraft.vehicle.client.ArmorClient;
import com.bobincraft.vehicle.client.GrenadeClient;
import com.bobincraft.weapon.grom7.client.Grom7Client;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityPose;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.*;
import org.slf4j.LoggerFactory;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/** Minecraft bridge only. Camera/injury/item state is observed, never changed. */
public final class FirstPersonDiagnostic {
    private static FirstPersonDiagnostic active;
    private final FirstPersonResources resources;
    private final FabricGlbRenderer renderer;
    private final double[] handView=new double[16],projection=new double[16],cameraToWorld=new double[16];
    private final float[] transfer=new float[16],normalTransfer=new float[16];
    private final Matrix4f viewProjection=new Matrix4f(),cameraMatrix=new Matrix4f(),normalMatrix4=new Matrix4f();
    private final Matrix3f normalMatrix=new Matrix3f();
    private FirstPersonResources.Bundle generation;
    private FirstPersonWeaponPresenter presenter;
    private boolean requested,cachedEmpty=true;private Boolean inertiaOverride;
    private ClientPlayerEntity owner;
    private net.minecraft.client.world.ClientWorld world;
    private String reason="hidden";private double fov;
    public FirstPersonDiagnostic(FirstPersonResources resources,FabricGlbRenderer renderer){this.resources=resources;this.renderer=renderer;}
    public void register(){
        active=this;
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,registry)->{
            var fp=literal("fp")
                .then(literal("show").executes(ctx->{requested=true;if(resources.current().isEmpty())resources.reload(MinecraftClient.getInstance().getResourceManager());update();ctx.getSource().sendFeedback(Text.literal(status()));return presenter==null?0:1;}))
                .then(literal("hide").executes(ctx->{requested=false;drop();reason="hidden";return 1;}))
                .then(literal("status").executes(ctx->{ctx.getSource().sendFeedback(Text.literal(status()));return 1;}));
            var animation=literal("anim");
            for(String label:new String[]{"idle","inspect","raise","lower","move"})animation.then(literal(label).executes(ctx->{
                update();if(presenter==null){ctx.getSource().sendError(Text.literal(status()));return 0;}
                presenter.animation(label,MinecraftClient.getInstance().world.getTime()/20.0);return 1;
            }));
            fp.then(animation).then(literal("inertia")
                .then(literal("on").executes(ctx->{inertiaOverride=true;if(presenter!=null)presenter.inertia(true);return 1;}))
                .then(literal("off").executes(ctx->{inertiaOverride=false;if(presenter!=null)presenter.inertia(false);return 1;})));
            dispatcher.register(literal("bobin_glb").then(fp));
        });
        ClientTickEvents.END_CLIENT_TICK.register(client->update());
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{requested=false;drop();reason="disconnected";});
        ClientLifecycleEvents.CLIENT_STOPPING.register(client->{active=null;drop();resources.close();});
    }
    private FirstPersonEligibility.View observations(){
        var c=MinecraftClient.getInstance();var p=c.player;boolean present=p!=null&&c.world!=null;
        boolean compatible=present&&((p.getPose()==EntityPose.STANDING||p.getPose()==EntityPose.CROUCHING)&&!p.isSleeping()&&!p.isInvisible());
        if(present&&p instanceof InjuryCarrier carrier){var injury=carrier.bobinInjuries();compatible&=injury.missingLimbs()==0&&injury.severity()==InjurySeverity.NONE&&injury.faceSeverity()==0;}
        boolean owner=present&&(DroneClient.isActive()||DroneClient.isReturningToPlayer()||ArmorClient.inside()||ArmorClient.thirdPersonTank()||ArmorClient.isClimbingOut(p)||GrenadeClient.ownsHands(p)||Grom7Client.getActiveAnimation(p.getUuid())!=null);
        return new FirstPersonEligibility.View(Boolean.getBoolean("bobin.armament.glbDebug"),requested,present,present&&p.isAlive(),present&&p.isSpectator(),
            c.options.getPerspective().isFirstPerson()&&!c.gameRenderer.getCamera().isThirdPerson(),present&&c.getCameraEntity()==p&&!owner,
            present&&p.getMainHandStack().isEmpty()&&cachedEmpty,present&&p.getOffHandStack().isEmpty()&&cachedEmpty,compatible,owner,present&&(p.isUsingItem()||p.handSwinging),present&&p.hasVehicle(),!c.options.hudHidden,
            c.getWindow().getFramebufferWidth()>0&&c.getWindow().getFramebufferHeight()>0);
    }
    private void drop(){presenter=null;generation=null;owner=null;world=null;}
    private void update(){
        try{updateUnchecked();}catch(RuntimeException e){disable(e);}
    }
    private void updateUnchecked(){
        reason=FirstPersonEligibility.reason(observations());if(!reason.equals("ready")){drop();return;}
        var bundle=resources.current().orElse(null);if(bundle==null){drop();reason="resources_unavailable";return;}
        var client=MinecraftClient.getInstance();long tick=client.world.getTime();
        if(bundle!=generation||owner!=client.player||world!=client.world){generation=bundle;owner=client.player;world=client.world;presenter=new FirstPersonWeaponPresenter(bundle.cpu());presenter.show(tick/20.0);if(inertiaOverride!=null)presenter.inertia(inertiaOverride);}
        Vec3d velocity=client.player.getVelocity();double threshold=bundle.cpu().profile().movementThreshold();
        presenter.tick(tick,client.player.getYaw(),client.player.getPitch(),(velocity.x*velocity.x+velocity.z*velocity.z)*400>threshold*threshold);
    }
    /** Called once from HeldItemRenderer's outer hand pass. Cancel vanilla only after a successful draw. */
    public static boolean render(float delta,MatrixStack matrices,VertexConsumerProvider.Immediate consumers,ClientPlayerEntity player,int light,boolean cachedStacksEmpty){
        FirstPersonDiagnostic diagnostic=active;if(diagnostic==null||!diagnostic.requested)return false;
        return diagnostic.draw(delta,matrices,consumers,player,light,cachedStacksEmpty);
    }
    private boolean draw(float delta,MatrixStack matrices,VertexConsumerProvider.Immediate consumers,ClientPlayerEntity player,int light,boolean cachedStacksEmpty){
        var client=MinecraftClient.getInstance();
        cachedEmpty=cachedStacksEmpty;
        if(player!=client.player||!cachedEmpty){reason="cached_item_transition";drop();return false;}
        update();if(presenter==null)return false;
        if(!renderer.hasShader()||Tessellator.getInstance().getBuffer().isBuilding()){reason="shader_or_buffer_unavailable";return false;}
        try{
            var camera=client.gameRenderer.getCamera();
            fov=((GameRendererFovAccess)client.gameRenderer).bobin$armamentFov(camera,delta,true);
            matrix(client.gameRenderer.getBasicProjectionMatrix(fov),projection);matrix(matrices.peek().getPositionMatrix(),handView);
            cameraMatrix.identity().rotation(camera.getRotation());matrix(cameraMatrix,cameraToWorld);
            if(!presenter.sample(client.world.getTime(),delta,handView,projection,cameraToWorld)){reason=presenter.rejection();return false;}
            presenter.copyProjection(transfer);viewProjection.set(transfer);presenter.copyNormals(normalTransfer);normalMatrix4.set(normalTransfer);normalMatrix.set(normalMatrix4);
            // Preserve the caller's pending immediate batch before using the GLB backend.
            consumers.draw();
            renderer.render(generation.cpu().model(),presenter.pose(),new GlbRenderContext(viewProjection,light,new Vec3d(presenter.cameraInModel(0),presenter.cameraInModel(1),presenter.cameraInModel(2)),generation.gpu(),normalMatrix));
            reason="rendered";return true;
        }catch(RuntimeException e){disable(e);return false;}
    }
    private void disable(RuntimeException e){requested=false;drop();reason="evaluation_error: "+e.getMessage();LoggerFactory.getLogger("bobin_craft/armament_fp").warn("Only the first-person diagnostic was disabled",e);}
    private void matrix(Matrix4f source,double[] destination){source.get(transfer);for(int i=0;i<16;i++)destination[i]=transfer[i];}
    public String status(){
        var client=MinecraftClient.getInstance();String policy=FirstPersonEligibility.reason(observations());
        return "fpRequested="+requested+", policy="+policy+", render="+reason+", fov="+fov+", viewport="+client.getWindow().getFramebufferWidth()+"x"+client.getWindow().getFramebufferHeight()+
            (presenter==null?"":", clip="+presenter.action()+", inertia="+presenter.inertiaEnabled()+", contactMetres="+presenter.contactMetres()+", contactDegrees="+presenter.contactDegrees()+", minDepth="+presenter.minimumDepth())+", "+resources.status();
    }
}
