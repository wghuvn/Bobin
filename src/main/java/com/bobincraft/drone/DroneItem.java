package com.bobincraft.drone;

import com.bobincraft.raid.HubSafety;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** The vehicle kit is consumed only after a successful spawn, never for failed placement or reconnect. */
public final class DroneItem extends Item {
    public static final double STANDARD_LAUNCH_DISTANCE = 1.5;
    public static final double LARGE_DRONE_EXTRA_OFFSET = 3.0;

    private final DroneType type;
    private final DroneVariant variant;

    public DroneItem(Settings settings, DroneType type) { this(settings, DroneVariant.standard(type)); }
    public DroneItem(Settings settings, DroneVariant variant) { super(settings); this.type = variant.type; this.variant = variant; }
    public DroneVariant variant() { return variant; }

    /**
     * Large drones (FP-1 fixed-wing and Heavy bomber) spawn 3 blocks further away from the player
     * (4.5 blocks total vs 1.5 blocks) so the player never gets trapped inside the airframe (Task 5).
     */
    public static double launchForwardDistance(DroneType type) {
        return (type == DroneType.FIXED_WING || type == DroneType.HEAVY)
                ? STANDARD_LAUNCH_DISTANCE + LARGE_DRONE_EXTRA_OFFSET
                : STANDARD_LAUNCH_DISTANCE;
    }

    public static Vec3d computeLaunchPosition(Vec3d playerPos, float playerYaw, DroneType type) {
        Vec3d forward = Vec3d.fromPolar(0, playerYaw);
        double dist = launchForwardDistance(type);
        double yOffset = type == DroneType.FIXED_WING ? 1.35 : 0.6;
        return playerPos.add(forward.multiply(dist)).add(0, yOffset, 0);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) return TypedActionResult.success(stack);
        ServerPlayerEntity player = (ServerPlayerEntity) user;
        ServerWorld serverWorld = (ServerWorld) world;
        if (!user.isAlive() || user.isSpectator() || user.hasVehicle() || HubSafety.isHub(serverWorld)) {
            user.sendMessage(Text.translatable("drone.bobin_craft.launch_blocked"), true);
            return TypedActionResult.fail(stack);
        }
        DroneEntity existing = DroneNetworking.owned(player);
        if (existing != null) {
            DroneNetworking.begin(player, existing);
            return TypedActionResult.success(stack);
        }
        DroneEntity drone = DroneContent.entityTypeFor(type).create(serverWorld);
        if (drone == null) return TypedActionResult.fail(stack);
        Vec3d launch = computeLaunchPosition(user.getPos(), user.getYaw(), type);
        drone.refreshPositionAndAngles(launch.x, launch.y, launch.z, user.getYaw(), 0);
        for (double extraY : new double[]{1.0, 2.0, 3.0}) {
            if (world.isSpaceEmpty(drone)) break;
            drone.refreshPositionAndAngles(launch.x, launch.y + extraY, launch.z, user.getYaw(), 0);
        }
        if (!world.isSpaceEmpty(drone) || !world.getWorldBorder().contains(drone.getBoundingBox())) {
            user.sendMessage(Text.translatable("drone.bobin_craft.launch_space"), true);
            return TypedActionResult.fail(stack);
        }
        drone.setVariant(variant);
        drone.configure(player, stack.getNbt());
        if (!serverWorld.spawnEntity(drone)) return TypedActionResult.fail(stack);
        if (!user.getAbilities().creativeMode) user.setStackInHand(hand, new ItemStack(DroneContent.DRONE_CONTROLLER));
        user.getItemCooldownManager().set(this, (type == DroneType.HEAVY || type == DroneType.FIXED_WING) ? 45 : 20);
        DroneNetworking.begin(player, drone);
        return TypedActionResult.success(user.getStackInHand(hand));
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("drone.bobin_craft.tooltip.launch"));
        String typeKey = switch (type) {
            case FPV -> "drone.bobin_craft.tooltip.fpv";
            case HEAVY -> "drone.bobin_craft.tooltip.heavy";
            case FIXED_WING -> "drone.bobin_craft.tooltip.fixed_wing";
            default -> "drone.bobin_craft.tooltip.bomber";
        };
        tooltip.add(Text.translatable(typeKey));
        tooltip.add(Text.translatable(variant.tooltipKey()));
        tooltip.add(Text.translatable("drone.bobin_craft.tooltip.damage", Math.round(variant.blast.pressureDamage), Math.round(variant.blast.maxFragmentDamage)));
    }
}
