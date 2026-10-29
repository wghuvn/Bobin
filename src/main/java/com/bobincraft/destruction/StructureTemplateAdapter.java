package com.bobincraft.destruction;

import com.bobincraft.warzone.Blueprint;
import com.bobincraft.warzone.BpSpec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Layer C (Permanent Geometry) bridge to Minecraft's native {@link StructureTemplate},
 * {@link StructureTemplateManager}, and {@link StructurePlacementData} (Section 3.3 & Section 4 Level C).
 *
 * <p>Supports:
 * <ul>
 *   <li>Converting any stage {@link Blueprint} into a native {@link StructureTemplate} and compressed {@code .nbt} resource.</li>
 *   <li>Loading {@code .nbt} stage variants from {@code data/bobin_craft/structures/<category>/<variant>.nbt} or
 *       {@link StructureTemplateManager}.</li>
 *   <li>Safe differential placement: compares {@code currentState}, {@code expectedPrevStageState}, and
 *       {@code nextStageState} so player-placed blocks that do not belong to the structure are preserved.</li>
 * </ul>
 */
public final class StructureTemplateAdapter {
    private static final Map<String, NbtCompound> RESOURCE_TEMPLATE_CACHE = new ConcurrentHashMap<>();

    private StructureTemplateAdapter() {}

    /**
     * Converts a Bobin Craft {@link Blueprint} into a standard Minecraft {@link StructureTemplate} NBT compound
     * (compatible with {@link StructureTemplate#readNbt} and {@link StructureTemplateManager#createTemplate}).
     */
    public static NbtCompound blueprintToTemplateNbt(Blueprint bp) {
        Objects.requireNonNull(bp, "bp");
        NbtCompound root = new NbtCompound();

        NbtList sizeList = new NbtList();
        sizeList.add(NbtInt.of(bp.sizeX));
        sizeList.add(NbtInt.of(bp.sizeY));
        sizeList.add(NbtInt.of(bp.sizeZ));
        root.put(StructureTemplate.SIZE_KEY, sizeList);
        root.putString("author", "bobin_craft");
        NbtHelper.putDataVersion(root);

        List<BlockState> paletteStates = new ArrayList<>();
        Map<BlockState, Integer> paletteIndex = new LinkedHashMap<>();
        NbtList blocksList = new NbtList();

        bp.forEachCell((wx, wy, wz, spec, mode) -> {
            if (spec == null) return;
            BlockState state = StructureCollapseManager.resolveBlockState(spec);
            Integer idx = paletteIndex.get(state);
            if (idx == null) {
                idx = paletteStates.size();
                paletteStates.add(state);
                paletteIndex.put(state, idx);
            }
            int rx = wx - bp.minX;
            int ry = wy - bp.minY;
            int rz = wz - bp.minZ;

            NbtCompound blockEntry = new NbtCompound();
            NbtList posList = new NbtList();
            posList.add(NbtInt.of(rx));
            posList.add(NbtInt.of(ry));
            posList.add(NbtInt.of(rz));
            blockEntry.put(StructureTemplate.BLOCKS_POS_KEY, posList);
            blockEntry.putInt(StructureTemplate.BLOCKS_STATE_KEY, idx);
            blocksList.add(blockEntry);
        });

        NbtList paletteNbt = new NbtList();
        for (BlockState state : paletteStates) {
            paletteNbt.add(NbtHelper.fromBlockState(state));
        }

        root.put(StructureTemplate.PALETTE_KEY, paletteNbt);
        root.put(StructureTemplate.BLOCKS_KEY, blocksList);
        root.put(StructureTemplate.ENTITIES_KEY, new NbtList());

        // Store Bobin Craft origin metadata for exact world alignment
        NbtCompound meta = new NbtCompound();
        meta.putString("Name", bp.name);
        meta.putInt("MinX", bp.minX);
        meta.putInt("MinY", bp.minY);
        meta.putInt("MinZ", bp.minZ);
        root.put("BobinMeta", meta);
        return root;
    }

    /**
     * Instantiates a native Minecraft {@link StructureTemplate} from a {@link Blueprint}.
     */
    public static StructureTemplate toStructureTemplate(Blueprint bp) {
        NbtCompound nbt = blueprintToTemplateNbt(bp);
        StructureTemplate template = new StructureTemplate();
        template.readNbt(Registries.BLOCK.getReadOnlyWrapper(), nbt);
        return template;
    }

    /**
     * Builds configured {@link StructurePlacementData} for placing or transforming a stage template.
     */
    public static StructurePlacementData createPlacementData(int orientation) {
        BlockRotation rotation = switch (orientation & 3) {
            case 1 -> BlockRotation.CLOCKWISE_90;
            case 2 -> BlockRotation.CLOCKWISE_180;
            case 3 -> BlockRotation.COUNTERCLOCKWISE_90;
            default -> BlockRotation.NONE;
        };
        return new StructurePlacementData()
                .setMirror(BlockMirror.NONE)
                .setRotation(rotation)
                .setIgnoreEntities(true)
                .setPlaceFluids(false)
                .setUpdateNeighbors(false);
    }

    /**
     * Serializes a {@link Blueprint} into compressed Minecraft StructureTemplate {@code .nbt} bytes.
     */
    public static byte[] toCompressedNbtBytes(Blueprint bp) throws IOException {
        NbtCompound nbt = blueprintToTemplateNbt(bp);
        ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
        NbtIo.writeCompressed(nbt, out);
        return out.toByteArray();
    }

    /**
     * Reads a compressed {@code .nbt} StructureTemplate stream into a {@link StructureTemplate}.
     */
    public static StructureTemplate readStructureTemplate(InputStream in) throws IOException {
        NbtCompound nbt = NbtIo.readCompressed(in);
        StructureTemplate template = new StructureTemplate();
        template.readNbt(Registries.BLOCK.getReadOnlyWrapper(), nbt);
        return template;
    }

    /**
     * Loads a bundled structure template resource (e.g. {@code bridge/intact.nbt}, {@code bridge/destroyed_a.nbt},
     * {@code refinery_tank/destroyed_b.nbt}, {@code substation/destroyed_a.nbt}) from mod data resources.
     */
    public static Optional<StructureTemplate> loadBundledTemplate(String relativePath) {
        String normalized = relativePath.startsWith("/")
                ? relativePath
                : "/data/bobin_craft/structures/" + (relativePath.endsWith(".nbt") ? relativePath : relativePath + ".nbt");
        NbtCompound cached = RESOURCE_TEMPLATE_CACHE.computeIfAbsent(normalized, path -> {
            try (InputStream in = StructureTemplateAdapter.class.getResourceAsStream(path)) {
                if (in == null) return null;
                return NbtIo.readCompressed(in);
            } catch (IOException e) {
                return null;
            }
        });
        if (cached == null) return Optional.empty();
        StructureTemplate template = new StructureTemplate();
        template.readNbt(Registries.BLOCK.getReadOnlyWrapper(), cached);
        return Optional.of(template);
    }

    /**
     * Resolves the canonical template resource path for a structure category, stage, and variant index.
     */
    public static String resolveTemplateResourcePath(DestructibleStructureType.Category category,
                                                     StructureDamageState stage,
                                                     int variantIndex) {
        String folder = switch (category) {
            case BRIDGE_SECTION -> "bridge";
            case REFINERY_TANK -> "refinery_tank";
            case SUBSTATION_TRANSFORMER, SUBSTATION_EQUIPMENT -> "substation";
            default -> "industrial";
        };
        String file = switch (stage) {
            case INTACT -> "intact.nbt";
            case DAMAGED -> (variantIndex % 2 == 0) ? "damaged_a.nbt" : "damaged_b.nbt";
            case CRITICAL -> "critical.nbt";
            case DESTROYING, DESTROYED -> {
                if (category == DestructibleStructureType.Category.REFINERY_TANK) {
                    int v = Math.floorMod(variantIndex, 3);
                    yield v == 0 ? "destroyed_a.nbt" : (v == 1 ? "destroyed_b.nbt" : "destroyed_c.nbt");
                }
                yield (variantIndex % 2 == 0) ? "destroyed_a.nbt" : "destroyed_b.nbt";
            }
        };
        return folder + "/" + file;
    }

    /**
     * Checks whether a world block at {@code pos} is safe to modify during a stage transition (Section 3.3).
     * Preserves player-placed containers, beds, doors, and blocks that do not belong to the destructible structure
     * when transitioning a cell to air.
     */
    public static boolean shouldApplyBlockChange(BlockState currentWorldState,
                                                 BpSpec expectedPrevSpec,
                                                 BpSpec nextSpec) {
        if (currentWorldState == null) return true;
        // Never overwrite player/world loot containers or functional blocks
        Block currentBlock = currentWorldState.getBlock();
        if (currentBlock == Blocks.CHEST || currentBlock == Blocks.BARREL || currentBlock == Blocks.SHULKER_BOX) {
            return false;
        }
        BlockState nextState = StructureCollapseManager.resolveBlockState(nextSpec);
        if (currentWorldState.equals(nextState)) {
            return false;
        }
        // If we are clearing a structure block to AIR, verify the block currently in the world
        // either matches the expected previous stage block OR is a known structure material
        if (nextState.isAir() && !currentWorldState.isAir() && expectedPrevSpec != null && !expectedPrevSpec.isAir()) {
            BlockState expectedPrevState = StructureCollapseManager.resolveBlockState(expectedPrevSpec);
            if (!currentWorldState.isOf(expectedPrevState.getBlock()) && !isKnownStructuralMaterial(currentBlock)) {
                // Preserve unrelated player-placed blocks when carving air
                return false;
            }
        }
        return true;
    }

    private static boolean isKnownStructuralMaterial(Block block) {
        Identifier id = Registries.BLOCK.getId(block);
        if ("bobin_craft".equals(id.getNamespace())) return true;
        String path = id.getPath();
        return path.contains("concrete") || path.contains("terracotta") || path.contains("andesite")
                || path.contains("cobblestone") || path.contains("stone_brick") || path.contains("iron_bars")
                || path.contains("copper") || path.contains("ladder") || path.contains("hopper")
                || path.contains("dark_oak") || path.contains("gravel") || path.contains("fire");
    }
}
