package nl.hauntedmc.dungeons.experience;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import nl.hauntedmc.featureframework.hud.HudFrame;
import nl.hauntedmc.featureframework.hud.HudService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/** Bridges the optional provider's bundled HUD API across Paper plugin class loaders. */
final class SharedHudBackend implements HudService {
    private final Object provider;
    private final ClassLoader loader;
    private final Class<?> channelType;
    private final Class<?> frameType;
    private final Class<?> leaseType;
    private final Class<?> serviceType;

    static HudService discover(Plugin features, HudService previous) {
        if (features == null || !features.isEnabled()) return null;
        try {
            Class<?> api =
                    Class.forName(
                            HudService.class.getName(),
                            false,
                            features.getClass().getClassLoader());
            var registration = Bukkit.getServicesManager().getRegistration(api);
            if (registration == null) return null;
            Object provider = registration.getProvider();
            if (provider instanceof HudService local) return local;
            if (previous instanceof SharedHudBackend bridge && bridge.provider == provider)
                return previous;
            return new SharedHudBackend(api, provider);
        } catch (ClassNotFoundException absent) {
            return null;
        }
    }

    private SharedHudBackend(Class<?> api, Object provider) throws ClassNotFoundException {
        this.provider = provider;
        serviceType = api;
        loader = api.getClassLoader();
        channelType = Class.forName(HudService.Channel.class.getName(), false, loader);
        frameType = Class.forName(HudFrame.class.getName(), false, loader);
        leaseType = Class.forName(HudService.Lease.class.getName(), false, loader);
    }

    @Override
    public Lease claim(
            String owner, UUID player, Channel channel, int priority, Duration lifetime) {
        try {
            Object remoteChannel = channelType.getField(channel.name()).get(null);
            Object lease =
                    call(
                            serviceType.getMethod(
                                    "claim",
                                    String.class,
                                    UUID.class,
                                    channelType,
                                    int.class,
                                    Duration.class),
                            provider,
                            owner,
                            player,
                            remoteChannel,
                            priority,
                            lifetime);
            return new Lease() {
                @Override
                public void update(HudFrame frame) {
                    invokeLease("update", frameType, convert(frame), lease);
                }

                @Override
                public void renew(Duration duration) {
                    invokeLease("renew", Duration.class, duration, lease);
                }

                @Override
                public boolean active() {
                    return (boolean) invokeLease("active", lease);
                }

                @Override
                public void close() {
                    invokeLease("close", lease);
                }
            };
        } catch (ReflectiveOperationException failure) {
            throw incompatible(failure);
        }
    }

    @Override
    public void releaseOwner(String owner) {
        invokeService("releaseOwner", String.class, owner);
    }

    @Override
    public void removePlayer(UUID player) {
        invokeService("removePlayer", UUID.class, player);
    }

    private void invokeService(String name, Class<?> type, Object argument) {
        try {
            call(serviceType.getMethod(name, type), provider, argument);
        } catch (ReflectiveOperationException failure) {
            throw incompatible(failure);
        }
    }

    private Object invokeLease(String name, Object lease) {
        try {
            return call(leaseType.getMethod(name), lease);
        } catch (ReflectiveOperationException failure) {
            throw incompatible(failure);
        }
    }

    private void invokeLease(String name, Class<?> type, Object argument, Object lease) {
        try {
            call(leaseType.getMethod(name, type), lease, argument);
        } catch (ReflectiveOperationException failure) {
            throw incompatible(failure);
        }
    }

    /** Only our immutable HUD records cross this boundary; Adventure and JDK types are shared. */
    private Object convert(Object value) {
        if (value == null) return null;
        if (value instanceof List<?> list) return list.stream().map(this::convert).toList();
        Class<?> type = value.getClass();
        if (!(value instanceof HudFrame) && !(value instanceof HudFrame.Bar)) return value;
        try {
            var components = type.getRecordComponents();
            Class<?>[] parameters =
                    Arrays.stream(components)
                            .map(component -> component.getType())
                            .toArray(Class<?>[]::new);
            var arguments = new Object[components.length];
            for (int i = 0; i < components.length; i++)
                arguments[i] = convert(call(components[i].getAccessor(), value));
            return Class.forName(type.getName(), false, loader)
                    .getConstructor(parameters)
                    .newInstance(arguments);
        } catch (ReflectiveOperationException failure) {
            throw incompatible(failure);
        }
    }

    private static Object call(Method method, Object target, Object... arguments)
            throws IllegalAccessException {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
            if (failure.getCause() instanceof Error error) throw error;
            throw incompatible(failure);
        }
    }

    private static IllegalStateException incompatible(Exception failure) {
        return new IllegalStateException("ServerFeatures HUD API is incompatible", failure);
    }
}
