package com.bobincraft.combat;

import com.bobincraft.raid.HubSafety;
import com.bobincraft.raid.guard.WarzoneGuardEntity;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.Locale;

/** Only the server can create/treat wounds or advance their countdown. No injury C2S packets exist. */
public final class InjurySystem {
    public static final Identifier SYNC = new Identifier("bobin_craft", "injury_state");

    private InjurySystem() { }

    public static boolean vulnerable(LivingEntity entity) {
        return entity.isAlive() && !entity.isInvulnerable() && !entity.isSpectator()
                && !(entity instanceof ServerPlayerEntity player && player.isCreative())
                && !(entity instanceof WarzoneGuardEntity guard && guard.isHubGuard());
    }

    public static void wound(LivingEntity entity, InjurySeverity severity, boolean face, int severed) {
        if (!(entity.getWorld() instanceof ServerWorld)) return;
        if (!(entity instanceof InjuryCarrier carrier) || !vulnerable(entity)) return;
        InjuryState state = carrier.bobinInjuries();
        boolean wasBleeding = state.isBleeding();
        int oldFace = state.faceSeverity();
        state.wound(severity, face, severed);
        if (severity != InjurySeverity.NONE || severed != 0)
            CombatEffects.blood((ServerWorld) entity.getWorld(), entity, severed != 0 ? 16 : 7);
        if (entity instanceof WarzoneGuardEntity guard) guard.refreshInjuries(true);
        if (entity instanceof ServerPlayerEntity player) {
            if (!wasBleeding || state.faceSeverity() > oldFace)
                player.sendMessage(Text.translatable(face ? "injury.bobin_craft.face_wound" : "injury.bobin_craft.new_wound"), false);
            sync(player);
        }
    }

    public static void tick(LivingEntity entity) {
        if (!(entity.getWorld() instanceof ServerWorld world) || !(entity instanceof InjuryCarrier carrier)) return;
        InjuryState state = carrier.bobinInjuries();
        if (entity instanceof net.minecraft.entity.player.PlayerEntity player) PlayerInjuries.mobility(player);
        if (entity.isAlive() && vulnerable(entity) && !HubSafety.isHub(world) && state.isBleeding()) {
            boolean expired = state.tick();
            if (entity instanceof WarzoneGuardEntity && entity.age % 40 == 0) CombatEffects.blood(world, entity, 1);
            if (expired || state.remainingTicks() % 20 == 0) {
                entity.damage(CombatDamage.source(world, CombatDamage.BLEEDING, null, null),
                        expired ? Float.MAX_VALUE : state.severity().damagePerSecond);
            }
        }
        if (entity instanceof ServerPlayerEntity player) {
            boolean changed = state.consumeDirty();
            if (changed || (state.isBleeding() && entity.age % 20 == 0)) sync(player);
        }
    }

    public static boolean treat(LivingEntity entity) {
        if (!(entity instanceof InjuryCarrier carrier) || !entity.isAlive() || !carrier.bobinInjuries().treat()) return false;
        if (entity instanceof ServerPlayerEntity player) {
            sync(player);
            player.sendMessage(Text.translatable("injury.bobin_craft.treated"), true);
        }
        return true;
    }

    public static void sync(ServerPlayerEntity player) {
        if (!(player instanceof InjuryCarrier carrier)) return;
        if (player instanceof PlayerInjurySync tracked) tracked.bobinSyncLimbs();
        if (!ServerPlayNetworking.canSend(player, SYNC)) return;
        InjuryState state = carrier.bobinInjuries();
        var buf = PacketByteBufs.create();
        buf.writeByte(state.severity().ordinal());
        buf.writeInt(state.remainingTicks());
        buf.writeByte(state.faceSeverity());
        buf.writeByte(GuardInjuryPose.pack(state.missingLimbs(), PlayerInjuries.deathPose(player)));
        buf.writeBoolean(vulnerable(player) && !HubSafety.isHub(player.getServerWorld()));
        ServerPlayNetworking.send(player, SYNC, buf);
    }

    public static void register() {
        PlayerInjuries.register();
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sync(handler.player));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity player && player instanceof InjuryCarrier carrier) {
                int raw = carrier.bobinInjuries().missingLimbs() & HitRegion.ALL_SEVERABLE;
                String msg = source.getName() != null ? source.getName().toLowerCase(Locale.ROOT) : "";
                boolean blast = msg.contains("explosion") || (source.isOf(CombatDamage.SHRAPNEL) && !PlayerInjuries.wasCrawlingBeforeDeath(player));
                int pose = PlayerInjuries.wasCrawlingBeforeDeath(player) ? GuardInjuryPose.POSE_CRAWL
                        : (blast ? GuardInjuryPose.POSE_BLAST
                        : ((raw & HitRegion.LEGS) != 0 ? GuardInjuryPose.POSE_CRAWL : GuardInjuryPose.POSE_FALLEN));
                PlayerInjuries.setDeathPose(player.getUuid(), pose);
                sync(player);
            }
        });
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            // End-return recreation preserves injuries; actual death starts with a clean body.
            if (alive && oldPlayer instanceof InjuryCarrier oldState && newPlayer instanceof InjuryCarrier newState)
                newState.bobinInjuries().restore(oldState.bobinInjuries().snapshot());
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            PlayerInjuries.setDeathPose(newPlayer.getUuid(), 0);
            sync(newPlayer);
        });
    }
}
