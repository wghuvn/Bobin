package com.bobincraft.raid;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/** Original, locally authored voice recordings and radio/ordnance Foley. */
public final class HubAudio {
    private static final Map<String, SoundEvent> EVENTS = new LinkedHashMap<>();
    public static final SoundEvent DESERTER_WARNING = add("hub.deserter.warning");
    public static final SoundEvent BOMB_WHISTLE = add("hub.deserter.whistle");
    public static final SoundEvent DESERTER_EXPLOSION = add("hub.deserter.explosion");

    static {
        for (String role : new String[]{"sentry", "quartermaster", "cook", "medic", "commander"}) {
            for (String cue : new String[]{"greeting", "reply", "accept", "goodbye"}) {
                add("hub." + role + "." + cue);
            }
        }
    }

    private HubAudio() { }

    private static SoundEvent add(String name) {
        SoundEvent sound = SoundEvent.of(new Identifier("bobin_craft", name));
        EVENTS.put(name, sound);
        return sound;
    }

    public static void init() {
        EVENTS.forEach((name, sound) -> Registry.register(Registries.SOUND_EVENT,
                new Identifier("bobin_craft", name), sound));
    }

    public static SoundEvent soundForRole(HubNpcInteraction.Role role, String cue) {
        return soundForRole(role.key(), cue);
    }

    public static SoundEvent soundForRole(String role, String cue) {
        SoundEvent sound = EVENTS.get("hub." + role + "." + cue);
        if (sound == null) throw new IllegalArgumentException("Unknown hub sound: " + role + "/" + cue);
        return sound;
    }
}
