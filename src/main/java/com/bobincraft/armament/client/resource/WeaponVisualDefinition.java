package com.bobincraft.armament.client.resource;

import com.bobincraft.armament.api.WeaponId;
import com.bobincraft.armament.validation.Checks;
import java.util.Map;

/** Client resource references only; not part of WeaponDefinition or server state. */
public record WeaponVisualDefinition(int schemaVersion, WeaponId id, WeaponId model,
        WeaponId rigId, WeaponId socketsId, WeaponId eventsId, Map<View, TransformDefinition> views) {
    public enum View { FIRST_PERSON, THIRD_PERSON, NPC, GUI, GROUND, FIXED }

    public WeaponVisualDefinition {
        Checks.version(schemaVersion);
        Checks.present(id, "$.id");
        Checks.present(model, "$.model");
        Checks.require(model.path().endsWith(".glb") || model.path().endsWith(".gltf"), "$.model", "expected .glb or .gltf resource");
        Checks.present(rigId, "$.rig_id");
        Checks.present(socketsId, "$.sockets_id");
        Checks.present(eventsId, "$.events_id");
        Checks.map(views, 3, View.values().length, "$.views");
        for (View required : new View[]{View.FIRST_PERSON, View.THIRD_PERSON, View.NPC})
            Checks.require(views.containsKey(required), "$.views." + required.name().toLowerCase(java.util.Locale.ROOT), "required view is missing");
        views = Map.copyOf(views);
    }
}
