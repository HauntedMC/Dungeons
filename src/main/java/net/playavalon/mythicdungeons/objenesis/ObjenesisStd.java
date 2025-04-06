package net.playavalon.mythicdungeons.objenesis;

import net.playavalon.mythicdungeons.objenesis.strategy.StdInstantiatorStrategy;

public class ObjenesisStd extends ObjenesisBase {
   public ObjenesisStd() {
      super(new StdInstantiatorStrategy());
   }

   public ObjenesisStd(boolean useCache) {
      super(new StdInstantiatorStrategy(), useCache);
   }
}
