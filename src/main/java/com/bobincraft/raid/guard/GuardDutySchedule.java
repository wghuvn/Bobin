package com.bobincraft.raid.guard;

/** A deterministic, bounded work rota. Progress pauses during combat and survives unloading. */
public final class GuardDutySchedule {
    public enum Assignment { SENTRY, PATROL, QUARTERMASTER, MECHANIC, RADIO, REST }
    public enum Activity { WATCH, PATROL, WORK, REST }
    public record Snapshot(Assignment assignment, int shiftTicks, int routeIndex, int dwellTicks) {}
    private Assignment assignment = Assignment.SENTRY;
    private int shiftTicks;
    private int routeIndex;
    private int dwellTicks;

    public void assign(Assignment value, int stagger) {
        assignment = value;
        shiftTicks = Math.floorMod(stagger, 2400);
        routeIndex = 0;
        dwellTicks = 0;
    }
    public Assignment assignment() { return assignment; }
    public int routeIndex() { return routeIndex; }
    public Activity activity() {
        if (assignment == Assignment.SENTRY) return Activity.WATCH;
        if (assignment == Assignment.REST) return shiftTicks < 1800 ? Activity.REST : Activity.PATROL;
        if (assignment == Assignment.PATROL) return shiftTicks < 2100 ? Activity.PATROL : Activity.REST;
        // Specialists inspect their own short route, work at their station, and take a short break.
        if (shiftTicks < 1500) return Activity.WORK;
        return shiftTicks < 2100 ? Activity.PATROL : Activity.REST;
    }
    public void tick(boolean calm) {
        if (calm) shiftTicks = (shiftTicks + 1) % 2400;
    }
    /** A guard pauses to inspect each waypoint instead of instantly turning and running again. */
    public void arrived(int routeSize) {
        if (routeSize <= 0) { routeIndex = 0; dwellTicks = 0; return; }
        if (++dwellTicks >= 80) {
            routeIndex = (routeIndex + 1) % routeSize;
            dwellTicks = 0;
        }
    }
    public void enRoute() { dwellTicks = 0; }
    public Snapshot snapshot() { return new Snapshot(assignment, shiftTicks, routeIndex, dwellTicks); }
    public void restore(Snapshot saved, int routeSize) {
        assignment = saved.assignment() == null ? Assignment.SENTRY : saved.assignment();
        shiftTicks = Math.floorMod(saved.shiftTicks(), 2400);
        routeIndex = routeSize <= 0 ? 0 : Math.floorMod(saved.routeIndex(), routeSize);
        dwellTicks = Math.max(0, Math.min(79, saved.dwellTicks()));
    }
}
