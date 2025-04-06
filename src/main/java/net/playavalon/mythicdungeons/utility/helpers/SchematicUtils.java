package net.playavalon.mythicdungeons.utility.helpers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterOutputStream;
import net.playavalon.mythicdungeons.api.generation.StructurePiece;
import net.playavalon.mythicdungeons.api.generation.StructurePieceBlock;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;
import org.joml.Vector3i;

public final class SchematicUtils {
   public static void saveStructurePiece(File file, StructurePiece structure) throws IOException {
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

         String nbtStr = block.getBlockNbt() != null ? block.getBlockNbt().toJson() : null;
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
      } catch (IOException var13) {
         var13.printStackTrace();
      }
   }

   public static StructurePiece loadStructurePiece(File file) throws IOException {
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
                  Vector3i pos = new Vector3i(Integer.parseInt(posSplit[0]), Integer.parseInt(posSplit[1]), Integer.parseInt(posSplit[2]));
                  BlockData blockData = data.get(blockDataPointer);
                  NBTEditor.NBTCompound blockNbt = !nbtStr.equals("null") ? NBTEditor.NBTCompound.fromJson(nbtStr) : null;
                  struct.add(new StructurePieceBlock(pos, blockData, blockNbt));
               }
            }
         }
      } catch (IOException var21) {
         var21.printStackTrace();
      }

      return struct;
   }

   private static byte[] stringsToBytes(List<String> data) throws IOException {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      DataOutputStream out = new DataOutputStream(baos);

      for (String element : data) {
         out.writeUTF(element);
      }

      return baos.toByteArray();
   }

   private static List<String> bytesToStrings(byte[] bytes) throws IOException {
      List<String> data = new ArrayList<>();
      ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
      DataInputStream in = new DataInputStream(bais);

      while (in.available() > 0) {
         String element = in.readUTF();
         data.add(element);
      }

      return data;
   }

   private static String serializeBlockDataList(List<BlockData> datas) {
      StringBuilder builder = new StringBuilder();

      for (BlockData data : datas) {
         if (!builder.toString().isEmpty()) {
            builder.append("|");
         }

         builder.append(data.getAsString(true));
      }

      return builder.toString();
   }

   private static List<BlockData> deserializeBlockDataList(String str) {
      List<BlockData> datas = new ArrayList<>();
      String[] split = str.split("\\|");

      for (String data : split) {
         datas.add(Bukkit.createBlockData(data));
      }

      return datas;
   }

   public static byte[] compress(String text) {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();

      try {
         OutputStream out = new DeflaterOutputStream(baos);
         out.write(text.getBytes("UTF-8"));
         out.close();
      } catch (IOException var3) {
         throw new AssertionError(var3);
      }

      return baos.toByteArray();
   }

   public static String decompress(byte[] bytes) {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();

      try {
         OutputStream out = new InflaterOutputStream(baos);
         out.write(bytes);
         out.close();
         return new String(baos.toByteArray(), "UTF-8");
      } catch (IOException var3) {
         throw new AssertionError(var3);
      }
   }
}
