package com.bobincraft.combat.client;
import com.bobincraft.combat.Concussion;
import com.bobincraft.vehicle.ArmorContent;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.*;

public final class ConcussionClient {
    private static float strength;private static int remaining,duration;private static Ring ring;
    private ConcussionClient() { }
    public static float amount(){return duration==0?0:strength*MathHelper.clamp(remaining/(float)duration,0,1);}
    public static float yaw(float age){return MathHelper.sin(age*1.9f)*amount()*.55f;}
    public static float pitch(float age){return MathHelper.cos(age*1.4f)*amount()*.35f;}
    private static void clear(MinecraftClient c){remaining=duration=0;strength=0;if(ring!=null)c.getSoundManager().stop(ring);ring=null;}
    public static void register(){
        ClientPlayNetworking.registerGlobalReceiver(Concussion.PACKET,(c,h,b,s)->{
            if(b.readableBytes()!=8)return;float power=b.readFloat();int ticks=b.readInt();if(!Float.isFinite(power))return;
            c.execute(()->{strength=Math.max(amount(),MathHelper.clamp(power,0,1));duration=remaining=MathHelper.clamp(ticks,1,160);
                if(ring==null||ring.isDone()){ring=new Ring();c.getSoundManager().play(ring);}});
        });
        ClientPlayConnectionEvents.DISCONNECT.register((h,c)->clear(c));
        ClientTickEvents.END_CLIENT_TICK.register(c->{if(c.player==null||!c.player.isAlive()){clear(c);return;}if(!c.isPaused()&&remaining>0)remaining--;});
        HudRenderCallback.EVENT.register((ctx,delta)->{float a=amount();if(a<.01)return;int w=ctx.getScaledWindowWidth(),h=ctx.getScaledWindowHeight();int edge=(int)(a*64)<<24|0x22211B;
            ctx.fillGradient(0,0,w,h/3,edge,0x0022211B);ctx.fillGradient(0,h*2/3,w,h,0x0022211B,edge);
            int elapsed=duration-remaining;if(elapsed<5)ctx.fill(0,0,w,h,(int)(a*(5-elapsed)*10)<<24|0xCDC7AC);
        });
    }
    private static final class Ring extends MovingSoundInstance {
        Ring(){super(ArmorContent.RING,SoundCategory.PLAYERS,net.minecraft.util.math.random.Random.create());repeat=true;repeatDelay=0;relative=true;attenuationType=net.minecraft.client.sound.SoundInstance.AttenuationType.NONE;volume=amount()*.16f;}
        @Override public void tick(){if(remaining<=0){setDone();return;}volume=amount()*.16f;pitch=.92f+amount()*.08f;}
    }
}
