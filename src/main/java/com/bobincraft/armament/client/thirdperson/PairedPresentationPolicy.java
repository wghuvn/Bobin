package com.bobincraft.armament.client.thirdperson;

/** F5 selects a draw pass; while paired diagnostics are active it does not stop simulation. */
public final class PairedPresentationPolicy {
    private PairedPresentationPolicy() {}
    public static boolean simulates(boolean firstPerson,boolean paired,boolean debug,boolean ownCamera) {
        return firstPerson || paired && debug && ownCamera;
    }
}
