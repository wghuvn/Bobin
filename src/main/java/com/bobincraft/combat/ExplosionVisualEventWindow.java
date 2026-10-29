package com.bobincraft.combat;

import java.util.LinkedHashSet;
import java.util.UUID;

/** Bounded per-client-world replay window. Cleared on disconnect and world replacement. */
public final class ExplosionVisualEventWindow {
    private final int capacity;
    private final LinkedHashSet<UUID> seen = new LinkedHashSet<>();
    public ExplosionVisualEventWindow(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity");
        this.capacity = capacity;
    }
    public boolean accept(UUID id) {
        if (!seen.add(id)) return false;
        if (seen.size() > capacity) seen.remove(seen.iterator().next());
        return true;
    }
    public void clear() { seen.clear(); }
    public int size() { return seen.size(); }
}
