package nl.hauntedmc.dungeons.util.world;

import nl.hauntedmc.dungeons.generation.structure.StructurePiece;
import nl.hauntedmc.dungeons.generation.structure.StructurePieceBlock;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;

import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;
import org.joml.Vector3i;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterOutputStream;

/**
 * Serialization helpers for compact structure-piece storage.
 */
public final class SchematicUtils {
    private static final String FORMAT_HEADER = "DUNGEONS-STRUCTURE-2\n";

    private SchematicUtils() {}

    /** Saves one structure piece to a compressed custom text format. */
    public static void saveStructurePiece(File file, StructurePiece structure) {
        Map<BlockData, Integer> cachedBlocks = new HashMap<>();
        StringBuilder blockDatas = new StringBuilder();
        StringBuilder blocksStr = new StringBuilder();
        boolean dataStrEmpty = true;
        boolean blockStrEmpty = true;

        for (StructurePieceBlock block : structure) {
            String posString = block.getX() + "," + block.getY() + "," + block.getZ();
            BlockData blockData = block.getBlockData();
            Integer index = cachedBlocks.get(blockData);
            if (index == null) {
                index = cachedBlocks.size();
                cachedBlocks.put(blockData, index);
                if (!dataStrEmpty) {
                    blockDatas.append("|");
                } else {
                    dataStrEmpty = false;
                }

                blockDatas.append(blockData.getAsString(true));
            }

            String nbt = block.getBlockNbt();
            String nbtStr =
                    nbt == null
                            ? "null"
                            : Base64.getEncoder()
                                    .encodeToString(nbt.getBytes(StandardCharsets.UTF_8));
            if (!blockStrEmpty) {
                blocksStr.append("!");
            } else {
                blockStrEmpty = false;
            }

            blocksStr.append(posString).append("|").append(index).append("|").append(nbtStr);
        }

        byte[] bytes = compress(FORMAT_HEADER + blockDatas + "\n" + blocksStr);

        try {
            FileUtils.writeByteArrayToFile(file, bytes);
        } catch (IOException exception) {
            RuntimeContext.logger()
                    .error(
                            "Failed to save structure piece to '{}'.",
                            file.getAbsolutePath(),
                            exception);
        }
    }

    /** Loads one structure piece from the compressed custom text format. */
    public static StructurePiece loadStructurePiece(File file) {
        StructurePiece struct = new StructurePiece();

        try {
            byte[] bytes = FileUtils.readFileToByteArray(file);
            String full = decompress(bytes);
            boolean versioned = full.startsWith(FORMAT_HEADER);
            String payload = versioned ? full.substring(FORMAT_HEADER.length()) : full;
            String[] pair = payload.split("\n", 2);
            String blocks = pair[0];
            if (blocks.isEmpty()) {
                return struct;
            }
            List<BlockData> data = deserializeBlockDataList(blocks);
            String positions = pair[1];
            String[] entries = positions.split("!");

            for (String strData : entries) {
                if (strData != null && !strData.isEmpty()) {
                    String[] set = strData.split("\\|", 3);
                    String posString = set[0];
                    int blockDataPointer = Integer.parseInt(set[1]);
                    if (blockDataPointer < data.size()) {
                        String nbtStr = set[2];
                        if (versioned && !nbtStr.equals("null")) {
                            nbtStr =
                                    new String(
                                            Base64.getDecoder().decode(nbtStr),
                                            StandardCharsets.UTF_8);
                        }
                        String[] posSplit = posString.split(",");
                        Vector3i pos =
                                new Vector3i(
                                        Integer.parseInt(posSplit[0]),
                                        Integer.parseInt(posSplit[1]),
                                        Integer.parseInt(posSplit[2]));
                        BlockData blockData = data.get(blockDataPointer);
                        String blockNbt =
                                !nbtStr.equals("null") ? BlockNbtUtils.normalizeSnbt(nbtStr) : null;
                        struct.add(new StructurePieceBlock(pos, blockData, blockNbt));
                    }
                }
            }
        } catch (IOException exception) {
            RuntimeContext.logger()
                    .error(
                            "Failed to load structure piece from '{}'.",
                            file.getAbsolutePath(),
                            exception);
        }

        return struct;
    }

    /** Decodes serialized block-data entries into Bukkit block data instances. */
    private static List<BlockData> deserializeBlockDataList(String str) {
        List<BlockData> datas = new ArrayList<>();
        String[] split = str.split("\\|");

        for (String data : split) {
            datas.add(Bukkit.createBlockData(data));
        }

        return datas;
    }

    /** Compresses text content using deflate. */
    public static byte[] compress(String text) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (OutputStream out = new DeflaterOutputStream(baos)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));

        } catch (IOException exception) {
            throw new AssertionError(exception);
        }

        return baos.toByteArray();
    }

    /** Decompresses deflated bytes back into UTF-8 text. */
    public static String decompress(byte[] bytes) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (OutputStream out = new InflaterOutputStream(baos)) {
            out.write(bytes);

        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
        return baos.toString(StandardCharsets.UTF_8);
    }
}
