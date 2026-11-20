package nl.hauntedmc.dungeons.command;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class CommandRoutesTest {
    @Test
    void removedRoutesAreNotPublicAliases() {
        for (String removed :
                List.of(
                        "admin overview",
                        "dungeon create crypt static",
                        "config crypt enabled true",
                        "debug doors test-all",
                        "team delete",
                        "team deny",
                        "team revoke Steve",
                        "team info")) {
            assertTrue(
                    CommandRoutes.ALL.stream()
                            .noneMatch(route -> route.match(removed.split(" ")) != null),
                    removed);
        }
    }

    @Test
    void everyRouteAcceptsItsDeclaredCombinationAndRejectsMissingOrSurplusArguments() {
        var syntaxes = new HashSet<String>();
        for (var route : CommandRoutes.ALL) {
            assertTrue(syntaxes.add(route.syntax()), "Duplicate syntax " + route.syntax());
            var sample = new ArrayList<String>();
            for (String part : route.syntax().split(" ")) {
                if (part.startsWith("*")) {
                    sample.add("first");
                    sample.add("second");
                } else if (part.startsWith("<")) {
                    sample.add(example(part));
                } else {
                    sample.add(part.toUpperCase(Locale.ROOT));
                }
            }
            String[] args = sample.toArray(String[]::new);
            var firstMatch =
                    CommandRoutes.ALL.stream().filter(r -> r.match(args) != null).findFirst();
            assertEquals(route, firstMatch.orElseThrow(), String.join(" ", sample));
            assertNotEquals(0, route.arguments(route.match(args)).length, route.syntax());

            boolean trailingWords = route.syntax().contains(" *");
            int requiredArgs = args.length - (trailingWords ? 1 : 0);
            assertNull(route.match(Arrays.copyOf(args, requiredArgs - 1)), route.syntax());
            var missingLast = Arrays.copyOf(args, requiredArgs);
            missingLast[requiredArgs - 1] = "";
            assertNull(route.match(missingLast), route.syntax());

            sample.add("unexpected");
            if (!trailingWords)
                assertNull(route.match(sample.toArray(String[]::new)), route.syntax());
        }
    }

    @Test
    void rejectedParameterCombinationsCannotReachAnotherRoute() {
        for (String invalid :
                List.of(
                        "repo pull crypt --version",
                        "repo pull crypt --force --force",
                        "repo pull crypt --version 1.0.0 --confirm token",
                        "repo diff crypt 1 extra",
                        "edit testplay crypt hard",
                        "edit experience save extra",
                        "hud minimal false",
                        "hud titles true extra",
                        "objectives pin",
                        "objectives hint goal",
                        "map settings crypt clear gamemode extra",
                        "map settings crypt set gamemode",
                        "player Alex send crypt --difficulty",
                        "leave token")) {
            assertTrue(
                    CommandRoutes.ALL.stream()
                            .noneMatch(route -> route.match(invalid.split(" ")) != null),
                    invalid);
        }
    }

    @Test
    void forcedPullAcceptsBothFlagOrdersAsTheSameOperation() {
        var versionFirst = new String[] {"repo", "pull", "crypt", "--version", "1.2.3", "--force"};
        var forceFirst = new String[] {"repo", "pull", "crypt", "--force", "--version", "1.2.3"};
        var operations =
                List.of(versionFirst, forceFirst).stream()
                        .map(
                                args -> {
                                    var route =
                                            CommandRoutes.ALL.stream()
                                                    .filter(
                                                            candidate ->
                                                                    candidate.match(args) != null)
                                                    .findFirst()
                                                    .orElseThrow();
                                    return route.arguments(route.match(args));
                                })
                        .toList();
        assertArrayEquals(operations.getFirst(), operations.getLast());
    }

    private static String example(String parameter) {
        return switch (parameter) {
            case "<page>", "<count>", "<seconds>", "<filter-or-page>" -> "2";
            case "<enabled>" -> "true";
            case "<state>" -> "off";
            case "<environment>" -> "the_end";
            case "<version>" -> "1.2.3";
            case "<bump>" -> "patch";
            case "<type>" -> "branching";
            case "<difficulty>" -> "hard";
            case "<recipe>" -> "waves";
            case "<player>" -> "Alex";
            default -> "example";
        };
    }

    @Test
    void everyStaffActionHasAnExecutableCanonicalConfirmationRoute() {
        for (var route : CommandRoutes.ALL) {
            if (route.handler() != CommandRoutes.Handler.STAFF) continue;
            String[] arguments =
                    Arrays.stream(route.syntax().split(" "))
                            .map(
                                    part ->
                                            part.startsWith("<")
                                                    ? "example"
                                                    : part.startsWith("*")
                                                            ? "example-filter"
                                                            : part)
                            .toArray(String[]::new);
            var values = route.match(arguments);
            assertNotNull(values, route.syntax());
            var operation = route.arguments(values);
            String canonical = CommandRoutes.staffCommand(List.of(operation));
            String[] redirected = canonical.substring("/dungeon ".length()).split(" ");
            assertTrue(
                    CommandRoutes.ALL.stream()
                            .anyMatch(
                                    candidate ->
                                            candidate.handler() == route.handler()
                                                    && candidate.match(redirected) != null
                                                    && Arrays.equals(
                                                            operation,
                                                            candidate.arguments(
                                                                    candidate.match(redirected)))),
                    canonical);
        }
    }
}
