package net.playavalon.mythicdungeons.objenesis.strategy;

import java.io.Serializable;
import net.playavalon.mythicdungeons.objenesis.instantiator.ObjectInstantiator;
import net.playavalon.mythicdungeons.objenesis.instantiator.android.Android10Instantiator;
import net.playavalon.mythicdungeons.objenesis.instantiator.android.Android17Instantiator;
import net.playavalon.mythicdungeons.objenesis.instantiator.android.Android18Instantiator;
import net.playavalon.mythicdungeons.objenesis.instantiator.basic.AccessibleInstantiator;
import net.playavalon.mythicdungeons.objenesis.instantiator.basic.ObjectInputStreamInstantiator;
import net.playavalon.mythicdungeons.objenesis.instantiator.gcj.GCJInstantiator;
import net.playavalon.mythicdungeons.objenesis.instantiator.perc.PercInstantiator;
import net.playavalon.mythicdungeons.objenesis.instantiator.sun.SunReflectionFactoryInstantiator;
import net.playavalon.mythicdungeons.objenesis.instantiator.sun.UnsafeFactoryInstantiator;

public class StdInstantiatorStrategy extends BaseInstantiatorStrategy {
   @Override
   public <T> ObjectInstantiator<T> newInstantiatorOf(Class<T> type) {
      if (!PlatformDescription.isThisJVM("Java HotSpot") && !PlatformDescription.isThisJVM("OpenJDK")) {
         if (PlatformDescription.isThisJVM("Dalvik")) {
            if (PlatformDescription.isAndroidOpenJDK()) {
               return new UnsafeFactoryInstantiator<>(type);
            } else if (PlatformDescription.ANDROID_VERSION <= 10) {
               return new Android10Instantiator<>(type);
            } else {
               return (ObjectInstantiator<T>)(PlatformDescription.ANDROID_VERSION <= 17 ? new Android17Instantiator<>(type) : new Android18Instantiator<>(type));
            }
         } else if (PlatformDescription.isThisJVM("GNU libgcj")) {
            return new GCJInstantiator<>(type);
         } else {
            return (ObjectInstantiator<T>)(PlatformDescription.isThisJVM("PERC") ? new PercInstantiator<>(type) : new UnsafeFactoryInstantiator<>(type));
         }
      } else if (!PlatformDescription.isGoogleAppEngine() || !PlatformDescription.SPECIFICATION_VERSION.equals("1.7")) {
         return new SunReflectionFactoryInstantiator<>(type);
      } else {
         return (ObjectInstantiator<T>)(Serializable.class.isAssignableFrom(type)
            ? new ObjectInputStreamInstantiator<>(type)
            : new AccessibleInstantiator<>(type));
      }
   }
}
