package nl.hauntedmc.dungeons.util.world;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterOutputStream;
import nl.hauntedmc.dungeons.generation.structure.StructurePiece;
import nl.hauntedmc.dungeons.generation.structure.StructurePieceBlock;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;
import org.joml.Vector3i;

/**
 * Serialization helpers for compact structure-piece storage.
 */
public final class SchematicUtils {
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

            String nbtStr = block.getBlockNbt();
            if (!blockStrEmpty) {
                blocksStr.append("!");
            } else {
                blockStrEmpty = false;
            }

            blocksStr.append(posString).append("|").append(index).append("|").append(nbtStr);
        }

        byte[] bytes = compress(blockDatas + "\n" + blocksStr);

        try {
            FileUtils.writeByteArrayToFile(file, bytes);
        } catch (IOException exception) {
            RuntimeContext.logger()
                    .error("Failed to save structure piece to '{}'.", file.getAbsolutePath(), exception);
        }
    }

    /** Loads one structure piece from the compressed custom text format. */
    public static StructurePiece loadStructurePiece(File file) {
        StructurePiece struct = new StructurePiece();

        try {
            byte[] bytes = FileUtils.readFileToByteArray(file);
            String full = decompress(bytes);
            String[] pair = full.split("\n");
            String blocks = pair[0];
            List<BlockData> data = deserializeBlockDataList(blocks);
            String positions = pair[1];
            String[] entries = positions.split("!");

            for (String strData : entries) {
                if (strData != null && !strData.isEmpty()) {
                    String[] set = strData.split("\\|");
                    String posString = set[0];
                    int blockDataPointer = Integer.parseInt(set[1]);
                    if (blockDataPointer < data.size()) {
                        String nbtStr = set[2];
                        String[] posSplit = posString.split(",");
                        Vector3i pos =
                                new Vector3i(
                                        Integer.parseInt(posSplit[0]),
                                        Integer.parseInt(posSplit[1]),
                                        Integer.parseInt(posSplit[2]));
                        BlockData blockData = data.get(blockDataPointer);
                        String blockNbt = !nbtStr.equals("null") ? BlockNbtUtils.normalizeSnbt(nbtStr) : null;
                        struct.add(new StructurePieceBlock(pos, blockData, blockNbt));
                    }
                }
            }
        } catch (IOException exception) {
            RuntimeContext.logger()
                    .error("Failed to load structure piece from '{}'.", file.getAbsolutePath(), exception);
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

        try {
            OutputStream out = new DeflaterOutputStream(baos);
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.close();
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }

        return baos.toByteArray();
    }

    /** Decompresses deflated bytes back into UTF-8 text. */
    public static String decompress(byte[] bytes) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try {
            OutputStream out = new InflaterOutputStream(baos);
            out.write(bytes);
            out.close();
            return baos.toString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }
}
