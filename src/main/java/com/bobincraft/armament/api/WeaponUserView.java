package com.bobincraft.armament.api;

import java.util.Optional;
import java.util.UUID;

/** Read-only user contract shared by players and NPCs; no entity or client types. */
public interface WeaponUserView {
    enum Hand { MAIN_HAND, OFF_HAND }

    UUID userId();

    /** A returned snapshot must describe this user and the requested hand. */
    Optional<WeaponStateSnapshot> weaponState(Hand hand);
}
