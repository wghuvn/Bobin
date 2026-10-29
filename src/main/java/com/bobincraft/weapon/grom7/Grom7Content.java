package com.bobincraft.weapon.grom7;

import com.bobincraft.BobinCraft;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/**
 * Registry and configurable balance parameters for the GROM-7 remote demolition charge system (Section 10).
 *
 * <p>Explosion balance (~75% of FPV kamikaze power, pure high-explosive without shrapnel):
 * <ul>
 *   <li>FPV Kamikaze: {@code pressurePower = 2.8f}, {@code pressureDamage = 16.0f}, {@code structuralDamage = 220.0f}</li>
 *   <li>GROM-7 Charge: {@code pressurePower = 2.1f}, {@code maxPressureDamage = 12.0f}, {@code structuralDamage = 165.0f}, {@code fragmentCount = 0}</li>
 * </ul>
 */
public final class Grom7Content {
    public static float pressurePower = 2.1f;         // 75% of KAMIKAZE (2.8f)
    public static double blastRadius = 4.2;           // 2.0 * pressurePower
    public static float maxPressureDamage = 12.0f;    // 75% of KAMIKAZE (16.0f)
    public static float structuralDamage = 165.0f;    // 75% of KAMIKAZE (220.0f)
    public static int fragmentCount = 0;              // Pure high-explosive, zero shrapnel
    public static double maxRemoteRange = 160.0;      // Radio link range before total loss

    public static final SoundEvent GROM7_ATTACH_SOUND = registerSound("grom7_attach");
    public static final SoundEvent GROM7_BUTTON_SOUND = registerSound("grom7_button");
    public static final SoundEvent GROM7_ERROR_SOUND = registerSound("grom7_error");
    public static final SoundEvent GROM7_SLAP_SOUND = registerSound("grom7_slap");
    public static final SoundEvent GROM7_CLICK_SOUND = registerSound("grom7_click");
    public static final SoundEvent GROM7_DETONATE_CRACK_SOUND = registerSound("grom7_detonate_crack");
    public static final SoundEvent GROM7_DETONATE_SUB_SOUND = registerSound("grom7_detonate_sub");
    public static final SoundEvent GROM7_DEBRIS_CRUMBLE_SOUND = registerSound("grom7_debris_crumble");

    public static final EntityType<Grom7ChargeEntity> GROM7_CHARGE_ENTITY = registerEntityType();

    public static final Item GROM7_CHARGE = new Grom7ChargeItem(new FabricItemSettings().maxCount(8));
    public static final Item GROM7_REMOTE = new Grom7RemoteItem(new FabricItemSettings().maxCount(1));

    private Grom7Content() {}

    private static SoundEvent registerSound(String name) {
        Identifier id = new Identifier(BobinCraft.MOD_ID, name);
        if (Registries.SOUND_EVENT.containsId(id)) {
            return Registries.SOUND_EVENT.get(id);
        }
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    @SuppressWarnings("unchecked")
    private static EntityType<Grom7ChargeEntity> registerEntityType() {
        Identifier id = new Identifier(BobinCraft.MOD_ID, "grom7_charge");
        if (Registries.ENTITY_TYPE.containsId(id)) {
            return (EntityType<Grom7ChargeEntity>) Registries.ENTITY_TYPE.get(id);
        }
        EntityType<Grom7ChargeEntity> type = FabricEntityTypeBuilder.<Grom7ChargeEntity>create(SpawnGroup.MISC, Grom7ChargeEntity::new)
                .dimensions(EntityDimensions.fixed(0.24f, 0.16f))
                .trackRangeChunks(10)
                .trackedUpdateRate(2)
                .build();
        return Registry.register(Registries.ENTITY_TYPE, id, type);
    }

    public static void register() {
        Identifier chargeId = new Identifier(BobinCraft.MOD_ID, "grom7_charge");
        if (!Registries.ITEM.containsId(chargeId)) {
            Registry.register(Registries.ITEM, chargeId, GROM7_CHARGE);
        }
        Identifier remoteId = new Identifier(BobinCraft.MOD_ID, "grom7_remote");
        if (!Registries.ITEM.containsId(remoteId)) {
            Registry.register(Registries.ITEM, remoteId, GROM7_REMOTE);
        }
        Grom7Manager.register();
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
            entries.add(GROM7_CHARGE);
            entries.add(GROM7_REMOTE);
        });
    }
}
