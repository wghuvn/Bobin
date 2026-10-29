package com.bobincraft.destruction;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configurable definition of a destructible industrial structure type (Section 3.1).
 * Supports JSON configuration loading as well as built-in defaults.
 */
public final class DestructibleStructureType {
    public record StageSpec(float healthFraction, String model) {}

    public enum Category {
        REFINERY_TANK,
        SUBSTATION_TRANSFORMER,
        SUBSTATION_EQUIPMENT,
        BRIDGE_SECTION,
        INDUSTRIAL_GENERIC
    }

    private static final Map<String, DestructibleStructureType> TYPES = new ConcurrentHashMap<>();

    public static final DestructibleStructureType REFINERY_STORAGE_TANK = registerDefault(new DestructibleStructureType(
            "refinery_storage_tank", Category.REFINERY_TANK, 1000.0f, true, true, 1200, 1.5f,
            List.of(
                    new StageSpec(1.0f, "intact"),
                    new StageSpec(0.7f, "damaged"),
                    new StageSpec(0.3f, "critical"),
                    new StageSpec(0.0f, "destroyed")
            )
    ));

    public static final DestructibleStructureType SUBSTATION_TRANSFORMER = registerDefault(new DestructibleStructureType(
            "substation_transformer", Category.SUBSTATION_TRANSFORMER, 650.0f, true, true, 800, 1.0f,
            List.of(
                    new StageSpec(1.0f, "intact"),
                    new StageSpec(0.7f, "damaged"),
                    new StageSpec(0.3f, "critical"),
                    new StageSpec(0.0f, "destroyed")
            )
    ));

    public static final DestructibleStructureType SUBSTATION_EQUIPMENT = registerDefault(new DestructibleStructureType(
            "substation_equipment", Category.SUBSTATION_EQUIPMENT, 400.0f, true, false, 400, 0.5f,
            List.of(
                    new StageSpec(1.0f, "intact"),
                    new StageSpec(0.7f, "damaged"),
                    new StageSpec(0.3f, "critical"),
                    new StageSpec(0.0f, "destroyed")
            )
    ));

    public static final DestructibleStructureType BRIDGE_SECTION = registerDefault(new DestructibleStructureType(
            "bridge_section", Category.BRIDGE_SECTION, 800.0f, true, false, 0, 0.0f,
            List.of(
                    new StageSpec(1.0f, "intact"),
                    new StageSpec(0.7f, "damaged"),
                    new StageSpec(0.3f, "critical"),
                    new StageSpec(0.0f, "destroyed")
            )
    ));

    static {
        loadFromResources();
    }

    private final String id;
    private final Category category;
    private final float maxHealth;
    private final boolean persistent;
    private final boolean flammable;
    private final int defaultBurnTicks;
    private final float fireDamagePerSecond;
    private final List<StageSpec> damageStages;

    public DestructibleStructureType(String id, Category category, float maxHealth, boolean persistent,
                                     boolean flammable, int defaultBurnTicks, float fireDamagePerSecond,
                                     List<StageSpec> damageStages) {
        this.id = Objects.requireNonNull(id);
        this.category = Objects.requireNonNull(category);
        this.maxHealth = Math.max(1.0f, maxHealth);
        this.persistent = persistent;
        this.flammable = flammable;
        this.defaultBurnTicks = Math.max(0, defaultBurnTicks);
        this.fireDamagePerSecond = Math.max(0.0f, fireDamagePerSecond);
        this.damageStages = List.copyOf(damageStages);
    }

    private static DestructibleStructureType registerDefault(DestructibleStructureType type) {
        TYPES.put(type.id, type);
        return type;
    }

    public static void register(DestructibleStructureType type) {
        TYPES.put(type.id(), type);
    }

    public static DestructibleStructureType get(String id) {
        return TYPES.getOrDefault(id, REFINERY_STORAGE_TANK);
    }

    public static Collection<DestructibleStructureType> all() {
        return Collections.unmodifiableCollection(TYPES.values());
    }

    public static DestructibleStructureType fromJson(JsonObject obj) {
        String id = obj.get("id").getAsString();
        float maxHealth = obj.has("max_health") ? obj.get("max_health").getAsFloat() : 1000.0f;
        boolean persistent = !obj.has("persistent") || obj.get("persistent").getAsBoolean();
        Category cat = Category.INDUSTRIAL_GENERIC;
        if (id.contains("tank")) cat = Category.REFINERY_TANK;
        else if (id.contains("transformer")) cat = Category.SUBSTATION_TRANSFORMER;
        else if (id.contains("substation")) cat = Category.SUBSTATION_EQUIPMENT;
        else if (id.contains("bridge")) cat = Category.BRIDGE_SECTION;
        boolean flammable = obj.has("flammable") ? obj.get("flammable").getAsBoolean()
                : (cat == Category.REFINERY_TANK || cat == Category.SUBSTATION_TRANSFORMER);
        int burnTicks = obj.has("burn_ticks") ? obj.get("burn_ticks").getAsInt()
                : (cat == Category.REFINERY_TANK ? 1200 : cat == Category.SUBSTATION_TRANSFORMER ? 800 : 0);
        float fireDps = obj.has("fire_dps") ? obj.get("fire_dps").getAsFloat()
                : (cat == Category.REFINERY_TANK ? 1.5f : cat == Category.SUBSTATION_TRANSFORMER ? 1.0f : 0.0f);
        List<StageSpec> stages = new ArrayList<>();
        if (obj.has("damage_stages")) {
            JsonArray arr = obj.getAsJsonArray("damage_stages");
            for (JsonElement el : arr) {
                JsonObject st = el.getAsJsonObject();
                stages.add(new StageSpec(st.get("health_fraction").getAsFloat(), st.get("model").getAsString()));
            }
        } else {
            stages.addAll(REFINERY_STORAGE_TANK.damageStages);
        }
        return new DestructibleStructureType(id, cat, maxHealth, persistent, flammable, burnTicks, fireDps, stages);
    }

    private static void loadFromResources() {
        String[] paths = {
                "/data/bobin_craft/destruction/refinery_storage_tank.json",
                "/data/bobin_craft/destruction/substation_transformer.json",
                "/data/bobin_craft/destruction/substation_equipment.json",
                "/data/bobin_craft/destruction/bridge_section.json"
        };
        for (String path : paths) {
            try (InputStream in = DestructibleStructureType.class.getResourceAsStream(path)) {
                if (in != null) {
                    JsonObject obj = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                    register(fromJson(obj));
                }
            } catch (Exception ignored) {
            }
        }
    }

    public StructureDamageState resolveStage(float currentHealth) {
        if (!Float.isFinite(currentHealth) || currentHealth <= 0.0f) return StructureDamageState.DESTROYED;
        float frac = currentHealth / maxHealth;
        for (int i = damageStages.size() - 1; i >= 0; i--) {
            StageSpec spec = damageStages.get(i);
            if (spec.healthFraction <= 0.0f) continue;
            if (frac <= spec.healthFraction) {
                StructureDamageState mapped = StructureDamageState.byName(spec.model);
                if (mapped != null) return mapped;
            }
        }
        return StructureDamageState.fromFraction(frac);
    }

    public String id() { return id; }
    public Category category() { return category; }
    public float maxHealth() { return maxHealth; }
    public boolean persistent() { return persistent; }
    public boolean flammable() { return flammable; }
    public int defaultBurnTicks() { return defaultBurnTicks; }
    public float fireDamagePerSecond() { return fireDamagePerSecond; }
    public List<StageSpec> damageStages() { return damageStages; }
}
