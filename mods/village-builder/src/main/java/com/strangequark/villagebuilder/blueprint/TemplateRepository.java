package com.strangequark.villagebuilder.blueprint;

import com.strangequark.villagebuilder.VillageBuilderMod;
import com.strangequark.villagebuilder.network.VillageBuilderNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.state.property.Property;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/** Server-owned, validated blueprint files. Entity and block-entity data is intentionally ignored. */
public final class TemplateRepository {
    private static final int MAX_COMPRESSED_BYTES = 512 * 1024;
    private static final long MAX_NBT_BYTES = 8L * 1024 * 1024;
    private static final int MAX_DIMENSION = 48;
    private static final int MAX_BLOCKS = 8_000;
    private static final int MAX_TEMPLATES = 64;
    private static final Map<String, BlueprintTemplate> TEMPLATES = new LinkedHashMap<>();

    private TemplateRepository() {
    }

    public static Path directory(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("quarkmod").resolve("village-builder").resolve("templates");
    }

    public static List<BlueprintTemplate> all() {
        return List.copyOf(TEMPLATES.values());
    }

    public static Optional<BlueprintTemplate> get(String id) {
        return Optional.ofNullable(TEMPLATES.get(id));
    }

    public static int indexOf(String id) {
        int index = 0;
        for (String templateId : TEMPLATES.keySet()) {
            if (templateId.equals(id)) return index;
            index++;
        }
        return -1;
    }

    public static void reload(MinecraftServer server) {
        Path root = directory(server);
        TEMPLATES.clear();
        try {
            Files.createDirectories(root);
            ensureSamples(root);
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(Files::isRegularFile)
                        .filter(TemplateRepository::isSupported)
                        .sorted(Comparator.naturalOrder())
                        .forEach(path -> load(root, path));
            }
        } catch (IOException exception) {
            VillageBuilderMod.LOGGER.error("Could not load village blueprints from {}", root, exception);
        }
        VillageBuilderMod.LOGGER.info("Loaded {} village blueprints from {}", TEMPLATES.size(), root);
        VillageBuilderNetworking.syncTemplates(server);
        VillageBuilderNetworking.syncPlans(server);
    }

    private static boolean isSupported(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".nbt") || name.endsWith(".litematic");
    }

    private static void load(Path root, Path path) {
        try {
            if (TEMPLATES.size() >= MAX_TEMPLATES) {
                throw new IllegalArgumentException("only " + MAX_TEMPLATES + " templates may be synchronized at once");
            }
            if (Files.size(path) > MAX_COMPRESSED_BYTES) {
                throw new IllegalArgumentException("file exceeds 512 KiB compressed limit");
            }
            String relative = root.relativize(path).toString().replace(path.getFileSystem().getSeparator(), "/");
            String id = relative.substring(0, relative.lastIndexOf('.'));
            if (!id.matches("[a-zA-Z0-9_./-]+")) {
                throw new IllegalArgumentException("path contains unsupported characters");
            }
            NbtCompound rootNbt = NbtIo.readCompressed(path, NbtSizeTracker.of(MAX_NBT_BYTES));
            BlueprintTemplate template = path.getFileName().toString().endsWith(".litematic")
                    ? readLitematic(id, rootNbt)
                    : readStructureNbt(id, rootNbt);
            TEMPLATES.put(id, template);
        } catch (Exception exception) {
            VillageBuilderMod.LOGGER.warn("Skipping invalid village blueprint {}: {}", path, exception.getMessage());
        }
    }

    private static BlueprintTemplate readStructureNbt(String id, NbtCompound nbt) {
        int[] size = nbt.getIntArray("size").orElseThrow(() -> new IllegalArgumentException("missing size"));
        if (size.length != 3) throw new IllegalArgumentException("size must contain three entries");
        List<BlockState> palette = readPalette(nbt.getList("palette").orElseThrow(() -> new IllegalArgumentException("missing palette")));
        List<BlueprintTemplate.BlueprintBlock> blocks = new ArrayList<>();
        for (NbtElement element : nbt.getList("blocks").orElseThrow(() -> new IllegalArgumentException("missing blocks"))) {
            if (!(element instanceof NbtCompound block)) continue;
            int[] pos = block.getIntArray("pos").orElse(new int[0]);
            int state = block.getInt("state").orElse(-1);
            if (pos.length != 3 || state < 0 || state >= palette.size()) continue;
            addNonAir(blocks, new BlockPos(pos[0], pos[1], pos[2]), palette.get(state));
        }
        return finish(id, size[0], size[1], size[2], blocks);
    }

    private static BlueprintTemplate readLitematic(String id, NbtCompound nbt) {
        NbtCompound regions = nbt.getCompound("Regions").orElseThrow(() -> new IllegalArgumentException("missing Regions"));
        List<Region> parsed = new ArrayList<>();
        for (String key : regions.getKeys()) {
            NbtCompound region = regions.getCompound(key).orElseThrow();
            int[] position = region.getIntArray("Position").orElse(new int[0]);
            int[] size = region.getIntArray("Size").orElse(new int[0]);
            if (position.length != 3 || size.length != 3) throw new IllegalArgumentException("invalid region dimensions");
            parsed.add(new Region(position, size, region));
        }
        if (parsed.isEmpty()) throw new IllegalArgumentException("no regions");
        int minX = parsed.stream().mapToInt(region -> region.min(0)).min().orElse(0);
        int minY = parsed.stream().mapToInt(region -> region.min(1)).min().orElse(0);
        int minZ = parsed.stream().mapToInt(region -> region.min(2)).min().orElse(0);
        int maxX = parsed.stream().mapToInt(region -> region.max(0)).max().orElse(0);
        int maxY = parsed.stream().mapToInt(region -> region.max(1)).max().orElse(0);
        int maxZ = parsed.stream().mapToInt(region -> region.max(2)).max().orElse(0);
        List<BlueprintTemplate.BlueprintBlock> blocks = new ArrayList<>();
        for (Region region : parsed) readLitematicRegion(region, minX, minY, minZ, blocks);
        return finish(id, maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1, blocks);
    }

    private static void readLitematicRegion(Region region, int minX, int minY, int minZ, List<BlueprintTemplate.BlueprintBlock> result) {
        List<BlockState> palette = readPalette(region.nbt().getList("BlockStatePalette").orElseThrow(() -> new IllegalArgumentException("missing BlockStatePalette")));
        long[] states = region.nbt().getLongArray("BlockStates").orElseThrow(() -> new IllegalArgumentException("missing BlockStates"));
        int xSize = Math.abs(region.size()[0]);
        int ySize = Math.abs(region.size()[1]);
        int zSize = Math.abs(region.size()[2]);
        int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(0, palette.size() - 1)));
        long mask = (1L << bits) - 1L;
        for (int y = 0; y < ySize; y++) for (int z = 0; z < zSize; z++) for (int x = 0; x < xSize; x++) {
            int index = (y * zSize + z) * xSize + x;
            int paletteIndex = unpack(states, index, bits, mask);
            if (paletteIndex < 0 || paletteIndex >= palette.size()) throw new IllegalArgumentException("invalid packed block state");
            int worldX = region.min(0) + x - minX;
            int worldY = region.min(1) + y - minY;
            int worldZ = region.min(2) + z - minZ;
            addNonAir(result, new BlockPos(worldX, worldY, worldZ), palette.get(paletteIndex));
        }
    }

    private static int unpack(long[] values, int index, int bits, long mask) {
        long bitIndex = (long) index * bits;
        int word = (int) (bitIndex >>> 6);
        int shift = (int) (bitIndex & 63);
        if (word >= values.length) throw new IllegalArgumentException("truncated BlockStates");
        long value = values[word] >>> shift;
        if (shift + bits > 64) {
            if (word + 1 >= values.length) throw new IllegalArgumentException("truncated BlockStates");
            value |= values[word + 1] << (64 - shift);
        }
        return (int) (value & mask);
    }

    private static List<BlockState> readPalette(NbtList paletteNbt) {
        List<BlockState> palette = new ArrayList<>();
        for (NbtElement element : paletteNbt) {
            if (!(element instanceof NbtCompound stateNbt)) throw new IllegalArgumentException("invalid palette entry");
            String name = stateNbt.getString("Name", "");
            Identifier id = Identifier.tryParse(name);
            Block block = id == null ? null : Registries.BLOCK.get(id);
            if (block == null || block == Blocks.AIR && !"minecraft:air".equals(name)) throw new IllegalArgumentException("unknown block " + name);
            if (block == Blocks.COMMAND_BLOCK || block == Blocks.CHAIN_COMMAND_BLOCK || block == Blocks.REPEATING_COMMAND_BLOCK || block == Blocks.STRUCTURE_BLOCK || block == Blocks.JIGSAW) {
                throw new IllegalArgumentException("administrative block " + name + " is not allowed");
            }
            BlockState state = block.getDefaultState();
            NbtCompound properties = stateNbt.getCompoundOrEmpty("Properties");
            for (Property<?> property : block.getStateManager().getProperties()) {
                String value = properties.getString(property.getName(), "");
                if (!value.isEmpty()) state = applyProperty(state, property, value);
            }
            palette.add(state);
        }
        if (palette.isEmpty()) throw new IllegalArgumentException("empty palette");
        return palette;
    }

    private static <T extends Comparable<T>> BlockState applyProperty(BlockState state, Property<T> property, String value) {
        return property.parse(value).map(parsed -> state.with(property, parsed)).orElseThrow(() -> new IllegalArgumentException("invalid property " + property.getName()));
    }

    private static void addNonAir(List<BlueprintTemplate.BlueprintBlock> blocks, BlockPos pos, BlockState state) {
        if (!state.isAir()) {
            if (blocks.size() >= MAX_BLOCKS) throw new IllegalArgumentException("more than " + MAX_BLOCKS + " non-air blocks");
            blocks.add(new BlueprintTemplate.BlueprintBlock(pos.toImmutable(), state));
        }
    }

    private static BlueprintTemplate finish(String id, int x, int y, int z, List<BlueprintTemplate.BlueprintBlock> blocks) {
        if (x < 1 || y < 1 || z < 1 || x > MAX_DIMENSION || y > MAX_DIMENSION || z > MAX_DIMENSION) throw new IllegalArgumentException("dimensions must be 1-" + MAX_DIMENSION);
        Map<String, Integer> materials = new LinkedHashMap<>();
        for (BlueprintTemplate.BlueprintBlock block : blocks) {
            Identifier key = Registries.BLOCK.getId(block.state().getBlock());
            materials.merge(key.toString(), 1, Integer::sum);
        }
        return new BlueprintTemplate(id, x, y, z, blocks, materials);
    }

    private static void ensureSamples(Path root) throws IOException {
        Path nbt = root.resolve("sample_nbt.nbt");
        Path litematic = root.resolve("sample_litematic.litematic");
        if (!Files.exists(nbt)) NbtIo.writeCompressed(nativeSample(), nbt);
        if (!Files.exists(litematic)) NbtIo.writeCompressed(litematicSample(), litematic);
    }

    private static NbtCompound nativeSample() {
        NbtCompound root = new NbtCompound(); root.putIntArray("size", new int[]{3, 2, 3});
        NbtList palette = new NbtList(); palette.add(blockState("minecraft:air")); palette.add(blockState("minecraft:oak_planks")); root.put("palette", palette);
        NbtList blocks = new NbtList();
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) { NbtCompound block = new NbtCompound(); block.putIntArray("pos", new int[]{x, 0, z}); block.putInt("state", 1); blocks.add(block); }
        root.put("blocks", blocks); return root;
    }

    private static NbtCompound litematicSample() {
        NbtCompound root = new NbtCompound(); root.putInt("Version", 6); root.putInt("SubVersion", 1); root.putInt("MinecraftDataVersion", 1);
        NbtCompound metadata = new NbtCompound(); metadata.putString("Name", "Sample Litematic"); root.put("Metadata", metadata);
        NbtCompound regions = new NbtCompound(); NbtCompound region = new NbtCompound(); region.putIntArray("Position", new int[]{0, 0, 0}); region.putIntArray("Size", new int[]{2, 2, 2});
        NbtList palette = new NbtList(); palette.add(blockState("minecraft:air")); palette.add(blockState("minecraft:cobblestone")); region.put("BlockStatePalette", palette);
        long packed = 0L; for (int i = 0; i < 8; i++) packed |= 1L << (i * 2); region.putLongArray("BlockStates", new long[]{packed}); regions.put("sample", region); root.put("Regions", regions); return root;
    }

    private static NbtCompound blockState(String name) { NbtCompound state = new NbtCompound(); state.putString("Name", name); return state; }

    private record Region(int[] position, int[] size, NbtCompound nbt) {
        int min(int axis) { return position[axis] + (size[axis] < 0 ? size[axis] + 1 : 0); }
        int max(int axis) { return min(axis) + Math.abs(size[axis]) - 1; }
    }
}
