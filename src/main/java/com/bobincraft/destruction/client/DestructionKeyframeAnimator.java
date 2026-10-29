package com.bobincraft.destruction.client;

import com.bobincraft.BobinCraft;
import com.bobincraft.combat.client.ImpactDebrisClient;
import com.bobincraft.destruction.StructureNetworkSync;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Stage 5 lifecycle adapter: legacy assets remain parsed for the later Stage 7 redesign;
 * cracks remain transient. Source-derived persistent meshes belong solely to StructureDebrisRenderer. */
@Environment(EnvType.CLIENT)
public final class DestructionKeyframeAnimator {
    public static int maxConcurrentActiveAnimations = 6;
    public static int maxSettledWreckagePieces = 96;
    private static final Identifier CRACK_DECAL_TEX = new Identifier(BobinCraft.MOD_ID, "textures/decal/structural_crack.png");

    public record BoneCube(float ox, float oy, float oz, float sx, float sy, float sz, String material) {}
    public record GeoBone(String name, float px, float py, float pz, List<BoneCube> cubes) {}
    public record KeyframeSample(float timeSec, float x, float y, float z) {}
    public record BoneChannel(List<KeyframeSample> positions, List<KeyframeSample> rotations) {
        public float[] samplePos(float tSec) { return interpolate(positions, tSec); }
        public float[] sampleRot(float tSec) { return interpolate(rotations, tSec); }

        private static float[] interpolate(List<KeyframeSample> keys, float t) {
            if (keys == null || keys.isEmpty()) return new float[]{0f, 0f, 0f};
            if (t <= keys.get(0).timeSec) {
                KeyframeSample k = keys.get(0);
                return new float[]{k.x, k.y, k.z};
            }
            for (int i = 0; i < keys.size() - 1; i++) {
                KeyframeSample a = keys.get(i);
                KeyframeSample b = keys.get(i + 1);
                if (t >= a.timeSec && t <= b.timeSec) {
                    float span = Math.max(0.0001f, b.timeSec - a.timeSec);
                    float f = (t - a.timeSec) / span;
                    float s = f * f * (3.0f - 2.0f * f);
                    return new float[]{
                            MathHelper.lerp(s, a.x, b.x),
                            MathHelper.lerp(s, a.y, b.y),
                            MathHelper.lerp(s, a.z, b.z)
                    };
                }
            }
            KeyframeSample last = keys.get(keys.size() - 1);
            return new float[]{last.x, last.y, last.z};
        }
    }

    public record AnimationClip(String name, float lengthSec, Map<String, BoneChannel> boneChannels) {}

    public record ActiveCrackDecal(Vec3d position,long startTick,int durationTicks) {}

    private static final Map<String, List<GeoBone>> MODEL_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, AnimationClip> CLIP_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, ActiveCrackDecal> HIT_CRACK_DECALS = new ConcurrentHashMap<>();


    static {
        loadResources();
    }

    private DestructionKeyframeAnimator() {}

    public static synchronized void loadResources() {
        MODEL_CACHE.clear();
        CLIP_CACHE.clear();
        String[] models = {"bridge_collapse", "refinery_tank_collapse", "substation_collapse"};
        for (String m : models) {
            loadGeoModel(m, "/assets/bobin_craft/destruction/models/" + m + ".geo.json");
            loadAnimFile("/assets/bobin_craft/destruction/animations/" + m + ".animation.json");
        }
    }

    private static void loadGeoModel(String modelKey, String resourcePath) {
        try (InputStream in = DestructionKeyframeAnimator.class.getResourceAsStream(resourcePath)) {
            if (in == null) return;
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray geoArr = root.getAsJsonArray("minecraft:geometry");
            if (geoArr == null || geoArr.size() == 0) return;
            JsonObject geo = geoArr.get(0).getAsJsonObject();
            JsonArray bonesArr = geo.getAsJsonArray("bones");
            List<GeoBone> bones = new ArrayList<>();
            for (JsonElement el : bonesArr) {
                JsonObject bObj = el.getAsJsonObject();
                String name = bObj.get("name").getAsString();
                JsonArray piv = bObj.getAsJsonArray("pivot");
                float px = piv.get(0).getAsFloat() / 16.0f;
                float py = piv.get(1).getAsFloat() / 16.0f;
                float pz = piv.get(2).getAsFloat() / 16.0f;
                List<BoneCube> cubes = new ArrayList<>();
                if (bObj.has("cubes")) {
                    for (JsonElement cEl : bObj.getAsJsonArray("cubes")) {
                        JsonObject cObj = cEl.getAsJsonObject();
                        JsonArray orig = cObj.getAsJsonArray("origin");
                        JsonArray sz = cObj.getAsJsonArray("size");
                        String mat = cObj.has("material") ? cObj.get("material").getAsString() : "cracked_concrete";
                        cubes.add(new BoneCube(
                                orig.get(0).getAsFloat() / 16.0f,
                                orig.get(1).getAsFloat() / 16.0f,
                                orig.get(2).getAsFloat() / 16.0f,
                                sz.get(0).getAsFloat() / 16.0f,
                                sz.get(1).getAsFloat() / 16.0f,
                                sz.get(2).getAsFloat() / 16.0f,
                                mat
                        ));
                    }
                }
                if (!cubes.isEmpty()) {
                    bones.add(new GeoBone(name, px, py, pz, List.copyOf(cubes)));
                }
            }
            MODEL_CACHE.put(modelKey, List.copyOf(bones));
        } catch (Exception ignored) {
        }
    }

    private static void loadAnimFile(String resourcePath) {
        try (InputStream in = DestructionKeyframeAnimator.class.getResourceAsStream(resourcePath)) {
            if (in == null) return;
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject anims = root.getAsJsonObject("animations");
            if (anims == null) return;
            for (Map.Entry<String, JsonElement> entry : anims.entrySet()) {
                String clipName = entry.getKey();
                JsonObject cObj = entry.getValue().getAsJsonObject();
                float len = cObj.has("animation_length") ? cObj.get("animation_length").getAsFloat() : 1.25f;
                Map<String, BoneChannel> channels = new LinkedHashMap<>();
                if (cObj.has("bones")) {
                    JsonObject bonesObj = cObj.getAsJsonObject("bones");
                    for (Map.Entry<String, JsonElement> bEntry : bonesObj.entrySet()) {
                        String boneName = bEntry.getKey();
                        JsonObject chObj = bEntry.getValue().getAsJsonObject();
                        List<KeyframeSample> posKeys = parseKeyframes(chObj.getAsJsonObject("position"), 1.0f / 16.0f);
                        List<KeyframeSample> rotKeys = parseKeyframes(chObj.getAsJsonObject("rotation"), 1.0f);
                        channels.put(boneName, new BoneChannel(posKeys, rotKeys));
                    }
                }
                CLIP_CACHE.put(clipName, new AnimationClip(clipName, len, Map.copyOf(channels)));
            }
        } catch (Exception ignored) {
        }
    }

    private static List<KeyframeSample> parseKeyframes(JsonObject kfObj, float scale) {
        if (kfObj == null) return List.of();
        List<KeyframeSample> list = new ArrayList<>();
        for (Map.Entry<String, JsonElement> e : kfObj.entrySet()) {
            try {
                float t = Float.parseFloat(e.getKey());
                JsonArray arr = e.getValue().getAsJsonArray();
                list.add(new KeyframeSample(
                        t,
                        arr.get(0).getAsFloat() * scale,
                        arr.get(1).getAsFloat() * scale,
                        arr.get(2).getAsFloat() * scale
                ));
            } catch (Exception ignored) {
            }
        }
        list.sort(Comparator.comparingDouble(KeyframeSample::timeSec));
        return List.copyOf(list);
    }

    /**
     * Records a localized crack decal on {@code STRUCTURE_HIT} (HP > 0) without playing any collapse animation.
     */
    public static void addHitCrackDecal(ClientWorld world, String structureId, Vec3d impactPoint) {
        if (world == null || structureId == null || impactPoint == null) return;
        HIT_CRACK_DECALS.put(structureId, new ActiveCrackDecal(impactPoint, world.getTime(), 120));
    }

    /** Stage 5: stage packets own crack feedback only. Geometry ownership transfers on real server commits. */
    public static synchronized void startScenario(ClientWorld world, StructureNetworkSync.TransitionScenarioPacket packet) {
        if(world!=null&&packet!=null)addHitCrackDecal(world,packet.structureId(),packet.impactPoint());
    }

    /** Maintenance runs on client ticks; rendering cannot create, move or settle wreckage. */
    public static void tick(ClientWorld world) {
        if(world!=null)HIT_CRACK_DECALS.values().removeIf(d->world.getTime()-d.startTick>d.durationTicks);
    }

    public static void renderActive(MatrixStack matrices, VertexConsumerProvider consumers, Vec3d cameraPos, float tickDelta) {
        var client=MinecraftClient.getInstance();if(client.world==null||consumers==null)return;
        long now=client.world.getTime();
        for(var decal:HIT_CRACKDecalsSnapshot()) {
            if(cameraPos.squaredDistanceTo(decal.position)>128*128)continue;
            float progress=MathHelper.clamp(((now-decal.startTick)+tickDelta)/decal.durationTicks,0,1);
            int light=ImpactDebrisClient.sampleFragmentLight(client.world,decal.position.x,decal.position.y+.35,decal.position.z,-1);
            renderCrackOverlay(matrices,consumers,cameraPos,decal.position,light,1-progress*.75f);
        }
    }

    private static Collection<ActiveCrackDecal> HIT_CRACKDecalsSnapshot() {
        return HIT_CRACK_DECALS.values();
    }

    private static void renderCrackOverlay(MatrixStack matrices,
                                           VertexConsumerProvider consumers,
                                           Vec3d cam,
                                           Vec3d impact,
                                           int light,
                                           float alpha) {
        VertexConsumer vc = consumers.getBuffer(RenderLayer.getEntityTranslucent(CRACK_DECAL_TEX));
        matrices.push();
        matrices.translate(impact.x - cam.x, impact.y + 0.03 - cam.y, impact.z - cam.z);
        MatrixStack.Entry entry = matrices.peek();
        int a = MathHelper.clamp((int) (alpha * 235), 0, 255);
        float r = 1.45f;
        vc.vertex(entry.getPositionMatrix(), -r, 0f, -r).color(255, 255, 255, a).texture(0f, 0f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry.getNormalMatrix(), 0f, 1f, 0f).next();
        vc.vertex(entry.getPositionMatrix(), -r, 0f,  r).color(255, 255, 255, a).texture(0f, 1f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry.getNormalMatrix(), 0f, 1f, 0f).next();
        vc.vertex(entry.getPositionMatrix(),  r, 0f,  r).color(255, 255, 255, a).texture(1f, 1f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry.getNormalMatrix(), 0f, 1f, 0f).next();
        vc.vertex(entry.getPositionMatrix(),  r, 0f, -r).color(255, 255, 255, a).texture(1f, 0f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry.getNormalMatrix(), 0f, 1f, 0f).next();
        matrices.pop();
    }

    private static BlockState resolveMaterialState(String mat) {
        if ("cracked_asphalt".equals(mat)) return com.bobincraft.warzone.ForestBlocks.CRACKED_ASPHALT.getDefaultState();
        if ("cracked_concrete".equals(mat)) return com.bobincraft.warzone.CombatZoneBlocks.CRACKED_CONCRETE.getDefaultState();
        if ("mossy_concrete".equals(mat)) return com.bobincraft.warzone.CombatZoneBlocks.MOSSY_CONCRETE.getDefaultState();
        if ("rusted_sheet_metal".equals(mat)) return com.bobincraft.warzone.CombatZoneBlocks.RUSTED_SHEET_METAL.getDefaultState();
        if ("pipe_iron".equals(mat)) return com.bobincraft.warzone.CombatZoneBlocks.UTILITY_PIPE.getDefaultState();
        if ("busbar_metal".equals(mat)) return com.bobincraft.warzone.CombatZoneBlocks.BUSBAR.getDefaultState();
        // Unknown visual material is not permission to invent a cobblestone structural fragment.
        return Blocks.AIR.getDefaultState();
    }

    public static synchronized void clear() {
        HIT_CRACK_DECALS.clear();
    }

    public static synchronized int settledWreckageCount() {
        return 0;
    }

    public static Map<String, List<GeoBone>> loadedModels() {
        return Collections.unmodifiableMap(MODEL_CACHE);
    }

    public static Map<String, AnimationClip> loadedClips() {
        return Collections.unmodifiableMap(CLIP_CACHE);
    }

    public static int activeSequenceCount() {
        return 0;
    }
}
