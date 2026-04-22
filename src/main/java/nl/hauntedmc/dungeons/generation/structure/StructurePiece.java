package nl.hauntedmc.dungeons.generation.structure;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;

/**
 * Lightweight ordered collection of captured structure blocks.
 */
public class StructurePiece implements Iterable<StructurePieceBlock> {
    private final List<StructurePieceBlock> blocks = new ArrayList<>();

    /**
     * Captures the current block state and appends it to this structure piece.
     */
    public void set(Block block) {
        StructurePieceBlock struct = StructurePieceBlock.from(block);
        this.add(struct);
    }

    /**
     * Appends a prebuilt block snapshot to this structure piece.
     */
    public void add(StructurePieceBlock block) {
        this.blocks.add(block);
    }

    @NotNull @Override
        public Iterator<StructurePieceBlock> iterator() {
        return this.blocks.iterator();
    }

    /**
     * Returns the number of captured blocks in this structure.
     */
    public int size() {
        return this.blocks.size();
    }
}
