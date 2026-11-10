package nl.hauntedmc.dungeons.command;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

class CommandRoutesTest {
    @Test
    void removedRoutesAreNotPublicAliases() {
        for (String removed : List.of("admin overview", "dungeon create crypt static", "config crypt enabled true",
                "debug doors test-all", "team delete", "team deny", "team revoke Steve", "team info")) {
            assertTrue(CommandRoutes.ALL.stream().noneMatch(route -> route.match(removed.split(" ")) != null), removed);
        }
    }

    @Test
    void everyStaffActionHasAnExecutableCanonicalConfirmationRoute() {
        for (var route : CommandRoutes.ALL) {
            if (route.handler() != CommandRoutes.Handler.STAFF) continue;
            String[] arguments = Arrays.stream(route.syntax().split(" "))
                    .map(part -> part.startsWith("<") ? "example" : part.startsWith("*") ? "verified-reason" : part)
                    .toArray(String[]::new);
            var values = route.match(arguments);
            assertNotNull(values, route.syntax());
            var operation = route.arguments(values);
            String canonical = CommandRoutes.staffCommand(List.of(operation));
            String[] redirected = canonical.substring("/dungeon ".length()).split(" ");
            assertTrue(CommandRoutes.ALL.stream().anyMatch(candidate -> candidate.handler() == route.handler()
                    && candidate.match(redirected) != null
                    && Arrays.equals(operation, candidate.arguments(candidate.match(redirected)))), canonical);
        }
    }

    @Test
    void reasonsAndConfirmationTokensRetainTheirExactArguments() {
        var route = CommandRoutes.ALL.stream().filter(value -> value.syntax().startsWith("player <player> compensate")).findFirst().orElseThrow();
        var arguments = "player Steve compensate DIAMOND 3 Lost items --confirm receipt".split(" ");
        assertArrayEquals("compensate Steve DIAMOND 3 Lost items --confirm receipt".split(" "),
                route.arguments(route.match(arguments)));
        assertNull(route.match("player Steve compensate DIAMOND 3".split(" ")));
    }
}
