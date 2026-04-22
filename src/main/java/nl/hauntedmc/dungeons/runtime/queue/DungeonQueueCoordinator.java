package nl.hauntedmc.dungeons.runtime.queue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.plugin.DungeonsPlugin;
import nl.hauntedmc.dungeons.runtime.dungeon.DungeonRepository;
import nl.hauntedmc.dungeons.runtime.instance.ActiveInstanceRegistry;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.runtime.player.PlayerSessionRegistry;
import nl.hauntedmc.dungeons.runtime.team.DungeonTeam;
import nl.hauntedmc.dungeons.runtime.team.DungeonTeamService;
import nl.hauntedmc.dungeons.runtime.team.TeamRequirementPolicy;
import nl.hauntedmc.dungeons.util.item.ItemUtils;
import nl.hauntedmc.dungeons.util.lang.LangPlaceholder;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.Nullable;

/**
 * Coordinates queue creation, validation, and queue-to-instance startup.
 *
 * <p>The registry stores queue state, but this class owns the workflow rules around team
 * eligibility, required keys, and retrying queued starts as capacity becomes available.</p>
 */
public final class DungeonQueueCoordinator {
    private final DungeonsPlugin plugin;
    private final PlayerSessionRegistry playerManager;
    private final ActiveInstanceRegistry activeInstanceManager;
    private final DungeonQueueRegistry queueManager;
    private final DungeonTeamService teamManager;
    private DungeonRepository dungeonManager;

    /**
     * Creates the coordinator with the services required to validate and launch queued runs.
     */
    public DungeonQueueCoordinator(
            DungeonsPlugin plugin,
            PlayerSessionRegistry playerManager,
            ActiveInstanceRegistry activeInstanceManager,
            DungeonQueueRegistry queueManager,
            DungeonTeamService teamManager) {
        this.plugin = plugin;
        this.playerManager = playerManager;
        this.activeInstanceManager = activeInstanceManager;
        this.queueManager = queueManager;
        this.teamManager = teamManager;
    }

    /**
     * Binds the loaded dungeon catalogue after bootstrap has populated it.
     */
    public void setDungeonCatalog(DungeonRepository dungeonManager) {
        this.dungeonManager = dungeonManager;
    }

    /**
     * Queues or starts a dungeon for a solo player or their active team.
     */
    public void sendToDungeon(Player player, String dungeonName, String difficulty) {
        DungeonTeam team = this.teamManager.getTeam(player.getUniqueId());
        if (team != null) {
            if (!team.isLeader(player.getUniqueId())) {
                LangUtils.sendMessage(player, "commands.play.team.only-leader-start");
                return;
            }

            this.sendTeamToDungeon(team, dungeonName, difficulty);
            return;
        }

        this.sendSoloToDungeon(player, dungeonName, difficulty);
    }

    /**
     * Verifies that all queued players are still online and not already inside another instance.
     */
    public boolean prepareQueueEntryForStart(DungeonQueueEntry queue) {
        for (UUID playerId : new ArrayList<>(queue.getPlayers())) {
            DungeonPlayerSession dungeonPlayer = this.playerManager.get(playerId);
            Player player = dungeonPlayer == null ? null : dungeonPlayer.getPlayer();
            if (dungeonPlayer == null || player == null || !player.isOnline()) {
                return false;
            }

            if (dungeonPlayer.getInstance() != null) {
                LangUtils.sendMessage(player, "commands.play.already-in-dungeon-self");
                return false;
            }
        }

        return true;
    }

    /**
     * Attempts to start a queue and discards it when it can no longer remain valid.
     */
    public boolean tryStartQueuedDungeon(DungeonQueueEntry queue) {
        boolean started = this.startQueue(queue);
        if (started) {
            this.queueManager.unqueue(queue, false);
        } else if (!this.canRemainQueued(queue)) {
            this.discardQueue(queue);
        }

        return started;
    }

    /**
     * Removes a queue and clears any queued state tracked on the owning team.
     */
    public void discardQueue(DungeonQueueEntry queue) {
        this.queueManager.unqueue(queue);
        if (queue != null && queue.isTeamQueue()) {
            this.teamManager.clearQueued(this.teamManager.getTeamById(queue.getTeamId()));
        }
    }

        private void sendSoloToDungeon(Player player, String dungeonName, String difficulty) {
        DungeonPlayerSession playerSession = this.playerManager.get(player);
        DungeonDefinition targetDungeon =
                this.dungeonManager == null ? null : this.dungeonManager.get(dungeonName);
        if (playerSession == null || targetDungeon == null) {
            this.plugin
                    .getSLF4JLogger()
                    .warn(
                            "Failed to queue player '{}' for dungeon '{}': player state or dungeon was missing.",
                            player.getName(),
                            dungeonName);
            return;
        }

        DungeonQueueEntry queue = new DungeonQueueEntry(playerSession, targetDungeon, difficulty);
        List<DungeonPlayerSession> players = List.of(playerSession);
        if (!this.validateSoloRequest(playerSession, targetDungeon)) {
            return;
        }

        if (!this.validateRequestedTeamSize(targetDungeon, 1, players)) {
            return;
        }

        if (!this.reserveRequiredAccessKeys(targetDungeon, players, null)) {
            return;
        }

        playerSession.setAwaitingDungeon(true);
        if (this.activeInstanceManager.canStartDungeonNow(targetDungeon, players.size(), difficulty)) {
            if (!this.startQueue(queue)) {
                if (this.canRemainQueued(queue)
                        && !this.activeInstanceManager.canStartDungeonNow(
                                targetDungeon, players.size(), difficulty)) {
                    this.queueManager.enqueue(queue);
                    LangUtils.sendMessage(player, "commands.play.instances-full");
                    LangUtils.sendMessage(player, "commands.play.how-to-cancel");
                } else {
                    this.refundReservedAccessKeys(players, targetDungeon);
                    playerSession.setAwaitingDungeon(false);
                }
            }
        } else {
            this.queueManager.enqueue(queue);
            LangUtils.sendMessage(player, "commands.play.instances-full");
            LangUtils.sendMessage(player, "commands.play.how-to-cancel");
        }
    }

        private void sendTeamToDungeon(DungeonTeam team, String dungeonName, String difficulty) {
        DungeonPlayerSession leaderState = this.playerManager.get(team.getLeaderId());
        Player leader = leaderState == null ? null : leaderState.getPlayer();
        DungeonDefinition targetDungeon =
                this.dungeonManager == null ? null : this.dungeonManager.get(dungeonName);
        if (leaderState == null || leader == null || targetDungeon == null) {
            String leaderName = leader == null ? team.getLeaderId().toString() : leader.getName();
            this.plugin
                    .getSLF4JLogger()
                    .warn(
                            "Failed to queue team led by '{}' for dungeon '{}': player state or dungeon was missing.",
                            leaderName,
                            dungeonName);
            return;
        }

        if (team.isLocked()) {
            LangUtils.sendMessage(leader, "commands.play.team.already-locked");
            return;
        }

        List<DungeonPlayerSession> members = this.collectReadyTeamMembers(team, leader);
        if (members == null) {
            return;
        }

        if (!this.validateTeamEntryRequirements(targetDungeon, leader, members)) {
            return;
        }

        if (!this.validateRequestedTeamSize(targetDungeon, members.size(), members)) {
            return;
        }

        if (!this.reserveRequiredAccessKeys(targetDungeon, members, leader)) {
            return;
        }

        this.setAwaitingDungeon(members, true);
        DungeonQueueEntry queue =
                                new DungeonQueueEntry(
                        leaderState, targetDungeon, difficulty, team.getMemberIds(), team.getTeamId());
        this.teamManager.markQueued(team, dungeonName, difficulty);

        if (this.activeInstanceManager.canStartDungeonNow(targetDungeon, members.size(), difficulty)) {
            if (!this.startQueue(queue)) {
                if (this.canRemainQueued(queue)
                        && !this.activeInstanceManager.canStartDungeonNow(
                                targetDungeon, members.size(), difficulty)) {
                    this.queueManager.enqueue(queue);
                    for (UUID memberId : team.getMemberIds()) {
                        DungeonPlayerSession memberState = this.playerManager.get(memberId);
                        if (memberState != null && memberState.getPlayer() != null) {
                            LangUtils.sendMessage(memberState.getPlayer(), "commands.play.instances-full");
                            LangUtils.sendMessage(memberState.getPlayer(), "commands.play.how-to-cancel");
                        }
                    }
                } else {
                    this.refundReservedAccessKeys(members, targetDungeon);
                    this.teamManager.clearQueued(team);
                    this.setAwaitingDungeon(members, false);
                }
            }
        } else {
            this.queueManager.enqueue(queue);
            for (UUID memberId : team.getMemberIds()) {
                DungeonPlayerSession memberState = this.playerManager.get(memberId);
                if (memberState != null && memberState.getPlayer() != null) {
                    LangUtils.sendMessage(memberState.getPlayer(), "commands.play.instances-full");
                    LangUtils.sendMessage(memberState.getPlayer(), "commands.play.how-to-cancel");
                }
            }
        }
    }

        private boolean hasRequiredAccessKey(DungeonDefinition dungeon, Player player) {
        if (dungeon.getValidKeys().isEmpty()) {
            return true;
        }

        return dungeon.getFirstKeyAmount(player) != -1;
    }

        private boolean reserveRequiredAccessKeys(
            DungeonDefinition dungeon, List<DungeonPlayerSession> players, Player leader) {
        if (!dungeon.getConfig().getBoolean("access.keys.consume_on_entry", true)
                || dungeon.getValidKeys().isEmpty()) {
            return true;
        }

        UUID leaderId = leader == null ? null : leader.getUniqueId();
        List<DungeonPlayerSession> reservedPlayers = new ArrayList<>();
        for (DungeonPlayerSession dungeonPlayer : players) {
            Player player = dungeonPlayer == null ? null : dungeonPlayer.getPlayer();
            if (player == null || !player.isOnline()) {
                this.refundReservedAccessKeys(reservedPlayers, dungeon);
                return false;
            }

            if (dungeonPlayer.hasReservedAccessKey()) {
                this.plugin
                        .getSLF4JLogger()
                        .warn(
                                "Refunding stale reserved access key for player '{}' from dungeon '{}' before preparing '{}'.",
                                player.getName(),
                                dungeonPlayer.getReservedAccessKeyDungeon(),
                                dungeon.getWorldName());
                dungeonPlayer.refundReservedAccessKey();
            }

            // Keys are reserved up front so a queue cannot hold a slot indefinitely and then fail later
            // because one member spent or moved the required key while waiting for capacity.
            if (leaderId != null
                    && !TeamRequirementPolicy.requiresAccessKey(
                            dungeon.isOnlyLeaderNeedsKey(), leaderId, player.getUniqueId())) {
                continue;
            }

            if (!this.hasRequiredAccessKey(dungeon, player)) {
                this.refundReservedAccessKeys(reservedPlayers, dungeon);
                this.sendMissingKeyMessage(dungeon, leader, player);
                return false;
            }

            ItemStack reservedKey = this.reserveAccessKey(dungeon, player);
            if (!dungeonPlayer.reserveAccessKey(dungeon.getWorldName(), reservedKey)) {
                if (reservedKey != null && !reservedKey.getType().isAir()) {
                    ItemUtils.giveOrDrop(player, reservedKey);
                    player.updateInventory();
                }
                this.refundReservedAccessKeys(reservedPlayers, dungeon);
                this.sendMissingKeyMessage(dungeon, leader, player);
                this.plugin
                        .getSLF4JLogger()
                        .warn(
                                "Failed to reserve an access key for player '{}' while preparing dungeon '{}'.",
                                player.getName(),
                                dungeon.getWorldName());
                return false;
            }

            reservedPlayers.add(dungeonPlayer);
        }

        return true;
    }

        private ItemStack reserveAccessKey(DungeonDefinition dungeon, Player player) {
        if (dungeon.getValidKeys().isEmpty()) {
            return null;
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack heldKey = contents[slot];
            ItemStack validKey = dungeon.isValidKey(heldKey);
            if (heldKey == null || validKey == null) {
                continue;
            }

            int newAmount = heldKey.getAmount() - validKey.getAmount();
            if (newAmount < 0) {
                return null;
            }

            ItemStack reservedKey = heldKey.clone();
            reservedKey.setAmount(validKey.getAmount());
            if (newAmount == 0) {
                inventory.setItem(slot, null);
            } else {
                heldKey.setAmount(newAmount);
                inventory.setItem(slot, heldKey);
            }

            player.updateInventory();
            return reservedKey;
        }

        return null;
    }

        private boolean startQueue(DungeonQueueEntry queue) {
        if (this.dungeonManager == null || !this.prepareQueueEntryForStart(queue)) {
            return false;
        }

        List<DungeonPlayerSession> queuedPlayers = this.resolveQueuedPlayers(queue);
        if (queuedPlayers.size() != queue.getPlayers().size()) {
            return false;
        }

        if (!this.validateRequestedTeamSize(
                queue.getDungeon(), queue.getPlayers().size(), queuedPlayers)) {
            return false;
        }

        if (!this.validateQueuedCooldownRequirements(queue, queuedPlayers, true)) {
            return false;
        }

        if (!this.validateQueuedAccessKeyRequirements(queue, queuedPlayers, true)) {
            return false;
        }

        this.reconcileQueuedReservedAccessKeys(queue, queuedPlayers);

        if (!this.hasReservedAccessKeys(queue)) {
            this.discardQueue(queue);
            return false;
        }

        UUID startedTeamLeaderId = queue.isTeamQueue() ? queue.getLeaderId() : null;
        boolean started =
                this.dungeonManager.createInstance(
                        queue.getDungeon().getWorldName(),
                        queue.getQueuedPlayer().getPlayer(),
                        queue.getDifficulty(),
                        queue.getPlayers().size(),
                        startedTeamLeaderId);
        if (started && queue.isTeamQueue()) {
            queue.scheduleTeamJoin(this.plugin, this.teamManager, this.playerManager);
        }

        return started;
    }

        private boolean validateSoloRequest(DungeonPlayerSession dungeonPlayer, DungeonDefinition dungeon) {
        Player player = dungeonPlayer.getPlayer();
        if (player == null || !player.isOnline()) {
            return false;
        }

        if (dungeonPlayer.getInstance() != null) {
            LangUtils.sendMessage(player, "commands.play.already-in-dungeon-self");
            return false;
        }

        if (dungeonPlayer.isAwaitingDungeon()) {
            LangUtils.sendMessage(player, "commands.play.already-in-queue");
            return false;
        }

        if (this.queueManager.getQueue(dungeonPlayer) != null) {
            LangUtils.sendMessage(player, "commands.play.already-in-queue");
            return false;
        }

        if (dungeon.hasAccessCooldown(player)) {
            LangUtils.sendMessage(
                    player,
                    "commands.play.on-cooldown",
                    LangUtils.placeholder("dungeon", dungeon.getWorldName()));
            return false;
        }

        if (!this.hasRequiredAccessKey(dungeon, player)) {
            LangUtils.sendMessage(
                    player,
                    "commands.play.missing-key",
                    LangUtils.placeholder("dungeon", dungeon.getWorldName()));
            return false;
        }

        return true;
    }

        private List<DungeonPlayerSession> collectReadyTeamMembers(DungeonTeam team, Player leader) {
        List<DungeonPlayerSession> members = new ArrayList<>();
        for (UUID memberId : team.getMemberIds()) {
            DungeonPlayerSession memberState = this.playerManager.get(memberId);
            if (memberState == null
                    || memberState.getPlayer() == null
                    || !memberState.getPlayer().isOnline()) {
                LangUtils.sendMessage(leader, "commands.play.team.members-must-be-online");
                return null;
            }

            if (memberState.getInstance() != null
                    || memberState.isAwaitingDungeon()
                    || this.queueManager.getQueue(memberState) != null) {
                LangUtils.sendMessage(leader, "commands.play.team.members-must-be-free");
                return null;
            }

            members.add(memberState);
        }

        return members;
    }

        private void setAwaitingDungeon(List<DungeonPlayerSession> players, boolean awaitingDungeon) {
        for (DungeonPlayerSession player : players) {
            if (player != null) {
                player.setAwaitingDungeon(awaitingDungeon);
            }
        }
    }

        private void sendMissingKeyMessage(DungeonDefinition dungeon, Player leader, Player player) {
        if (leader == null || leader.getUniqueId().equals(player.getUniqueId())) {
            LangUtils.sendMessage(
                    player,
                    "commands.play.missing-key",
                    LangUtils.placeholder("dungeon", dungeon.getWorldName()));
            return;
        }

        LangUtils.sendMessage(
                leader,
                "commands.play.team.member-missing-key",
                LangUtils.placeholder("player", player.getName()),
                LangUtils.placeholder("dungeon", dungeon.getWorldName()));
    }

        private boolean canRemainQueued(DungeonQueueEntry queue) {
        if (this.dungeonManager == null) {
            return false;
        }

        if (!queue.getDungeon().canEverFitTeamSize(queue.getPlayers().size())) {
            return false;
        }

        List<DungeonPlayerSession> queuedPlayers = this.resolveQueuedPlayers(queue);
        if (queuedPlayers.size() != queue.getPlayers().size()) {
            return false;
        }

        for (DungeonPlayerSession dungeonPlayer : queuedPlayers) {
            Player player = dungeonPlayer.getPlayer();
            if (player == null || !player.isOnline() || dungeonPlayer.getInstance() != null) {
                return false;
            }
        }

        return this.validateQueuedCooldownRequirements(queue, queuedPlayers, false)
                && this.validateQueuedAccessKeyRequirements(queue, queuedPlayers, false);
    }

        private boolean validateRequestedTeamSize(
            DungeonDefinition dungeon, int requestedPlayers, List<DungeonPlayerSession> players) {
        int normalizedPlayers = Math.max(1, requestedPlayers);
        if (!dungeon.canFitTeamSize(normalizedPlayers)) {
            this.notifyPlayers(
                    players,
                    "commands.play.team.team-too-large",
                    LangUtils.placeholder("count", String.valueOf(normalizedPlayers)),
                    LangUtils.placeholder("max", String.valueOf(dungeon.getMaxTeamSize())));
            return false;
        }

        if (!dungeon.canEverFitTeamSize(normalizedPlayers)) {
            this.notifyPlayers(
                    players,
                    "commands.play.team.open-too-large",
                    LangUtils.placeholder("count", String.valueOf(normalizedPlayers)),
                    LangUtils.placeholder(
                            "max", String.valueOf(dungeon.getConfig().getInt("open.max_players", 0))));
            return false;
        }

        return true;
    }

        private List<DungeonPlayerSession> resolveQueuedPlayers(DungeonQueueEntry queue) {
        List<DungeonPlayerSession> players = new ArrayList<>();
        for (UUID playerId : new ArrayList<>(queue.getPlayers())) {
            DungeonPlayerSession player = this.playerManager.get(playerId);
            if (player != null) {
                players.add(player);
            }
        }

        return players;
    }

        private void notifyPlayers(
            List<DungeonPlayerSession> players, String messageKey, LangPlaceholder... placeholders) {
        for (DungeonPlayerSession player : players) {
            Player bukkitPlayer = player == null ? null : player.getPlayer();
            if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                LangUtils.sendMessage(bukkitPlayer, messageKey, placeholders);
            }
        }
    }

        private boolean hasReservedAccessKeys(DungeonQueueEntry queue) {
        DungeonDefinition dungeon = queue.getDungeon();
        if (!dungeon.getConfig().getBoolean("access.keys.consume_on_entry", true)
                || dungeon.getValidKeys().isEmpty()) {
            return true;
        }

        Player leader = queue.isTeamQueue() ? this.getQueuedLeader(queue) : null;
        for (UUID playerId : new ArrayList<>(queue.getPlayers())) {
            DungeonPlayerSession dungeonPlayer = this.playerManager.get(playerId);
            Player player = dungeonPlayer == null ? null : dungeonPlayer.getPlayer();
            if (dungeonPlayer == null || player == null || !player.isOnline()) {
                return false;
            }

            if (!queue.requiresAccessKey(playerId)) {
                continue;
            }

            if (!dungeonPlayer.hasReservedAccessKey(dungeon.getWorldName())) {
                this.sendMissingKeyMessage(dungeon, leader, player);
                return false;
            }
        }

        return true;
    }

        private void refundReservedAccessKeys(
            List<DungeonPlayerSession> players, DungeonDefinition dungeon) {
        for (DungeonPlayerSession player : players) {
            if (player != null && player.getInstance() == null) {
                player.refundReservedAccessKey(dungeon.getWorldName());
            }
        }
    }

        private void reconcileQueuedReservedAccessKeys(
            DungeonQueueEntry queue, List<DungeonPlayerSession> players) {
        DungeonDefinition dungeon = queue.getDungeon();
        if (!queue.isTeamQueue()
                || !dungeon.getConfig().getBoolean("access.keys.consume_on_entry", true)
                || !dungeon.isOnlyLeaderNeedsKey()) {
            return;
        }

        for (DungeonPlayerSession member : players) {
            Player player = member.getPlayer();
            if (player == null) {
                continue;
            }

            if (queue.requiresAccessKey(player.getUniqueId())) {
                continue;
            }

            member.refundReservedAccessKey(dungeon.getWorldName());
        }
    }

        private boolean validateTeamEntryRequirements(
            DungeonDefinition dungeon, Player leader, List<DungeonPlayerSession> members) {
        return this.validateTeamCooldownRequirements(dungeon, leader, members, true)
                && this.validateTeamAccessKeyRequirements(dungeon, leader, members, true);
    }

        private boolean validateTeamCooldownRequirements(
            DungeonDefinition dungeon, Player leader, List<DungeonPlayerSession> members, boolean notify) {
        UUID leaderId = leader.getUniqueId();
        for (DungeonPlayerSession memberState : members) {
            Player member = memberState.getPlayer();
            if (member == null) {
                return false;
            }

            if (!TeamRequirementPolicy.requiresAccessCooldown(
                    dungeon.isOnlyLeaderNeedsCooldown(), leaderId, member.getUniqueId())) {
                continue;
            }

            if (!dungeon.hasAccessCooldown(member)) {
                continue;
            }

            if (notify) {
                this.sendCooldownMessage(dungeon, leader, member);
            }
            return false;
        }

        return true;
    }

        private boolean validateTeamAccessKeyRequirements(
            DungeonDefinition dungeon, Player leader, List<DungeonPlayerSession> members, boolean notify) {
        UUID leaderId = leader.getUniqueId();
        for (DungeonPlayerSession memberState : members) {
            Player member = memberState.getPlayer();
            if (member == null) {
                return false;
            }

            if (!TeamRequirementPolicy.requiresAccessKey(
                    dungeon.isOnlyLeaderNeedsKey(), leaderId, member.getUniqueId())) {
                continue;
            }

            if (!this.hasRequiredAccessKey(dungeon, member)) {
                if (notify) {
                    this.sendMissingKeyMessage(dungeon, leader, member);
                }
                return false;
            }
        }

        return true;
    }

        private boolean validateQueuedCooldownRequirements(
            DungeonQueueEntry queue, List<DungeonPlayerSession> players, boolean notify) {
        if (queue.isTeamQueue()) {
            Player leader = this.getQueuedLeader(queue);
            if (leader == null) {
                return false;
            }

            for (DungeonPlayerSession memberState : players) {
                Player member = memberState.getPlayer();
                if (member == null) {
                    return false;
                }

                if (!queue.requiresAccessCooldown(member.getUniqueId())) {
                    continue;
                }

                if (!queue.getDungeon().hasAccessCooldown(member)) {
                    continue;
                }

                if (notify) {
                    this.sendCooldownMessage(queue.getDungeon(), leader, member);
                }
                return false;
            }

            return true;
        }

        Player player = queue.getQueuedPlayer().getPlayer();
        if (player == null || !queue.getDungeon().hasAccessCooldown(player)) {
            return true;
        }

        if (notify) {
            LangUtils.sendMessage(
                    player,
                    "commands.play.on-cooldown",
                    LangUtils.placeholder("dungeon", queue.getDungeon().getWorldName()));
        }
        return false;
    }

        private boolean validateQueuedAccessKeyRequirements(
            DungeonQueueEntry queue, List<DungeonPlayerSession> players, boolean notify) {
        DungeonDefinition dungeon = queue.getDungeon();
        if (dungeon.getValidKeys().isEmpty()
                || dungeon.getConfig().getBoolean("access.keys.consume_on_entry", true)) {
            return true;
        }

        if (queue.isTeamQueue()) {
            Player leader = this.getQueuedLeader(queue);
            if (leader == null) {
                return false;
            }

            for (DungeonPlayerSession memberState : players) {
                Player member = memberState.getPlayer();
                if (member == null) {
                    return false;
                }

                if (!queue.requiresAccessKey(member.getUniqueId())) {
                    continue;
                }

                if (this.hasRequiredAccessKey(dungeon, member)) {
                    continue;
                }

                if (notify) {
                    this.sendMissingKeyMessage(dungeon, leader, member);
                }
                return false;
            }

            return true;
        }

        Player player = queue.getQueuedPlayer().getPlayer();
        if (player == null || this.hasRequiredAccessKey(dungeon, player)) {
            return true;
        }

        if (notify) {
            LangUtils.sendMessage(
                    player,
                    "commands.play.missing-key",
                    LangUtils.placeholder("dungeon", dungeon.getWorldName()));
        }
        return false;
    }

        private void sendCooldownMessage(DungeonDefinition dungeon, Player leader, Player player) {
        if (leader.getUniqueId().equals(player.getUniqueId())) {
            LangUtils.sendMessage(
                    leader,
                    "commands.play.on-cooldown",
                    LangUtils.placeholder("dungeon", dungeon.getWorldName()));
            return;
        }

        LangUtils.sendMessage(
                leader,
                "commands.play.team.member-on-cooldown",
                LangUtils.placeholder("player", player.getName()),
                LangUtils.placeholder("dungeon", dungeon.getWorldName()));
    }

    @Nullable private Player getQueuedLeader(DungeonQueueEntry queue) {
        DungeonPlayerSession leaderState = this.playerManager.get(queue.getLeaderId());
        if (leaderState != null && leaderState.getPlayer() != null) {
            return leaderState.getPlayer();
        }

        return queue.getQueuedPlayer().getPlayer();
    }
}
