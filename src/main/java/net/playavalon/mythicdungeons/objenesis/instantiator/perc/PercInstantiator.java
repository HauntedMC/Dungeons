package net.playavalon.mythicdungeons.objenesis.instantiator.perc;

import java.io.ObjectInputStream;
import java.lang.reflect.Method;
import net.playavalon.mythicdungeons.objenesis.ObjenesisException;
import net.playavalon.mythicdungeons.objenesis.instantiator.ObjectInstantiator;
import net.playavalon.mythicdungeons.objenesis.instantiator.annotations.Instantiator;
import net.playavalon.mythicdungeons.objenesis.instantiator.annotations.Typology;

@Instantiator(Typology.STANDARD)
public class PercInstantiator<T> implements ObjectInstantiator<T> {
   private final Method newInstanceMethod;
   private final Object[] typeArgs = new Object[]{null, Boolean.FALSE};

   public PercInstantiator(Class<T> type) {
      this.typeArgs[0] = type;

      try {
         this.newInstanceMethod = ObjectInputStream.class.getDeclaredMethod("newInstance", Class.class, boolean.class);
         this.newInstanceMethod.setAccessible(true);
      } catch (NoSuchMethodException | RuntimeException var3) {
         throw new ObjenesisException(var3);
      }
   }

   @Override
   public T newInstance() {
      try {
         return (T)this.newInstanceMethod.invoke(null, this.typeArgs);
      } catch (Exception var2) {
         throw new ObjenesisException(var2);
      }
   }
}
