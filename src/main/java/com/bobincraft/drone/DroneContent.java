package com.bobincraft.drone;

import com.bobincraft.BobinCraft;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemGroup;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;

import java.util.EnumMap;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class DroneContent {
    public static final Identifier FPV_MOTOR_ID = id("fpv_motor_loop");
    public static final SoundEvent FPV_MOTOR_SOUND = SoundEvent.of(FPV_MOTOR_ID);
    public static final Identifier FP1_ENGINE_ID = id("fp1_engine_loop");
    public static final SoundEvent FP1_ENGINE_SOUND = SoundEvent.of(FP1_ENGINE_ID);
    public static final Identifier FP1_MEGA_EXPLOSION_ID = id("fp1_mega_explosion");
    public static final SoundEvent FP1_MEGA_EXPLOSION_SOUND = SoundEvent.of(FP1_MEGA_EXPLOSION_ID);

    public static final EntityType<DroneEntity> FPV_DRONE = drone(DroneType.FPV);
    public static final EntityType<DroneEntity> BOMBER_DRONE = drone(DroneType.BOMBER);
    public static final EntityType<DroneEntity> HEAVY_DRONE = drone(DroneType.HEAVY);
    public static final EntityType<DroneEntity> FIXED_WING_DRONE = drone(DroneType.FIXED_WING);
    public static final EntityType<DronePayloadEntity> DRONE_PAYLOAD = Registry.register(Registries.ENTITY_TYPE,
            id("drone_payload"), FabricEntityTypeBuilder.<DronePayloadEntity>create(SpawnGroup.MISC, DronePayloadEntity::new)
                    .dimensions(EntityDimensions.fixed(0.36f, 0.48f)).trackRangeChunks(24).trackedUpdateRate(1).build());
    private static final EnumMap<DroneVariant, Item> KITS = new EnumMap<>(DroneVariant.class);
    static { for (DroneVariant variant : DroneVariant.values()) KITS.put(variant,
            new DroneItem(new FabricItemSettings().maxCount(1), variant)); }
    public static final Item FPV_CONTROLLER = KITS.get(DroneVariant.FPV);
    public static final Item BOMBER_CONTROLLER = KITS.get(DroneVariant.BOMBER);
    public static final Item HEAVY_CONTROLLER = KITS.get(DroneVariant.HEAVY_BOMBER);
    public static final Item FP1_CONTROLLER = KITS.get(DroneVariant.FP1);
    public static final Item DRONE_CONTROLLER = new DroneControllerItem(new FabricItemSettings().maxCount(1));

    private DroneContent() { }

    private static EntityType<DroneEntity> drone(DroneType type) {
        int trackChunks = type == DroneType.FIXED_WING ? 80 : 32;
        return Registry.register(Registries.ENTITY_TYPE, id(type.id),
                FabricEntityTypeBuilder.<DroneEntity>create(SpawnGroup.MISC, DroneEntity::new)
                        .dimensions(EntityDimensions.fixed(type.width, type.height)).trackRangeChunks(trackChunks).trackedUpdateRate(1).build());
    }

    public static EntityType<DroneEntity> entityTypeFor(DroneType type) {
        return switch (type) {
            case FPV -> FPV_DRONE;
            case HEAVY -> HEAVY_DRONE;
            case FIXED_WING -> FIXED_WING_DRONE;
            default -> BOMBER_DRONE;
        };
    }

    private static Identifier id(String name) { return new Identifier(BobinCraft.MOD_ID, name); }

    public static Item itemFor(DroneType type) {
        return switch (type) {
            case FPV -> FPV_CONTROLLER;
            case HEAVY -> HEAVY_CONTROLLER;
            case FIXED_WING -> FP1_CONTROLLER;
            default -> BOMBER_CONTROLLER;
        };
    }
    public static Item itemFor(DroneVariant variant) { return KITS.get(variant); }
    public static boolean isController(ItemStack stack) { return stack.getItem() instanceof DroneItem || stack.isOf(DRONE_CONTROLLER); }

    public static void register() {
        if (!Registries.SOUND_EVENT.containsId(FPV_MOTOR_ID))
            Registry.register(Registries.SOUND_EVENT, FPV_MOTOR_ID, FPV_MOTOR_SOUND);
        if (!Registries.SOUND_EVENT.containsId(FP1_ENGINE_ID)) {
            Registry.register(Registries.SOUND_EVENT, FP1_ENGINE_ID, FP1_ENGINE_SOUND);
        }
        if (!Registries.SOUND_EVENT.containsId(FP1_MEGA_EXPLOSION_ID)) {
            Registry.register(Registries.SOUND_EVENT, FP1_MEGA_EXPLOSION_ID, FP1_MEGA_EXPLOSION_SOUND);
        }
        for (DroneVariant variant : DroneVariant.values()) Registry.register(Registries.ITEM, id(variant.id), KITS.get(variant));
        Registry.register(Registries.ITEM, id("drone_controller"), DRONE_CONTROLLER);
        registerGroup("kamikaze_drones", DroneType.FPV);
        registerGroup("bomber_drones", DroneType.BOMBER);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
            for (DroneVariant variant : DroneVariant.values()) entries.add(KITS.get(variant));
            entries.add(DRONE_CONTROLLER);
        });
        DroneNetworking.register();
    }

    private static void registerGroup(String name, DroneType type) {
        ItemGroup group = FabricItemGroup.builder().displayName(Text.translatable("itemGroup.bobin_craft." + name))
                .icon(() -> new ItemStack(itemFor(type))).entries((context, entries) -> {
                    for (DroneVariant variant : DroneVariant.values()) {
                        if (variant.type == type
                                || (type == DroneType.FPV && variant.type == DroneType.FIXED_WING)
                                || (type == DroneType.BOMBER && variant.type == DroneType.HEAVY)) {
                            entries.add(KITS.get(variant));
                        }
                    }
                    entries.add(DRONE_CONTROLLER);
                    entries.add(com.bobincraft.combat.CombatContent.TOURNIQUET);
                    entries.add(com.bobincraft.raid.WarzoneEncounterManager.GUARD_SPAWN_EGG);
                }).build();
        Registry.register(Registries.ITEM_GROUP, id(name), group);
    }
}
