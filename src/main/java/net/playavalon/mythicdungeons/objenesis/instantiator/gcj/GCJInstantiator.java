package net.playavalon.mythicdungeons.objenesis.instantiator.gcj;

import java.lang.reflect.InvocationTargetException;
import net.playavalon.mythicdungeons.objenesis.ObjenesisException;
import net.playavalon.mythicdungeons.objenesis.instantiator.annotations.Instantiator;
import net.playavalon.mythicdungeons.objenesis.instantiator.annotations.Typology;

@Instantiator(Typology.STANDARD)
public class GCJInstantiator<T> extends GCJInstantiatorBase<T> {
   public GCJInstantiator(Class<T> type) {
      super(type);
   }

   @Override
   public T newInstance() {
      try {
         return this.type.cast(newObjectMethod.invoke(dummyStream, this.type, Object.class));
      } catch (IllegalAccessException | InvocationTargetException | RuntimeException var2) {
         throw new ObjenesisException(var2);
      }
   }
}
