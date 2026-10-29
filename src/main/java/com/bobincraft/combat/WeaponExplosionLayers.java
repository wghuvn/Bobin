package com.bobincraft.combat;

import java.util.*;

/** Deterministic choreography within one event, with separate flash/fire/dust/smoke envelopes. */
public final class WeaponExplosionLayers {
    public enum Kind { FLASH, FIRE, SMOKE, DUST, EMBER }
    public record Layer(Kind kind, VoxelExplosionShape.Puff puff, float intensity, float expansion, int delayTicks) {
        public Layer(Kind kind, VoxelExplosionShape.Puff puff, float intensity, float expansion) {
            this(kind, puff, intensity, expansion, 0);
        }
    }
    private WeaponExplosionLayers() {}
    public static List<Layer> create(ExplosionVisualProfile p, long seed, int budget) {
        if (budget <= 0) return List.of();
        if (p == ExplosionVisualProfile.FP1_STRIKE || p == ExplosionVisualProfile.GROM7_DEMOLITION)
            return special(p, seed, budget);
        var rng = new Random(seed);
        var result = new ArrayList<Layer>();
        result.add(new Layer(Kind.FLASH, new VoxelExplosionShape.Puff(0,.1,0,0,0,0,
                (float)(p.scale * .85), p.flashTicks, 0, seed), (float)p.flashIntensity, (float)p.expansionSpeed));
        int fire = Math.max(2, (int)(p.smokeCount * p.heatFraction));
        int dust = Math.max(3, p.smokeCount / 3);
        for (int i = 1; i < budget; i++) {
            Kind kind = i <= fire ? Kind.FIRE : i <= fire + dust ? Kind.DUST : i % 7 == 0 ? Kind.EMBER : Kind.SMOKE;
            double a = rng.nextDouble() * Math.PI * 2, r = Math.sqrt(rng.nextDouble()) * p.scale;
            double width = kind == Kind.DUST ? p.spread * 1.6 : kind == Kind.FIRE ? .65 : .9;
            double vy = kind == Kind.DUST ? .012 : kind == Kind.EMBER ? .10 : .03;
            int life = kind == Kind.FIRE ? p.fireTicks : kind == Kind.DUST ? 24 : kind == Kind.EMBER ? 18 : p.smokeLife;
            float size = (float)(p.scale * (kind == Kind.EMBER ? .035 : kind == Kind.FIRE ? .42 : .28) * (.7 + rng.nextDouble()*.6));
            result.add(new Layer(kind, new VoxelExplosionShape.Puff(Math.cos(a)*r*width,
                    rng.nextDouble()*p.scale*(kind == Kind.SMOKE ? 1.1 : .35), Math.sin(a)*r*width,
                    Math.cos(a)*.07*p.scale*width, vy*p.scale, Math.sin(a)*.07*p.scale*width,
                    size, life + (kind == Kind.FIRE ? rng.nextInt(4) : rng.nextInt(12)), .55f, rng.nextLong()),
                    kind == Kind.SMOKE ? .78f : .9f, (float)p.expansionSpeed));
        }
        return List.copyOf(result);
    }

    private static List<Layer> special(ExplosionVisualProfile p, long seed, int budget) {
        boolean strike = p == ExplosionVisualProfile.FP1_STRIKE;
        var rng = new Random(seed);
        var out = new ArrayList<Layer>();
        out.add(new Layer(Kind.FLASH, new VoxelExplosionShape.Puff(0,.15,0,0,0,0,
                (float)(p.scale*.85),p.flashTicks,0,seed),(float)p.flashIntensity,(float)p.expansionSpeed,0));
        for (int i=1; i<budget; i++) {
            // Interleave layers so low budgets keep the identity instead of spending everything on fire.
            Kind kind = switch (i % 8) {
                case 1, 5 -> Kind.DUST;
                case 2, 3 -> Kind.FIRE;
                case 7 -> Kind.EMBER;
                default -> Kind.SMOKE;
            };
            double a=rng.nextDouble()*Math.PI*2, jitter=.75+rng.nextDouble()*.5;
            double x,y,z,vx,vy,vz; int delay,life; float radius;
            if (strike) {
                if (kind==Kind.DUST) { // Expanding ground-pressure skirt, ahead of rising combustion lobes.
                    double r=p.scale*(.75+rng.nextDouble()*.8);
                    x=Math.cos(a)*r; z=Math.sin(a)*r; y=.08;
                    vx=Math.cos(a)*.48; vz=Math.sin(a)*.48; vy=.015; delay=1+rng.nextInt(3);
                    radius=(float)(p.scale*.33*jitter); life=30;
                } else if (kind==Kind.FIRE) {
                    double r=p.scale*(.2+rng.nextDouble()*.9);
                    x=Math.cos(a)*r; z=Math.sin(a)*r; y=.4+rng.nextDouble()*p.scale*.8;
                    vx=x*.045; vz=z*.045; vy=.11; delay=2+rng.nextInt(7);
                    radius=(float)(p.scale*.55*jitter); life=p.fireTicks;
                } else { // Stem then separated upper rolling cloud, not a persistent industrial column.
                    boolean upper=i%4==0;
                    double r=p.scale*(upper ? .9 : .35)*Math.sqrt(rng.nextDouble());
                    x=Math.cos(a)*r; z=Math.sin(a)*r; y=p.scale*(upper ? 1.8 : .65);
                    vx=x*.025; vz=z*.025; vy=upper?.075:.16; delay=upper?14+rng.nextInt(7):7+rng.nextInt(6);
                    radius=(float)(p.scale*(upper?.45:.30)*jitter); life=p.smokeLife;
                }
            } else {
                // Demolition: a compressed hemispherical ejecta fan, dense low torus and short local crown.
                double r=p.scale*(kind==Kind.DUST ? 1.2+rng.nextDouble() : rng.nextDouble()*.75);
                x=Math.cos(a)*r; z=Math.sin(a)*r;
                y=kind==Kind.DUST?.06:rng.nextDouble()*p.scale*.6;
                vx=Math.cos(a)*(kind==Kind.DUST?.32:.13); vz=Math.sin(a)*(kind==Kind.DUST?.32:.13);
                vy=kind==Kind.DUST?.008:kind==Kind.SMOKE?.055:.08;
                delay=kind==Kind.DUST?0:kind==Kind.FIRE?1+rng.nextInt(3):4+rng.nextInt(4);
                radius=(float)(p.scale*(kind==Kind.DUST?.52:kind==Kind.FIRE?.62:.45)*jitter);
                life=kind==Kind.DUST?24:kind==Kind.FIRE?p.fireTicks:48;
            }
            if (kind==Kind.EMBER) {radius*=.12f;life=18;vy+=.12;}
            out.add(new Layer(kind,new VoxelExplosionShape.Puff(x,y,z,vx,vy,vz,radius,life,.55f,rng.nextLong()),
                    kind==Kind.SMOKE?.74f:.9f,(float)p.expansionSpeed,delay));
        }
        return List.copyOf(out);
    }
}
