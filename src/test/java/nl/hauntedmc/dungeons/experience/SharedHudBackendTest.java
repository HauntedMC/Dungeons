package nl.hauntedmc.dungeons.experience;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Proxy;
import java.net.URLClassLoader;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.dungeons.support.PaperBoundary;
import nl.hauntedmc.featureframework.hud.HudFrame;
import nl.hauntedmc.featureframework.hud.HudService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.Test;

class SharedHudBackendTest {
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void separatePluginApiCopiesShareActualCoordinatorAndAllFrameTypes() throws Exception {
        try (var paper = new PaperBoundary();
                var loader =
                        new URLClassLoader(
                                new java.net.URL[] {
                                    HudService.class
                                            .getProtectionDomain()
                                            .getCodeSource()
                                            .getLocation()
                                },
                                getClass().getClassLoader()) {
                            @Override
                            protected synchronized Class<?> loadClass(String name, boolean resolve)
                                    throws ClassNotFoundException {
                                if (!name.startsWith("nl.hauntedmc.featureframework.hud."))
                                    return super.loadClass(name, resolve);
                                Class<?> type = findLoadedClass(name);
                                if (type == null) type = findClass(name);
                                if (resolve) resolveClass(type);
                                return type;
                            }
                        }) {
            Class remoteApi = loader.loadClass(HudService.class.getName());
            assertNotSame(HudService.class, remoteApi);
            var rendererType =
                    loader.loadClass("nl.hauntedmc.featureframework.hud.HudCoordinator$Renderer");
            var rendered = new AtomicReference<Object>();
            var failure = new AtomicReference<RuntimeException>();
            var renderer =
                    Proxy.newProxyInstance(
                            loader,
                            new Class<?>[] {rendererType},
                            (proxy, method, args) -> {
                                if (failure.get() != null) throw failure.get();
                                rendered.set(args[2]);
                                return null;
                            });
            var clock = new AtomicLong();
            Object provider =
                    loader.loadClass("nl.hauntedmc.featureframework.hud.HudCoordinator")
                            .getConstructor(
                                    rendererType,
                                    java.util.function.LongSupplier.class,
                                    Runnable.class)
                            .newInstance(
                                    renderer,
                                    (java.util.function.LongSupplier) clock::get,
                                    (Runnable) () -> {});
            var plugin =
                    (Plugin)
                            Proxy.newProxyInstance(
                                    loader,
                                    new Class<?>[] {Plugin.class},
                                    (proxy, method, args) ->
                                            method.getName().equals("isEnabled") ? true : null);
            var services = mock(ServicesManager.class);
            var registration = mock(RegisteredServiceProvider.class);
            doReturn(provider).when(registration).getProvider();
            doReturn(registration).when(services).getRegistration(remoteApi);
            paper.bukkit.when(Bukkit::getServicesManager).thenReturn(services);
            var bridge = SharedHudBackend.discover(plugin, null);
            assertNotNull(bridge);
            assertSame(bridge, SharedHudBackend.discover(plugin, bridge));
            var id = UUID.randomUUID();
            for (HudFrame frame :
                    List.of(
                            new HudFrame.Sidebar(
                                    Component.text("sidebar"), List.of(Component.text("line"))),
                            new HudFrame.ActionBar(Component.text("action")),
                            new HudFrame.Bars(
                                    List.of(
                                            new HudFrame.Bar(
                                                    Component.text("boss"),
                                                    .5f,
                                                    BossBar.Color.RED,
                                                    BossBar.Overlay.PROGRESS,
                                                    java.util.Set.of(BossBar.Flag.DARKEN_SCREEN)))),
                            new HudFrame.Title(
                                    Component.text("title"),
                                    Component.text("subtitle"),
                                    Duration.ZERO,
                                    Duration.ofSeconds(1),
                                    Duration.ZERO))) {
                var lease =
                        bridge.claim(
                                "Dungeons",
                                id,
                                frame.channel(),
                                HudService.SESSION,
                                Duration.ofSeconds(5));
                lease.update(frame);
                assertNotSame(frame.getClass(), rendered.get().getClass());
                assertEquals(frame.toString(), rendered.get().toString());
                lease.renew(Duration.ofSeconds(10));
                assertTrue(lease.active());
                lease.update(null);
                assertNull(rendered.get());
                lease.close();
                assertFalse(lease.active());
            }
            var lease =
                    bridge.claim(
                            "Dungeons:child",
                            id,
                            HudService.Channel.ACTION_BAR,
                            100,
                            Duration.ofSeconds(5));
            var problem = new IllegalStateException("renderer failed");
            failure.set(problem);
            assertSame(
                    problem,
                    assertThrows(
                            IllegalStateException.class,
                            () -> lease.update(new HudFrame.ActionBar(Component.text("failure")))));
            failure.set(null);
            bridge.releaseOwner("Dungeons");
            assertFalse(lease.active());
            var playerLease =
                    bridge.claim(
                            "Dungeons", id, HudService.Channel.SIDEBAR, 100, Duration.ofSeconds(5));
            bridge.removePlayer(id);
            assertFalse(playerLease.active());
        }
    }
}
