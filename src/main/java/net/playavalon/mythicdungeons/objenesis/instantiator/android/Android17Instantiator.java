package net.playavalon.mythicdungeons.objenesis.instantiator.android;

import java.io.ObjectStreamClass;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.playavalon.mythicdungeons.objenesis.ObjenesisException;
import net.playavalon.mythicdungeons.objenesis.instantiator.ObjectInstantiator;
import net.playavalon.mythicdungeons.objenesis.instantiator.annotations.Instantiator;
import net.playavalon.mythicdungeons.objenesis.instantiator.annotations.Typology;

@Instantiator(Typology.STANDARD)
public class Android17Instantiator<T> implements ObjectInstantiator<T> {
   private final Class<T> type;
   private final Method newInstanceMethod;
   private final Integer objectConstructorId;

   public Android17Instantiator(Class<T> type) {
      this.type = type;
      this.newInstanceMethod = getNewInstanceMethod();
      this.objectConstructorId = findConstructorIdForJavaLangObjectConstructor();
   }

   @Override
   public T newInstance() {
      try {
         return this.type.cast(this.newInstanceMethod.invoke(null, this.type, this.objectConstructorId));
      } catch (Exception var2) {
         throw new ObjenesisException(var2);
      }
   }

   private static Method getNewInstanceMethod() {
      try {
         Method newInstanceMethod = ObjectStreamClass.class.getDeclaredMethod("newInstance", Class.class, int.class);
         newInstanceMethod.setAccessible(true);
         return newInstanceMethod;
      } catch (NoSuchMethodException | RuntimeException var1) {
         throw new ObjenesisException(var1);
      }
   }

   private static Integer findConstructorIdForJavaLangObjectConstructor() {
      try {
         Method newInstanceMethod = ObjectStreamClass.class.getDeclaredMethod("getConstructorId", Class.class);
         newInstanceMethod.setAccessible(true);
         return (Integer)newInstanceMethod.invoke(null, Object.class);
      } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException | RuntimeException var1) {
         throw new ObjenesisException(var1);
      }
   }
}
