package net.playavalon.mythicdungeons.objenesis.instantiator.android;

import java.io.ObjectInputStream;
import java.lang.reflect.Method;
import net.playavalon.mythicdungeons.objenesis.ObjenesisException;
import net.playavalon.mythicdungeons.objenesis.instantiator.ObjectInstantiator;
import net.playavalon.mythicdungeons.objenesis.instantiator.annotations.Instantiator;
import net.playavalon.mythicdungeons.objenesis.instantiator.annotations.Typology;

@Instantiator(Typology.STANDARD)
public class Android10Instantiator<T> implements ObjectInstantiator<T> {
   private final Class<T> type;
   private final Method newStaticMethod;

   public Android10Instantiator(Class<T> type) {
      this.type = type;
      this.newStaticMethod = getNewStaticMethod();
   }

   @Override
   public T newInstance() {
      try {
         return this.type.cast(this.newStaticMethod.invoke(null, this.type, Object.class));
      } catch (Exception var2) {
         throw new ObjenesisException(var2);
      }
   }

   private static Method getNewStaticMethod() {
      try {
         Method newStaticMethod = ObjectInputStream.class.getDeclaredMethod("newInstance", Class.class, Class.class);
         newStaticMethod.setAccessible(true);
         return newStaticMethod;
      } catch (NoSuchMethodException | RuntimeException var1) {
         throw new ObjenesisException(var1);
      }
   }
}
