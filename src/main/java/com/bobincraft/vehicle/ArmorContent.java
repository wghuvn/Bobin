package com.bobincraft.vehicle;

import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.entity.*;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.util.Identifier;

public final class ArmorContent {
    public static final net.minecraft.sound.SoundEvent RING=sound("blast_ring");
    public static final Identifier HATCH=new Identifier("bobin_craft","armor_hatch");
    public static final Identifier EXIT=new Identifier("bobin_craft","armor_exit");
    public static final Identifier CLIMB_OUT=new Identifier("bobin_craft","armor_climb_out");
    public static final Item GRENADE=new HandGrenadeItem(new FabricItemSettings().maxCount(8));
    public static final Identifier RELOAD=new Identifier("bobin_craft","armor_reload");
    public static final net.minecraft.sound.SoundEvent ENGINE=sound("armor_engine"),TRACKS=sound("armor_tracks"),CANNON=sound("armor_cannon"),IMPACT=sound("armor_impact"),BREECH=sound("armor_breech");
    private static net.minecraft.sound.SoundEvent sound(String name){var id=new Identifier("bobin_craft",name);return Registry.register(Registries.SOUND_EVENT,id,net.minecraft.sound.SoundEvent.of(id));}
    public static final Identifier INPUT=new Identifier("bobin_craft","armor_input");
    public static final EntityType<ArmoredVehicleEntity> VEHICLE=Registry.register(Registries.ENTITY_TYPE,new Identifier("bobin_craft","armored_vehicle"),FabricEntityTypeBuilder.<ArmoredVehicleEntity>create(SpawnGroup.MISC,ArmoredVehicleEntity::new).dimensions(EntityDimensions.fixed(3.8f,2.95f)).trackRangeChunks(12).trackedUpdateRate(2).build());
    public static final EntityType<ArmorShellEntity> PROJECTILE=Registry.register(Registries.ENTITY_TYPE,new Identifier("bobin_craft","armor_shell"),FabricEntityTypeBuilder.<ArmorShellEntity>create(SpawnGroup.MISC,ArmorShellEntity::new).dimensions(EntityDimensions.fixed(.2f,.2f)).trackRangeChunks(10).trackedUpdateRate(1).build());
    public static final Item MOBILE_KIT=new ArmoredVehicleItem(new FabricItemSettings().maxCount(1),false),FIXED_KIT=new ArmoredVehicleItem(new FabricItemSettings().maxCount(1),true),SHELL=new Item(new FabricItemSettings().maxCount(16));
    private ArmorContent() { }
    public static boolean inHub(net.minecraft.world.World world){return world instanceof net.minecraft.server.world.ServerWorld server&&com.bobincraft.raid.HubSafety.isHub(server);}
    public static void register(){
        GrenadeThrow.register();
        Registry.register(Registries.ITEM,new Identifier("bobin_craft","hand_grenade"),GRENADE);
        Registry.register(Registries.ITEM,new Identifier("bobin_craft","armored_vehicle"),MOBILE_KIT);Registry.register(Registries.ITEM,new Identifier("bobin_craft","fixed_turret"),FIXED_KIT);Registry.register(Registries.ITEM,new Identifier("bobin_craft","armor_shell"),SHELL);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries->{entries.add(MOBILE_KIT);entries.add(FIXED_KIT);entries.add(SHELL);entries.add(GRENADE);});
        ServerPlayNetworking.registerGlobalReceiver(INPUT,(server,player,handler,buf,response)->{
            if(buf.readableBytes()!=17)return;var input=new ArmorControls.Input(buf.readFloat(),buf.readFloat(),buf.readFloat(),buf.readFloat(),buf.readBoolean());if(!input.valid())return;
            server.execute(()->{if(player.getVehicle() instanceof ArmoredVehicleEntity vehicle)vehicle.acceptInput(player,input);});
        });
        ServerPlayNetworking.registerGlobalReceiver(RELOAD,(server,player,handler,buf,response)->{if(buf.readableBytes()!=0)return;server.execute(()->{if(player.getVehicle() instanceof ArmoredVehicleEntity v)v.requestReload(player);});});
        ServerPlayNetworking.registerGlobalReceiver(HATCH,(server,player,handler,buf,response)->{if(buf.readableBytes()!=0)return;server.execute(()->{if(player.getVehicle() instanceof ArmoredVehicleEntity v)v.toggleHatch(player);});});
        ServerPlayNetworking.registerGlobalReceiver(EXIT,(server,player,handler,buf,response)->{
            int id=buf.readableBytes()>=4?buf.readInt():-1;
            server.execute(()->{
                if(player.getVehicle() instanceof ArmoredVehicleEntity v){v.startClimbOut(player);return;}
                if(id>=0&&player.getWorld().getEntityById(id) instanceof ArmoredVehicleEntity v&&v.isInCabin(player)){v.startClimbOut(player);return;}
                for(var v:player.getWorld().getEntitiesByClass(ArmoredVehicleEntity.class,player.getBoundingBox().expand(3.5),e->e.isAlive()&&e.isInCabin(player))){v.startClimbOut(player);break;}
            });
        });
        ServerTickEvents.END_WORLD_TICK.register(world->{if(world.getTime()%20==0)ArmoredPositions.tick(world);});
        ServerEntityEvents.ENTITY_LOAD.register((entity,world)->{if(entity instanceof ArmoredVehicleEntity vehicle&&vehicle.siteId()>=0)ArmorRoster.get(world).observe(vehicle.siteId(),vehicle.getUuid());});
    }
}
