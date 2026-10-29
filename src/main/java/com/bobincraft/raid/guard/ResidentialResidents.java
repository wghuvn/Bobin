package com.bobincraft.raid.guard;

import com.bobincraft.warzone.Faction;
import com.bobincraft.warzone.ResidentialPlan;

/** Explicit identities for forest occupants; no inherited military faction selection. */
public final class ResidentialResidents {
    private ResidentialResidents() {}

    public static Faction faction(ResidentialPlan.Group group) {
        return switch (group) {
            case MARAUDER -> Faction.MARAUDERS;
            case BANDIT -> Faction.BANDITS;
            case UNIDENTIFIED -> Faction.UNIDENTIFIED;
        };
    }

    public static GuardDutySchedule.Assignment assignment(ResidentialPlan.Duty duty) {
        return GuardDutySchedule.Assignment.valueOf(duty.name());
    }
}
