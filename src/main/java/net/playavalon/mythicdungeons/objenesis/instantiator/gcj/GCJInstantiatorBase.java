package net.playavalon.mythicdungeons.objenesis.instantiator.gcj;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.reflect.Method;
import net.playavalon.mythicdungeons.objenesis.ObjenesisException;
import net.playavalon.mythicdungeons.objenesis.instantiator.ObjectInstantiator;

public abstract class GCJInstantiatorBase<T> implements ObjectInstantiator<T> {
   static Method newObjectMethod = null;
   static ObjectInputStream dummyStream;
   protected final Class<T> type;

   private static void initialize() {
      if (newObjectMethod == null) {
         try {
            newObjectMethod = ObjectInputStream.class.getDeclaredMethod("newObject", Class.class, Class.class);
            newObjectMethod.setAccessible(true);
            dummyStream = new GCJInstantiatorBase.DummyStream();
         } catch (NoSuchMethodException | IOException | RuntimeException var1) {
            throw new ObjenesisException(var1);
         }
      }
   }

   public GCJInstantiatorBase(Class<T> type) {
      this.type = type;
      initialize();
   }

   @Override
   public abstract T newInstance();

   private static class DummyStream extends ObjectInputStream {
      public DummyStream() throws IOException {
      }
   }
}
