package com.bobincraft.combat;

import java.io.*;
import java.util.UUID;
import java.util.regex.Pattern;

/** Versioned, bounded cosmetic wire format; no gameplay or persistent state. */
public record ExplosionVisualEvent(UUID eventId, String dimension, String profileId,
        double x, double y, double z, long seed, boolean water,
        int material, double surfaceX, double surfaceY, double surfaceZ) {
    public static final int VERSION = 1, MAX_BYTES = 512;
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
    public ExplosionVisualEvent {
        if (eventId == null || !validId(dimension) || !validId(profileId)
                || !coordinate(x) || !coordinate(y) || !coordinate(z)
                || !coordinate(surfaceX) || !coordinate(surfaceY) || !coordinate(surfaceZ)
                || material < -1 || material > 1_000_000) throw new IllegalArgumentException("Invalid explosion visual event");
    }
    private static boolean validId(String id) { return id != null && id.length() <= 128 && ID.matcher(id).matches(); }
    private static boolean coordinate(double x) { return Double.isFinite(x) && Math.abs(x) <= 30_000_000; }
    public byte[] encode() {
        try {
            var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes);
            out.writeByte(VERSION); out.writeLong(eventId.getMostSignificantBits()); out.writeLong(eventId.getLeastSignificantBits());
            out.writeUTF(dimension); out.writeUTF(profileId);
            out.writeDouble(x); out.writeDouble(y); out.writeDouble(z); out.writeLong(seed);
            out.writeBoolean(water); out.writeInt(material);
            out.writeDouble(surfaceX); out.writeDouble(surfaceY); out.writeDouble(surfaceZ);
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }
    public static ExplosionVisualEvent decode(byte[] bytes) throws IOException {
        if (bytes.length > MAX_BYTES) throw new IOException("Oversized visual event");
        var in = new DataInputStream(new ByteArrayInputStream(bytes));
        if (in.readUnsignedByte() != VERSION) throw new IOException("Unsupported visual protocol");
        try {
            var event = new ExplosionVisualEvent(new UUID(in.readLong(), in.readLong()), in.readUTF(), in.readUTF(),
                    in.readDouble(), in.readDouble(), in.readDouble(), in.readLong(), in.readBoolean(), in.readInt(),
                    in.readDouble(), in.readDouble(), in.readDouble());
            if (in.available() != 0) throw new IOException("Trailing visual data");
            return event;
        } catch (IllegalArgumentException ex) { throw new IOException("Invalid visual event", ex); }
    }
}
