package com.bobincraft.vehicle.client;

import com.bobincraft.drone.DronePayloadProfile;
import com.bobincraft.mixin.GrenadeArmAccess;
import com.bobincraft.vehicle.*;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.client.model.*;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/**
 * PUBG: BATTLEGROUNDS style grenade client controller:
 * <ul>
 *   <li>Preparation: draws grenade in right hand, left hand realistically pulls the safety pin.</li>
 *   <li>Windup & Throw: holding LMB cocks the arm back; releasing LMB executes full throw animation with camera/arm inertia.</li>
 *   <li>Two Throw Modes: Overhand (high trajectory) and Underhand (low trajectory), toggled with RMB.</li>
 *   <li>Cooking Timer: pressing {@code R} releases the safety lever and starts a 5-second countdown even in hand.</li>
 *   <li>Trajectory Preview: renders an obstacle-aware ballistic arc and landing ring while preparing the throw.</li>
 * </ul>
 */
public final class GrenadeClient {
    private static final class ClientAnim {
        final Hand hand;
        final long startTick;
        byte stateCode;
        boolean lowArc;
        boolean cooked;
        long cookStartTick;
        long releaseStartTick;

        ClientAnim(Hand hand, long startTick, byte stateCode, boolean lowArc, boolean cooked, long cookStartTick, long releaseStartTick) {
            this.hand = hand;
            this.startTick = startTick;
            this.stateCode = stateCode;
            this.lowArc = lowArc;
            this.cooked = cooked;
            this.cookStartTick = cookStartTick;
            this.releaseStartTick = releaseStartTick;
        }
    }

    private static final Map<UUID, ClientAnim> ANIMATIONS = new HashMap<>();
    private static net.minecraft.client.world.ClientWorld world;
    private static final ModelPart PIN = pinModel();
    private static final ModelPart LEVER = leverModel();
    private static final Identifier METAL = new Identifier("bobin_craft", "textures/block/armor_steel.png");
    private static final Identifier ARC_TEXTURE = new Identifier("bobin_craft", "textures/particle/weapon_cloud_soft.png");

    // Client input & spring-damper state for local player
    private static boolean wasLmbDown = false;
    private static boolean wasRmbDown = false;
    private static boolean wasRKeyDown = false;
    private static boolean localLowArc = false;
    private static float lowArcBlend = 0.0f, lowArcVel = 0.0f;
    private static float equipProgress = 1.0f, equipVel = 0.0f;
    private static boolean wasHoldingGrenade = false;
    private static float swayYaw = 0f, swayPitch = 0f, swayVelYaw = 0f, swayVelPitch = 0f;
    private static float lastPlayerYaw = 0f, lastPlayerPitch = 0f;
    private static boolean anglesInit = false;
    private static long lastFrameNs = 0L;

    private GrenadeClient() {}

    private static ModelPart pinModel() {
        var data = new ModelData();
        var b = ModelPartBuilder.create();
        b.uv(0, 0)
                .cuboid(-1.2f, 0, -.13f, 2.4f, .3f, .26f)
                .cuboid(-1.2f, 2.2f, -.13f, 2.4f, .3f, .26f)
                .cuboid(-1.2f, .3f, -.13f, .3f, 1.9f, .26f)
                .cuboid(.9f, .3f, -.13f, .3f, 1.9f, .26f);
        data.getRoot().addChild("pin", b, ModelTransform.NONE);
        return TexturedModelData.of(data, 256, 256).createModel().getChild("pin");
    }

    private static ModelPart leverModel() {
        var data = new ModelData();
        var b = ModelPartBuilder.create();
        b.uv(0, 0)
                .cuboid(-0.4f, -2.2f, -0.4f, 0.8f, 3.8f, 0.8f)
                .cuboid(-0.6f, 1.2f, -0.8f, 1.2f, 0.6f, 1.6f);
        data.getRoot().addChild("lever", b, ModelTransform.NONE);
        return TexturedModelData.of(data, 64, 64).createModel().getChild("lever");
    }

    private static void sendC2S(byte action, Hand hand, boolean lowMode) {
        if (!ClientPlayNetworking.canSend(GrenadeThrow.ACTION_C2S)) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByte(action);
        buf.writeBoolean(hand == Hand.MAIN_HAND);
        buf.writeBoolean(lowMode);
        ClientPlayNetworking.send(GrenadeThrow.ACTION_C2S, buf);
    }

    public static boolean isHoldingGrenade(PlayerEntity player) {
        if (player == null) return false;
        return player.getMainHandStack().isOf(ArmorContent.GRENADE)
                || player.getOffHandStack().isOf(ArmorContent.GRENADE);
    }

    private static Hand activeGrenadeHand(PlayerEntity player) {
        if (player.getMainHandStack().isOf(ArmorContent.GRENADE)) return Hand.MAIN_HAND;
        if (player.getOffHandStack().isOf(ArmorContent.GRENADE)) return Hand.OFF_HAND;
        return null;
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(GrenadeThrow.ANIMATION, (client, handler, buf, response) -> {
            int readable = buf.readableBytes();
            if (readable < 25) return;
            UUID id = buf.readUuid();
            Hand hand = buf.readBoolean() ? Hand.MAIN_HAND : Hand.OFF_HAND;
            long start = buf.readLong();
            byte stateCode = readable >= 26 ? buf.readByte() : GrenadeThrow.STATE_WINDUP;
            boolean lowArc = readable >= 27 && buf.readBoolean();
            boolean cooked = readable >= 28 && buf.readBoolean();
            long cookStart = readable >= 36 ? buf.readLong() : -1L;
            long relStart = readable >= 44 ? buf.readLong() : (readable == 25 ? start + GrenadeTiming.WINDUP_TICKS : -1L);
            client.execute(() -> {
                if (client.world == null) return;
                if (stateCode == GrenadeThrow.STATE_CANCELLED) {
                    ANIMATIONS.remove(id);
                } else {
                    ANIMATIONS.put(id, new ClientAnim(hand, start, stateCode, lowArc, cooked, cookStart, relStart));
                    if (client.player != null && client.player.getUuid().equals(id)) {
                        localLowArc = lowArc;
                    }
                }
            });
        });

        ModelPredicateProviderRegistry.register(
                ArmorContent.GRENADE,
                new Identifier("bobin_craft", "pin_removed"),
                (stack, w, e, seed) -> stack.hasNbt() && stack.getNbt().getBoolean("PinRemoved") ? 1 : 0);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world != world) {
                ANIMATIONS.clear();
                world = client.world;
                wasLmbDown = wasRmbDown = wasRKeyDown = false;
                wasHoldingGrenade = false;
            }
            if (world == null) return;

            long now = world.getTime();
            ANIMATIONS.values().removeIf(a ->
                    a.releaseStartTick >= 0 && now - a.releaseStartTick > GrenadeTiming.THROW_FOLLOW_THROUGH_TICKS + 2);

            var player = client.player;
            if (player == null || client.currentScreen != null) {
                wasLmbDown = wasRmbDown = wasRKeyDown = false;
                return;
            }

            Hand gHand = activeGrenadeHand(player);
            if (gHand == null || !GrenadeThrow.valid(player, gHand)) {
                wasHoldingGrenade = false;
                wasLmbDown = wasRmbDown = wasRKeyDown = false;
                return;
            }

            if (!wasHoldingGrenade) {
                wasHoldingGrenade = true;
                equipProgress = 0.0f;
                equipVel = 0.0f;
            }

            long win = client.getWindow() != null ? client.getWindow().getHandle() : 0L;
            boolean lmb = client.options.attackKey.isPressed()
                    || (win != 0L && GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS);
            boolean rmb = client.options.useKey.isPressed()
                    || (win != 0L && GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS);
            boolean rKey = win != 0L && InputUtil.isKeyPressed(win, GLFW.GLFW_KEY_R);

            ClientAnim myAnim = ANIMATIONS.get(player.getUuid());

            // 1. RMB toggles Overhand (High Trajectory) vs Underhand (Low Trajectory)
            if (rmb && !wasRmbDown) {
                localLowArc = !localLowArc;
                if (myAnim != null && myAnim.releaseStartTick < 0) {
                    myAnim.lowArc = localLowArc;
                }
                sendC2S(GrenadeThrow.ACTION_TOGGLE_MODE, gHand, localLowArc);
            }
            wasRmbDown = rmb;

            // 2. Holding LMB initiates preparation (pin pull) & windup hold
            if (lmb && !wasLmbDown) {
                if (myAnim == null && !player.getItemCooldownManager().isCoolingDown(ArmorContent.GRENADE)) {
                    ANIMATIONS.put(
                            player.getUuid(),
                            new ClientAnim(gHand, now, GrenadeThrow.STATE_WINDUP, localLowArc, false, -1L, -1L));
                    sendC2S(GrenadeThrow.ACTION_START_WINDUP, gHand, localLowArc);
                }
            } else if (!lmb && wasLmbDown) {
                // Releasing LMB triggers the throw animation!
                if (myAnim != null && myAnim.releaseStartTick < 0) {
                    long minRel = myAnim.startTick + GrenadeTiming.PIN_TICKS;
                    myAnim.releaseStartTick = Math.max(now, minRel);
                    myAnim.stateCode = GrenadeThrow.STATE_RELEASING;
                    sendC2S(GrenadeThrow.ACTION_RELEASE_THROW, gHand, localLowArc);
                }
            }
            wasLmbDown = lmb;

            // 3. Pressing R starts the 5-second cooking timer even while held in hand!
            if (rKey && !wasRKeyDown) {
                if (myAnim == null && !player.getItemCooldownManager().isCoolingDown(ArmorContent.GRENADE)) {
                    myAnim = new ClientAnim(gHand, now, GrenadeThrow.STATE_WINDUP, localLowArc, true, now, -1L);
                    ANIMATIONS.put(player.getUuid(), myAnim);
                } else if (myAnim != null && !myAnim.cooked && myAnim.releaseStartTick < 0) {
                    myAnim.cooked = true;
                    myAnim.cookStartTick = now;
                }
                sendC2S(GrenadeThrow.ACTION_COOK_TIMER, gHand, localLowArc);
            }
            wasRKeyDown = rKey;
        });

        // Render PUBG-style 3D Ballistic Trajectory Preview Arc & Landing Target Ring
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.world == null || client.options.hudHidden) return;
            ClientAnim anim = ANIMATIONS.get(client.player.getUuid());
            if (anim == null || anim.releaseStartTick >= 0) return;
            if (!isHoldingGrenade(client.player)) return;

            var matrices = context.matrixStack();
            var consumers = context.consumers();
            if (matrices == null || consumers == null) return;

            renderTrajectoryPreview(client, client.player, anim.lowArc, context.camera().getPos(), matrices, consumers);
        });
    }

    private static void stepClientSprings(AbstractClientPlayerEntity player, boolean targetLowArc) {
        long nowNs = Util.getMeasuringTimeNano();
        float dt = lastFrameNs == 0L ? (1f / 60f) : (nowNs - lastFrameNs) * 1e-9f;
        lastFrameNs = nowNs;
        dt = MathHelper.clamp(dt, 0.001f, 0.050f);

        float eqForce = (1.0f - equipProgress) * 180f - equipVel * 20f;
        equipVel += eqForce * dt;
        equipProgress = MathHelper.clamp(equipProgress + equipVel * dt, 0f, 1.05f);

        float targetLow = targetLowArc ? 1.0f : 0.0f;
        float lowForce = (targetLow - lowArcBlend) * 165f - lowArcVel * 21f;
        lowArcVel += lowForce * dt;
        lowArcBlend = MathHelper.clamp(lowArcBlend + lowArcVel * dt, 0f, 1f);

        float yaw = player.getYaw();
        float pitch = player.getPitch();
        if (!anglesInit) {
            lastPlayerYaw = yaw;
            lastPlayerPitch = pitch;
            anglesInit = true;
        }
        float dYaw = MathHelper.wrapDegrees(yaw - lastPlayerYaw);
        float dPitch = pitch - lastPlayerPitch;
        lastPlayerYaw = yaw;
        lastPlayerPitch = pitch;

        swayVelYaw += (-dYaw * 0.032f - 120f * swayYaw - 16f * swayVelYaw) * dt;
        swayYaw = MathHelper.clamp(swayYaw + swayVelYaw * dt, -0.12f, 0.12f);

        swayVelPitch += (-dPitch * 0.028f - 120f * swayPitch - 16f * swayVelPitch) * dt;
        swayPitch = MathHelper.clamp(swayPitch + swayVelPitch * dt, -0.10f, 0.10f);
    }

    private static GrenadeTiming.Pose evalPose(PlayerEntity p, ClientAnim a, float delta) {
        float prepAge = (float) (p.getWorld().getTime() - a.startTick) + delta;
        float relAge = a.releaseStartTick >= 0 ? (float) (p.getWorld().getTime() - a.releaseStartTick) + delta : -1.0f;
        return GrenadeTiming.interactivePose(prepAge, relAge);
    }

    public static boolean render(
            HeldItemRenderer renderer,
            AbstractClientPlayerEntity p,
            float delta,
            Hand hand,
            MatrixStack m,
            VertexConsumerProvider out,
            int light) {
        var animation = ANIMATIONS.get(p.getUuid());
        boolean isLocal = MinecraftClient.getInstance().player == p;
        Hand activeHand = activeGrenadeHand(p);

        // Even before pressing LMB, render custom PUBG two-handed ready posture when holding a grenade!
        if (animation == null) {
            if (!isLocal || activeHand == null || !GrenadeThrow.valid(p, activeHand)) return false;
            if (hand != activeHand) return true;
            stepClientSprings(p, localLowArc);
            renderIdleHold(renderer, p, hand, m, out, light);
            return true;
        }

        float prepAge = (float) (p.getWorld().getTime() - animation.startTick) + delta;
        float relAge = animation.releaseStartTick >= 0
                ? (float) (p.getWorld().getTime() - animation.releaseStartTick) + delta
                : -1.0f;
        if (prepAge < 0 || (relAge >= GrenadeTiming.THROW_FOLLOW_THROUGH_TICKS)) {
            return false;
        }
        if (relAge < GrenadeTiming.THROW_RELEASE_DELAY_TICKS && !p.getStackInHand(animation.hand).isOf(ArmorContent.GRENADE)) {
            ANIMATIONS.remove(p.getUuid());
            return false;
        }
        if (hand != animation.hand) return true;

        if (isLocal) {
            stepClientSprings(p, animation.lowArc);
        }

        Arm main = hand == Hand.MAIN_HAND ? p.getMainArm() : p.getMainArm().getOpposite();
        Arm off = main.getOpposite();
        int sign = main == Arm.RIGHT ? 1 : -1;
        var pose = GrenadeTiming.interactivePose(prepAge, relAge);
        float pin = pose.pin();
        float wind = pose.windup();
        float release = pose.release();
        float recover = pose.recovery();
        float weight = 1.0f - recover;
        float low = isLocal ? lowArcBlend : (animation.lowArc ? 1.0f : 0.0f);
        var arms = (GrenadeArmAccess) renderer;

        // 1. Unified Parent Rig for Right Hand (mainArm) + Held Grenade (zero levitation or detachment!)
        m.push();
        float overX = -sign * 0.11f * wind + sign * 0.04f * release;
        float overY = (-0.03f + 0.16f * wind - 0.19f * release) * weight;
        float overZ = (0.09f * wind - 0.18f * release) * weight;
        float overPitch = (-16f * pin - 62f * wind + 108f * release) * weight;

        float underX = -sign * 0.06f * wind + sign * 0.03f * release;
        float underY = (-0.12f * wind + 0.14f * release) * weight;
        float underZ = (0.06f * wind - 0.16f * release) * weight;
        float underPitch = (-12f * pin + 28f * wind - 64f * release) * weight;

        float tx = MathHelper.lerp(low, overX, underX) + swayYaw * 0.18f;
        float ty = MathHelper.lerp(low, overY, underY) + swayPitch * 0.15f;
        float tz = MathHelper.lerp(low, overZ, underZ);
        float rx = MathHelper.lerp(low, overPitch, underPitch) + swayPitch * 28f;
        float ry = sign * (-8f + 14f * wind - 10f * release) * weight + swayYaw * 32f;
        float rz = sign * (6f * wind - 12f * release) * weight;

        m.translate(tx, ty, tz);
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(ry));
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rx));
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rz));

        // Render throwing hand
        m.push();
        arms.bobin_craft$grenadeArm(m, out, light, 0, 0, main);
        m.pop();

        // Render Grenade strictly locked inside mainArm's coordinate space until exact release frame!
        if (relAge < 0f || relAge < GrenadeTiming.THROW_RELEASE_DELAY_TICKS) {
            ItemStack grenade = new ItemStack(ArmorContent.GRENADE);
            if (prepAge >= GrenadeTiming.PIN_TICKS) {
                grenade.getOrCreateNbt().putBoolean("PinRemoved", true);
            }
            m.push();
            m.translate(sign * 0.24f, -0.22f, -0.58f);
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(14f - 18f * low));
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-sign * 10f));
            m.scale(0.66f, 0.66f, 0.66f);
            renderer.renderItem(
                    p,
                    grenade,
                    main == Arm.RIGHT ? ModelTransformationMode.FIRST_PERSON_RIGHT_HAND : ModelTransformationMode.FIRST_PERSON_LEFT_HAND,
                    main == Arm.LEFT,
                    m,
                    out,
                    light);
            m.pop();

            // If cooked via 'R', animate the safety lever (spoon) flipping off and flying away!
            if (animation.cooked && animation.cookStartTick >= 0) {
                float cookAge = (float) (p.getWorld().getTime() - animation.cookStartTick) + delta;
                if (cookAge >= 0f && cookAge < 12f) {
                    float lt = cookAge / 12f;
                    m.push();
                    m.translate(
                            sign * (0.24f + 0.22f * lt),
                            -0.16f + 0.18f * lt - 0.32f * lt * lt,
                            -0.58f - 0.12f * lt);
                    m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(sign * (25f + 380f * lt)));
                    m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(240f * lt));
                    m.scale(0.55f, 0.55f, 0.55f);
                    LEVER.render(m, out.getBuffer(RenderLayer.getEntityCutout(METAL)), light, OverlayTexture.DEFAULT_UV, 0.42f, 0.48f, 0.36f, 1f);
                    m.pop();
                }
            }
        }
        m.pop();

        // 2. Left Support Hand (offArm): reaches to grenade top, pulls pin ring out, then braces torso in windup
        if (relAge < 0f || relAge < GrenadeTiming.THROW_RELEASE_DELAY_TICKS + 4) {
            float offFade = relAge < 0f ? 1.0f : MathHelper.clamp(1.0f - relAge / 6.0f, 0f, 1f);
            m.push();
            float ox = sign * (0.28f - 0.26f * pin - 0.14f * wind);
            float oy = (0.10f - 0.04f * pin - 0.08f * wind) * offFade - (1.0f - offFade) * 0.22f;
            float oz = (-0.04f - 0.05f * pin + 0.04f * wind);
            m.translate(ox, oy, oz);
            m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-sign * (32f - 12f * pin + 18f * wind)));
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-26f + 16f * pin + 12f * wind));
            arms.bobin_craft$grenadeArm(m, out, light, 0.12f, 0, off);

            // Render 3D pulled safety pin in the left hand's fingers during ticks [2..15]
            if (prepAge >= 2.0f && prepAge < 15.5f) {
                m.push();
                m.translate(-sign * 0.16f, -0.22f, -0.52f);
                m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(sign * (28f - 45f * pin)));
                m.scale(0.58f, 0.58f, 0.58f);
                PIN.render(m, out.getBuffer(RenderLayer.getEntityCutout(METAL)), light, OverlayTexture.DEFAULT_UV, 0.85f, 0.88f, 0.82f, 1);
                m.pop();
            }
            m.pop();
        }

        return true;
    }

    private static void renderIdleHold(
            HeldItemRenderer renderer,
            AbstractClientPlayerEntity p,
            Hand hand,
            MatrixStack m,
            VertexConsumerProvider out,
            int light) {
        Arm main = hand == Hand.MAIN_HAND ? p.getMainArm() : p.getMainArm().getOpposite();
        Arm off = main.getOpposite();
        int sign = main == Arm.RIGHT ? 1 : -1;
        var arms = (GrenadeArmAccess) renderer;
        float eq = 1.0f - equipProgress;
        float low = lowArcBlend;

        m.push();
        m.translate(
                sign * 0.03f * eq + swayYaw * 0.16f,
                -0.14f * eq - 0.04f * low + swayPitch * 0.14f,
                0.06f * eq);
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-24f * eq + 8f * low + swayPitch * 22f));
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(sign * (-6f + 12f * eq) + swayYaw * 26f));

        m.push();
        arms.bobin_craft$grenadeArm(m, out, light, 0, 0, main);
        m.pop();

        ItemStack grenade = p.getStackInHand(hand);
        m.push();
        m.translate(sign * 0.24f, -0.22f, -0.58f);
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(14f - 14f * low));
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-sign * 10f));
        m.scale(0.66f, 0.66f, 0.66f);
        renderer.renderItem(
                p,
                grenade,
                main == Arm.RIGHT ? ModelTransformationMode.FIRST_PERSON_RIGHT_HAND : ModelTransformationMode.FIRST_PERSON_LEFT_HAND,
                main == Arm.LEFT,
                m,
                out,
                light);
        m.pop();
        m.pop();

        // Left hand poised near the safety pin ring
        m.push();
        m.translate(sign * 0.26f, 0.06f - 0.18f * eq, -0.04f);
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-sign * 30f));
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-24f));
        arms.bobin_craft$grenadeArm(m, out, light, 0.14f, 0, off);
        m.pop();
    }

    /**
     * Simulates the exact server ballistic trajectory and renders a smooth PUBG-style preview arc + landing circle.
     */
    private static void renderTrajectoryPreview(
            MinecraftClient client,
            PlayerEntity player,
            boolean lowArc,
            Vec3d camPos,
            MatrixStack matrices,
            VertexConsumerProvider consumers) {
        var world = client.world;
        if (world == null) return;

        Vec3d pos = GrenadeThrow.computeLaunchOrigin(world, player, lowArc);
        Vec3d vel = GrenadeThrow.computeLaunchVelocity(player, lowArc);
        DronePayloadProfile profile = DronePayloadProfile.GRENADE;

        List<Vec3d> points = new ArrayList<>(64);
        points.add(pos);
        Vec3d impactNormal = new Vec3d(0, 1, 0);
        boolean hitSurface = false;

        for (int step = 0; step < 72; step++) {
            vel = profile.fallingVelocity(vel);
            Vec3d next = pos.add(vel);
            BlockHitResult blockHit = world.raycast(new RaycastContext(
                    pos, next, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
            if (blockHit.getType() == HitResult.Type.BLOCK) {
                points.add(blockHit.getPos());
                impactNormal = Vec3d.of(blockHit.getSide().getVector());
                hitSurface = true;
                break;
            }
            boolean hitVehicle = false;
            for (var vehicle : world.getEntitiesByClass(
                    ArmoredVehicleEntity.class, new Box(pos, next).expand(0.35), EntityPredicates.EXCEPT_SPECTATOR)) {
                var vHit = ArmorGeometry.raycast(vehicle, pos, next);
                if (vHit.isPresent()) {
                    points.add(vHit.get().point());
                    impactNormal = new Vec3d(0, 1, 0);
                    hitSurface = true;
                    hitVehicle = true;
                    break;
                }
            }
            if (hitVehicle) break;
            points.add(next);
            pos = next;
        }

        if (points.size() < 2) return;

        VertexConsumer vc = consumers.getBuffer(RenderLayer.getEntityTranslucent(ARC_TEXTURE));
        int fullLight = LightmapTextureManager.MAX_LIGHT_COORDINATE;

        // Check if cooking to tint arc from tactical warm amber-white toward urgent red-orange as timer nears 0
        ClientAnim anim = ANIMATIONS.get(player.getUuid());
        float urgency = 0f;
        if (anim != null && anim.cooked && anim.cookStartTick >= 0) {
            float elapsed = (float) (world.getTime() - anim.cookStartTick);
            urgency = MathHelper.clamp(elapsed / GrenadeTiming.FUSE_TICKS, 0f, 1f);
        }
        int rCol = (int) MathHelper.lerp(urgency, 235f, 255f);
        int gCol = (int) MathHelper.lerp(urgency, 210f, 75f);
        int bCol = (int) MathHelper.lerp(urgency, 145f, 55f);

        matrices.push();
        try {
            matrices.translate(-camPos.x, -camPos.y, -camPos.z);
            Matrix4f pm = matrices.peek().getPositionMatrix();
            Matrix3f nm = matrices.peek().getNormalMatrix();

            for (int i = 0; i < points.size() - 1; i++) {
                Vec3d p0 = points.get(i);
                Vec3d p1 = points.get(i + 1);
                Vec3d seg = p1.subtract(p0);
                if (seg.lengthSquared() < 1e-6) continue;
                Vec3d side = seg.crossProduct(camPos.subtract(p0));
                double sLen = side.length();
                if (sLen <= 1e-5) continue;
                side = side.multiply(0.022 / sLen);

                float u = (float) i / (float) points.size();
                int alpha = (int) (MathHelper.clamp((1.0f - u * 0.55f) * 185f, 35f, 185f));
                emitQuad(vc, pm, nm,
                        p0.subtract(side), p0.add(side), p1.add(side), p1.subtract(side),
                        rCol, gCol, bCol, alpha, fullLight);
            }

            // Render landing ring at the predicted impact point
            if (hitSurface) {
                Vec3d center = points.get(points.size() - 1).add(impactNormal.multiply(0.015));
                Vec3d upRef = Math.abs(impactNormal.y) > 0.9 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
                Vec3d t1 = impactNormal.crossProduct(upRef).normalize();
                Vec3d t2 = impactNormal.crossProduct(t1).normalize();
                int segs = 16;
                double rOuter = 0.24, rInner = 0.17;
                for (int s = 0; s < segs; s++) {
                    double a0 = s * (Math.PI * 2.0 / segs);
                    double a1 = (s + 1) * (Math.PI * 2.0 / segs);
                    Vec3d dir0 = t1.multiply(Math.cos(a0)).add(t2.multiply(Math.sin(a0)));
                    Vec3d dir1 = t1.multiply(Math.cos(a1)).add(t2.multiply(Math.sin(a1)));
                    emitQuad(vc, pm, nm,
                            center.add(dir0.multiply(rInner)),
                            center.add(dir0.multiply(rOuter)),
                            center.add(dir1.multiply(rOuter)),
                            center.add(dir1.multiply(rInner)),
                            rCol, gCol, bCol, 210, fullLight);
                }
            }
        } finally {
            matrices.pop();
        }
    }

    private static void emitQuad(
            VertexConsumer vc,
            Matrix4f pm,
            Matrix3f nm,
            Vec3d p0,
            Vec3d p1,
            Vec3d p2,
            Vec3d p3,
            int r,
            int g,
            int b,
            int a,
            int light) {
        vc.vertex(pm, (float) p0.x, (float) p0.y, (float) p0.z)
                .color(r, g, b, a).texture(0.2f, 0.8f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(nm, 0, 1, 0).next();
        vc.vertex(pm, (float) p1.x, (float) p1.y, (float) p1.z)
                .color(r, g, b, a).texture(0.8f, 0.8f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(nm, 0, 1, 0).next();
        vc.vertex(pm, (float) p2.x, (float) p2.y, (float) p2.z)
                .color(r, g, b, a).texture(0.8f, 0.2f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(nm, 0, 1, 0).next();
        vc.vertex(pm, (float) p3.x, (float) p3.y, (float) p3.z)
                .color(r, g, b, a).texture(0.2f, 0.2f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(nm, 0, 1, 0).next();
    }

    /** Presentation ownership query; preserves grenade follow-through after an item switch. */
    public static boolean ownsHands(PlayerEntity player) {
        var a = ANIMATIONS.get(player.getUuid());
        if (a == null) return false;
        long now = player.getWorld().getTime();
        if (a.releaseStartTick < 0) return now >= a.startTick;
        return now - a.releaseStartTick < GrenadeTiming.THROW_FOLLOW_THROUGH_TICKS;
    }

    public static void pose(PlayerEntity player, BipedEntityModel<?> model, float delta) {
        var animation = ANIMATIONS.get(player.getUuid());
        if (animation == null) return;
        float prepAge = (float) (player.getWorld().getTime() - animation.startTick) + delta;
        float relAge = animation.releaseStartTick >= 0
                ? (float) (player.getWorld().getTime() - animation.releaseStartTick) + delta
                : -1.0f;
        if (prepAge < 0 || relAge >= GrenadeTiming.THROW_FOLLOW_THROUGH_TICKS) return;
        Arm arm = animation.hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        GrenadeArmPose.apply(model, arm, GrenadeTiming.interactivePose(prepAge, relAge), animation.lowArc);
    }

    public static void renderHud(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.options.hudHidden || client.currentScreen != null) return;
        if (!isHoldingGrenade(client.player)) return;
        var tr = client.textRenderer;
        int sw = context.getScaledWindowWidth();
        int sh = context.getScaledWindowHeight();
        ClientAnim anim = ANIMATIONS.get(client.player.getUuid());
        boolean low = anim != null ? anim.lowArc : localLowArc;
        String modeText = low ? "Бросок: Снизу (ПКМ)" : "Бросок: Сверху (ПКМ)";
        context.drawTextWithShadow(tr, modeText, sw / 2 + 12, sh / 2 + 8, 0xFFE2E8D5);
        if (anim != null && anim.cooked && anim.cookStartTick >= 0 && anim.releaseStartTick < 0) {
            long remTicks = Math.max(0L, GrenadeTiming.FUSE_TICKS - (client.world.getTime() - anim.cookStartTick));
            String timerStr = String.format(Locale.ROOT, "Таймер: %.1f с", remTicks / 20.0f);
            context.drawTextWithShadow(tr, timerStr, sw / 2 + 12, sh / 2 + 19, remTicks <= 30 ? 0xFFFF5245 : 0xFFFFC855);
        } else if (anim != null && anim.releaseStartTick < 0) {
            context.drawTextWithShadow(tr, "[R] Сорвать рычаг (5с)", sw / 2 + 12, sh / 2 + 19, 0xFFAEB8A8);
        }
    }
}
