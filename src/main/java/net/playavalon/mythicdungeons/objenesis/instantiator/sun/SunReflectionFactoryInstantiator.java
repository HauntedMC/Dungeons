package net.playavalon.mythicdungeons.objenesis.instantiator.sun;

import java.lang.reflect.Constructor;
import net.playavalon.mythicdungeons.objenesis.ObjenesisException;
import net.playavalon.mythicdungeons.objenesis.instantiator.ObjectInstantiator;
import net.playavalon.mythicdungeons.objenesis.instantiator.annotations.Instantiator;
import net.playavalon.mythicdungeons.objenesis.instantiator.annotations.Typology;

@Instantiator(Typology.STANDARD)
public class SunReflectionFactoryInstantiator<T> implements ObjectInstantiator<T> {
   private final Constructor<T> mungedConstructor;

   public SunReflectionFactoryInstantiator(Class<T> type) {
      Constructor<Object> javaLangObjectConstructor = getJavaLangObjectConstructor();
      this.mungedConstructor = SunReflectionFactoryHelper.newConstructorForSerialization(type, javaLangObjectConstructor);
      this.mungedConstructor.setAccessible(true);
   }

   @Override
   public T newInstance() {
      try {
         return this.mungedConstructor.newInstance((Object[])null);
      } catch (Exception var2) {
         throw new ObjenesisException(var2);
      }
   }

   private static Constructor<Object> getJavaLangObjectConstructor() {
      try {
         return Object.class.getConstructor((Class<?>[])null);
      } catch (NoSuchMethodException var1) {
         throw new ObjenesisException(var1);
      }
   }
}
