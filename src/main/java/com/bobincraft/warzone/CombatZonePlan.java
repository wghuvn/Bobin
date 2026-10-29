package com.bobincraft.warzone;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Immutable version-four occupation plan. It samples only the underlying natural landscape:
 * terrain flattening, structure placement and NPC assignments therefore cannot recurse into one another.
 */
public final class CombatZonePlan {
    public enum Faction { MILITARY, BANDIT }
    public enum Kind { MILITARY_BASE, BANDIT_SETTLEMENT, INDUSTRIAL_CAMP, FOREST_CAMP, CHECKPOINT }
    public enum Duty { SENTRY, PATROL, QUARTERMASTER, MECHANIC, RADIO, REST }
    public record Point(int x, int y, int z) { }
    public record Post(int index, Duty duty, Point spawn, Point focus, List<Point> route) {
        public Post { route = List.copyOf(route); }
    }
    public record Site(int id, Kind kind, Faction faction, int x, int z, int radius,
                       int groundY, List<Post> posts) {
        public Site { posts = List.copyOf(posts); }
        public int feetY() { return groundY + 1; }
        public String label() {
            return switch(kind) {
                case MILITARY_BASE -> "Garrison " + (id%2==0?"Vector":"Granite");
                case BANDIT_SETTLEMENT -> "Rustwater settlement";
                case INDUSTRIAL_CAMP -> "Old industrial depot";
                case FOREST_CAMP -> "Woodland camp";
                case CHECKPOINT -> "Road checkpoint";
            } + " #" + id;
        }
        public boolean contains(int px, int pz) {
            return Math.abs(px - x) <= radius && Math.abs(pz - z) <= radius;
        }
        public boolean intersectsChunk(int minX, int minZ) {
            return minX + 15 >= x - radius && minX <= x + radius
                    && minZ + 15 >= z - radius && minZ <= z + radius;
        }
    }

    private static final CombatZonePlan EMPTY = new CombatZonePlan(List.of());
    private final List<Site> sites;

    private CombatZonePlan(List<Site> sites) { this.sites = List.copyOf(sites); }
    public static CombatZonePlan empty() { return EMPTY; }
    public List<Site> sites() { return sites; }

    /** Explicit profile gate also protects callers accidentally asking about an old saved generator. */
    public static CombatZonePlan forTerrain(WarzoneTerrain terrain) {
        if (terrain.generationVersion() != 4) return EMPTY;
        List<Site> sites = new ArrayList<>();
        NaturalLandscape landscape = terrain.naturalLandscape();
        for (NaturalLandscape.Reservation reservation : landscape.reservations()) {
            Kind kind = switch (reservation.type) {
                case COMPOUND -> Kind.MILITARY_BASE;
                case INDUSTRY -> Kind.INDUSTRIAL_CAMP;
                case SETTLEMENT -> Kind.BANDIT_SETTLEMENT;
                case FOREST_OUTPOST -> Kind.FOREST_CAMP;
                case CHECKPOINT -> Kind.CHECKPOINT;
            };
            int radius = Math.max(18, reservation.radius - 2);
            if (kind == Kind.CHECKPOINT) radius = Math.min(radius, 21);
            Candidate candidate = candidate(terrain, landscape, sites, reservation.x, reservation.z, radius, 80);
            if (candidate != null) sites.add(site(sites.size() + 1, kind, candidate));
        }
        // Small maps and unusually wet seeds may have few reservations. Search dry local opportunities
        // rather than silently leaving the requested combat zone uninhabited.
        int minimum = terrain.size >= 1024 ? 5 : terrain.size >= 512 ? 3 : 2;
        for (int attempt = 0; attempt < 5 && (sites.size() < minimum || missingPrimarySite(sites)); attempt++) {
            Kind wanted = sites.stream().noneMatch(s -> s.kind == Kind.MILITARY_BASE)
                    ? Kind.MILITARY_BASE : sites.stream().noneMatch(s -> s.kind == Kind.BANDIT_SETTLEMENT)
                    ? Kind.BANDIT_SETTLEMENT : switch (attempt % 3) {
                        case 0 -> Kind.INDUSTRIAL_CAMP;
                        case 1 -> Kind.FOREST_CAMP;
                        default -> Kind.CHECKPOINT;
                    };
            int nominal = terrain.size >= 1024 ? (wanted == Kind.MILITARY_BASE ? 46 : wanted == Kind.BANDIT_SETTLEMENT ? 42 : 34)
                    : terrain.size >= 512 ? 26 : 18;
            Candidate best = null;
            for (int radius = nominal; radius >= 18 && best == null; radius -= 4) {
                int edge = radius + 23;
                int step = terrain.size >= 1024 ? 24 : 12;
                for (int z = terrain.min + edge; z < terrain.max - edge; z += step) {
                    for (int x = terrain.min + edge; x < terrain.max - edge; x += step) {
                        Candidate c = candidate(terrain, landscape, sites, x, z, radius, 80);
                        if (c != null && (best == null || c.score < best.score)) best = c;
                    }
                }
            }
            if (best == null) break;
            sites.add(site(sites.size() + 1, wanted, best));
        }
        return new CombatZonePlan(sites);
    }

    private static boolean missingPrimarySite(List<Site> sites) {
        return sites.stream().noneMatch(s -> s.kind == Kind.MILITARY_BASE)
                || sites.stream().noneMatch(s -> s.kind == Kind.BANDIT_SETTLEMENT);
    }

    private record Candidate(int x, int z, int radius, int y, double score) { }

    private static Candidate candidate(WarzoneTerrain terrain, NaturalLandscape landscape, List<Site> sites,
                                       int x, int z, int radius, int landingClearance) {
        if (Math.abs(x) + radius + 16 >= terrain.half - 16
                || Math.abs(z) + radius + 16 >= terrain.half - 16) return null;
        int landingZ = (int) (terrain.half * 0.68);
        if (Math.hypot(x, z - landingZ) < radius + landingClearance) return null;
        for (Site prior : sites) {
            if (Math.abs(x - prior.x) < radius + prior.radius + 24
                    && Math.abs(z - prior.z) < radius + prior.radius + 24) return null;
        }
        List<Integer> heights = new ArrayList<>();
        WarzoneTerrain.Sample sample = new WarzoneTerrain.Sample();
        int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
        // Include the full square apron and blend edge. A dry circular perimeter is insufficient:
        // a courtyard corner or its earthwork could otherwise bury a creek.
        int extent = radius + 12;
        for (int dz = -extent; dz <= extent; dz += Math.max(4, extent / 4)) {
            for (int dx = -extent; dx <= extent; dx += Math.max(4, extent / 4)) {
                landscape.sample(x + dx, z + dz, sample);
                if (sample.height < 65 || sample.channel > 0.06 || sample.pool > 0.10
                        || sample.bog > 0.50 || sample.edge < 18) return null;
                heights.add(sample.height);
                low = Math.min(low, sample.height);
                high = Math.max(high, sample.height);
            }
        }
        if (high - low > Math.max(11, radius * 0.45)) return null;
        heights.sort(Comparator.naturalOrder());
        int y = heights.get(heights.size() / 2);
        double random = WarzoneNoise.unit(WarzoneNoise.hash(terrain.seed + 4431, x, z));
        double score = high - low + Math.abs(y - 76) * 0.04 + random * 3;
        return new Candidate(x, z, radius, y, score);
    }

    private static Site site(int id, Kind kind, Candidate c) {
        Faction faction = switch (kind) {
            case MILITARY_BASE, CHECKPOINT -> Faction.MILITARY;
            case BANDIT_SETTLEMENT, FOREST_CAMP -> Faction.BANDIT;
            case INDUSTRIAL_CAMP -> id % 2 == 0 ? Faction.BANDIT : Faction.MILITARY;
        };
        List<Post> posts = new ArrayList<>();
        int ring = c.radius - 3;
        List<Point> patrol = List.of(point(c, -ring, ring), point(c, -ring, -ring),
                point(c, ring, -ring), point(c, ring, ring));
        add(posts, Duty.SENTRY, point(c, -4, c.radius - 6), point(c, -4, c.radius + 4), List.of());
        add(posts, Duty.PATROL, patrol.get(0), patrol.get(1), patrol);
        add(posts, Duty.QUARTERMASTER, point(c, -6, 5), point(c, -8, 5),
                List.of(point(c, -6, 5), point(c, -6, -5), point(c, 0, -5)));
        add(posts, Duty.RADIO, point(c, -6, -5), point(c, -8, -5),
                List.of(point(c, -6, -5), point(c, 0, -5), point(c, 0, 3)));
        if (c.radius >= 24) {
            add(posts, Duty.SENTRY, point(c, 4, c.radius - 6), point(c, 4, c.radius + 4), List.of());
            add(posts, Duty.PATROL, patrol.get(2), patrol.get(3),
                    List.of(patrol.get(2), patrol.get(1), patrol.get(0), patrol.get(3)));
            add(posts, Duty.MECHANIC, point(c, 7, 4), point(c, 9, 4),
                    List.of(point(c, 7, 4), point(c, 0, 4), point(c, 0, -5)));
            add(posts, Duty.REST, point(c, 6, -5), point(c, 8, -5),
                    List.of(point(c, 6, -5), point(c, 0, -5), point(c, 0, 3)));
        }
        if (c.radius >= 38) {
            add(posts, Duty.PATROL, point(c, 0, -ring), point(c, -ring, -ring), patrol);
            add(posts, Duty.SENTRY, point(c, -4, -c.radius + 6), point(c, -4, -c.radius - 4), List.of());
        }
        return new Site(id, kind, faction, c.x, c.z, c.radius, c.y, posts);
    }

    private static Point point(Candidate c, int x, int z) { return new Point(c.x + x, c.y + 1, c.z + z); }
    private static void add(List<Post> posts, Duty duty, Point spawn, Point focus, List<Point> route) {
        posts.add(new Post(posts.size(), duty, spawn, focus, route));
    }

    /** Amount of a modest square earthwork, with a smooth twelve-block transition to natural hills. */
    public double padWeight(int x, int z) {
        double weight = 0;
        for (Site site : sites) {
            int distance = Math.max(Math.abs(x - site.x), Math.abs(z - site.z));
            double t = Math.max(0, Math.min(1, (distance - site.radius - 1) / 12.0));
            weight = Math.max(weight, 1 - t * t * (3 - 2 * t));
        }
        return weight;
    }

    public int targetHeight(int x, int z, int naturalHeight) {
        for (Site site : sites) {
            int distance = Math.max(Math.abs(x - site.x), Math.abs(z - site.z));
            if (distance > site.radius + 13) continue;
            double t = Math.max(0, Math.min(1, (distance - site.radius - 1) / 12.0));
            double weight = 1 - t * t * (3 - 2 * t);
            return (int) Math.round(naturalHeight + (site.groundY - naturalHeight) * weight);
        }
        return naturalHeight;
    }

    /** Tree origins and crowns are excluded only from an actual constructed courtyard. */
    public boolean isReserved(int x, int z) {
        for (Site site : sites) if (Math.abs(x - site.x) <= site.radius + 2
                && Math.abs(z - site.z) <= site.radius + 2) return true;
        return false;
    }
}
