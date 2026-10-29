package com.bobincraft.raid.guard;

import com.bobincraft.weapon.BulletPenetration;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

/** Server-owned NPC combat profiles, independent of any player weapon system. */
public enum NpcWeapon {
    STANDARD(30, 40, 4, 90.0, 1, 5.0f, 0.2, 2.0, 0xE1B26B, BulletPenetration.Caliber.RIFLE_762),
    SNIPER_RIFLE(10, 50, 40, 120.0, 1, 18.0f, 0.5, 2.0, 0xE1B26B, BulletPenetration.Caliber.HEAVY_50BMG),
    BREACHER(6, 45, 18, 14.0, 7, 2.5f, 0.12, 12.0, 0xFF9A2E, BulletPenetration.Caliber.SHOTGUN_12G),
    SPOTTER(8, 30, 9, 40.0, 1, 4.0f, 0.15, 2.0, 0xB070FF, BulletPenetration.Caliber.PISTOL_9MM),
    CRYO(24, 40, 4, 24.0, 1, 2.0f, 0.1, 2.0, 0x4EE2FF, BulletPenetration.Caliber.PISTOL_9MM),
    MEDIC(10, 35, 7, 28.0, 1, 2.0f, 0.1, 2.0, 0x3DFF8B, BulletPenetration.Caliber.DART);

    private final int magazineSize;
    private final int reloadTicks;
    private final int intervalTicks;
    private final double range;
    private final int pellets;
    private final float damage;
    private final double knockback;
    private final double minSpreadDegrees;
    private final int traceColor;
    private final BulletPenetration.Caliber caliber;

    NpcWeapon(int magazineSize, int reloadTicks, int intervalTicks, double range,
              int pellets, float damage, double knockback, double minSpreadDegrees,
              int traceColor, BulletPenetration.Caliber caliber) {
        this.magazineSize = magazineSize;
        this.reloadTicks = reloadTicks;
        this.intervalTicks = intervalTicks;
        this.range = range;
        this.pellets = pellets;
        this.damage = damage;
        this.knockback = knockback;
        this.minSpreadDegrees = minSpreadDegrees;
        this.traceColor = traceColor;
        this.caliber = caliber;
    }

    /** Held item for humanoid guard arm posing and third-person 3D rendering (never dropped). */
    public Item item() {
        return Items.CROSSBOW;
    }
    public int magazineSize() { return magazineSize; }
    public int reloadTicks() { return reloadTicks; }
    public int intervalTicks() { return intervalTicks; }
    public double range() { return range; }
    public int pellets() { return pellets; }
    public float damage() { return damage; }
    public double knockback() { return knockback; }
    public double minSpreadDegrees() { return minSpreadDegrees; }
    public int traceColor() { return traceColor; }
    public BulletPenetration.Caliber caliber() { return caliber; }
    public boolean hasSpecialEffect() { return this != STANDARD && this != SNIPER_RIFLE; }

    public static NpcWeapon fromName(String name) {
        try { return valueOf(name); } catch (IllegalArgumentException ignored) { return STANDARD; }
    }
}
