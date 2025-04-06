package net.playavalon.mythicdungeons.objenesis.strategy;

import net.playavalon.mythicdungeons.objenesis.instantiator.ObjectInstantiator;

public interface InstantiatorStrategy {
   <T> ObjectInstantiator<T> newInstantiatorOf(Class<T> var1);
}
