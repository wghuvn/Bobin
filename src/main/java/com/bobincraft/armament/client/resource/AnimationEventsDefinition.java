package com.bobincraft.armament.client.resource;

import com.bobincraft.armament.api.WeaponId;
import com.bobincraft.armament.validation.Checks;
import java.util.*;

/** Cosmetic marker metadata only. No clips, sampling, callbacks or executable payloads. */
public record AnimationEventsDefinition(int schemaVersion, WeaponId id, List<Clip> clips) {
    public record Marker(String name, double timeSeconds, WeaponId event) {
        public Marker {
            Checks.name(name, "$.name");
            Checks.require(Double.isFinite(timeSeconds) && timeSeconds >= 0 && timeSeconds <= 3600,
                    "$.time_seconds", "expected finite time in 0..3600");
            Checks.present(event, "$.event");
        }
    }

    public record Clip(String name, double durationSeconds, List<Marker> markers) {
        public Clip {
            Checks.name(name, "$.name");
            Checks.require(Double.isFinite(durationSeconds) && durationSeconds > 0 && durationSeconds <= 3600,
                    "$.duration_seconds", "expected finite duration in (0,3600]");
            Checks.size(markers, 0, 1024, "$.markers");
            Set<String> names = new HashSet<>();
            double previous = -1;
            for (int i = 0; i < markers.size(); i++) {
                Marker marker = markers.get(i);
                Checks.require(names.add(marker.name()), "$.markers[" + i + "].name", "duplicate marker name");
                Checks.require(marker.timeSeconds() <= durationSeconds, "$.markers[" + i + "].time_seconds", "marker exceeds clip duration");
                Checks.require(marker.timeSeconds() >= previous, "$.markers[" + i + "].time_seconds", "markers must be ordered by time");
                previous = marker.timeSeconds();
            }
            markers = List.copyOf(markers);
        }
    }

    public AnimationEventsDefinition {
        Checks.version(schemaVersion);
        Checks.present(id, "$.id");
        Checks.size(clips, 0, 128, "$.clips");
        Set<String> names = new HashSet<>();
        for (int i = 0; i < clips.size(); i++)
            Checks.require(names.add(clips.get(i).name()), "$.clips[" + i + "].name", "duplicate clip name");
        clips = List.copyOf(clips);
    }
}
