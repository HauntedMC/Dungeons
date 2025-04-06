package net.playavalon.mythicdungeons.objenesis.instantiator.basic;

import net.playavalon.mythicdungeons.objenesis.instantiator.annotations.Instantiator;
import net.playavalon.mythicdungeons.objenesis.instantiator.annotations.Typology;

@Instantiator(Typology.NOT_COMPLIANT)
public class AccessibleInstantiator<T> extends ConstructorInstantiator<T> {
   public AccessibleInstantiator(Class<T> type) {
      super(type);
      if (this.constructor != null) {
         this.constructor.setAccessible(true);
      }
   }
}
