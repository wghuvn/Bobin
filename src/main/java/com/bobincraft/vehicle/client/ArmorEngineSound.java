package com.bobincraft.vehicle.client;
import com.bobincraft.vehicle.*;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
final class ArmorEngineSound extends MovingSoundInstance {
    private final ArmoredVehicleEntity vehicle;private final boolean tracks;
    ArmorEngineSound(ArmoredVehicleEntity v,boolean tracks){super(tracks?ArmorContent.TRACKS:ArmorContent.ENGINE,SoundCategory.HOSTILE,Random.create());vehicle=v;this.tracks=tracks;repeat=true;repeatDelay=0;volume=tracks?0:.3f;x=v.getX();y=v.getY();z=v.getZ();}
    @Override public void tick(){if(!vehicle.isAlive()||!vehicle.engineRunning()||vehicle.getWorld()!=net.minecraft.client.MinecraftClient.getInstance().world){setDone();return;}x=vehicle.getX();y=vehicle.getY();z=vehicle.getZ();float speed=(float)Math.min(1,vehicle.drivingSpeed()/.19);volume=MathHelper.lerp(.12f,volume,tracks?speed*.65f:.4f+speed*.4f);pitch=MathHelper.lerp(.1f,pitch,tracks?.75f+speed*.5f:.75f+speed*.55f);}
    @Override public boolean shouldAlwaysPlay(){return true;}
}
