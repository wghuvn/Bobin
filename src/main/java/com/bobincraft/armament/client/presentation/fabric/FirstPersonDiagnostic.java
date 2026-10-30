package com.bobincraft.armament.client.presentation.fabric;

import com.bobincraft.armament.client.presentation.*;
import com.bobincraft.armament.client.render.fabric.FabricGlbRenderer;
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
import org.slf4j.LoggerFactory;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/** Minecraft bridge only. Camera/injury/item state is observed, never changed. */
public final class FirstPersonDiagnostic {
    private static FirstPersonDiagnostic active;
    private final FirstPersonResources resources;
    private final FirstPersonGlbRenderer renderer;
    private FirstPersonResources.Bundle generation;
    private FirstPersonWeaponPresenter presenter;
    private boolean requested,cachedEmpty=true;private Boolean inertiaOverride;
    private ClientPlayerEntity owner;
    private net.minecraft.client.world.ClientWorld world;
    private String reason="hidden";
    // Session-local client simulation time. Server world-time corrections must
    // not seek cosmetic clips; rendering never advances this clock.
    private long visualTick;
    public FirstPersonDiagnostic(FirstPersonResources resources,FabricGlbRenderer renderer){this.resources=resources;this.renderer=new FirstPersonGlbRenderer(renderer);}
    public void register(){
        active=this;
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,registry)->{
            var fp=literal("fp")
                .then(literal("show").executes(ctx->{requested=true;if(resources.current().isEmpty())resources.reload(MinecraftClient.getInstance().getResourceManager());update();if(presenter!=null)presenter.calibration();ctx.getSource().sendFeedback(Text.literal(status()));return presenter==null?0:1;}))
                .then(literal("hide").executes(ctx->{requested=false;drop();reason="hidden";return 1;}))
                .then(literal("static").executes(ctx->{update();if(presenter==null)return 0;presenter.calibration();return 1;}))
                .then(literal("diagnose").executes(ctx->{
                    if(presenter==null||!presenter.rejection().equals("ready")){ctx.getSource().sendError(Text.literal("Show the FPS diagnostic and wait for a rendered frame first. "+status()));return 0;}
                    var snapshot=FirstPersonPoseDiagnostics.capture(presenter);
                    ctx.getSource().sendFeedback(Text.literal("Last accepted FPS pose: view metres (depth = -z); bounds are unclipped NDC, not visible silhouette coverage."));
                    snapshot.joints().forEach((name,p)->ctx.getSource().sendFeedback(Text.literal(String.format(java.util.Locale.ROOT,"%s xyz=(%.4f, %.4f, %.4f), depth=%.4fm",name,p.x(),p.y(),p.z(),p.depth()))));
                    snapshot.elbowDegrees().forEach((side,degrees)->ctx.getSource().sendFeedback(Text.literal(String.format(java.util.Locale.ROOT,"%s elbow bend=%.2fdeg (straight=0)",side,degrees))));
                    snapshot.meshes().forEach((name,b)->ctx.getSource().sendFeedback(Text.literal(String.format(java.util.Locale.ROOT,"%s NDC=[%.3f,%.3f]-[%.3f,%.3f], depth=[%.4f,%.4f]m",name,b.minX(),b.minY(),b.maxX(),b.maxY(),b.minDepth(),b.maxDepth()))));
                    snapshot.contacts().forEach(c->ctx.getSource().sendFeedback(Text.literal(String.format(java.util.Locale.ROOT,"%s socket error=%.6fm / %.4fdeg, view palm normal=(%.3f,%.3f,%.3f)",c.name(),c.metres(),c.degrees(),c.palmNormal().x(),c.palmNormal().y(),c.palmNormal().z()))));return 1;
                }))
                .then(literal("status").executes(ctx->{ctx.getSource().sendFeedback(Text.literal(status()));return 1;}));
            var animation=literal("anim");
            for(String label:new String[]{"idle","inspect","raise","lower","move"})animation.then(literal(label).executes(ctx->{
                update();if(presenter==null){ctx.getSource().sendError(Text.literal(status()));return 0;}
                presenter.animation(label,visualTick/20.0);return 1;
            }));
            fp.then(animation).then(literal("inertia")
                .then(literal("on").executes(ctx->{inertiaOverride=true;if(presenter!=null)presenter.inertia(true);return 1;}))
                .then(literal("off").executes(ctx->{inertiaOverride=false;if(presenter!=null)presenter.inertia(false);return 1;})));
            dispatcher.register(literal("bobin_glb").then(fp));
        });
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            boolean advance=client.world!=null&&!client.isPaused();
            if(advance)visualTick++;
            update(advance);
        });
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
        update(false);
    }
    private void update(boolean advance){
        try{updateUnchecked(advance);}catch(RuntimeException e){disable(e);}
    }
    private void updateUnchecked(boolean advance){
        reason=FirstPersonEligibility.reason(observations());if(!reason.equals("ready")){drop();return;}
        var client=MinecraftClient.getInstance();
        var bundle=resources.current(client.player.getModel()).orElse(null);if(bundle==null){drop();reason="resources_unavailable";return;}
        if(bundle!=generation||owner!=client.player||world!=client.world){generation=bundle;owner=client.player;world=client.world;presenter=new FirstPersonWeaponPresenter(bundle.cpu());presenter.show(visualTick/20.0);if(inertiaOverride!=null)presenter.inertia(inertiaOverride);}
        if(advance){
            Vec3d velocity=client.player.getVelocity();double threshold=bundle.cpu().profile().movementThreshold();
            presenter.tick(visualTick,client.player.getYaw(),client.player.getPitch(),(velocity.x*velocity.x+velocity.z*velocity.z)*400>threshold*threshold);
        }
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
        try{
            boolean rendered=renderer.render(client,visualTick,delta,matrices,consumers,player,light,presenter,generation);
            reason=renderer.result();
            return rendered;
        }catch(RuntimeException e){disable(e);return false;}
    }
    private void disable(RuntimeException e){requested=false;drop();reason="evaluation_error: "+e.getMessage();LoggerFactory.getLogger("bobin_craft/armament_fp").warn("Only the first-person diagnostic was disabled",e);}
    public String status(){
        var client=MinecraftClient.getInstance();String policy=FirstPersonEligibility.reason(observations());
        return "fpRequested="+requested+", policy="+policy+", render="+reason+", visualTick="+visualTick+", fov="+renderer.fov()+", viewport="+client.getWindow().getFramebufferWidth()+"x"+client.getWindow().getFramebufferHeight()+
            (presenter==null?"":", staticIdle="+presenter.staticIdle()+", skinModel="+client.player.getModel()+", skin="+client.player.getSkinTexture()+", clip="+presenter.action()+", phase="+presenter.phase()+", movementLayer="+presenter.movementLayerEnabled()+", inertiaRequested="+presenter.inertiaRequested()+", inertia="+presenter.inertiaEnabled()+", contactMetres="+presenter.contactMetres()+", contactDegrees="+presenter.contactDegrees()+", minDepth="+presenter.minimumDepth())+", "+resources.status();
    }
}
