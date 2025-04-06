package net.playavalon.mythicdungeons.api.blocks;

import java.util.ArrayList;
import java.util.List;

public class PushableBlockCluster {
   private List<PushableBlock> blocks = new ArrayList<>();

   public List<PushableBlock> getBlocks() {
      return this.blocks;
   }
}
