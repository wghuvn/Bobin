package com.bobincraft.armament.client.thirdperson.fabric;

import com.bobincraft.armament.client.thirdperson.*;
import com.bobincraft.armament.client.render.fabric.*;
import com.bobincraft.combat.*;
import com.bobincraft.drone.client.DroneClient;
import com.bobincraft.vehicle.client.*;
import com.bobincraft.weapon.grom7.client.Grom7Client;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityPose;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import java.util.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/** Client-local diagnostic only. No items, packets, gameplay state or NPC animation are created. */
public final class ThirdPersonDiagnostic {
    private static ThirdPersonDiagnostic active;
    private final ThirdPersonResources resources;
    private final FabricGlbRenderer renderer;
    private final ThirdPersonSemanticPort semantic;
    private final Map<UUID,Session> sessions=new HashMap<>();
    private boolean requested;
    private ClientWorld world;
    private String result="hidden";
    private static final class Session {
        final ThirdPersonResources.Bundle bundle;
        final ThirdPersonWeaponPresenter presenter;
        ThirdPersonWeaponPresenter.Pose rendered;
        Session(ThirdPersonResources.Bundle bundle){this.bundle=bundle;presenter=new ThirdPersonWeaponPresenter(bundle.cpu());}
    }
    public ThirdPersonDiagnostic(ThirdPersonResources resources,FabricGlbRenderer renderer,ThirdPersonSemanticPort semantic){
        this.resources=resources;this.renderer=renderer;this.semantic=semantic;
    }
    public void register(){
        active=this;
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type,entityRenderer,helper,context)->{
            if(entityRenderer instanceof PlayerEntityRenderer player)helper.register(new ThirdPersonWeaponFeature(player));
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,registry)->{
            var tp=literal("tp")
                .then(literal("show").executes(ctx->{
                    requested=true;
                    if(resources.current("default").isEmpty())resources.reload(MinecraftClient.getInstance().getResourceManager());
                    semantic.bobin$enablePairedPresentation();update();
                    ctx.getSource().sendFeedback(Text.literal(status()));return sessions.isEmpty()?0:1;
                }))
                .then(literal("hide").executes(ctx->{requested=false;sessions.clear();result="hidden";return 1;}))
                .then(literal("status").executes(ctx->{ctx.getSource().sendFeedback(Text.literal(status()));return 1;}));
            var animation=literal("anim");
            for(var action:PresentationAction.Action.values())animation.then(literal(action.key()).executes(ctx->{
                if(!requested){ctx.getSource().sendError(Text.literal("Use /bobin_glb tp show first."));return 0;}
                if(!semantic.bobin$requestPresentation(action.key())){ctx.getSource().sendError(Text.literal(status()));return 0;}
                update();return 1;
            }));
            dispatcher.register(literal("bobin_glb").then(tp.then(animation)));
        });
        ClientTickEvents.END_CLIENT_TICK.register(client->update());
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{requested=false;sessions.clear();world=null;result="disconnected";});
        ClientLifecycleEvents.CLIENT_STOPPING.register(client->{requested=false;sessions.clear();resources.close();active=null;});
    }
    public static boolean paired(){return active!=null&&active.requested;}
    private String policy(AbstractClientPlayerEntity p,boolean drawing){
        var c=MinecraftClient.getInstance();
        var view=new ThirdPersonEligibility.View(
                Boolean.getBoolean("bobin.armament.glbDebug"),requested,p!=null&&p==c.player&&c.world!=null,
                p!=null&&p.isAlive()&&!p.isSpectator()&&!p.isInvisible(),
                p!=null&&c.getCameraEntity()==p,
                drawing&&!c.options.getPerspective().isFirstPerson(),
                p!=null&&p.getMainHandStack().isEmpty()&&p.getOffHandStack().isEmpty(),
                p!=null&&compatible(p),p!=null&&otherOwner(p),p!=null&&(p.hasVehicle()||p.isUsingItem()||p.handSwinging),!c.options.hudHidden);
        return drawing && !view.externalView() ? "first_person" : ThirdPersonEligibility.reason(view);
    }
    private boolean compatible(AbstractClientPlayerEntity p){
        if((p.getPose()!=EntityPose.STANDING&&p.getPose()!=EntityPose.CROUCHING)||p.isSleeping())return false;
        if(p instanceof InjuryCarrier carrier){var i=carrier.bobinInjuries();return i.missingLimbs()==0&&i.severity()==InjurySeverity.NONE&&i.faceSeverity()==0;}
        return true;
    }
    private boolean otherOwner(AbstractClientPlayerEntity p){
        return DroneClient.isActive()||DroneClient.isReturningToPlayer()||ArmorClient.inside()||ArmorClient.thirdPersonTank()||ArmorClient.isClimbingOut(p)||GrenadeClient.ownsHands(p)||Grom7Client.getActiveAnimation(p.getUuid())!=null;
    }
    private void update(){
        try {
            var c=MinecraftClient.getInstance();
            if(c.world!=world){sessions.clear();world=c.world;}
            String policy=policy(c.player,false);
            if(!policy.equals("ready")){sessions.clear();result=policy;return;}
            var action=semantic.bobin$presentationAction();
            if(action==null){sessions.clear();result="semantic_inactive";return;}
            var p=c.player;var bundle=resources.current(p.getModel()).orElse(null);
            if(bundle==null){sessions.clear();result="resources_unavailable";return;}
            sessions.keySet().removeIf(id->!id.equals(p.getUuid()));
            Session session=sessions.get(p.getUuid());
            if(session==null||session.bundle!=bundle){session=new Session(bundle);sessions.put(p.getUuid(),session);}
            double speed=p.getVelocity().horizontalLengthSquared()*400;
            session.presenter.update(action,semantic.bobin$presentationTick()/20.0,speed>.0025,p.isSprinting(),p.isSneaking());
            result="ready";
        }catch(RuntimeException e){disable(e);}
    }
    public static ThirdPersonModelAdapter pose(AbstractClientPlayerEntity player,PlayerEntityModel<?> model,float delta,VertexConsumerProvider consumers,MatrixStack matrices){
        if(active==null||!active.requested)return null;
        return active.prepare(player,model,delta,consumers,matrices);
    }
    private ThirdPersonModelAdapter prepare(AbstractClientPlayerEntity player,PlayerEntityModel<?> model,float delta,VertexConsumerProvider consumers,MatrixStack matrices){
        if(!policy(player,true).equals("ready")||!renderer.hasShader()||!(consumers instanceof VertexConsumerProvider.Immediate))return null;
        update();var session=sessions.get(player.getUuid());if(session==null)return null;
        try {
            double now=(semantic.bobin$presentationTick()+(MinecraftClient.getInstance().isPaused()?0:delta))/20.0;
            var action=semantic.bobin$presentationAction();
            double follow=action.action()==PresentationAction.Action.INSPECT||action.action()==PresentationAction.Action.LOWER?0:1;
            var basis=matrices.peek().getPositionMatrix();
            double scale=Math.sqrt(basis.m00()*basis.m00()+basis.m01()*basis.m01()+basis.m02()*basis.m02());
            session.rendered=session.presenter.sample(now,-Math.toDegrees(model.head.yaw)*follow,-Math.toDegrees(model.head.pitch)*follow,1/scale);
            return new ThirdPersonModelAdapter(model,session.rendered);
        }catch(RuntimeException e){disable(e);return null;}
    }
    public static void render(AbstractClientPlayerEntity player,MatrixStack matrices,VertexConsumerProvider consumers,int light){
        if(active!=null)active.draw(player,matrices,consumers,light);
    }
    private void draw(AbstractClientPlayerEntity player,MatrixStack matrices,VertexConsumerProvider consumers,int light){
        if(!policy(player,true).equals("ready")||!renderer.hasShader()||!(consumers instanceof VertexConsumerProvider.Immediate immediate))return;
        var session=sessions.get(player.getUuid());if(session==null||session.rendered==null)return;
        try {
            // Entity transform carries world position, interpolated body yaw and model placement.
            // diag(-1,-1,1) converts glTF axes; it has determinant +1 and never changes scale.
            var pose=session.rendered;
            var placement=new Matrix4f();float[] values=new float[16];
            for(int i=0;i<16;i++)values[i]=(float)pose.placement()[i];placement.set(values);
            var view=new Matrix4f(matrices.peek().getPositionMatrix()).scale(-1,-1,1).mul(placement);
            var projection=new Matrix4f(RenderSystem.getProjectionMatrix()).mul(view);
            var normalWorld=new Matrix4f().rotation(MinecraftClient.getInstance().gameRenderer.getCamera().getRotation()).mul(view);
            var normals=new Matrix3f(normalWorld).invert().transpose();
            var camera=new Matrix4f(view).invert().transformPosition(new Vector3f());
            immediate.draw();
            if(Tessellator.getInstance().getBuffer().isBuilding()){result="buffer_busy";return;}
            renderer.render(session.bundle.cpu().model(),pose.geometry(),new GlbRenderContext(projection,light,
                    new Vec3d(camera.x,camera.y,camera.z),session.bundle.gpu(),normals));
            result="rendered";
        }catch(RuntimeException e){disable(e);}
    }
    private void disable(RuntimeException e){requested=false;sessions.clear();result="evaluation_error: "+e.getMessage();org.slf4j.LoggerFactory.getLogger("bobin_craft/armament_tp").warn("Only the third-person diagnostic was disabled",e);}
    public String status(){
        var action=semantic.bobin$presentationAction();
        var c=MinecraftClient.getInstance();
        var s=c.player==null?null:sessions.get(c.player.getUuid());
        return "tpRequested="+requested+", policy="+policy(c.player,false)+", render="+result+", action="+(action==null?"none":action.action().key())+
                ", started="+(action==null?0:action.started())+", visualTick="+semantic.bobin$presentationTick()+", instances="+sessions.size()+
                ", network=local_only, arms=vanilla_rigid, "+resources.status()+
                (s==null||s.rendered==null?"":", contactMetres="+s.rendered.contactError()+", blendCorrectionMetres="+s.rendered.correctionMetres());
    }
}
