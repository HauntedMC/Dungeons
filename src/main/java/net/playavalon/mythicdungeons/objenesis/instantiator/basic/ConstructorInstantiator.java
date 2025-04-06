package net.playavalon.mythicdungeons.objenesis.instantiator.basic;

import java.lang.reflect.Constructor;
import net.playavalon.mythicdungeons.objenesis.ObjenesisException;
import net.playavalon.mythicdungeons.objenesis.instantiator.ObjectInstantiator;
import net.playavalon.mythicdungeons.objenesis.instantiator.annotations.Instantiator;
import net.playavalon.mythicdungeons.objenesis.instantiator.annotations.Typology;

@Instantiator(Typology.NOT_COMPLIANT)
public class ConstructorInstantiator<T> implements ObjectInstantiator<T> {
   protected Constructor<T> constructor;

   public ConstructorInstantiator(Class<T> type) {
      try {
         this.constructor = type.getDeclaredConstructor((Class<?>[])null);
      } catch (Exception var3) {
         throw new ObjenesisException(var3);
      }
   }

   @Override
   public T newInstance() {
      try {
         return this.constructor.newInstance((Object[])null);
      } catch (Exception var2) {
         throw new ObjenesisException(var2);
      }
   }
}
