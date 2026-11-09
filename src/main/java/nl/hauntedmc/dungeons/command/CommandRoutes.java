package nl.hauntedmc.dungeons.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Canonical public syntax. Internal operation arguments are not registered public aliases. */
final class CommandRoutes {
    enum Handler { GAMEPLAY, TEAM, MAP, SETTINGS, EDIT, DEBUG, LOOT, STAFF, QUERY, DISCOVERY }

    record Route(String syntax, String permission, boolean playerOnly, Handler handler, String operation) {
        Map<String, List<String>> match(String[] arguments) {
            String[] pattern = syntax.split(" ");
            var values = new LinkedHashMap<String, List<String>>();
            int position = 0;
            for (String part : pattern) {
                if (part.startsWith("*")) {
                    if (position >= arguments.length) return null;
                    values.put(part.substring(1), List.of(arguments).subList(position, arguments.length));
                    position = arguments.length;
                    break;
                }
                if (position >= arguments.length) return null;
                if (variable(part)) values.put(part.substring(1, part.length() - 1), List.of(arguments[position]));
                else if (!part.equalsIgnoreCase(arguments[position])) return null;
                position++;
            }
            return position == arguments.length ? values : null;
        }

        String[] arguments(Map<String, List<String>> values) {
            var result = new ArrayList<String>();
            for (String part : operation.split(" ")) {
                if (part.startsWith("*")) result.addAll(values.get(part.substring(1)));
                else if (variable(part)) result.addAll(values.get(part.substring(1, part.length() - 1)));
                else if (!part.isEmpty()) result.add(part);
            }
            return result.toArray(String[]::new);
        }

        String usage() {
            return syntax.replaceAll("\\*([a-z_-]+)", "<$1...>");
        }
    }

    private static boolean variable(String part) { return part.startsWith("<") && part.endsWith(">"); }
    private static Route r(String syntax, String permission, boolean player, Handler handler, String operation) {
        return new Route(syntax, permission, player, handler, operation);
    }

    static final List<Route> ALL = List.of(
        r("list", "play", false, Handler.DISCOVERY, "list"),
        r("list *filter", "play", false, Handler.DISCOVERY, "list *filter"),
        r("info <map>", "play", false, Handler.DISCOVERY, "info <map>"),
        r("play <map>", "play", true, Handler.GAMEPLAY, "play <map>"),
        r("play <map> <difficulty>", "play", true, Handler.GAMEPLAY, "play <map> <difficulty>"),
        r("leave", "play", true, Handler.GAMEPLAY, "leave"),
        r("lives", "lives", true, Handler.GAMEPLAY, "lives"),
        r("stuck", "stuck", true, Handler.GAMEPLAY, "stuck"),
        r("rewards", "rewards", true, Handler.GAMEPLAY, "rewards"),
        r("team", "play", true, Handler.TEAM, "team info"),
        r("team create", "play", true, Handler.TEAM, "team create"),
        r("team invite <player>", "play", true, Handler.TEAM, "team invite <player>"),
        r("team cancel-invite <player>", "play", true, Handler.TEAM, "team revoke <player>"),
        r("team accept", "play", true, Handler.TEAM, "team accept"),
        r("team decline", "play", true, Handler.TEAM, "team deny"),
        r("team kick <player>", "play", true, Handler.TEAM, "team kick <player>"),
        r("team leave", "play", true, Handler.TEAM, "team leave"),
        r("team disband", "play", true, Handler.TEAM, "team delete"),
        r("map list", "staff.inspect", false, Handler.STAFF, "dungeons"),
        r("map list *filter", "staff.inspect", false, Handler.STAFF, "dungeons *filter"),
        r("map info <map>", "staff.inspect", false, Handler.STAFF, "dungeon <map>"),
        r("map validate <map>", "staff.inspect", false, Handler.STAFF, "dungeon <map> validate"),
        r("map create <map> <type>", "staff.edit", true, Handler.MAP, "dungeon create <map> <type>"),
        r("map create <map> <type> --generator <generator>", "staff.edit", true, Handler.MAP, "dungeon create <map> <type> <generator>"),
        r("map import <world> <map> --environment <environment>", "staff.edit", false, Handler.MAP, "dungeon import <world> <map> <environment>"),
        r("map import <world> <map>", "staff.edit", false, Handler.MAP, "dungeon import <world> <map>"),
        r("map enable <map> *reason", "staff.edit", false, Handler.MAP, "dungeon enable <map> *reason"),
        r("map disable <map> *reason", "staff.edit", false, Handler.MAP, "dungeon disable <map> *reason"),
        r("map reload <map> *reason", "staff.reload", false, Handler.STAFF, "reload <map> *reason"),
        r("map maintenance <map> <state> *reason", "staff.maintenance", false, Handler.STAFF, "maintenance <map> <state> *reason"),
        r("map backup list <map>", "staff.inspect", false, Handler.STAFF, "backups <map>"),
        r("map backup create <map> *reason", "staff.backup", false, Handler.STAFF, "backup <map> *reason"),
        r("map backup restore <map> <backup> *reason", "staff.restore", false, Handler.STAFF, "restore <map> <backup> *reason"),
        r("map archive info <archive>", "staff.inspect", false, Handler.STAFF, "archive-info <archive>"),
        r("map archive list", "staff.inspect", false, Handler.STAFF, "archives"),
        r("map archive create <map> *reason", "staff.delete", false, Handler.STAFF, "archive <map> *reason"),
        r("map archive restore <archive> *reason", "staff.restore", false, Handler.STAFF, "unarchive <archive> *reason"),
        r("map archive purge <archive> *reason", "staff.purge", false, Handler.STAFF, "purge <archive> *reason"),
        r("map keys list <map>", "staff.keys", false, Handler.STAFF, "keys <map>"),
        r("map keys add <map> *reason", "staff.keys", true, Handler.MAP, "dungeon keys add <map> *reason"),
        r("map keys remove <map> <key> *reason", "staff.keys", true, Handler.MAP, "dungeon keys remove <map> <key> *reason"),
        r("map keys clear <map> *reason", "staff.keys", true, Handler.MAP, "dungeon keys clear <map> *reason"),
        r("map keys give <map> <player> <key> *reason", "staff.keys", false, Handler.STAFF, "keys <map> give <player> <key> *reason"),
        r("map settings <map> list", "staff.edit", false, Handler.SETTINGS, "config <map> list"),
        r("map settings <map> list *filter", "staff.edit", false, Handler.SETTINGS, "config <map> list *filter"),
        r("map settings <map> get <setting>", "staff.edit", false, Handler.SETTINGS, "config <map> <setting>"),
        r("map settings <map> set <setting> *value", "staff.edit", false, Handler.SETTINGS, "config <map> <setting> *value"),
        r("map settings <map> inherit <setting>", "staff.edit", false, Handler.SETTINGS, "config <map> <setting> inherit"),
        r("map settings <map> clear <setting>", "staff.edit", false, Handler.SETTINGS, "config <map> <setting> clear"),
        r("map settings <map> add <setting> *value", "staff.edit", false, Handler.SETTINGS, "config <map> <setting> add *value"),
        r("map settings <map> remove <setting> *value", "staff.edit", false, Handler.SETTINGS, "config <map> <setting> remove *value"),
        r("edit open <map>", "staff.edit", true, Handler.STAFF, "edit <map> editor-command"),
        r("edit open <map> *reason", "staff.edit", true, Handler.STAFF, "edit <map> *reason"),
        r("edit save", "staff.edit", true, Handler.EDIT, "edit save"),
        r("edit close", "staff.edit", true, Handler.EDIT, "edit close"),
        r("edit location lobby set", "staff.edit", true, Handler.EDIT, "edit lobby set"),
        r("edit location start set", "staff.edit", true, Handler.EDIT, "edit start set"),
        r("edit location exit set", "staff.edit", true, Handler.EDIT, "edit exit set"),
        r("edit items ban", "staff.edit", true, Handler.EDIT, "edit items ban"),
        r("edit items unban", "staff.edit", true, Handler.EDIT, "edit items unban"),
        r("edit items mark-dungeon", "staff.edit", true, Handler.EDIT, "edit items mark-dungeon"),
        r("edit tools function", "staff.edit", true, Handler.EDIT, "edit tools function"),
        r("edit tools room", "staff.edit", true, Handler.EDIT, "edit tools room"),
        r("edit signs clean", "staff.edit", true, Handler.EDIT, "edit signs clean"),
        r("edit test map", "staff.diagnostics", true, Handler.DEBUG, "debug map"),
        r("edit test doors all", "staff.diagnostics", true, Handler.DEBUG, "debug doors test-all"),
        r("edit test doors <door>", "staff.diagnostics", true, Handler.DEBUG, "debug doors test <door>"),
        r("loot list", "staff.loot", false, Handler.STAFF, "loot"),
        r("loot info <table>", "staff.loot", false, Handler.LOOT, "loot info <table>"),
        r("loot create <table>", "staff.loot", true, Handler.LOOT, "loot create <table>"),
        r("loot edit <table>", "staff.loot", true, Handler.LOOT, "loot edit <table>"),
        r("loot delete <table> *reason", "staff.loot", false, Handler.STAFF, "loot-remove <table> *reason"),
        r("player <player> send <map> --difficulty <difficulty> *reason", "play.send", false, Handler.GAMEPLAY, "send <player> <map> --difficulty <difficulty> *reason"),
        r("player <player> send <map> *reason", "play.send", false, Handler.GAMEPLAY, "send <player> <map> *reason"),
        r("player <player> info", "staff.inspect", false, Handler.STAFF, "player <player>"),
        r("player <player> rescue *reason", "staff.rescue", false, Handler.STAFF, "player <player> rescue *reason"),
        r("player <player> remove *reason", "staff.remove", false, Handler.STAFF, "player <player> remove *reason"),
        r("player <player> recover *reason", "staff.recovery", false, Handler.STAFF, "player <player> recover *reason"),
        r("player <player> revive *reason", "staff.lives", false, Handler.STAFF, "player <player> revive *reason"),
        r("player <player> lives <count> *reason", "staff.lives", false, Handler.STAFF, "player <player> lives <count> *reason"),
        r("player <player> compensate <material> <amount> *reason", "staff.compensate", false, Handler.STAFF, "compensate <player> <material> <amount> *reason"),
        r("player <player> completion <map> set *reason", "staff.progression", false, Handler.STAFF, "completion <player> <map> true *reason"),
        r("player <player> completion <map> clear *reason", "staff.progression", false, Handler.STAFF, "completion <player> <map> false *reason"),
        r("player <player> cooldown access <map> <value> *reason", "staff.cooldown", false, Handler.STAFF, "cooldown <player> <map> <value> *reason"),
        r("player <player> cooldown rewards <map> clear *reason", "staff.cooldown", false, Handler.STAFF, "loot-cooldown <player> <map> clear *reason"),
        r("player <player> restriction <map> <value> *reason", "staff.restrict", false, Handler.STAFF, "restrict <player> <map> <value> *reason"),
        r("instance list", "staff.inspect", false, Handler.STAFF, "instances"),
        r("instance list <map>", "staff.inspect", false, Handler.STAFF, "instances <map>"),
        r("instance info <instance>", "staff.inspect", false, Handler.STAFF, "instance <instance>"),
        r("instance observe <instance>", "staff.observe", true, Handler.STAFF, "instance <instance> observe"),
        r("instance stop-observing", "staff.observe", true, Handler.STAFF, "stop-observing"),
        r("instance extend <instance> <seconds> *reason", "staff.timer", false, Handler.STAFF, "instance <instance> extend <seconds> *reason"),
        r("instance evacuate <instance> *reason", "staff.rescue", false, Handler.STAFF, "instance <instance> evacuate *reason"),
        r("instance retry-cleanup <instance> *reason", "staff.recovery", false, Handler.STAFF, "instance <instance> retry *reason"),
        r("queue list <map>", "staff.inspect", false, Handler.STAFF, "queues <map>"),
        r("queue info <admission>", "staff.inspect", false, Handler.STAFF, "queue-info <admission>"),
        r("queue cancel <admission> *reason", "staff.queue", false, Handler.STAFF, "queue-cancel <admission> *reason"),
        r("queue list", "staff.inspect", false, Handler.STAFF, "queues"),
        r("queue cancel-player <player> *reason", "staff.queue", false, Handler.STAFF, "player <player> queue-clear *reason"),
        r("server", "staff.inspect", false, Handler.STAFF, "dashboard"),
        r("server status", "staff.inspect", false, Handler.STAFF, "overview"),
        r("server problems", "staff.diagnostics", false, Handler.STAFF, "problems"),
        r("server reload config *reason", "staff.reload", false, Handler.STAFF, "reload config *reason"),
        r("server reload maps *reason", "staff.reload", false, Handler.STAFF, "reload all *reason"),
        r("server reload all *reason", "staff.reload", false, Handler.STAFF, "reload-all all *reason"),
        r("server maintenance <state> *reason", "staff.maintenance", false, Handler.STAFF, "maintenance all <state> *reason"),
        r("server operations list", "staff.inspect", false, Handler.STAFF, "operations"),
        r("server operations info <operation>", "staff.inspect", false, Handler.STAFF, "operation <operation>"),
        r("server operations cancel <operation> *reason", "staff.maintenance", false, Handler.STAFF, "cancel <operation> *reason")
    );

    static String staffCommand(List<String> operation) {
        return canonicalCommand(Handler.STAFF, operation);
    }

    static String canonicalCommand(Handler handler, List<String> operation) {
        String[] args = operation.toArray(String[]::new);
        for (var route : ALL) {
            if (route.handler != handler) continue;
            var reverse = new Route(route.operation, route.permission, route.playerOnly, route.handler, route.syntax);
            var values = reverse.match(args);
            if (values != null) return "/dungeon " + String.join(" ", reverse.arguments(values));
        }
        throw new IllegalArgumentException("No canonical route for operation " + String.join(" ", operation));
    }

    private CommandRoutes() {}
}
