package com.bobincraft.raid;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** Conditional prop upgrade. Connected changes are applied together, preserving player edits and storage. */
public final class HubRepair {
    private record Edit(BlockPos pos, List<BlockState> expected, BlockState replacement, String group) { }
    private HubRepair() { }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(
                CommandManager.literal("bobin").then(CommandManager.literal("hub")
                        .then(CommandManager.literal("repair").requires(source -> source.hasPermissionLevel(2))
                                .executes(context -> repair(context.getSource()))))));
    }

    static boolean shouldReplace(BlockState current, BlockState expected, boolean containsPersistentData) {
        return !containsPersistentData && (current.equals(expected) || current.isAir());
    }

    private static int repair(ServerCommandSource source) {
        ServerWorld world = source.getWorld();
        RaidPersistentState state = RaidPersistentState.get(source.getServer());
        if (!HubSafety.isHub(world) || !state.isSupported() || !state.hubInitialized || state.hubDesign != HubLayout.DESIGN_VERSION) {
            source.sendError(Text.translatable("hub.bobin_craft.repair.unavailable"));
            return 0;
        }
        final List<Edit> edits;
        try { edits = load(); }
        catch (Exception failure) {
            source.sendError(Text.translatable("hub.bobin_craft.repair.invalid"));
            org.slf4j.LoggerFactory.getLogger("bobin_craft/hub").error("Invalid Hub repair data; no edits applied", failure);
            return 0;
        }
        int changed = 0, skipped = 0;
        // Decode everything first. Preflight entire groups before changing any member: a
        // player block inside a wreck must never leave a partially placed vehicle or locker.
        edits.sort(Comparator.comparingInt((Edit e) -> e.pos.getY())
                .thenComparingInt(e -> e.pos.getZ()).thenComparingInt(e -> e.pos.getX()));
        Map<String, List<Edit>> groups = new LinkedHashMap<>();
        for (Edit edit : edits) groups.computeIfAbsent(edit.group, key -> new ArrayList<>()).add(edit);
        for (List<Edit> group : groups.values()) {
            boolean safe = true;
            for (Edit edit : group) {
                BlockState current = world.getBlockState(edit.pos);
                if (current.equals(edit.replacement)) continue;
                var entity = world.getBlockEntity(edit.pos);
                boolean protectedData = entity instanceof Inventory || entity != null && !(entity instanceof SignBlockEntity);
                if (edit.expected.stream().noneMatch(expected -> shouldReplace(current, expected, protectedData))) {
                    safe = false;
                    break;
                }
            }
            if (!safe) { skipped += group.size(); continue; }
            for (Edit edit : group) {
                if (world.getBlockState(edit.pos).equals(edit.replacement)) continue;
                world.setBlockState(edit.pos, edit.replacement, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
                changed++;
            }
        }
        changed += HubSigns.removeGenerated(world);
        final int count = changed, preserved = skipped;
        source.sendFeedback(() -> Text.translatable("hub.bobin_craft.repair.done", count, preserved), false);
        return changed;
    }

    private static List<Edit> load() throws Exception {
        var stream = HubRepair.class.getResourceAsStream("/data/bobin_craft/hub/repair_1_1_4.json");
        if (stream == null) throw new IllegalStateException("Missing patch");
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.get("design").getAsInt() != HubLayout.DESIGN_VERSION) throw new IllegalStateException("Wrong patch layout");
            var edits = new ArrayList<Edit>();
            var seen = new HashSet<BlockPos>();
            for (JsonElement value : root.getAsJsonArray("edits")) {
                JsonObject e = value.getAsJsonObject();
                BlockPos pos = new BlockPos(e.get("x").getAsInt(), e.get("y").getAsInt(), e.get("z").getAsInt());
                if (Math.abs(pos.getX()) > 54 || Math.abs(pos.getZ()) > 58 || pos.getY() < 70 || pos.getY() > 90
                        || !seen.add(pos)) throw new IllegalStateException("Patch outside authored structure");
                var expected = new ArrayList<BlockState>();
                expected.add(decode(e.getAsJsonObject("from")));
                if (e.has("legacy")) expected.add(decode(e.getAsJsonObject("legacy")));
                if (e.has("previous")) expected.add(decode(e.getAsJsonObject("previous")));
                edits.add(new Edit(pos, List.copyOf(expected), decode(e.getAsJsonObject("to")), e.get("group").getAsString()));
            }
            if (edits.size() > 10000) throw new IllegalStateException("Excessive patch size");
            return edits;
        }
    }

    private static BlockState decode(JsonObject json) {
        Identifier id = new Identifier(json.get("block").getAsString());
        if (!Registries.BLOCK.containsId(id)) throw new IllegalArgumentException("Unknown patch block: " + id);
        BlockState state = Registries.BLOCK.get(id).getDefaultState();
        for (var entry : json.getAsJsonObject("properties").entrySet()) {
            Property<?> property = state.getBlock().getStateManager().getProperty(entry.getKey());
            if (property == null) throw new IllegalArgumentException("Unknown patch property");
            state = apply(state, property, entry.getValue().getAsString());
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState apply(BlockState state, Property<T> property, String value) {
        return state.with(property, property.parse(value).orElseThrow(() -> new IllegalArgumentException("Invalid patch property")));
    }
}
