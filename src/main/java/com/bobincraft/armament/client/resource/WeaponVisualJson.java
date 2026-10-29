package com.bobincraft.armament.client.resource;

import com.bobincraft.armament.api.WeaponId;
import com.bobincraft.armament.serialization.*;
import com.bobincraft.armament.validation.*;
import com.google.gson.*;
import java.util.*;
import java.util.function.Supplier;

/** Client metadata codecs. No asset loading, model evaluation or renderer registration. */
public final class WeaponVisualJson {
    private WeaponVisualJson() {}

    public static WeaponVisualDefinition readVisual(String json) {
        JsonFields f = fields(StrictJson.parse(json), "$", "schema_version", "id", "model", "rig_id", "sockets_id", "events_id", "views");
        JsonObject viewObject = object(f.get("views"), "$.views", 3, 6);
        Map<WeaponVisualDefinition.View, TransformDefinition> views = new EnumMap<>(WeaponVisualDefinition.View.class);
        for (String name : viewObject.keySet()) {
            WeaponVisualDefinition.View view = Arrays.stream(WeaponVisualDefinition.View.values())
                    .filter(v -> v.name().toLowerCase(Locale.ROOT).equals(name)).findFirst()
                    .orElseThrow(() -> new DefinitionValidationException("$.views." + name, "unknown view"));
            views.put(view, readTransform(viewObject.get(name), "$.views." + name));
        }
        return new WeaponVisualDefinition(f.intValue("schema_version"), f.id("id"), f.id("model"),
                f.id("rig_id"), f.id("sockets_id"), f.id("events_id"), views);
    }

    public static WeaponRigDefinition readRig(String json) {
        JsonFields f = fields(StrictJson.parse(json), "$", "schema_version", "id", "nodes", "roles");
        JsonArray source = f.array("nodes", 1, 512);
        List<WeaponRigDefinition.Node> nodes = new ArrayList<>();
        for (int i = 0; i < source.size(); i++) {
            String path = "$.nodes[" + i + "]";
            JsonFields n = fields(source.get(i), path, "name", "node_index", "parent");
            nodes.add(at(path, () -> new WeaponRigDefinition.Node(n.string("name"), n.intValue("node_index"),
                    n.has("parent") ? Optional.of(n.string("parent")) : Optional.empty())));
        }
        JsonObject sourceRoles = object(f.get("roles"), "$.roles", 1, 64);
        Map<String, String> roles = new LinkedHashMap<>();
        sourceRoles.entrySet().forEach(e -> roles.put(e.getKey(), JsonFields.stringValue(e.getValue(), "$.roles." + e.getKey())));
        return new WeaponRigDefinition(f.intValue("schema_version"), f.id("id"), nodes, roles);
    }

    public static WeaponSocketsDefinition readSockets(String json) {
        JsonFields f = fields(StrictJson.parse(json), "$", "schema_version", "id", "rig_id", "sockets");
        JsonArray source = f.array("sockets", 0, 128);
        List<WeaponSocketsDefinition.Socket> sockets = new ArrayList<>();
        for (int i = 0; i < source.size(); i++) {
            String path = "$.sockets[" + i + "]";
            JsonFields s = fields(source.get(i), path, "name", "node", "transform");
            TransformDefinition transform = readTransform(s.get("transform"), path + ".transform");
            sockets.add(at(path, () -> new WeaponSocketsDefinition.Socket(s.string("name"), s.string("node"), transform)));
        }
        return new WeaponSocketsDefinition(f.intValue("schema_version"), f.id("id"), f.id("rig_id"), sockets);
    }

    public static AnimationEventsDefinition readEvents(String json) {
        JsonFields f = fields(StrictJson.parse(json), "$", "schema_version", "id", "clips");
        JsonArray source = f.array("clips", 0, 128);
        List<AnimationEventsDefinition.Clip> clips = new ArrayList<>();
        for (int i = 0; i < source.size(); i++) {
            String path = "$.clips[" + i + "]";
            JsonFields c = fields(source.get(i), path, "name", "duration_seconds", "markers");
            JsonArray markerSource = c.array("markers", 0, 1024);
            List<AnimationEventsDefinition.Marker> markers = new ArrayList<>();
            for (int j = 0; j < markerSource.size(); j++) {
                String markerPath = path + ".markers[" + j + "]";
                JsonFields m = fields(markerSource.get(j), markerPath, "name", "time_seconds", "kind", "event");
                Checks.require(m.string("kind").equals("cosmetic"), markerPath + ".kind", "only cosmetic markers are allowed");
                markers.add(at(markerPath, () -> new AnimationEventsDefinition.Marker(m.string("name"), m.number("time_seconds"), m.id("event"))));
            }
            clips.add(at(path, () -> new AnimationEventsDefinition.Clip(c.string("name"), c.number("duration_seconds"), markers)));
        }
        return new AnimationEventsDefinition(f.intValue("schema_version"), f.id("id"), clips);
    }

    private static TransformDefinition readTransform(JsonElement value, String path) {
        JsonFields f = fields(value, path, "translation", "rotation", "scale");
        List<Double> translation = vector(f, "translation", 3);
        List<Double> rotation = vector(f, "rotation", 4);
        List<Double> scale = vector(f, "scale", 3);
        return at(path, () -> new TransformDefinition(translation, rotation, scale));
    }

    private static List<Double> vector(JsonFields f, String key, int size) {
        JsonArray source = f.array(key, size, size);
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < size; i++) result.add(JsonFields.numberValue(source.get(i), f.at(key) + "[" + i + "]"));
        return result;
    }

    public static String writeVisual(WeaponVisualDefinition value) {
        JsonObject result = header(value.schemaVersion(), value.id());
        result.addProperty("model", value.model().toString());
        result.addProperty("rig_id", value.rigId().toString());
        result.addProperty("sockets_id", value.socketsId().toString());
        result.addProperty("events_id", value.eventsId().toString());
        JsonObject views = new JsonObject();
        Arrays.stream(WeaponVisualDefinition.View.values()).filter(value.views()::containsKey).forEach(view ->
                views.add(view.name().toLowerCase(Locale.ROOT), transform(value.views().get(view))));
        result.add("views", views);
        return StrictJson.write(result);
    }

    public static String writeRig(WeaponRigDefinition value) {
        JsonObject result = header(value.schemaVersion(), value.id());
        JsonArray nodes = new JsonArray();
        for (WeaponRigDefinition.Node node : value.nodes()) {
            JsonObject n = new JsonObject();
            n.addProperty("name", node.name());
            n.addProperty("node_index", node.nodeIndex());
            node.parent().ifPresent(parent -> n.addProperty("parent", parent));
            nodes.add(n);
        }
        result.add("nodes", nodes);
        JsonObject roles = new JsonObject();
        value.roles().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> roles.addProperty(e.getKey(), e.getValue()));
        result.add("roles", roles);
        return StrictJson.write(result);
    }

    public static String writeSockets(WeaponSocketsDefinition value) {
        JsonObject result = header(value.schemaVersion(), value.id());
        result.addProperty("rig_id", value.rigId().toString());
        JsonArray sockets = new JsonArray();
        for (WeaponSocketsDefinition.Socket socket : value.sockets()) {
            JsonObject s = new JsonObject();
            s.addProperty("name", socket.name());
            s.addProperty("node", socket.node());
            s.add("transform", transform(socket.transform()));
            sockets.add(s);
        }
        result.add("sockets", sockets);
        return StrictJson.write(result);
    }

    public static String writeEvents(AnimationEventsDefinition value) {
        JsonObject result = header(value.schemaVersion(), value.id());
        JsonArray clips = new JsonArray();
        for (AnimationEventsDefinition.Clip clip : value.clips()) {
            JsonObject c = new JsonObject();
            c.addProperty("name", clip.name());
            c.addProperty("duration_seconds", clip.durationSeconds());
            JsonArray markers = new JsonArray();
            for (AnimationEventsDefinition.Marker marker : clip.markers()) {
                JsonObject m = new JsonObject();
                m.addProperty("name", marker.name());
                m.addProperty("time_seconds", marker.timeSeconds());
                m.addProperty("kind", "cosmetic");
                m.addProperty("event", marker.event().toString());
                markers.add(m);
            }
            c.add("markers", markers);
            clips.add(c);
        }
        result.add("clips", clips);
        return StrictJson.write(result);
    }

    private static JsonObject transform(TransformDefinition t) {
        JsonObject result = new JsonObject();
        result.add("translation", numbers(t.translation()));
        result.add("rotation", numbers(t.rotation()));
        result.add("scale", numbers(t.scale()));
        return result;
    }

    private static JsonArray numbers(List<Double> values) {
        JsonArray result = new JsonArray();
        values.forEach(result::add);
        return result;
    }

    private static JsonObject header(int version, WeaponId id) {
        JsonObject result = new JsonObject();
        result.addProperty("schema_version", version);
        result.addProperty("id", id.toString());
        return result;
    }

    private static JsonObject object(JsonElement value, String path, int min, int max) {
        Checks.require(value.isJsonObject(), path, "expected object");
        JsonObject result = value.getAsJsonObject();
        Checks.require(result.size() >= min && result.size() <= max, path, "expected " + min + ".." + max + " fields");
        return result;
    }

    private static JsonFields fields(JsonElement value, String path, String... names) { return new JsonFields(value, path, names); }

    private static <T> T at(String path, Supplier<T> constructor) {
        try { return constructor.get(); }
        catch (DefinitionValidationException e) {
            // Field readers already know their full path; only rebase constructor-local paths.
            if (e.path().equals(path) || e.path().startsWith(path + ".")) throw e;
            throw new DefinitionValidationException(path + e.path().substring(1), e.reason());
        }
    }
}
