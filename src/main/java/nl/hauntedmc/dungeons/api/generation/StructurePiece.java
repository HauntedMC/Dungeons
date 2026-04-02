package nl.hauntedmc.dungeons.api.generation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;

public class StructurePiece implements Iterable<StructurePieceBlock> {
   private final List<StructurePieceBlock> blocks = new ArrayList<>();

    public void set(Block block) {
      StructurePieceBlock struct = StructurePieceBlock.from(block);
      this.add(struct);
   }

   public void add(StructurePieceBlock block) {
      this.blocks.add(block);
   }

   @NotNull
   @Override
   public Iterator<StructurePieceBlock> iterator() {
      return this.blocks.iterator();
   }

   public int size() {
      return this.blocks.size();
   }

}
