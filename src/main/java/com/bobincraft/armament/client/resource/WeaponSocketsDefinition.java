package com.bobincraft.armament.client.resource;

import com.bobincraft.armament.api.WeaponId;
import com.bobincraft.armament.validation.Checks;
import java.util.*;

/** Declarative attachment points. Does not position a model or implement attachments. */
public record WeaponSocketsDefinition(int schemaVersion, WeaponId id, WeaponId rigId, List<Socket> sockets) {
    public record Socket(String name, String node, TransformDefinition transform) {
        public Socket {
            Checks.name(name, "$.name");
            Checks.name(node, "$.node");
            Checks.present(transform, "$.transform");
        }
    }

    public WeaponSocketsDefinition {
        Checks.version(schemaVersion);
        Checks.present(id, "$.id");
        Checks.present(rigId, "$.rig_id");
        Checks.size(sockets, 0, 128, "$.sockets");
        Set<String> names = new HashSet<>();
        for (int i = 0; i < sockets.size(); i++)
            Checks.require(names.add(sockets.get(i).name()), "$.sockets[" + i + "].name", "duplicate socket name");
        sockets = List.copyOf(sockets);
    }
}
