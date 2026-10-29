package com.bobincraft.raid.guard;

import com.bobincraft.warzone.Faction;

/** Legacy army groups cooperate; forest gangs defend against competing armed groups. */
public final class GuardFactionRelations {
    private GuardFactionRelations() {}
    public static boolean hostile(Faction first, Faction second) {
        return first != second && (first.isBandit() || second.isBandit());
    }
}
