package net.playavalon.mythicdungeons.objenesis.instantiator.sun;

import net.playavalon.mythicdungeons.objenesis.ObjenesisException;
import net.playavalon.mythicdungeons.objenesis.instantiator.ObjectInstantiator;
import net.playavalon.mythicdungeons.objenesis.instantiator.annotations.Instantiator;
import net.playavalon.mythicdungeons.objenesis.instantiator.annotations.Typology;
import net.playavalon.mythicdungeons.objenesis.instantiator.util.UnsafeUtils;
import sun.misc.Unsafe;

@Instantiator(Typology.STANDARD)
public class UnsafeFactoryInstantiator<T> implements ObjectInstantiator<T> {
   private final Unsafe unsafe = UnsafeUtils.getUnsafe();
   private final Class<T> type;

   public UnsafeFactoryInstantiator(Class<T> type) {
      this.type = type;
   }

   @Override
   public T newInstance() {
      try {
         return this.type.cast(this.unsafe.allocateInstance(this.type));
      } catch (InstantiationException var2) {
         throw new ObjenesisException(var2);
      }
   }
}
