package com.bobincraft.vehicle.client;

import com.bobincraft.camera.BobinCameraCollision;
import com.bobincraft.vehicle.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.*;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** War Thunder single-player driver+gunner controls, scroll-wheel 3rd-person orbit camera, RMB optical sight, and Enlisted-style Ctrl climb-out camera & animation. */
public final class ArmorClient {
    public static final float MIN_THIRD_PERSON_DIST = 2.4f;
    public static final float DEFAULT_THIRD_PERSON_DIST = 7.6f;
    public static final float MAX_THIRD_PERSON_DIST = 16.0f;
    // viewMode: 0 = driver periscope, 1 = gun optical sight, 2 = War Thunder 3rd-person orbit, 3 = cabin 1st-person look
    private static int viewMode = 2;
    private static float thirdPersonDistance = DEFAULT_THIRD_PERSON_DIST;
    private static float targetThirdPersonDistance = DEFAULT_THIRD_PERSON_DIST;
    private static int lockedHotbarSlot = -1;
    private static int lastSeat = -1;
    private static int lastReloadTicks = 0;
    private static float shotRecoilTicks = 0f;
    private static ArmoredVehicleEntity mounted;
    private static Perspective previousPerspective;
    private static boolean wasClimbingOut;
    private static float climbStartYaw;
    private static boolean viewHeld;
    private static boolean useHeld;
    private static int hintsUntil;
    private static final Map<UUID,ArmorEngineSound> motors=new HashMap<>(),tracks=new HashMap<>();
    private static net.minecraft.client.world.ClientWorld soundWorld;
    private ArmorClient() { }

    public static ArmoredVehicleEntity station(){var c=MinecraftClient.getInstance();return c.player!=null&&c.player.getVehicle() instanceof ArmoredVehicleEntity v?v:null;}
    public static boolean thirdPersonTank(){return station()!=null&&viewMode==2;}
    public static boolean inside(){return station()!=null&&viewMode!=2;}
    public static boolean optics(){return station()!=null&&viewMode==1;}
    public static boolean periscope(){return station()!=null&&viewMode==0;}
    public static float thirdPersonDistance(){return thirdPersonDistance;}
    public static boolean isClimbingOut(Entity entity){
        return entity instanceof PlayerEntity p&&ArmoredVehicleEntity.activeClimbOutProgress(p,0f)>=0f;
    }

    private static float smoothstep(float t){
        t=MathHelper.clamp(t,0f,1f);
        return t*t*(3f-2f*t);
    }

    private static ArmoredVehicleEntity findClimbOutVehicle(PlayerEntity player){
        if(player==null||player.getWorld()==null)return null;
        for(var e:player.getWorld().getOtherEntities(player,player.getBoundingBox().expand(4.5),e->e instanceof ArmoredVehicleEntity&&e.isAlive())){
            ArmoredVehicleEntity v=(ArmoredVehicleEntity)e;
            if(v.climbOutProgress(player,0f)>=0f)return v;
        }
        return null;
    }

    /** Enlisted-style smooth camera yaw during climb-out: aligns smoothly with the turret roof direction. */
    public static float climbOutCameraYaw(PlayerEntity player,float delta){
        float p=MathHelper.clamp(ArmoredVehicleEntity.activeClimbOutProgress(player,delta),0f,1f);
        ArmoredVehicleEntity v=findClimbOutVehicle(player);
        float targetYaw=v!=null?MathHelper.lerpAngleDegrees(delta,v.previousTurret,v.turretYaw()):player.getYaw(delta);
        float s=smoothstep(Math.min(1f,p/0.65f));
        return MathHelper.lerpAngleDegrees(s,climbStartYaw,targetYaw);
    }

    /** Enlisted-style smooth camera pitch during climb-out: looks down at the hatch grip (26 deg), then levels out (6 deg) as entering 1st person. */
    public static float climbOutCameraPitch(PlayerEntity player,float delta){
        float p=MathHelper.clamp(ArmoredVehicleEntity.activeClimbOutProgress(player,delta),0f,1f);
        if(p<=0.60f){
            return MathHelper.lerp(smoothstep(p/0.60f),28f,20f);
        }
        return MathHelper.lerp(smoothstep((p-0.60f)/0.40f),20f,6f);
    }

    /**
     * Enlisted-style smooth camera zoom during climb-out:
     * Glides from a 3.4m exterior roof overview toward the climbing soldier's shoulders (1.20m at p=0.65),
     * then zooms smoothly right into the soldier's first-person eye point (0.04m at p=1.0) on the turret roof.
     */
    public static Vec3d climbOutCameraPos(BlockView area,PlayerEntity player,float delta){
        float p=MathHelper.clamp(ArmoredVehicleEntity.activeClimbOutProgress(player,delta),0f,1f);
        ArmoredVehicleEntity v=findClimbOutVehicle(player);
        Vec3d lerpedFeet=player.getLerpedPos(delta);
        double eyeHeight=player.getStandingEyeHeight()*MathHelper.lerp(smoothstep(p),0.72,1.0);
        Vec3d eye=lerpedFeet.add(0,eyeHeight,0);

        float yaw=climbOutCameraYaw(player,delta);
        float pitch=climbOutCameraPitch(player,delta);
        double yr=Math.toRadians(yaw),pr=Math.toRadians(pitch);
        double cp=Math.cos(pr),sp=Math.sin(pr),sy=Math.sin(yr),cy=Math.cos(yr);

        float dist;
        double liftY;
        if(p<=0.65f){
            float s=smoothstep(p/0.65f);
            dist=MathHelper.lerp(s,3.4f,1.20f);
            liftY=MathHelper.lerp(s,0.65,0.18);
        }else{
            float s=smoothstep((p-0.65f)/0.35f);
            dist=MathHelper.lerp(s,1.20f,0.04f);
            liftY=MathHelper.lerp(s,0.18,0.0);
        }
        Vec3d desired=new Vec3d(
            eye.x+sy*cp*dist,
            eye.y+sp*dist+liftY,
            eye.z-cy*cp*dist
        );
        return BobinCameraCollision.clipTankThirdPerson(area,v,eye,desired);
    }

    public static void pollHotbarScrollZoom(PlayerEntity player){
        if(player==null||!thirdPersonTank())return;
        var inv=player.getInventory();
        var opts=MinecraftClient.getInstance().options;
        for(int i=0;i<9;i++){
            if(opts.hotbarKeys[i].isPressed()){
                lockedHotbarSlot=inv.selectedSlot;
                return;
            }
        }
        if(lockedHotbarSlot<0||lockedHotbarSlot>8){
            lockedHotbarSlot=inv.selectedSlot;
            return;
        }
        if(inv.selectedSlot!=lockedHotbarSlot){
            int diff=inv.selectedSlot-lockedHotbarSlot;
            if(diff>4)diff-=9;
            else if(diff<-4)diff+=9;
            targetThirdPersonDistance=MathHelper.clamp(targetThirdPersonDistance+diff*.95f,MIN_THIRD_PERSON_DIST,MAX_THIRD_PERSON_DIST);
            inv.selectedSlot=lockedHotbarSlot;
        }
        thirdPersonDistance=MathHelper.lerp(.28f,thirdPersonDistance,targetThirdPersonDistance);
    }

    private static float rad(double deg){return (float)Math.toRadians(deg);}

    /**
     * War Thunder orbit pivot high above the turret roof (y = 3.38..3.78m above track base, well above the 2.81m roof armor).
     * When zoomed in with the scroll wheel, the pivot shifts smoothly toward the commander cupola roof station.
     */
    public static Vec3d thirdPersonPivot(ArmoredVehicleEntity v,float dist,float delta){
        Vec3d pos=v.getLerpedPos(delta);
        float hullYaw=MathHelper.lerpAngleDegrees(delta,v.prevYaw,v.getYaw());
        float pitch=MathHelper.lerp(delta,v.previousSuspension,v.suspension());
        float roll=MathHelper.lerp(delta,v.previousBank,v.bank());
        float zoomT=MathHelper.clamp((dist-MIN_THIRD_PERSON_DIST)/(MAX_THIRD_PERSON_DIST-MIN_THIRD_PERSON_DIST),0f,1f);
        double heightAboveTurretBase=1.55+0.40*zoomT;
        Vec3d roofAnchor=ArmorGeometry.TURRET_PIVOT.add(0,heightAboveTurretBase,0)
                .rotateZ(rad(roll)).rotateX(rad(-pitch)).rotateY(rad(180-hullYaw));
        return pos.add(roofAnchor);
    }

    public static Vec3d thirdPersonPivot(ArmoredVehicleEntity v,float delta){
        return thirdPersonPivot(v,thirdPersonDistance,delta);
    }

    /**
     * Computes the exterior War Thunder third-person camera position behind and above the turret roof,
     * clipping only against world terrain and other vehicles while completely ignoring the ridden tank.
     */
    public static Vec3d thirdPersonCameraPos(BlockView area,ArmoredVehicleEntity v,float yaw,float pitch,float dist,float delta){
        float clampedPitch=MathHelper.clamp(pitch,-32f,62f);
        float clampedDist=MathHelper.clamp(dist,MIN_THIRD_PERSON_DIST,MAX_THIRD_PERSON_DIST);
        Vec3d pivot=thirdPersonPivot(v,clampedDist,delta);
        float zoomT=MathHelper.clamp((clampedDist-MIN_THIRD_PERSON_DIST)/(MAX_THIRD_PERSON_DIST-MIN_THIRD_PERSON_DIST),0f,1f);
        double pr=Math.toRadians(clampedPitch),yr=Math.toRadians(yaw);
        double cp=Math.cos(pr),sp=Math.sin(pr),sy=Math.sin(yr),cy=Math.cos(yr);
        double liftY=0.22+0.38*zoomT;
        double verticalOffset=(sp<0?sp*0.42:sp)*clampedDist+liftY;
        double minExteriorY=v.getLerpedPos(delta).y+2.35;
        Vec3d desired=new Vec3d(
                pivot.x+sy*cp*clampedDist,
                Math.max(minExteriorY,pivot.y+verticalOffset),
                pivot.z-cy*cp*clampedDist
        );
        return BobinCameraCollision.clipTankThirdPerson(area,v,pivot,desired);
    }

    public static float shotShakePitch(float delta){
        if(shotRecoilTicks<=0f)return 0f;
        float t=Math.max(0f,shotRecoilTicks-delta)/10f;
        return (float)(Math.sin(t*Math.PI)*2.6f*t);
    }

    public static Vec3d cameraPosition(float delta){
        var c=MinecraftClient.getInstance();var v=station();if(v==null||c.player==null)return null;
        float yaw=MathHelper.lerpAngleDegrees(delta,v.previousTurret,v.turretYaw()),hullYaw=MathHelper.lerpAngleDegrees(delta,v.prevYaw,v.getYaw());
        float pitch=MathHelper.lerp(delta,v.previousSuspension,v.suspension()),roll=MathHelper.lerp(delta,v.previousBank,v.bank());
        float elev=MathHelper.lerp(delta,v.previousElevation,v.elevation());
        Vec3d pos=v.getLerpedPos(delta);
        if(viewMode==0)return pos.add(ArmorGeometry.DRIVER_VIEW.rotateZ(rad(roll)).rotateX(rad(-pitch)).rotateY(rad(180-hullYaw)));
        if(viewMode==1){
            Vec3d pivot=ArmorGeometry.GUN_PIVOT.rotateY(rad(hullYaw-yaw)).add(ArmorGeometry.TURRET_PIVOT).rotateZ(rad(roll)).rotateX(rad(-pitch)).rotateY(rad(180-hullYaw)).add(pos);
            double recoilBack=shotRecoilTicks>0?(shotRecoilTicks/10.0)*.14:0.0;
            return pivot.add(Vec3d.fromPolar(elev,yaw).multiply(.30-recoilBack));
        }
        int seat=v.seatFor(c.player);
        Vec3d localEye=seat==0?new Vec3d(.68,.36,.18):new Vec3d(-.68,.36,-.10);
        return localEye.rotateY(rad(hullYaw-yaw)).add(ArmorGeometry.TURRET_PIVOT).rotateZ(rad(roll)).rotateX(rad(-pitch)).rotateY(rad(180-hullYaw)).add(pos);
    }

    public static float cameraYaw(float delta){
        var v=station();
        if(viewMode==0)return MathHelper.lerpAngleDegrees(delta,v.prevYaw,v.getYaw());
        if(viewMode==1)return MathHelper.lerpAngleDegrees(delta,v.previousTurret,v.turretYaw());
        return MinecraftClient.getInstance().player.getYaw(delta);
    }

    public static float cameraPitch(float delta){
        var v=station();
        float shake=shotShakePitch(delta);
        if(viewMode==0)return -MathHelper.lerp(delta,v.previousSuspension,v.suspension())-shake*.5f;
        if(viewMode==1)return MathHelper.lerp(delta,v.previousElevation,v.elevation())-shake;
        return MathHelper.clamp(MinecraftClient.getInstance().player.getPitch(delta)-shake,-32f,62f);
    }

    /** Parallax-compensated War Thunder aim angles from the cannon trunnion toward the 3D point under the camera's center circle. */
    private static float[] computeAimAngles(MinecraftClient client,ArmoredVehicleEntity vehicle){
        float rawYaw=MathHelper.wrapDegrees(client.player.getYaw());
        float rawPitch=MathHelper.clamp(client.player.getPitch(),-28f,vehicle.fixed()?6f:12f);
        if(viewMode!=2||client.world==null)return new float[]{rawYaw,rawPitch};
        float camPitch=MathHelper.clamp(client.player.getPitch(),-32f,62f);
        Vec3d camOrigin=thirdPersonCameraPos(client.world,vehicle,rawYaw,camPitch,thirdPersonDistance,1.0f);
        Vec3d lookDir=Vec3d.fromPolar(camPitch,rawYaw);
        Vec3d rayStart=camOrigin.add(lookDir.multiply(Math.max(1.5,thirdPersonDistance-0.5)));
        Vec3d farPoint=camOrigin.add(lookDir.multiply(120.0));
        var hit=client.world.raycast(new RaycastContext(rayStart,farPoint,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,vehicle));
        Vec3d target=hit.getType()!=HitResult.Type.MISS?hit.getPos():farPoint;
        Vec3d toTarget=target.subtract(vehicle.muzzleBase());
        double horiz=Math.hypot(toTarget.x,toTarget.z);
        if(horiz<2.5)return new float[]{rawYaw,rawPitch};
        double flight=horiz/ArmorControls.SHELL_SPEED;
        float aimYaw=(float)Math.toDegrees(Math.atan2(-toTarget.x,toTarget.z));
        float aimPitch=(float)-Math.toDegrees(Math.atan2(toTarget.y+ArmorControls.SHELL_GRAVITY*.35*flight*flight,horiz));
        return new float[]{MathHelper.wrapDegrees(aimYaw),MathHelper.clamp(aimPitch,-28f,vehicle.fixed()?6f:12f)};
    }

    private static void drawAimCircle(DrawContext ctx,int cx,int cy,int r,int color){
        int span=Math.max(2,Math.round(r*0.42f));
        ctx.fill(cx-span,cy-r,cx+span+1,cy-r+1,color);
        ctx.fill(cx-span,cy+r,cx+span+1,cy+r+1,color);
        ctx.fill(cx-r,cy-span,cx-r+1,cy+span+1,color);
        ctx.fill(cx+r,cy-span,cx+r+1,cy+span+1,color);
        for(int d=1;d<=r-span-1;d++){
            int px=span+d,py=r-d;
            ctx.fill(cx+px,cy-py,cx+px+1,cy-py+1,color);
            ctx.fill(cx-px,cy-py,cx-px+1,cy-py+1,color);
            ctx.fill(cx+px,cy+py,cx+px+1,cy+py+1,color);
            ctx.fill(cx-px,cy+py,cx-px+1,cy+py+1,color);
        }
    }

    private static void drawTankDoll(DrawContext ctx,ArmoredVehicleEntity v,float camYaw,float delta,int x0,int y0){
        int size=42,cx=x0+size/2,cy=y0+size/2;
        ctx.fill(x0,y0,x0+size,y0+size,0xB0121612);
        ctx.fill(x0,y0,x0+size,y0+1,0x806A7862);
        ctx.fill(x0,y0+size-1,x0+size,y0+size,0x806A7862);
        ctx.fill(x0,y0,x0+1,y0+size,0x806A7862);
        ctx.fill(x0+size-1,y0,x0+size,y0+size,0x806A7862);
        ctx.fill(cx,y0+2,cx+1,y0+6,0xD0DCE2C8);

        float hullRel=(float)Math.toRadians(MathHelper.lerpAngleDegrees(delta,v.prevYaw,v.getYaw())-camYaw);
        float turRel=(float)Math.toRadians(MathHelper.lerpAngleDegrees(delta,v.previousTurret,v.turretYaw())-camYaw);
        double hc=Math.cos(hullRel),hs=Math.sin(hullRel);
        for(int hz=-10;hz<=10;hz+=2){
            for(int hx=-6;hx<=6;hx+=3){
                if(Math.abs(hx)<6&&Math.abs(hz)<10)continue;
                int px=cx+Math.round((float)(hx*hc+hz*hs));
                int py=cy+Math.round((float)(-hz*hc+hx*hs));
                ctx.fill(px,py,px+1,py+1,0xC07CA872);
            }
        }
        int hfx=cx+Math.round((float)(11*hs)),hfy=cy+Math.round((float)(-11*hc));
        ctx.fill(hfx-1,hfy-1,hfx+2,hfy+2,0xFFE2D888);

        double tc=Math.cos(turRel),ts=Math.sin(turRel);
        ctx.fill(cx-3,cy-3,cx+4,cy+4,0xD08CC284);
        for(int r=3;r<=16;r+=2){
            int gx=cx+Math.round((float)(r*ts));
            int gy=cy+Math.round((float)(-r*tc));
            ctx.fill(gx,gy,gx+1,gy+1,0xFF98F28E);
        }
    }

    /** Procedural 3-phase climb-out animation: walk/reach up -> grip hatch coaming rim with both hands & pull up -> mantle onto roof. */
    public static void poseClimbOut(PlayerEntity player,PlayerEntityModel<?> model,float tickDelta){
        float p=ArmoredVehicleEntity.activeClimbOutProgress(player,tickDelta);
        if(p<0f)return;
        if(p<=.28f){
            float t=p/.28f;
            float s=t*t*(3f-2f*t);
            model.head.pitch=MathHelper.lerp(s,model.head.pitch,-0.75f);
            model.head.yaw=0f;
            model.body.pitch=MathHelper.lerp(s,0f,0.18f);
            model.body.pivotY=MathHelper.lerp(s,0f,1.8f);
            model.leftArm.pitch=MathHelper.lerp(s,-0.6f,-2.85f);
            model.rightArm.pitch=MathHelper.lerp(s,-0.6f,-2.85f);
            model.leftArm.yaw=MathHelper.lerp(s,0f,0.18f);
            model.rightArm.yaw=MathHelper.lerp(s,0f,-0.18f);
            model.leftArm.roll=MathHelper.lerp(s,0f,-0.12f);
            model.rightArm.roll=MathHelper.lerp(s,0f,0.12f);
            model.leftLeg.pitch=MathHelper.lerp(s,0.35f,-0.55f);
            model.rightLeg.pitch=MathHelper.lerp(s,-0.35f,0.25f);
        }else if(p<=.72f){
            float t=(p-.28f)/.44f;
            float s=t*t*(3f-2f*t);
            model.head.pitch=MathHelper.lerp(s,-0.75f,0.15f);
            model.head.yaw=0f;
            model.body.pitch=MathHelper.lerp(s,0.18f,0.42f);
            model.body.pivotY=MathHelper.lerp(s,1.8f,0.6f);
            float pullArmPitch=MathHelper.lerp(s,-2.85f,-0.55f);
            model.leftArm.pitch=pullArmPitch;
            model.rightArm.pitch=pullArmPitch-0.12f*(float)Math.sin(t*Math.PI);
            model.leftArm.roll=MathHelper.lerp(s,-0.12f,-0.38f);
            model.rightArm.roll=MathHelper.lerp(s,0.12f,0.38f);
            model.leftArm.yaw=0.22f;
            model.rightArm.yaw=-0.22f;
            float kneeCycle=(float)Math.sin(t*Math.PI);
            model.leftLeg.pitch=-0.55f-0.65f*kneeCycle;
            model.rightLeg.pitch=0.15f-0.85f*kneeCycle;
            model.leftLeg.pivotY=12f-2.2f*kneeCycle;
            model.rightLeg.pivotY=12f-1.8f*kneeCycle;
        }else{
            float t=(p-.72f)/.28f;
            float s=t*t*(3f-2f*t);
            model.head.pitch=MathHelper.lerp(s,0.15f,0f);
            model.head.yaw=0f;
            model.body.pitch=MathHelper.lerp(s,0.42f,0f);
            model.body.pivotY=MathHelper.lerp(s,0.6f,0f);
            model.leftArm.pitch=MathHelper.lerp(s,-0.55f,0f);
            model.rightArm.pitch=MathHelper.lerp(s,-0.55f,0f);
            model.leftArm.roll=MathHelper.lerp(s,-0.38f,0f);
            model.rightArm.roll=MathHelper.lerp(s,0.38f,0f);
            model.leftArm.yaw=MathHelper.lerp(s,0.22f,0f);
            model.rightArm.yaw=MathHelper.lerp(s,-0.22f,0f);
            model.leftLeg.pitch=MathHelper.lerp(s,-0.75f,0f);
            model.rightLeg.pitch=MathHelper.lerp(s,-0.35f,0f);
            model.leftLeg.pivotY=MathHelper.lerp(s,10.2f,12f);
            model.rightLeg.pivotY=MathHelper.lerp(s,10.6f,12f);
        }
        model.hat.copyTransform(model.head);
    }

    public static void register(){
        EntityRendererRegistry.register(ArmorContent.VEHICLE,ArmoredVehicleRenderer::new);
        EntityRendererRegistry.register(ArmorContent.PROJECTILE,ArmorShellRenderer::new);
        var fire=key("cannon",GLFW.GLFW_KEY_V);
        var view=key("armor_view",GLFW.GLFW_KEY_C);
        var hatch=key("armor_hatch",GLFW.GLFW_KEY_H);
        var exit=key("armor_exit",GLFW.GLFW_KEY_LEFT_CONTROL);
        var reload=key("armor_reload",GLFW.GLFW_KEY_R);

        ClientPlayNetworking.registerGlobalReceiver(ArmorContent.CLIMB_OUT,(client,handler,buf,sender)->{
            if(buf.readableBytes()<28)return;
            int id=buf.readInt();UUID playerUuid=buf.readUuid();
            Vec3d startLocal=new Vec3d(buf.readFloat(),buf.readFloat(),buf.readFloat());
            client.execute(()->{
                if(client.world!=null&&client.world.getEntityById(id) instanceof ArmoredVehicleEntity v){
                    if(client.player!=null&&client.player.getUuid().equals(playerUuid)){
                        climbStartYaw=client.player.getYaw();
                        wasClimbingOut=true;
                    }
                    v.startClimbOutClient(playerUuid,startLocal);
                }
            });
        });

        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(shotRecoilTicks>0f)shotRecoilTicks=Math.max(0f,shotRecoilTicks-1f);
            boolean nowClimbingOut=client.player!=null&&isClimbingOut(client.player);
            if(wasClimbingOut&&!nowClimbingOut&&client.player!=null){
                wasClimbingOut=false;
                client.options.setPerspective(previousPerspective!=null?previousPerspective:Perspective.FIRST_PERSON);
                client.player.setPitch(6f);
            }else if(nowClimbingOut){
                wasClimbingOut=true;
            }
            var vehicle=station();
            if(vehicle!=mounted){
                if(mounted!=null&&previousPerspective!=null&&!nowClimbingOut){
                    client.options.setPerspective(previousPerspective);
                }
                mounted=vehicle;viewMode=2;viewHeld=false;lastSeat=-1;lastReloadTicks=0;lockedHotbarSlot=-1;
                if(vehicle!=null&&client.player!=null){
                    previousPerspective=client.options.getPerspective();
                    client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                    lockedHotbarSlot=client.player.getInventory().selectedSlot;
                    client.mouse.lockCursor();
                    hintsUntil=client.player.age+180;
                    lastSeat=vehicle.seatFor(client.player);
                    client.player.setYaw(vehicle.turretYaw());
                    client.player.setPitch(MathHelper.clamp(vehicle.elevation()+6f,-20f,25f));
                }
            }
            sounds(client);
            boolean pressed=false;while(view.wasPressed())pressed=true;
            boolean exitPressed=false;while(exit.wasPressed())exitPressed=true;
            while(hatch.wasPressed())if(vehicle!=null&&client.currentScreen==null&&client.isWindowFocused()&&ClientPlayNetworking.canSend(ArmorContent.HATCH))ClientPlayNetworking.send(ArmorContent.HATCH,PacketByteBufs.create());
            while(reload.wasPressed())requestReload();
            boolean active=client.currentScreen==null&&client.isWindowFocused();

            // Left Ctrl quick climb-out from either a seated station or inside the cabin
            if(exitPressed&&active&&client.player!=null&&client.world!=null&&ClientPlayNetworking.canSend(ArmorContent.EXIT)){
                ArmoredVehicleEntity targetVehicle=vehicle;
                if(targetVehicle==null){
                    for(var e:client.world.getEntities())if(e instanceof ArmoredVehicleEntity t&&t.isAlive()&&t.isInCabin(client.player)){targetVehicle=t;break;}
                }
                if(targetVehicle!=null){
                    climbStartYaw=vehicle!=null?cameraYaw(1.0f):client.player.getYaw();
                    wasClimbingOut=true;
                    if(previousPerspective==null)previousPerspective=Perspective.FIRST_PERSON;
                    Vec3d startLocal=ArmorGeometry.toLocal(targetVehicle,"turret",client.player.getPos());
                    startLocal=new Vec3d(MathHelper.clamp(startLocal.x,-.95,.95),MathHelper.clamp(startLocal.y,-1.04,-.72),MathHelper.clamp(startLocal.z,-.65,.65));
                    targetVehicle.startClimbOutClient(client.player.getUuid(),startLocal);
                    client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                    var buf=PacketByteBufs.create();buf.writeInt(targetVehicle.getId());
                    ClientPlayNetworking.send(ArmorContent.EXIT,buf);
                    return;
                }
            }

            boolean rightDown=active&&GLFW.glfwGetMouseButton(client.getWindow().getHandle(),GLFW.GLFW_MOUSE_BUTTON_RIGHT)==GLFW.GLFW_PRESS;
            if(vehicle==null||client.player==null){
                viewHeld=false;
                if(rightDown&&!useHeld&&client.player!=null&&client.world!=null&&!client.player.isSneaking()&&!nowClimbingOut){
                    for(var e:client.world.getEntities())if(e instanceof ArmoredVehicleEntity t&&t.isAlive()&&t.hasFreeSeat()&&t.canBoard(client.player)){
                        if(!(client.player.getMainHandStack().getItem() instanceof HandGrenadeItem)){
                            client.interactionManager.interactEntity(client.player,t,Hand.MAIN_HAND);break;
                        }
                    }
                }
                useHeld=rightDown;
                return;
            }

            pollHotbarScrollZoom(client.player);
            int curReload=vehicle.reloadTicks();
            if(curReload>lastReloadTicks&&curReload>=ArmorControls.RELOAD_TICKS-4){
                shotRecoilTicks=10f;
            }
            lastReloadTicks=curReload;

            int seat=vehicle.seatFor(client.player);
            boolean solo=vehicle.secondPassenger()==null;
            if(seat!=lastSeat){
                lastSeat=seat;
                if(!solo&&viewMode!=2&&viewMode!=3)viewMode=seat==0?0:1;
            }

            // ПКМ (Right Mouse Button) toggles between War Thunder 3rd-Person Orbit (2) and Gunner Optical Sight (1)
            if(rightDown&&!useHeld){
                int targetOptic=(!solo&&seat==0)?0:1;
                viewMode=(viewMode==targetOptic)?2:targetOptic;
                if(viewMode==2){
                    client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                    lockedHotbarSlot=client.player.getInventory().selectedSlot;
                }else{
                    client.options.setPerspective(Perspective.FIRST_PERSON);
                    if(targetOptic==1){client.player.setYaw(vehicle.turretYaw());client.player.setPitch(vehicle.elevation());}
                    else{client.player.setYaw(vehicle.getYaw());client.player.setPitch(0);}
                }
                client.mouse.lockCursor();hintsUntil=client.player.age+80;
            }
            useHeld=rightDown;

            // C key: switches seat for 2-player crew, or cycles view modes (3rd-person -> Gun Sight -> Periscope -> Cabin) when solo
            var bound=KeyBindingHelper.getBoundKeyOf(view);
            boolean held=active&&(view.isPressed()||bound.getCategory()==InputUtil.Type.KEYSYM&&InputUtil.isKeyPressed(client.getWindow().getHandle(),bound.getCode()));
            boolean cTriggered=active&&(pressed||held&&!viewHeld);
            viewHeld=held;
            boolean wantSwitchSeat=false;
            if(cTriggered){
                if(!solo){
                    wantSwitchSeat=true;
                }else{
                    viewMode=(viewMode==2)?1:(viewMode==1)?0:(viewMode==0)?3:2;
                    client.options.setPerspective(viewMode==2?Perspective.THIRD_PERSON_BACK:Perspective.FIRST_PERSON);
                    if(viewMode==2)lockedHotbarSlot=client.player.getInventory().selectedSlot;
                }
            }

            if(!wantSwitchSeat&&(client.player.age%2!=0||!ClientPlayNetworking.canSend(ArmorContent.INPUT)))return;
            if(!ClientPlayNetworking.canSend(ArmorContent.INPUT))return;
            var o=client.options;
            boolean canDrive=solo||seat==0;
            boolean canGun=solo||seat==1;
            float forward=(active&&canDrive)?(o.forwardKey.isPressed()?1:0)-(o.backKey.isPressed()?1:0):0;
            float steer=(active&&canDrive)?(o.rightKey.isPressed()?1:0)-(o.leftKey.isPressed()?1:0):0;
            float[] aim=canGun?computeAimAngles(client,vehicle):new float[]{vehicle.turretYaw(),vehicle.elevation()};
            float aimYaw=aim[0],aimPitch=aim[1];
            if(wantSwitchSeat)aimYaw+=36000f;
            boolean firing=active&&canGun&&(fire.isPressed()||o.attackKey.isPressed());
            if(firing&&vehicle.chambered()&&vehicle.ammo()>0&&shotRecoilTicks<=0f
                &&ArmorControls.fireReady(true,vehicle.ammo(),vehicle.reloadTicks(),MathHelper.wrapDegrees(aimYaw-vehicle.turretYaw()),aimPitch-vehicle.elevation())){
                shotRecoilTicks=10f;
            }
            var buf=PacketByteBufs.create();buf.writeFloat(forward);buf.writeFloat(steer);buf.writeFloat(aimYaw);buf.writeFloat(aimPitch);buf.writeBoolean(firing);ClientPlayNetworking.send(ArmorContent.INPUT,buf);
        });

        HudRenderCallback.EVENT.register((context,delta)->{
            var c=MinecraftClient.getInstance();var v=station();if(v==null||c.player==null||c.options.hudHidden)return;
            int w=context.getScaledWindowWidth(),h=context.getScaledWindowHeight(),mx=w/2,my=h/2;
            if(optics()){
                int edge=w/7;context.fill(0,0,edge,h,0xFF111613);context.fill(w-edge,0,w,h,0xFF111613);context.fill(edge,0,w-edge,h/7,0xFF111613);context.fill(edge,h*6/7,w-edge,h,0xFF111613);
                int color=0xC0B7C39A;context.fill(mx-35,my,mx-6,my+1,color);context.fill(mx+6,my,mx+36,my+1,color);context.fill(mx,my-28,mx+1,my-5,color);context.fill(mx,my+5,mx+1,my+29,color);
                for(int i=1;i<=3;i++)context.fill(mx-i*4,my+i*9,mx+i*4+1,my+i*9+1,color);
            }else if(periscope()){
                int edge=w/9;context.fill(0,0,edge,h,0xFF151A17);context.fill(w-edge,0,w,h,0xFF151A17);context.fill(edge,0,w-edge,h/5,0xFF151A17);context.fill(edge,h*4/5,w-edge,h,0xFF111613);
            }else if(thirdPersonTank()){
                drawAimCircle(context,mx,my,11,0xB8F0F4E8);

                float camYaw=cameraYaw(delta),camPitch=cameraPitch(delta);
                float gunYaw=MathHelper.lerpAngleDegrees(delta,v.previousTurret,v.turretYaw());
                float gunPitch=MathHelper.lerp(delta,v.previousElevation,v.elevation());
                int bx=mx,by=my;
                if(c.world!=null){
                    Vec3d camPos=thirdPersonCameraPos(c.world,v,camYaw,camPitch,thirdPersonDistance,delta);
                    Vec3d muzzle=v.muzzleBase();
                    Vec3d gunDir=Vec3d.fromPolar(gunPitch,gunYaw);
                    Vec3d gunEnd=muzzle.add(gunDir.multiply(96.0));
                    var gunHit=c.world.raycast(new RaycastContext(muzzle.add(gunDir.multiply(ArmorGeometry.BARREL_LENGTH)),gunEnd,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,v));
                    Vec3d gunPoint=gunHit.getType()!=HitResult.Type.MISS?gunHit.getPos():muzzle.add(gunDir.multiply(55.0));
                    Vec3d rel=gunPoint.subtract(camPos);
                    double horiz=Math.hypot(rel.x,rel.z);
                    if(horiz>0.5){
                        float targetYaw=(float)Math.toDegrees(Math.atan2(-rel.x,rel.z));
                        float targetPitch=(float)-Math.toDegrees(Math.atan2(rel.y,horiz));
                        float dYaw=MathHelper.wrapDegrees(targetYaw-camYaw);
                        float dPitch=targetPitch-camPitch;
                        float pxPerDeg=h/70.0f;
                        bx=MathHelper.clamp(mx+Math.round(dYaw*pxPerDeg),24,w-24);
                        by=MathHelper.clamp(my+Math.round(dPitch*pxPerDeg),24,h-24);
                    }
                }
                boolean ready=v.chambered()&&v.reloadTicks()==0&&v.ammo()>0;
                boolean aligned=Math.hypot(bx-mx,by-my)<=6.0;
                int boreColor=!ready?0xE8F04848:(aligned?0xE858F268:0xE0F0C850);
                context.fill(bx-7,by,bx-2,by+1,boreColor);context.fill(bx+3,by,bx+8,by+1,boreColor);
                context.fill(bx,by-7,bx+1,by-2,boreColor);context.fill(bx,by+3,bx+1,by+8,boreColor);
                context.fill(bx,by,bx+1,by+1,boreColor);
                if(v.reloadTicks()>0){
                    String sec=String.format(Locale.ROOT,"%.1fs",v.reloadTicks()/20.0f);
                    context.drawTextWithShadow(c.textRenderer,sec,bx+10,by-4,0xFFE86850);
                }

                drawTankDoll(context,v,camYaw,delta,14,h-58);
            }
            boolean solo=v.secondPassenger()==null;
            int seat=v.seatFor(c.player);
            var text=Text.translatable("vehicle.bobin_craft.status",(int)v.armorHealth(),v.ammo(),Text.translatable(v.reloadTicks()>0?"vehicle.bobin_craft.loading":v.chambered()?"vehicle.bobin_craft.ready":"vehicle.bobin_craft.empty_chamber")).append(" | ").append(Text.translatable(v.hatchOpen()?"vehicle.bobin_craft.hatch_open":"vehicle.bobin_craft.hatch_closed"));
            text.append(" | ").append(Text.translatable("vehicle.bobin_craft.view_"+viewMode)).append(solo?" [Командир / Наводчик]":(seat==0?" [Водитель]":" [Наводчик]"));
            int width=c.textRenderer.getWidth(text),x=(w-width)/2,y=h-58;
            context.fill(x-6,y-3,x+width+6,y+12,0xD0171B16);context.drawTextWithShadow(c.textRenderer,text,x,y,0xC9C7A2);
            if(c.player.age<hintsUntil){
                var hint=Text.translatable("vehicle.bobin_craft.station_hint");int lineY=12;
                for(var line:c.textRenderer.wrapLines(hint,w-24)){context.drawTextWithShadow(c.textRenderer,line,(w-c.textRenderer.getWidth(line))/2,lineY,0xB1B5A2);lineY+=10;}
            }
            if(v.reloadTicks()>0){int bw=120,progress=bw*(ArmorControls.RELOAD_TICKS-v.reloadTicks())/ArmorControls.RELOAD_TICKS;context.fill(mx-bw/2,y-10,mx+bw/2,y-6,0xC021271D);context.fill(mx-bw/2,y-10,mx-bw/2+progress,y-6,0xFFD2B777);}
        });
    }

    public static void requestReload(){
        var c=MinecraftClient.getInstance();var v=station();
        if(v!=null&&c.player!=null&&(v.secondPassenger()==null||v.seatFor(c.player)==1)&&c.currentScreen==null&&c.isWindowFocused()&&ClientPlayNetworking.canSend(ArmorContent.RELOAD))
            ClientPlayNetworking.send(ArmorContent.RELOAD,PacketByteBufs.create());
    }

    private static KeyBinding key(String name,int code){return KeyBindingHelper.registerKeyBinding(new KeyBinding("key.bobin_craft."+name,InputUtil.Type.KEYSYM,code,"key.category.bobin_craft"));}
    private static void sounds(MinecraftClient c){
        if(c.world!=soundWorld){for(var s:motors.values())c.getSoundManager().stop(s);for(var s:tracks.values())c.getSoundManager().stop(s);motors.clear();tracks.clear();soundWorld=c.world;}
        motors.entrySet().removeIf(e->e.getValue().isDone());tracks.entrySet().removeIf(e->e.getValue().isDone());
        if(c.world==null||c.player==null||c.player.age%10!=0)return;
        var lens=c.gameRenderer.getCamera().getPos();
        for(var e:c.world.getEntities())if(e instanceof ArmoredVehicleEntity v&&v.engineRunning()&&v.squaredDistanceTo(lens)<64*64){
            motors.computeIfAbsent(v.getUuid(),id->{var s=new ArmorEngineSound(v,false);c.getSoundManager().play(s);return s;});
            tracks.computeIfAbsent(v.getUuid(),id->{var s=new ArmorEngineSound(v,true);c.getSoundManager().play(s);return s;});
        }
    }
}
