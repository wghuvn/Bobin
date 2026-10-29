package com.bobincraft.weapon.grom7.client;

import com.bobincraft.mixin.GrenadeArmAccess;
import com.bobincraft.weapon.grom7.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.model.*;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Client-side animation renderer, LMB charge-binding controller, and HUD panel for GROM-7:
 * <ul>
 *   <li>LMB while holding remote: binds looked-at charge (up to 3 slots) with short indicator blink & beep.</li>
 *   <li>R key (or Shift+LMB on bound charge): manually unbinds selected charge to free a slot.</li>
 *   <li>6-Stage Misfire Animation ({@link Grom7Timing.Mode#REMOTE_FAIL}):
 *     <ol>
 *       <li>Thumb presses the remote button.</li>
 *       <li>Dry mechanical click plays, no explosion occurs.</li>
 *       <li>Remote indicator LED blinks bright red several times.</li>
 *       <li>Character slightly turns remote toward themselves to inspect it.</li>
 *       <li>Slightly shakes the device with wrist inertia and presses the button a second time.</li>
 *       <li>After no reaction, hand naturally lowers back to the rest position.</li>
 *     </ol>
 *   </li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public final class Grom7Client {
    public record ActiveAnimation(Hand hand, Grom7Timing.Mode mode, long startTick) {}

    public record RemoteHudState(
            int totalCharges,
            int selectedChargeNumber,
            String stateText,
            String signalText,
            int distanceMeters
    ) {}

    private static final Map<UUID, ActiveAnimation> ANIMATIONS = new HashMap<>();
    private static ClientWorld lastWorld;
    private static RemoteHudState hudState = new RemoteHudState(0, 0, "нет", "нет связи", 0);
    private static boolean wasRKeyDown = false;
    private static final ModelPart REMOTE_LED = createRemoteLedModel();
    private static final Identifier LED_TEXTURE = new Identifier("bobin_craft", "textures/entity/grom7_charge.png");

    private Grom7Client() {}

    private static ModelPart createRemoteLedModel() {
        ModelData data = new ModelData();
        data.getRoot().addChild(
                "led",
                ModelPartBuilder.create().uv(20, 0).cuboid(-0.45f, -0.45f, -0.45f, 0.9f, 0.9f, 0.9f),
                ModelTransform.NONE);
        return TexturedModelData.of(data, 32, 32).createModel().getChild("led");
    }

    public static boolean isHoldingRemote(PlayerEntity player) {
        if (player == null) return false;
        return player.getMainHandStack().isOf(Grom7Content.GROM7_REMOTE)
                || player.getOffHandStack().isOf(Grom7Content.GROM7_REMOTE);
    }

    public static boolean onRemoteAttackClick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.currentScreen != null) return false;
        if (!isHoldingRemote(client.player)) return false;
        Grom7ChargeEntity target = Grom7Manager.findLookedAtCharge(client.world, client.player);
        if (target == null) return false;
        if (ClientPlayNetworking.canSend(Grom7Manager.REMOTE_ACTION_C2S)) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeByte(Grom7Manager.REMOTE_ACTION_LMB_BIND);
            ClientPlayNetworking.send(Grom7Manager.REMOTE_ACTION_C2S, buf);
        }
        return true;
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(Grom7Manager.ANIMATION_PACKET, (client, handler, buf, response) -> {
            if (buf.readableBytes() < 26) return;
            UUID playerId = buf.readUuid();
            Hand hand = buf.readBoolean() ? Hand.MAIN_HAND : Hand.OFF_HAND;
            Grom7Timing.Mode mode = Grom7Timing.Mode.fromOrdinal(buf.readByte());
            long startTick = buf.readLong();
            client.execute(() -> {
                if (client.world != null) {
                    ANIMATIONS.put(playerId, new ActiveAnimation(hand, mode, startTick));
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(Grom7Manager.HUD_SYNC_PACKET, (client, handler, buf, response) -> {
            int total = buf.readInt();
            int selNum = buf.readInt();
            String st = buf.readString(64);
            String sig = buf.readString(64);
            int dist = buf.readInt();
            client.execute(() -> hudState = new RemoteHudState(total, selNum, st, sig, dist));
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world != lastWorld) {
                ANIMATIONS.clear();
                lastWorld = client.world;
                wasRKeyDown = false;
            }
            if (lastWorld != null) {
                ANIMATIONS.values().removeIf(a -> lastWorld.getTime() - a.startTick() > a.mode().durationTicks + 2);
            }
            if (client.player != null && client.currentScreen == null && isHoldingRemote(client.player)) {
                long win = client.getWindow() != null ? client.getWindow().getHandle() : 0L;
                boolean rDown = win != 0L && InputUtil.isKeyPressed(win, GLFW.GLFW_KEY_R);
                if (rDown && !wasRKeyDown) {
                    if (ClientPlayNetworking.canSend(Grom7Manager.REMOTE_ACTION_C2S)) {
                        PacketByteBuf buf = PacketByteBufs.create();
                        buf.writeByte(Grom7Manager.REMOTE_ACTION_UNBIND_SELECTED);
                        ClientPlayNetworking.send(Grom7Manager.REMOTE_ACTION_C2S, buf);
                    }
                }
                wasRKeyDown = rDown;
            } else {
                wasRKeyDown = false;
            }
        });
    }

    public static void triggerLocalAnimationForTest(UUID playerUuid, Hand hand, Grom7Timing.Mode mode, long startTick) {
        ANIMATIONS.put(playerUuid, new ActiveAnimation(hand, mode, startTick));
    }

    public static ActiveAnimation getActiveAnimation(UUID playerUuid) {
        return ANIMATIONS.get(playerUuid);
    }

    public static RemoteHudState getHudState() {
        return hudState;
    }

    public static void setHudStateForTest(RemoteHudState state) {
        hudState = state;
    }

    private static float age(PlayerEntity player, ActiveAnimation anim, float tickDelta) {
        if (player == null || player.getWorld() == null) return -1.0f;
        return (float) (player.getWorld().getTime() - anim.startTick()) + tickDelta;
    }

    public static boolean renderFirstPerson(HeldItemRenderer renderer,
                                            AbstractClientPlayerEntity player,
                                            float tickDelta,
                                            Hand hand,
                                            MatrixStack matrices,
                                            VertexConsumerProvider vertexConsumers,
                                            int light) {
        if (player == null || player.getWorld() == null) return false;
        ActiveAnimation anim = ANIMATIONS.get(player.getUuid());
        if (anim == null) return false;
        float age = age(player, anim, tickDelta);
        if (age < 0.0f || age >= anim.mode().durationTicks) return false;
        if (hand != anim.hand()) return true;

        Arm mainArm = hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        Arm offArm = mainArm.getOpposite();
        int sign = mainArm == Arm.RIGHT ? 1 : -1;
        var armAccess = (GrenadeArmAccess) renderer;
        ModelTransformationMode itemMode = mainArm == Arm.RIGHT
                ? ModelTransformationMode.FIRST_PERSON_RIGHT_HAND
                : ModelTransformationMode.FIRST_PERSON_LEFT_HAND;
        boolean leftHanded = mainArm == Arm.LEFT;

        switch (anim.mode()) {
            case THROW -> {
                var pose = Grom7Timing.throwPose(age);
                float wind = pose.windup(), rel = pose.release(), rec = pose.recovery(), w = 1.0f - rec;

                matrices.push();
                matrices.translate(-sign * (0.22 - 0.06 * wind) * w, (-0.18 + 0.08 * wind - 0.10 * rel) * w, (-0.12 - 0.06 * wind) * w);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-sign * (18.0f + 10.0f * wind) * w));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((-28.0f * wind + 18.0f * rel) * w));
                armAccess.bobin_craft$grenadeArm(matrices, vertexConsumers, light, 0.18f, 0.0f, offArm);
                matrices.pop();

                matrices.push();
                matrices.translate(
                        sign * (-0.08 * wind + 0.04 * rel) * w,
                        (-0.03 + 0.13 * wind - 0.15 * rel) * w,
                        (0.08 * wind - 0.14 * rel) * w
                );
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(sign * (-8.0f + 12.0f * wind - 6.0f * rel) * w));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((-48.0f * wind + 82.0f * rel) * w));

                armAccess.bobin_craft$grenadeArm(matrices, vertexConsumers, light, 0.0f, 0.0f, mainArm);

                if (age < Grom7Timing.THROW_RELEASE_TICKS) {
                    ItemStack chargeStack = new ItemStack(Grom7Content.GROM7_CHARGE);
                    matrices.push();
                    matrices.translate(sign * 0.24f, -0.22f, -0.58f);
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(12.0f));
                    matrices.scale(0.68f, 0.68f, 0.68f);
                    renderer.renderItem(player, chargeStack, itemMode, leftHanded, matrices, vertexConsumers, light);
                    matrices.pop();
                }
                matrices.pop();
                return true;
            }
            case ATTACH -> {
                var pose = Grom7Timing.attachPose(age);
                float reach = pose.reach(), press = pose.press(), rec = pose.recovery(), w = 1.0f - rec;
                float push = (0.62f * reach + 0.38f * press) * w;

                matrices.push();
                matrices.translate(0.0f, -0.04f + 0.07f * push - 0.015f * press, -0.18f * push - 0.03f * press);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-22.0f * push - 4.0f * press));

                matrices.push();
                matrices.translate(-sign * 0.08f * push, 0.0f, 0.0f);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(sign * 10.0f * push));
                armAccess.bobin_craft$grenadeArm(matrices, vertexConsumers, light, 0.0f, 0.0f, mainArm);
                matrices.pop();

                matrices.push();
                matrices.translate(sign * (0.28f - 0.12f * push), -0.01f, 0.02f);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-sign * (20.0f + 6.0f * push)));
                armAccess.bobin_craft$grenadeArm(matrices, vertexConsumers, light, 0.08f, 0.0f, offArm);
                matrices.pop();

                if (age < Grom7Timing.ATTACH_FIX_TICKS) {
                    ItemStack chargeStack = new ItemStack(Grom7Content.GROM7_CHARGE);
                    matrices.push();
                    matrices.translate(sign * (0.10f - 0.06f * push), -0.20f, -0.56f);
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(82.0f * push));
                    matrices.scale(0.70f, 0.70f, 0.70f);
                    renderer.renderItem(player, chargeStack, itemMode, leftHanded, matrices, vertexConsumers, light);
                    matrices.pop();
                }
                matrices.pop();
                return true;
            }
            case REMOTE_BIND -> {
                float u = age / Grom7Timing.Mode.REMOTE_BIND.durationTicks;
                float pulse = (float) Math.sin(u * Math.PI);
                matrices.push();
                matrices.translate(-sign * 0.05f * pulse, 0.05f * pulse, -0.08f * pulse);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-10.0f * pulse));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(sign * 8.0f * pulse));

                matrices.push();
                armAccess.bobin_craft$grenadeArm(matrices, vertexConsumers, light, 0.0f, 0.0f, mainArm);
                matrices.pop();

                ItemStack remoteStack = new ItemStack(Grom7Content.GROM7_REMOTE);
                matrices.push();
                matrices.translate(sign * 0.21f, -0.21f, -0.55f);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(6.0f));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-sign * 16.0f));
                matrices.scale(0.68f, 0.68f, 0.68f);
                renderer.renderItem(player, remoteStack, itemMode, leftHanded, matrices, vertexConsumers, light);
                renderRemoteIndicatorLed(matrices, vertexConsumers, 0.18f, 1.0f, 0.35f, true, light);
                matrices.pop();

                matrices.pop();
                return true;
            }
            case REMOTE_SUCCESS -> {
                var pose = Grom7Timing.successPose(age);
                float w = pose.raise() * (1.0f - pose.lower());
                float ant = pose.anticipation();
                float press = pose.press();
                float recoil = pose.clickRecoil();
                float hold = pose.followThrough();

                matrices.push();
                matrices.translate(
                        -sign * (0.09f * w + 0.01f * hold),
                        0.08f * w + 0.016f * ant - 0.024f * recoil - 0.008f * hold,
                        -0.10f * w - 0.012f * ant + 0.018f * recoil
                );
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(sign * (15.0f * w + 2.0f * hold)));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-12.0f * w + 3.5f * ant - 6.5f * recoil - 2.0f * hold));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-sign * (5.0f * w + 1.8f * recoil)));

                matrices.push();
                armAccess.bobin_craft$grenadeArm(matrices, vertexConsumers, light, 0.0f, 0.0f, mainArm);
                matrices.pop();

                ItemStack remoteStack = new ItemStack(Grom7Content.GROM7_REMOTE);
                matrices.push();
                matrices.translate(sign * 0.21f, -0.21f - 0.008f * press, -0.55f);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(6.0f - 2.5f * recoil));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-sign * 16.0f));
                matrices.scale(0.68f, 0.68f, 0.68f);
                renderer.renderItem(player, remoteStack, itemMode, leftHanded, matrices, vertexConsumers, light);
                renderRemoteIndicatorLed(matrices, vertexConsumers, 0.20f, 1.0f, 0.32f, age >= 5.0f && age <= 16.0f, light);
                matrices.pop();

                matrices.push();
                matrices.translate(
                        sign * (-0.16f + 0.12f * w + 0.02f * press),
                        -0.06f + 0.14f * w + 0.032f * ant - 0.048f * press - 0.012f * recoil,
                        -0.38f - 0.06f * w - 0.015f * press
                );
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-sign * (34.0f - 8.0f * press)));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-24.0f - 8.0f * ant + 18.0f * press + 4.0f * recoil));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-sign * (18.0f - 10.0f * press)));
                armAccess.bobin_craft$grenadeArm(matrices, vertexConsumers, light, 0.06f, 0.0f, offArm);
                matrices.pop();

                matrices.pop();
                return true;
            }
            case REMOTE_FAIL -> {
                var p = Grom7Timing.failPose(age);
                float w = p.raise();

                // Unified parent rig for the 6-stage misfire choreography:
                // 1. Thumb presses button -> 2. Dry click -> 3. Red LED blinks ->
                // 4. Turn remote toward face to inspect -> 5. Shake device & press button again -> 6. Lower to rest
                matrices.push();
                matrices.translate(
                        sign * (-0.08f * w - 0.06f * p.inspect() + 0.028f * p.slapWave()),
                        0.08f * w - 0.024f * p.thumbPress() + 0.055f * p.inspect() + 0.018f * Math.abs(p.slapWave()) - 0.15f * p.disappoint(),
                        -0.09f * w - 0.065f * p.inspect() + 0.015f * p.thumbPress()
                );
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(
                        -12.0f * w - 8.5f * p.thumbPress() - 18.0f * p.inspect() + 10.0f * p.slapWave() + 26.0f * p.disappoint()));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(
                        sign * (14.0f * w - 28.0f * p.inspect() + 14.0f * p.slapWave())));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(
                        sign * (22.0f * p.inspect() + 24.0f * p.slapWave())));

                // 1. Holding hand (mainArm)
                matrices.push();
                armAccess.bobin_craft$grenadeArm(matrices, vertexConsumers, light, 0.0f, 0.0f, mainArm);
                matrices.pop();

                // 2. Remote item locked in mainArm's parent coordinate space + 3D blinking red indicator LED
                ItemStack remoteStack = new ItemStack(Grom7Content.GROM7_REMOTE);
                matrices.push();
                matrices.translate(sign * 0.21f, -0.21f - 0.009f * p.thumbPress(), -0.55f);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(6.0f));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-sign * 16.0f));
                matrices.scale(0.68f, 0.68f, 0.68f);
                renderer.renderItem(player, remoteStack, itemMode, leftHanded, matrices, vertexConsumers, light);
                boolean redBlink = Grom7Timing.isFailRedLedOn(age);
                renderRemoteIndicatorLed(matrices, vertexConsumers, 1.0f, 0.14f, 0.12f, redBlink, light);
                matrices.pop();

                // 3. Thumb / support hand pressing the button on press #1 [0..8] and retry press #2 [39..47],
                //    and bracing the wrist during inspection/shake
                if (w > 0.05f) {
                    float press = p.thumbPress();
                    float shake = p.slapWave();
                    matrices.push();
                    matrices.translate(
                            sign * (-0.04f + 0.02f * press + 0.015f * shake),
                            0.07f * w - 0.044f * press - 0.02f * p.inspect(),
                            -0.42f - 0.01f * press
                    );
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-sign * (32.0f - 6.0f * press)));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-22.0f + 16.0f * press));
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-sign * (16.0f - 8.0f * press)));
                    armAccess.bobin_craft$grenadeArm(matrices, vertexConsumers, light, 0.06f, 0.0f, offArm);
                    matrices.pop();
                }

                matrices.pop();
                return true;
            }
        }
        return false;
    }

    private static void renderRemoteIndicatorLed(
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            float r,
            float g,
            float b,
            boolean lit,
            int worldLight) {
        matrices.push();
        matrices.translate(0.0f, 0.14f, -0.04f);
        matrices.scale(0.42f, 0.42f, 0.42f);
        int ledLight = lit ? LightmapTextureManager.MAX_LIGHT_COORDINATE : worldLight;
        float mult = lit ? 1.0f : 0.25f;
        REMOTE_LED.render(
                matrices,
                vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(LED_TEXTURE)),
                ledLight,
                OverlayTexture.DEFAULT_UV,
                r * mult,
                g * mult,
                b * mult,
                1.0f);
        matrices.pop();
    }

    public static void poseThirdPerson(PlayerEntity player, BipedEntityModel<?> model, float tickDelta) {
        if (player == null || player.getWorld() == null) return;
        ActiveAnimation anim = ANIMATIONS.get(player.getUuid());
        if (anim == null) return;
        float age = age(player, anim, tickDelta);
        if (age < 0.0f || age >= anim.mode().durationTicks) return;
        Arm arm = anim.hand() == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        Grom7ArmPose.apply(model, arm, anim.mode(), age);
    }

    public static boolean canRenderHud(MinecraftClient client) {
        if (client == null || client.world == null || client.player == null || client.textRenderer == null) {
            return false;
        }
        if (client.options == null || client.options.hudHidden || client.currentScreen != null) {
            return false;
        }
        PlayerEntity player = client.player;
        if (player.isRemoved() || !player.isAlive() || player.isSpectator() || player.hasVehicle()) {
            return false;
        }
        if (player.getWorld() != client.world) {
            return false;
        }
        return client.world.isChunkLoaded(player.getBlockX() >> 4, player.getBlockZ() >> 4);
    }

    public static boolean shouldPreviewAttachment(PlayerEntity player) {
        if (player == null || player.getWorld() == null) return false;
        boolean mainCharge = player.getMainHandStack().isOf(Grom7Content.GROM7_CHARGE)
                && Grom7Manager.canUse(player, Hand.MAIN_HAND);
        boolean offCharge = player.getOffHandStack().isOf(Grom7Content.GROM7_CHARGE)
                && Grom7Manager.canUse(player, Hand.OFF_HAND);
        if (!mainCharge && !offCharge) {
            return false;
        }
        if (ANIMATIONS.containsKey(player.getUuid())) {
            return false;
        }
        return !player.getItemCooldownManager().isCoolingDown(Grom7Content.GROM7_CHARGE);
    }

    public static void renderHud(DrawContext context) {
        if (context == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (!canRenderHud(client)) {
            return;
        }
        PlayerEntity player = client.player;
        TextRenderer tr = client.textRenderer;
        int sw = context.getScaledWindowWidth();
        int sh = context.getScaledWindowHeight();

        if (shouldPreviewAttachment(player)) {
            Grom7Placement.PlacementResult res = Grom7Placement.raycastAndEvaluate(client.world, player);
            if (res.valid()) {
                String prompt = "[Shift + ПКМ] Прикрепить заряд";
                int pw = tr.getWidth(prompt);
                context.drawTextWithShadow(tr, prompt, (sw - pw) / 2, sh / 2 + 14, 0xFFE2E8D5);
            } else if (player.isSneaking()
                    && !"out_of_range".equals(res.reason())
                    && !"unloaded_chunk".equals(res.reason())
                    && !"invalid_player_or_world".equals(res.reason())) {
                String warn = "[Недостаточно места для крепления]";
                int ww = tr.getWidth(warn);
                context.drawTextWithShadow(tr, warn, (sw - ww) / 2, sh / 2 + 14, 0xFFE07A5F);
            }
        }

        boolean holdingRemote = isHoldingRemote(player);
        if (holdingRemote) {
            Grom7ChargeEntity lookedCharge = Grom7Manager.findLookedAtCharge(client.world, player);
            if (lookedCharge != null) {
                String bindPrompt = lookedCharge.isBoundToRemote()
                        ? (player.isSneaking()
                                ? "[Shift + ЛКМ] Отвязать заряд #" + String.format("%02d", lookedCharge.getChargeNumber())
                                : "[ЛКМ] Выбрать заряд #" + String.format("%02d", lookedCharge.getChargeNumber()))
                        : "[ЛКМ] Привязать заряд #" + String.format("%02d", lookedCharge.getChargeNumber());
                int bw = tr.getWidth(bindPrompt);
                context.drawTextWithShadow(tr, bindPrompt, (sw - bw) / 2, sh / 2 + 14, 0xFFA8E6A2);
            }

            int boxW = 156;
            int boxH = 52;
            int x = sw - boxW - 12;
            int y = sh - boxH - 28;
            context.fill(x, y, x + boxW, y + boxH, 0xA8121814);
            context.fill(x, y, x + boxW, y + 1, 0xFF5A6E58);

            context.drawTextWithShadow(tr, "ГРОМ-7 (Слоты: " + hudState.totalCharges() + "/3)", x + 6, y + 5, 0xFFD8E4C8);
            if (hudState.totalCharges() <= 0) {
                context.drawTextWithShadow(tr, "Заряд: нет привязки", x + 6, y + 17, 0xFF9AA498);
                context.drawTextWithShadow(tr, "ЛКМ по заряду — привязать", x + 6, y + 28, 0xFF9AA498);
                context.drawTextWithShadow(tr, "Сигнал: ожидание", x + 6, y + 39, 0xFF9AA498);
            } else {
                String numStr = String.format("Заряд: #%02d [R-отвязка]", hudState.selectedChargeNumber());
                String stateStr = "Состояние: " + hudState.stateText();
                String sigStr = "Сигнал: " + hudState.signalText();
                int sigColor = switch (hudState.signalText()) {
                    case "высокий" -> 0xFF78D672;
                    case "средний" -> 0xFFE6C855;
                    case "слабый" -> 0xFFE68A45;
                    default -> 0xFFE05245;
                };
                context.drawTextWithShadow(tr, numStr, x + 6, y + 17, 0xFFE4ECE0);
                context.drawTextWithShadow(tr, stateStr, x + 6, y + 28, 0xFFCBD6C4);
                context.drawTextWithShadow(tr, sigStr, x + 6, y + 39, sigColor);
            }
        }
    }
}
