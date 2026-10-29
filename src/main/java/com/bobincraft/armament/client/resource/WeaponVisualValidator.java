package com.bobincraft.armament.client.resource;

import com.bobincraft.armament.validation.Checks;
import java.util.Set;
import java.util.stream.Collectors;

/** Validates references among supplied metadata; performs no I/O or model loading. */
public final class WeaponVisualValidator {
    private WeaponVisualValidator() {}

    public static void validate(WeaponVisualDefinition visual, WeaponRigDefinition rig,
            WeaponSocketsDefinition sockets, AnimationEventsDefinition events) {
        Checks.present(visual, "$.visual");
        Checks.present(rig, "$.rig");
        Checks.present(sockets, "$.sockets");
        Checks.present(events, "$.events");
        Checks.require(visual.rigId().equals(rig.id()), "$.visual.rig_id", "does not match supplied rig");
        Checks.require(visual.socketsId().equals(sockets.id()), "$.visual.sockets_id", "does not match supplied sockets");
        Checks.require(visual.eventsId().equals(events.id()), "$.visual.events_id", "does not match supplied events");
        Checks.require(sockets.rigId().equals(rig.id()), "$.sockets.rig_id", "does not match supplied rig");
        Set<String> nodes = rig.nodes().stream().map(WeaponRigDefinition.Node::name).collect(Collectors.toSet());
        for (int i = 0; i < sockets.sockets().size(); i++)
            Checks.require(nodes.contains(sockets.sockets().get(i).node()), "$.sockets.sockets[" + i + "].node", "unknown rig node");
    }
}
