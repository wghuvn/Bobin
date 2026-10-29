package com.bobincraft.destruction.debris;

import static com.bobincraft.destruction.debris.DebrisFragment.*;

/** Conservative crash reconciliation rules. A material match alone cannot create a fragment without a durable intent. */
public final class DebrisLifecycle {
    public enum Recovery { IGNORE, CONFIRM_REMOVAL, REMOVE_EXPECTED, KEEP_COMMITTED, CONFLICT }
    private DebrisLifecycle(){}
    public static boolean preventsNewClaim(Cell c,boolean sameStructure){return c.protectedSource||c.commit==Commit.REMOVED||!sameStructure&&c.commit!=Commit.CONFLICT;}
    public static boolean mayRemove(Cell c,String actual){return c.commit==Commit.ARMED&&c.durable&&c.state.equals(actual);}
    public static Recovery recover(Cell c,String actual,boolean targetAir,boolean protectedCell,boolean hasBlockEntity) {
        if(c.commit==Commit.RESERVED||c.commit==Commit.CONFLICT)return Recovery.IGNORE;
        if(targetAir)return c.commit==Commit.REMOVED||!protectedCell?Recovery.CONFIRM_REMOVAL:Recovery.CONFLICT;
        if(!protectedCell&&!c.protectedSource&&!hasBlockEntity&&c.state.equals(actual))return Recovery.REMOVE_EXPECTED;
        return c.commit==Commit.REMOVED?Recovery.KEEP_COMMITTED:Recovery.CONFLICT;
    }
}
