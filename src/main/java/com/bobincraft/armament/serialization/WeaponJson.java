package com.bobincraft.armament.serialization;

import com.bobincraft.armament.api.*;
import com.bobincraft.armament.definition.WeaponDefinition;
import com.bobincraft.armament.validation.Checks;
import com.google.gson.*;
import java.util.*;

/** Explicit codecs: validated constructors are always called; no reflective record decoding. */
public final class WeaponJson {
    private WeaponJson() {}

    public static WeaponDefinition readDefinition(String json) {
        JsonFields f = new JsonFields(StrictJson.parse(json), "$", "schema_version", "id", "actions");
        JsonArray values = f.array("actions", 0, 64);
        Set<WeaponId> actions = new LinkedHashSet<>();
        for (int i = 0; i < values.size(); i++) {
            WeaponId id = JsonFields.idValue(values.get(i), "$.actions[" + i + "]");
            Checks.require(actions.add(id), "$.actions[" + i + "]", "duplicate action '" + id + "'");
        }
        return new WeaponDefinition(f.intValue("schema_version"), f.id("id"), actions);
    }

    public static String writeDefinition(WeaponDefinition value) {
        JsonObject json = new JsonObject();
        json.addProperty("schema_version", value.schemaVersion());
        json.addProperty("id", value.id().toString());
        JsonArray actions = new JsonArray();
        value.actions().stream().sorted().forEach(id -> actions.add(id.toString()));
        json.add("actions", actions);
        return StrictJson.write(json);
    }

    public static WeaponStateSnapshot readSnapshot(String json) {
        JsonFields f = new JsonFields(StrictJson.parse(json), "$", "owner_id", "instance_id", "weapon_id",
                "hand", "server_tick", "revision", "action_id", "action_sequence", "action_start_tick");
        return new WeaponStateSnapshot(f.uuid("owner_id"), f.uuid("instance_id"), f.id("weapon_id"),
                f.enumeration("hand", WeaponUserView.Hand.class), f.integer("server_tick"), f.integer("revision"),
                f.has("action_id") ? Optional.of(f.id("action_id")) : Optional.empty(),
                f.integer("action_sequence"), f.integer("action_start_tick"));
    }

    public static String writeSnapshot(WeaponStateSnapshot value) {
        JsonObject json = identity(value.ownerId(), value.instanceId(), value.weaponId(), value.hand());
        json.addProperty("server_tick", value.serverTick());
        json.addProperty("revision", value.revision());
        value.actionId().ifPresent(id -> json.addProperty("action_id", id.toString()));
        json.addProperty("action_sequence", value.actionSequence());
        json.addProperty("action_start_tick", value.actionStartTick());
        return StrictJson.write(json);
    }

    public static WeaponActionEvent readActionEvent(String json) {
        JsonFields f = new JsonFields(StrictJson.parse(json), "$", "owner_id", "instance_id", "weapon_id",
                "hand", "action_id", "sequence", "start_tick");
        return new WeaponActionEvent(f.uuid("owner_id"), f.uuid("instance_id"), f.id("weapon_id"),
                f.enumeration("hand", WeaponUserView.Hand.class), f.id("action_id"), f.integer("sequence"), f.integer("start_tick"));
    }

    public static String writeActionEvent(WeaponActionEvent value) {
        JsonObject json = identity(value.ownerId(), value.instanceId(), value.weaponId(), value.hand());
        json.addProperty("action_id", value.actionId().toString());
        json.addProperty("sequence", value.sequence());
        json.addProperty("start_tick", value.startTick());
        return StrictJson.write(json);
    }

    private static JsonObject identity(UUID owner, UUID instance, WeaponId weapon, WeaponUserView.Hand hand) {
        JsonObject json = new JsonObject();
        json.addProperty("owner_id", owner.toString());
        json.addProperty("instance_id", instance.toString());
        json.addProperty("weapon_id", weapon.toString());
        json.addProperty("hand", hand.name().toLowerCase(Locale.ROOT));
        return json;
    }
}
