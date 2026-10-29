# Drone explosions and injuries — 1.5.0

Minecraft 1.20.1, Java 17, Fabric Loader 0.15.11+ and Fabric API 0.92.2+1.20.1.
Install `bobin_craft-1.5.0.jar` on the server and every client, removing the previous
Bobin Craft JAR. Existing saved worlds work without regeneration.

## What changes in play

Both dropped drone grenades and armed FPV kamikazes use a new fragmentation-blast
pipeline. The existing three-second grenade fuse, payload bay checks, carrier
protection, arming rules and FPV collision triggers remain in use.

The effect has a brief flash, low radial pressure dust, soil jets and smoke that
outlives the destroyed drone. The kamikaze profile creates a larger dust/smoke
burst. There is one native pressure/knockback calculation and one explosion sound.
There are no TNT craters or persistent fire blocks. Underwater explosions use
bubbles/splashes, reduced fragmentation and no grass scorching.

Exposed ordinary/tinted/stained glass blocks and glass panes shatter. Iron bars
remain solid cover. Grass, moss and the mod's forest soil receive a dark charcoal
center or patchy green/brown outer burn texture. Plants and thin snow directly
above burned soil are removed. Soil marks are saved as actual blocks, stay after
reloading, and yield dirt when dug up. A subsequent blast can deepen a partial
burn but cannot turn fully burned ground green again.

`/gamerule bobinDroneTerrainDamage false` disables window and soil changes for
that world. It does not disable blast damage or fragmentation. Terrain changes
also respect the world border, loaded chunks and native player spawn protection.
The safe Hub rejects drone blasts.

## Fragmentation and injury

Fragments are invisible server ray calculations. They have no rendered projectile
entities. Solid collision shapes stop them; windows attenuate them; the nearest
intersected living body intercepts each ray. Empty doorways allow fragments to
pass. Distance reduces impact energy. The hit location and the NPC's facing select
the torso, head/face, left/right arm or left/right leg. A ray to the back of a head
does not count as a facial injury.

Living entities can receive fragment damage. The additional injury state applies
to players and the mod's `WarzoneGuardEntity` combat NPCs, including settlement
residents. Hub personnel and creative/spectator players remain protected.

Surviving NPCs can lose an arm or a leg on sufficiently strong limb impacts.
The missing model parts synchronize to clients. Losing a leg reduces movement
speed; losing both reduces it further. Arm loss drops the held weapon once and
prevents further firearm use. Treatment does not regrow limbs. Injuries, missing
limbs and the remaining bleeding countdown survive NPC saves and chunk reloads.

Every bleeding wound has a treatment deadline:

| Severity | Deadline at 20 TPS | Server ticks |
| --- | --- | --- |
| Light | 10 minutes | 12,000 |
| Severe | 5 minutes | 6,000 |
| Critical | 60 seconds | 1,200 |

Bleeding also causes small health loss each second. Other damage or already-low
health can cause an earlier death. At the deadline, untreated bleeding is lethal,
even if food/regeneration restored health. Further wounds can increase severity
and shorten the deadline, but never reset or extend it.

A player's facial wound adds a red full-screen tint with darker edges. Its
intensity follows severity. The HUD shows the bleeding countdown and treatment
hint. Torso/limb bleeding shows the countdown without the facial tint. Ordinary
view and the existing top-down HUD both use the same injury state.

## Field Tourniquet

Craft **two tourniquets** using:

```text
 S 
LIL
 S 
```

S = string; L = leather; I = iron ingot. The item is also in the Combat creative
tab and available through `/give @s bobin_craft:tourniquet 2`.

Hold the use button with a tourniquet for **two seconds** to treat yourself. Use
it on an injured player or NPC to help them immediately. A successful treatment
consumes one item in Survival and stops bleeding, clears the fatal deadline and
fades the facial tint. Treating a healthy target consumes nothing. The requested
facial-wound treatment uses this same game item. It does not restore lost health
or missing limbs.

Wounds save on logout and reload with their remaining time. Timers advance in
active game ticks, so logout, unloaded NPC chunks, a paused integrated server,
the safe Hub and creative/spectator protection pause them. Death/respawn starts
the player healthy; living player recreation when returning from the End keeps
injuries. Disconnection clears the local injury HUD.

## Implementation and balance

`DroneBlastType` defines two game profiles, in blocks rather than real-world
weapon specifications. Pressure power continues to use 2.3 for the dropped
grenade (the existing payload safety constant) and 2.8 for the kamikaze.
The grenade's window/scorch/fragment ranges are 4.5/3.6/14 blocks; the kamikaze's
are 6/5/18. A dropped grenade calculates 192 rays and a kamikaze 288, with a
128-body cap in the nearby query. Water reduces fragment range to one quarter
and initial energy to 40%. No per-fragment tasks or entities are retained.

`DroneExplosion` captures cover, terrain candidates and fragment intersections
before editing the world. It then performs pressure, aggregated fragment damage,
terrain edits and visual effects. Custom damage types retain the operator as the
direct blast/fragment attacker. Fragments bypass the ordinary hurt cooldown so
the pressure pulse cannot silently discard them; armor still reduces damage.
Bleeding is internal damage and bypasses armor/resistance/enchantment reduction.

The `drone_shatterable_glass`, `drone_scorchable_soil` and
`drone_scorchable_plants` block tags can be extended by datapacks. Other mods'
region protection needs its own normal integration; no claim is made that native
spawn-protection checks replace a third-party claim system.

`InjuryState` is the shared countdown/severity/limb state. Player NBT is integrated
through `PlayerInjuryMixin`; NPC NBT and synchronized limb masks are integrated
directly into `WarzoneGuardEntity`. A server-to-client packet supplies only the
local HUD. Treatment uses normal server item interaction. Clients cannot submit
damage, body-part or countdown changes.

Original 16×16 block textures and the tourniquet icon are reproducible with
`python tools/generate_combat_assets.py`. New text is provided in English,
Russian and Ukrainian. No new entities, sounds or external asset files are required.

## Verification

See [the validation record](VALIDATION_1_5_0.md) for executed build/test results
and remaining client checks. Build normally with `gradlew build`, or use the
existing `tools/build_offline.py` and `tools/test_offline.py` workflow when all
dependencies are cached. The offline build uses official TinyRemapper and audits
every production Mixin selector against the intermediary Minecraft classes.

Before a public gameplay release, check the effect in a Minecraft client:

- Detonate each drone type near a window and a grass patch. Confirm different
  blast sizes, shattered glass and both burn textures after save/reload.
- Put a solid wall between the blast and a target. Compare with an open doorway
  and with a glass window. Confirm cover blocks damage and scorching.
- Use a close surviving NPC target to observe leg/arm loss, slowing, weapon
  dropping, bleeding and persistent limbs after reloading its chunk.
- Observe frontal head-height hits on a Survival player. Confirm red tint,
  countdown, treatment consumption and clearing on respawn/reconnect.
- Repeat with two clients and a dedicated server. Check underwater blasts,
  low particle settings, protected Hub, world border, gamerule and spawn protection.
