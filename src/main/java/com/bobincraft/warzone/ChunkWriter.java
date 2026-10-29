package com.bobincraft.warzone;

import net.minecraft.block.BarrelBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.CampfireBlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.Chunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Обёртка для записи блоков в строящийся чанк (в мировых координатах).
 * Записи за пределами чанка молча игнорируются — так генерация одного чанка никогда не лезёт в соседние.
 */
final class ChunkWriter {
    final Chunk chunk;
    final int minX, minZ;
    final int bottomY, topY;
    private final Heightmap oceanFloor;
    private final Heightmap worldSurface;
    private final BlockPos.Mutable pos = new BlockPos.Mutable();

    /** Позиции заборов, прутьев, стёкол-панелей и стен, поставленных после включения учёта (для пересчёта соединений). */
    private final List<BlockPos> connectables = new ArrayList<>();
    private boolean trackConnectables;

    void setTrackConnectables(boolean on) {
        this.trackConnectables = on;
    }

    boolean isTrackingConnectables() {
        return trackConnectables;
    }

    List<BlockPos> connectables() {
        return connectables;
    }

    ChunkWriter(Chunk chunk) {
        this.chunk = chunk;
        this.minX = chunk.getPos().getStartX();
        this.minZ = chunk.getPos().getStartZ();
        this.bottomY = chunk.getBottomY();
        this.topY = chunk.getTopY();
        this.oceanFloor = chunk.getHeightmap(Heightmap.Type.OCEAN_FLOOR_WG);
        this.worldSurface = chunk.getHeightmap(Heightmap.Type.WORLD_SURFACE_WG);
    }

    boolean contains(int x, int y, int z) {
        return x >= minX && x < minX + 16 && z >= minZ && z < minZ + 16 && y >= bottomY && y < topY;
    }

    boolean intersects(int x0, int z0, int x1, int z1) {
        return x1 >= minX && x0 < minX + 16 && z1 >= minZ && z0 < minZ + 16;
    }

    /** Ставит блок; возвращает false, если координата вне чанка. */
    boolean set(int x, int y, int z, BlockState state) {
        if (!contains(x, y, z)) return false;
        pos.set(x, y, z);
        BlockState prev = chunk.setBlockState(pos, state, false);
        if (prev != null && prev.hasBlockEntity() && (!state.hasBlockEntity() || prev.getBlock()!=state.getBlock())) {
            chunk.removeBlockEntity(pos.toImmutable());
        }
        // ProtoChunk.setBlockState does not construct native block entities. Beds, stoves and
        // signs therefore need an entity here too; loot-aware helpers replace the empty default.
        if(state.hasBlockEntity() && chunk.getBlockEntity(pos)==null && state.getBlock() instanceof BlockEntityProvider provider) {
            var entity=provider.createBlockEntity(pos.toImmutable(),state);
            if(entity!=null)chunk.setBlockEntity(entity);
        }
        if (trackConnectables) {
            Block b = state.getBlock();
            if (b instanceof FenceBlock || b instanceof PaneBlock || b instanceof WallBlock) {
                connectables.add(new BlockPos(x, y, z));
            }
        }
        oceanFloor.trackUpdate(x & 15, y, z & 15, state);
        worldSurface.trackUpdate(x & 15, y, z & 15, state);
        if (chunk.getBlockState(pos).equals(state))
            com.bobincraft.destruction.RefineryTransitions.generated(x,y,z,state);
        return true;
    }

    void setCable(Blueprint.Cable cable){
        var p=new BlockPos(cable.x(),cable.y(),cable.z());set(p.getX(),p.getY(),p.getZ(),CombatZoneBlocks.CABLE_SPAN.getDefaultState());
        var be=new CableSpanBlockEntity(p,CombatZoneBlocks.CABLE_SPAN.getDefaultState());be.setSegments(cable.segments());chunk.setBlockEntity(be);
    }

    /** Ставит блок только на место воздуха. */
    boolean setIfAir(int x, int y, int z, BlockState state) {
        if (!contains(x, y, z)) return false;
        if (!chunk.getBlockState(pos.set(x, y, z)).isAir()) return false;
        return set(x, y, z, state);
    }

    /** Ставит сундук с таблицей добычи (LootTable). */
    void setChest(int x, int y, int z, Direction facing, Identifier lootTable, long lootSeed) {
        if (!contains(x, y, z)) return;
        BlockState state = Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, facing);
        set(x, y, z, state);
        BlockPos immutable = new BlockPos(x, y, z);
        ChestBlockEntity chest = new ChestBlockEntity(immutable, state);
        if (lootTable != null) {
            chest.setLootTable(lootTable, lootSeed);
        }
        chunk.setBlockEntity(chest);
    }

    /** Ставит бочку (опционально с таблицей добычи). */
    void setBarrel(int x, int y, int z, Direction facing, Identifier lootTable, long lootSeed) {
        if (!contains(x, y, z)) return;
        BlockState state = Blocks.BARREL.getDefaultState().with(BarrelBlock.FACING, facing);
        set(x, y, z, state);
        BlockPos immutable = new BlockPos(x, y, z);
        BarrelBlockEntity barrel = new BarrelBlockEntity(immutable, state);
        if (lootTable != null) {
            barrel.setLootTable(lootTable, lootSeed);
        }
        chunk.setBlockEntity(barrel);
    }

    /** Ставит костёр вместе с BlockEntity. */
    void setCampfire(int x, int y, int z, boolean lit, boolean signalFire) {
        if (!contains(x, y, z)) return;
        BlockState state = Blocks.CAMPFIRE.getDefaultState()
                .with(CampfireBlock.LIT, lit)
                .with(CampfireBlock.SIGNAL_FIRE, signalFire);
        set(x, y, z, state);
        BlockPos immutable = new BlockPos(x, y, z);
        chunk.setBlockEntity(new CampfireBlockEntity(immutable, state));
    }

    BlockState get(int x, int y, int z) {
        if (!contains(x, y, z)) return Blocks.AIR.getDefaultState();
        return chunk.getBlockState(pos.set(x, y, z));
    }
}
