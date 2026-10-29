package com.bobincraft.combat;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Box;
import java.util.List;

/** A cosmetic mark bound to one existing block face, including slab/stair surface bounds. */
public record SurfaceMark(BlockPos block, Direction face, Vec3d local, float radius, int turn, int kind,
                          String blockId, float minU, float minV, float maxU, float maxV) {
    public static final int BLOOD = 0, FRAGMENT = 1;
    public static final int MAX_VISIBLE = 512;

    public boolean valid() {
        return block != null && face != null && local != null && validCoordinate(local.x) && validCoordinate(local.y)
                && validCoordinate(local.z) && Float.isFinite(radius) && radius >= .02f && radius <= .6f
                && turn >= 0 && turn < 4 && (kind == BLOOD || kind == FRAGMENT)
                && blockId != null && blockId.length() <= 256 && Identifier.tryParse(blockId) != null
                && validCoordinate(minU) && validCoordinate(minV) && validCoordinate(maxU) && validCoordinate(maxV)
                && minU < maxU && minV < maxV;
    }
    private static boolean validCoordinate(double value) { return Double.isFinite(value) && value >= 0 && value <= 1; }
    public Vec3d position() { return Vec3d.of(block).add(local); }
    public boolean matches(String current) {
        return blockId.equals(current) || isSoil(blockId) && isSoil(current);
    }
    public boolean supportedBy(List<Box> boxes) {
        for (Box box : boxes) {
            double plane = switch (face) {
                case DOWN -> box.minY; case UP -> box.maxY; case NORTH -> box.minZ;
                case SOUTH -> box.maxZ; case WEST -> box.minX; case EAST -> box.maxX;
            };
            if (Math.abs(plane - face.getAxis().choose(local.x, local.y, local.z)) > .001) continue;
            boolean x = face.getAxis() == Direction.Axis.X, y = face.getAxis() == Direction.Axis.Y;
            if (minU >= (x ? box.minZ : box.minX) - .001 && maxU <= (x ? box.maxZ : box.maxX) + .001
                    && minV >= (y ? box.minZ : box.minY) - .001 && maxV <= (y ? box.maxZ : box.maxY) + .001) return true;
        }
        return false;
    }
    private static boolean isSoil(String id) {
        return id.equals("minecraft:grass_block") || id.equals("minecraft:dirt")
                || id.equals("bobin_craft:scorched_grass") || id.equals("bobin_craft:partially_scorched_grass");
    }
    public NbtCompound write() {
        NbtCompound n = new NbtCompound();
        n.putLong("Block", block.asLong()); n.putByte("Face", (byte) face.getId());
        n.putDouble("X", local.x); n.putDouble("Y", local.y); n.putDouble("Z", local.z);
        n.putFloat("Radius", radius); n.putByte("Turn", (byte) turn); n.putByte("Kind", (byte) kind);
        n.putString("Material", blockId); n.putFloat("MinU", minU); n.putFloat("MinV", minV);
        n.putFloat("MaxU", maxU); n.putFloat("MaxV", maxV);
        return n;
    }
    public static SurfaceMark read(NbtCompound n) {
        int side = n.getByte("Face");
        if (side < 0 || side > 5) return null;
        SurfaceMark mark = new SurfaceMark(BlockPos.fromLong(n.getLong("Block")), Direction.byId(side),
                new Vec3d(n.getDouble("X"), n.getDouble("Y"), n.getDouble("Z")), n.getFloat("Radius"),
                n.getByte("Turn"), n.getByte("Kind"), n.getString("Material"),
                n.getFloat("MinU"), n.getFloat("MinV"), n.getFloat("MaxU"), n.getFloat("MaxV"));
        return mark.valid() ? mark : null;
    }
    public void write(PacketByteBuf b) {
        b.writeBlockPos(block); b.writeByte(face.getId()); b.writeFloat((float) local.x);
        b.writeFloat((float) local.y); b.writeFloat((float) local.z); b.writeFloat(radius);
        b.writeByte(turn); b.writeByte(kind); b.writeString(blockId, 256);
        b.writeFloat(minU); b.writeFloat(minV); b.writeFloat(maxU); b.writeFloat(maxV);
    }
    public static SurfaceMark read(PacketByteBuf b) {
        BlockPos block = b.readBlockPos(); int side = b.readUnsignedByte();
        Vec3d point = new Vec3d(b.readFloat(), b.readFloat(), b.readFloat()); float radius = b.readFloat();
        int turn = b.readUnsignedByte(), kind = b.readUnsignedByte(); String material = b.readString(256);
        float minU = b.readFloat(), minV = b.readFloat(), maxU = b.readFloat(), maxV = b.readFloat();
        if (side > 5) return null;
        SurfaceMark mark = new SurfaceMark(block, Direction.byId(side), point, radius, turn, kind, material, minU, minV, maxU, maxV);
        return mark.valid() ? mark : null;
    }
}
