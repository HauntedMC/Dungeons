package net.playavalon.mythicdungeons.objenesis;

import net.playavalon.mythicdungeons.objenesis.instantiator.ObjectInstantiator;

public interface Objenesis {
   <T> T newInstance(Class<T> var1);

   <T> ObjectInstantiator<T> getInstantiatorOf(Class<T> var1);
}
