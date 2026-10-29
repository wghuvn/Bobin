package com.bobincraft.armament.client.resource;

import com.bobincraft.armament.api.WeaponId;
import com.bobincraft.armament.validation.Checks;
import java.util.*;

/** Stable local node names mapped to future glTF node indices, not a loaded model. */
public record WeaponRigDefinition(int schemaVersion, WeaponId id, List<Node> nodes, Map<String, String> roles) {
    public record Node(String name, int nodeIndex, Optional<String> parent) {
        public Node {
            Checks.name(name, "$.name");
            Checks.require(nodeIndex >= 0 && nodeIndex <= 65535, "$.node_index", "expected index in 0..65535");
            Checks.present(parent, "$.parent");
            parent.ifPresent(value -> Checks.name(value, "$.parent"));
        }
    }

    public WeaponRigDefinition {
        Checks.version(schemaVersion);
        Checks.present(id, "$.id");
        Checks.size(nodes, 1, 512, "$.nodes");
        Checks.map(roles, 1, 64, "$.roles");
        Map<String, Node> byName = new HashMap<>();
        Set<Integer> indices = new HashSet<>();
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            Checks.require(byName.putIfAbsent(node.name(), node) == null, "$.nodes[" + i + "].name", "duplicate node '" + node.name() + "'");
            Checks.require(indices.add(node.nodeIndex()), "$.nodes[" + i + "].node_index", "duplicate model node index");
        }
        Checks.require(nodes.stream().filter(n -> n.parent().isEmpty()).count() == 1, "$.nodes", "expected exactly one root node");
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            Set<String> visited = new HashSet<>();
            while (true) {
                Checks.require(visited.add(node.name()), "$.nodes[" + i + "].parent", "cycle in node hierarchy");
                if (node.parent().isEmpty()) break;
                String parent = node.parent().orElseThrow();
                Checks.require(byName.containsKey(parent), "$.nodes[" + i + "].parent", "unknown parent '" + parent + "'");
                node = byName.get(parent);
            }
        }
        roles.forEach((role, node) -> {
            Checks.name(role, "$.roles." + role);
            Checks.require(byName.containsKey(node), "$.roles." + role, "unknown node '" + node + "'");
        });
        Checks.require(roles.containsKey("weapon_root"), "$.roles.weapon_root", "required role is missing");
        Checks.require(byName.get(roles.get("weapon_root")).parent().isEmpty(), "$.roles.weapon_root", "must reference the root node");
        nodes = List.copyOf(nodes);
        roles = Map.copyOf(roles);
    }
}
