package com.bobincraft.vehicle;

import com.bobincraft.drone.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.*;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.*;

/**
 * Authoritative PUBG-style grenade controller supporting:
 * <ul>
 *   <li>Interactive LMB preparation (pin pull) & windup hold</li>
 *   <li>RMB toggle between Overhand (high trajectory) and Underhand (low trajectory)</li>
 *   <li>{@code R} key 5-second cooking countdown ({@link GrenadeTiming#FUSE_TICKS} = 100 ticks) with in-hand explosion if held too long</li>
 *   <li>LMB release throw with exact remaining fuse transfer and ballistic trajectory helper</li>
 * </ul>
 */
public final class GrenadeThrow {
    public static final Identifier ANIMATION = new Identifier("bobin_craft", "grenade_throw");
    public static final Identifier ACTION_C2S = new Identifier("bobin_craft", "grenade_action");

    public static final byte ACTION_START_WINDUP = 1;
    public static final byte ACTION_RELEASE_THROW = 2;
    public static final byte ACTION_TOGGLE_MODE = 3;
    public static final byte ACTION_COOK_TIMER = 4;

    public static final byte STATE_WINDUP = 0;
    public static final byte STATE_RELEASING = 1;
    public static final byte STATE_CANCELLED = 2;

    private static final class Session {
        final ServerPlayerEntity player;
        final Hand hand;
        final ItemStack stack;
        final long startTick;
        final RegistryKey<World> worldKey;
        boolean lowArc;
        boolean cooked;
        long cookStartTick = -1L;
        long releaseStartTick = -1L;
        boolean thrown = false;

        Session(ServerPlayerEntity player, Hand hand, ItemStack stack, long startTick, RegistryKey<World> worldKey, boolean lowArc) {
            this.player = player;
            this.hand = hand;
            this.stack = stack;
            this.startTick = startTick;
            this.worldKey = worldKey;
            this.lowArc = lowArc;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Boolean> PLAYER_LOW_MODE = new HashMap<>();

    private GrenadeThrow() {}

    public static boolean valid(PlayerEntity p, Hand hand) {
        return p != null && p.isAlive() && !p.isSpectator() && !p.hasVehicle()
                && !ArmorContent.inHub(p.getWorld())
                && com.bobincraft.combat.PlayerInjuries.usableHand(p, hand)
                && com.bobincraft.combat.PlayerInjuries.usableHand(p, hand == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND);
    }

    public static boolean isLowMode(UUID playerUuid) {
        return PLAYER_LOW_MODE.getOrDefault(playerUuid, false);
    }

    /**
     * Legacy / convenience entry point: starts windup and schedules automatic release if not controlled interactively.
     */
    public static boolean begin(ServerPlayerEntity player, Hand hand) {
        return startWindup(player, hand, player.isSneaking(), true);
    }

    public static boolean startWindup(ServerPlayerEntity player, Hand hand, boolean lowMode, boolean autoRelease) {
        if (!valid(player, hand) || SESSIONS.containsKey(player.getUuid())) return false;
        ItemStack stack = player.getStackInHand(hand);
        if (!stack.isOf(ArmorContent.GRENADE) || player.getItemCooldownManager().isCoolingDown(ArmorContent.GRENADE)) {
            return false;
        }
        long now = player.getServerWorld().getTime();
        PLAYER_LOW_MODE.put(player.getUuid(), lowMode);
        Session session = new Session(player, hand, stack, now, player.getWorld().getRegistryKey(), lowMode);
        if (autoRelease) {
            session.releaseStartTick = now + GrenadeTiming.WINDUP_TICKS;
        }
        SESSIONS.put(player.getUuid(), session);
        broadcastState(session, STATE_WINDUP);
        return true;
    }

    public static void toggleMode(ServerPlayerEntity player, Hand hand) {
        if (player == null) return;
        boolean nextLow = !PLAYER_LOW_MODE.getOrDefault(player.getUuid(), false);
        PLAYER_LOW_MODE.put(player.getUuid(), nextLow);
        Session session = SESSIONS.get(player.getUuid());
        if (session != null && session.releaseStartTick < 0) {
            session.lowArc = nextLow;
            broadcastState(session, STATE_WINDUP);
        }
        player.getServerWorld().playSound(
                null, player.getBlockPos(), SoundEvents.ITEM_ARMOR_EQUIP_GENERIC, SoundCategory.PLAYERS, 0.45f, nextLow ? 0.9f : 1.25f);
    }

    public static void startCooking(ServerPlayerEntity player) {
        if (player == null) return;
        Session session = SESSIONS.get(player.getUuid());
        long now = player.getServerWorld().getTime();
        if (session == null) {
            Hand hand = player.getMainHandStack().isOf(ArmorContent.GRENADE) ? Hand.MAIN_HAND
                    : (player.getOffHandStack().isOf(ArmorContent.GRENADE) ? Hand.OFF_HAND : null);
            if (hand == null || !startWindup(player, hand, isLowMode(player.getUuid()), false)) return;
            session = SESSIONS.get(player.getUuid());
        }
        if (session != null && !session.cooked && session.releaseStartTick < 0) {
            session.cooked = true;
            session.cookStartTick = now;
            // Safety lever (spoon) eject metallic ping
            player.getServerWorld().playSound(
                    null, player.getBlockPos(), SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.PLAYERS, 0.75f, 1.85f);
            player.getServerWorld().playSound(
                    null, player.getBlockPos(), SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.PLAYERS, 0.45f, 1.95f);
            broadcastState(session, STATE_WINDUP);
        }
    }

    public static void releaseThrow(ServerPlayerEntity player) {
        if (player == null) return;
        Session session = SESSIONS.get(player.getUuid());
        if (session == null || session.releaseStartTick >= 0) return;
        long now = player.getServerWorld().getTime();
        // Ensure pin pull finishes before throw release
        long minReleaseStart = session.startTick + GrenadeTiming.PIN_TICKS;
        session.releaseStartTick = Math.max(now, minReleaseStart);
        broadcastState(session, STATE_RELEASING);
    }

    private static void broadcastState(Session s, byte stateCode) {
        Set<ServerPlayerEntity> viewers = new HashSet<>(PlayerLookup.tracking(s.player));
        viewers.add(s.player);
        for (ServerPlayerEntity viewer : viewers) {
            if (ServerPlayNetworking.canSend(viewer, ANIMATION)) {
                PacketByteBuf packet = PacketByteBufs.create();
                packet.writeUuid(s.player.getUuid());
                packet.writeBoolean(s.hand == Hand.MAIN_HAND);
                packet.writeLong(s.startTick);
                packet.writeByte(stateCode);
                packet.writeBoolean(s.lowArc);
                packet.writeBoolean(s.cooked);
                packet.writeLong(s.cookStartTick);
                packet.writeLong(s.releaseStartTick);
                ServerPlayNetworking.send(viewer, ANIMATION, packet);
            }
        }
    }

    public static void tick() {
        var iterator = SESSIONS.values().iterator();
        while (iterator.hasNext()) {
            Session s = iterator.next();
            ServerPlayerEntity p = s.player;
            if (p.isDisconnected()
                    || !valid(p, s.hand)
                    || !p.getWorld().getRegistryKey().equals(s.worldKey)
                    || p.getStackInHand(s.hand) != s.stack
                    || s.stack.isEmpty()) {
                broadcastState(s, STATE_CANCELLED);
                iterator.remove();
                continue;
            }
            ServerWorld world = p.getServerWorld();
            long now = world.getTime();
            long age = now - s.startTick;

            if (age == GrenadeTiming.PIN_TICKS) {
                world.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.PLAYERS, 0.55f, 1.75f);
            }

            // Check 5-second (100-tick) cooking fuse expiration while still in hand!
            if (s.cooked && s.cookStartTick >= 0 && !s.thrown) {
                long cookedTicks = now - s.cookStartTick;
                if (cookedTicks >= GrenadeTiming.FUSE_TICKS) {
                    // Explodes in player's hand!
                    s.thrown = true;
                    if (!p.isCreative()) {
                        s.stack.decrement(1);
                    }
                    p.getItemCooldownManager().set(ArmorContent.GRENADE, 20);
                    broadcastState(s, STATE_CANCELLED);
                    Vec3d blastPos = p.getEyePos().add(p.getRotationVec(1.0f).multiply(0.35));
                    DroneExplosion.explodeAt(world, p, p, DroneBlastType.DROPPED_GRENADE, blastPos);
                    iterator.remove();
                    continue;
                }
            }

            if (s.releaseStartTick >= 0) {
                long relAge = now - s.releaseStartTick;
                if (relAge >= GrenadeTiming.THROW_RELEASE_DELAY_TICKS && !s.thrown) {
                    s.thrown = true;
                    spawnThrownGrenade(s, now);
                }
                if (relAge >= GrenadeTiming.THROW_FOLLOW_THROUGH_TICKS) {
                    iterator.remove();
                }
            }
        }
    }

    /**
     * Computes initial launch position and velocity for both server entity spawning and client trajectory preview.
     */
    public static Vec3d computeLaunchVelocity(PlayerEntity player, boolean lowArc) {
        Vec3d direction = player.getRotationVec(1.0f).normalize();
        Vec3d inherited = player.getVelocity().multiply(0.45);
        if (lowArc) {
            // Underhand short-range toss / roll
            return direction.multiply(0.46).add(inherited).add(0.0, 0.04, 0.0);
        } else {
            // Overhand high-arc ballistic throw
            double upBoost = direction.y > -0.55 ? 0.16 : 0.04;
            return direction.multiply(0.96).add(inherited).add(0.0, upBoost, 0.0);
        }
    }

    public static Vec3d computeLaunchOrigin(World world, PlayerEntity player, boolean lowArc) {
        Vec3d direction = player.getRotationVec(1.0f).normalize();
        Vec3d eye = player.getEyePos();
        double verticalOffset = lowArc ? -0.24 : -0.08;
        Vec3d desired = eye.add(direction.multiply(0.62)).add(0.0, verticalOffset, 0.0);
        BlockHitResult block = world.raycast(new RaycastContext(
                eye, desired, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        Vec3d start = block.getType() == HitResult.Type.MISS ? desired : block.getPos().subtract(direction.multiply(0.13));
        for (var vehicle : world.getEntitiesByClass(ArmoredVehicleEntity.class, new Box(eye, desired).expand(0.5), EntityPredicates.EXCEPT_SPECTATOR)) {
            var hit = ArmorGeometry.raycast(vehicle, eye, start);
            if (hit.isPresent()) {
                start = hit.get().point().subtract(direction.multiply(0.13));
            }
        }
        return start;
    }

    private static void spawnThrownGrenade(Session s, long now) {
        ServerPlayerEntity p = s.player;
        ServerWorld world = p.getServerWorld();
        Vec3d start = computeLaunchOrigin(world, p, s.lowArc);
        Vec3d velocity = computeLaunchVelocity(p, s.lowArc);

        int remainingFuse = GrenadeTiming.FUSE_TICKS;
        if (s.cooked && s.cookStartTick >= 0) {
            long elapsed = now - s.cookStartTick;
            remainingFuse = (int) Math.max(1L, GrenadeTiming.FUSE_TICKS - elapsed);
        }

        var grenade = DroneContent.DRONE_PAYLOAD.create(world);
        if (grenade == null) return;
        grenade.configureHandGrenade(p.getUuid(), start, velocity, remainingFuse);
        if (world.spawnEntity(grenade)) {
            if (!p.isCreative()) {
                s.stack.decrement(1);
            }
            p.getItemCooldownManager().set(ArmorContent.GRENADE, 12);
            world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.PLAYERS, 0.65f, s.lowArc ? 0.72f : 0.88f);
        }
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> tick());
        ServerTickEvents.START_SERVER_TICK.register(server -> SESSIONS.values().removeIf(s -> s.player.getServer() != server));
        ServerPlayNetworking.registerGlobalReceiver(ACTION_C2S, (server, player, handler, buf, responseSender) -> {
            if (buf.readableBytes() < 1) return;
            byte action = buf.readByte();
            boolean mainHand = buf.readableBytes() >= 1 ? buf.readBoolean() : true;
            boolean lowMode = buf.readableBytes() >= 1 ? buf.readBoolean() : isLowMode(player.getUuid());
            Hand hand = mainHand ? Hand.MAIN_HAND : Hand.OFF_HAND;
            server.execute(() -> {
                if (action == ACTION_START_WINDUP) {
                    startWindup(player, hand, lowMode, false);
                } else if (action == ACTION_RELEASE_THROW) {
                    releaseThrow(player);
                } else if (action == ACTION_TOGGLE_MODE) {
                    toggleMode(player, hand);
                } else if (action == ACTION_COOK_TIMER) {
                    startCooking(player);
                }
            });
        });
    }
}
