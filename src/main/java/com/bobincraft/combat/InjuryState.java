package com.bobincraft.combat;

/** Server-owned, bounded state. Repeated hits can shorten, but never extend, a deadline. */
public final class InjuryState {
    private InjurySeverity severity = InjurySeverity.NONE;
    private int remainingTicks;
    private int faceSeverity;
    private int missingLimbs;
    private boolean dirty = true;

    public InjurySeverity severity() { return severity; }
    public int remainingTicks() { return remainingTicks; }
    public int faceSeverity() { return faceSeverity; }
    public int missingLimbs() { return missingLimbs; }
    public boolean isBleeding() { return severity != InjurySeverity.NONE; }

    public void wound(InjurySeverity incoming, boolean face, int severed) {
        missingLimbs |= severed & HitRegion.ALL_SEVERABLE;
        if ((severed & HitRegion.HEAD_SEVERED) != 0 && incoming.ordinal() < InjurySeverity.CRITICAL.ordinal()) {
            incoming = InjurySeverity.CRITICAL;
        }
        if (incoming != InjurySeverity.NONE) {
            remainingTicks = isBleeding() ? Math.min(remainingTicks, incoming.durationTicks) : incoming.durationTicks;
            if (incoming.ordinal() > severity.ordinal()) severity = incoming;
            if (face) faceSeverity = Math.max(faceSeverity, incoming.ordinal());
        }
        dirty = true;
    }

    /** Returns true at and after the lethal deadline, until treatment or death. */
    public boolean tick() {
        if (!isBleeding()) return false;
        if (remainingTicks > 0) remainingTicks--;
        return remainingTicks == 0;
    }

    public boolean treat() {
        if (!isBleeding()) return false;
        severity = InjurySeverity.NONE;
        remainingTicks = 0;
        faceSeverity = 0;
        dirty = true;
        return true;
    }

    public boolean consumeDirty() { boolean result = dirty; dirty = false; return result; }

    public Snapshot snapshot() { return new Snapshot(severity.ordinal(), remainingTicks, faceSeverity, missingLimbs); }

    public void restore(Snapshot saved) {
        severity = InjurySeverity.fromId(saved.severity());
        remainingTicks = Math.max(0, Math.min(severity.durationTicks, saved.remainingTicks()));
        faceSeverity = isBleeding() ? Math.max(0, Math.min(severity.ordinal(), saved.faceSeverity())) : 0;
        int raw = saved.missingLimbs();
        missingLimbs = (raw >= 0 && raw <= HitRegion.ALL_SEVERABLE) ? (raw & HitRegion.ALL_SEVERABLE) : (raw & HitRegion.ALL_LIMBS);
        dirty = true;
    }

    public record Snapshot(int severity, int remainingTicks, int faceSeverity, int missingLimbs) { }
}
