package nl.hauntedmc.dungeons.runtime.team;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import nl.hauntedmc.dungeons.event.DungeonDisposeEvent;
import nl.hauntedmc.dungeons.event.PlayerLeaveDungeonEvent;
import nl.hauntedmc.dungeons.model.instance.DungeonInstance;
import nl.hauntedmc.dungeons.plugin.DungeonsPlugin;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.runtime.player.PlayerSessionRegistry;
import nl.hauntedmc.dungeons.runtime.queue.DungeonQueueEntry;
import nl.hauntedmc.dungeons.runtime.queue.DungeonQueueRegistry;
import nl.hauntedmc.dungeons.util.config.PluginConfigView;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

/**
 * Runtime service for team membership, invitations, queued-start state, and lifecycle cleanup.
 */
public final class DungeonTeamService implements Listener {
    private final DungeonsPlugin plugin;
    private final PlayerSessionRegistry playerManager;
    private final DungeonQueueRegistry queueManager;
    private final Map<UUID, DungeonTeam> teamsById = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> teamIdByMember = new ConcurrentHashMap<>();
    private final Map<UUID, TeamInvite> invitesByTarget = new ConcurrentHashMap<>();
    private final Map<String, UUID> teamIdByInstanceWorld = new ConcurrentHashMap<>();
    private final Map<String, BukkitTask> disbandShutdownTasksByInstanceWorld =
            new ConcurrentHashMap<>();
    private final Set<UUID> dissolvingTeams = ConcurrentHashMap.newKeySet();

    /** Creates the team service with queue and player dependencies. */
    public DungeonTeamService(
            DungeonsPlugin plugin,
            PlayerSessionRegistry playerManager,
            DungeonQueueRegistry queueManager) {
        this.plugin = plugin;
        this.playerManager = playerManager;
        this.queueManager = queueManager;
    }

    /** Clears all team runtime state, invitations, and scheduled tasks. */
    public void shutdown() {
        for (DungeonTeam team : this.teamsById.values()) {
            team.cancelExpiryTask();
        }

        for (BukkitTask task : this.disbandShutdownTasksByInstanceWorld.values()) {
            task.cancel();
        }

        this.teamsById.clear();
        this.teamIdByMember.clear();
        this.invitesByTarget.clear();
        this.teamIdByInstanceWorld.clear();
        this.disbandShutdownTasksByInstanceWorld.clear();
        this.dissolvingTeams.clear();
    }

    /** Returns a team by its id, or null when absent. */
    public DungeonTeam getTeamById(UUID teamId) {
        return this.teamsById.get(teamId);
    }

    /** Returns the team containing a member id, or null when absent. */
    public DungeonTeam getTeam(UUID playerId) {
        UUID teamId = this.teamIdByMember.get(playerId);
        return teamId == null ? null : this.teamsById.get(teamId);
    }

    /**
     * Resolves the active team members that should be targeted alongside the given player inside the
     * current instance. Solo players are treated as a one-player team.
     */
    public List<DungeonPlayerSession> getActiveMembers(
            DungeonPlayerSession source, DungeonInstance instance) {
        LinkedHashSet<DungeonPlayerSession> members = new LinkedHashSet<>();
        if (source == null) {
            return new ArrayList<>();
        }

        DungeonTeam team = this.getTeam(source.getPlayer().getUniqueId());
        if (team == null || instance == null) {
            members.add(source);
            return new ArrayList<>(members);
        }

        if (source.getInstance() == instance) {
            members.add(source);
        }

        for (UUID memberId : team.getMemberIds()) {
            DungeonPlayerSession member = this.playerManager.get(memberId);
            if (member != null && member.getInstance() == instance) {
                members.add(member);
            }
        }

        if (members.isEmpty()) {
            members.add(source);
        }

        return new ArrayList<>(members);
    }

    /** Returns whether this player belongs to any team. */
    public boolean hasTeam(UUID playerId) {
        return this.getTeam(playerId) != null;
    }

    /** Returns whether this player is the leader of their team. */
    public boolean isLeader(UUID playerId) {
        DungeonTeam team = this.getTeam(playerId);
        return team != null && team.isLeader(playerId);
    }

    /** Handles `/team create` flow for one leader player. */
    public boolean handleCreateTeam(Player leader) {
        if (this.getTeam(leader.getUniqueId()) != null) {
            this.send(leader, "commands.team.already-in-team");
            return true;
        }

        DungeonPlayerSession dungeonPlayer = this.playerManager.get(leader);
        if (this.isBusyForTeamMembership(dungeonPlayer)) {
            if (dungeonPlayer == null) {
                this.plugin
                        .getSLF4JLogger()
                        .warn(
                                "Failed to create a team for '{}': player session was missing.", leader.getName());
                return true;
            }

            if (dungeonPlayer.getInstance() != null) {
                this.send(leader, "commands.team.create.in-dungeon");
                return true;
            }

            this.send(leader, "commands.team.create.in-queue");
            return true;
        }

        DungeonTeam team = new DungeonTeam(leader.getUniqueId());
        this.teamsById.put(team.getTeamId(), team);
        this.teamIdByMember.put(leader.getUniqueId(), team.getTeamId());
        this.scheduleExpiry(team);
        this.send(leader, "commands.team.create.success");
        this.send(leader, "commands.team.create.expiry-info");
        return true;
    }

    /** Handles `/team invite` flow and writes a time-limited invite. */
    public boolean handleInvite(Player leader, Player target) {
        DungeonTeam team = this.getTeam(leader.getUniqueId());
        if (team == null) {
            this.send(leader, "commands.team.no-team-create-hint");
            return true;
        }

        if (!team.isLeader(leader.getUniqueId())) {
            this.send(leader, "commands.team.invite.only-leader");
            return true;
        }

        if (team.isLocked()) {
            this.send(leader, "commands.team.locked");
            return true;
        }

        if (leader.getUniqueId().equals(target.getUniqueId())) {
            this.send(leader, "commands.team.invite.self");
            return true;
        }

        if (team.hasMember(target.getUniqueId())) {
            this.send(leader, "commands.team.invite.already-member");
            return true;
        }

        if (this.getTeam(target.getUniqueId()) != null) {
            this.send(leader, "commands.team.invite.target-in-team");
            return true;
        }

        DungeonPlayerSession targetDungeonPlayer = this.playerManager.get(target);
        if (this.isBusyForTeamMembership(targetDungeonPlayer)) {
            this.send(leader, "commands.team.invite.target-busy");
            return true;
        }

        long now = System.currentTimeMillis();
        TeamInvite invite =
                                new TeamInvite(
                        team.getTeamId(),
                        leader.getUniqueId(),
                        target.getUniqueId(),
                        now,
                        now + PluginConfigView.getTeamInviteExpiryMillis(this.plugin.getConfig()));
        this.invitesByTarget.put(target.getUniqueId(), invite);
        this.send(leader, "commands.team.invite.sent", target.getName());
        this.send(target, "commands.team.invite.received", leader.getName());
        this.send(target, "commands.team.invite.how-to-respond");
        return true;
    }

    /** Handles `/team accept` flow for the target player. */
    public boolean handleAcceptInvite(Player target) {
        TeamInvite invite = this.invitesByTarget.remove(target.getUniqueId());
        if (invite == null) {
            this.send(target, "commands.team.invite.none");
            return true;
        }

        if (invite.isExpired()) {
            this.send(target, "commands.team.invite.expired");
            return true;
        }

        if (this.getTeam(target.getUniqueId()) != null) {
            this.send(target, "commands.team.already-in-team");
            return true;
        }

        DungeonTeam team = this.teamsById.get(invite.teamId());
        if (team == null) {
            this.send(target, "commands.team.invite.team-missing");
            return true;
        }

        if (team.isLocked()) {
            this.send(target, "commands.team.invite.team-locked");
            return true;
        }

        DungeonPlayerSession targetDungeonPlayer = this.playerManager.get(target);
        if (this.isBusyForTeamMembership(targetDungeonPlayer)) {
            this.send(target, "commands.team.invite.target-busy-self");
            return true;
        }

        team.addMember(target.getUniqueId());
        this.teamIdByMember.put(target.getUniqueId(), team.getTeamId());
        this.notifyTeam(team, "commands.team.member-joined", target.getName());
        this.send(target, "commands.team.joined", this.getLeaderName(team));
        this.send(target, "commands.team.leave-hint");
        return true;
    }

    /** Handles `/team deny` flow for the target player. */
    public boolean handleDenyInvite(Player target) {
        TeamInvite invite = this.invitesByTarget.remove(target.getUniqueId());
        if (invite == null || invite.isExpired()) {
            this.send(target, "commands.team.invite.none");
            return true;
        }

        this.send(target, "commands.team.invite.denied-self");
        Player inviter = Bukkit.getPlayer(invite.inviterId());
        if (inviter != null && inviter.isOnline()) {
            this.send(inviter, "commands.team.invite.denied-other", target.getName());
        }

        return true;
    }

    /** Handles `/team delete` flow, including started-run leader behavior. */
    public boolean handleDeleteTeam(Player requester) {
        DungeonTeam team = this.getTeam(requester.getUniqueId());
        if (team == null) {
            this.send(requester, "commands.team.no-team");
            return true;
        }

        if (!team.isLeader(requester.getUniqueId())) {
            this.send(requester, "commands.team.delete.only-leader");
            return true;
        }

        DungeonPlayerSession requesterState = this.playerManager.get(requester);
        if (team.isStarted()) {
            DungeonInstance instance = requesterState == null ? null : requesterState.getInstance();
            if (instance == null
                    || instance.getInstanceWorld() == null
                    || !Objects.equals(team.getInstanceWorldName(), instance.getInstanceWorld().getName())) {
                this.send(requester, "commands.team.delete.started");
                return true;
            }

            this.disbandStartedTeamAndRemoveLeader(
                    team, instance, requesterState, "commands.team.deleted-by-leader");
            return true;
        }

        DungeonQueueEntry queue =
                requesterState == null ? null : this.queueManager.getQueue(requesterState);
        if (team.isQueued() && queue == null) {
            this.send(requester, "commands.team.delete.starting");
            return true;
        }

        if (queue != null) {
            this.queueManager.unqueue(queue);
        }

        this.dissolveTeam(team, "commands.team.deleted-by-leader", true);
        return true;
    }

    /** Handles `/team leave` flow for leaders and members. */
    public boolean handleLeaveTeam(Player requester) {
        DungeonTeam team = this.getTeam(requester.getUniqueId());
        if (team == null) {
            this.send(requester, "commands.team.not-in-team");
            return true;
        }

        if (team.isLeader(requester.getUniqueId())) {
            DungeonPlayerSession requesterState = this.playerManager.get(requester);
            if (team.isStarted()) {
                DungeonInstance instance = requesterState == null ? null : requesterState.getInstance();
                if (instance == null
                        || instance.getInstanceWorld() == null
                        || !Objects.equals(
                                team.getInstanceWorldName(), instance.getInstanceWorld().getName())) {
                    this.send(requester, "commands.team.leave.leader-cannot-leave");
                    this.send(requester, "commands.team.leave.leader-started-hint");
                    return true;
                }

                this.disbandStartedTeamAndRemoveLeader(
                        team, instance, requesterState, "commands.team.deleted-by-leader");
                return true;
            }

            DungeonQueueEntry queue =
                    requesterState == null ? null : this.queueManager.getQueue(requesterState);
            if (queue != null) {
                this.queueManager.unqueue(queue);
            }

            this.dissolveTeam(team, "commands.team.deleted-by-leader", true);
            return true;
        }

        if (team.isStarted()) {
            DungeonPlayerSession dungeonPlayer = this.playerManager.get(requester);
            DungeonInstance instance = dungeonPlayer == null ? null : dungeonPlayer.getInstance();
            if (instance != null
                    && instance.getInstanceWorld() != null
                    && Objects.equals(team.getInstanceWorldName(), instance.getInstanceWorld().getName())) {
                instance.removePlayer(dungeonPlayer);
                return true;
            }

            this.removeMember(
                    team,
                    requester.getUniqueId(),
                    "commands.team.leave.started-self",
                    "commands.team.leave.started-other",
                    requester.getName());
            return true;
        }

        if (team.isQueued()) {
            this.queueManager.removePlayer(requester.getUniqueId());
        }

        this.removeMember(
                team,
                requester.getUniqueId(),
                "commands.team.leave.self",
                "commands.team.leave.other",
                requester.getName());
        return true;
    }

    /** Handles `/team info` rendering for one requester. */
    public boolean handleInfo(Player requester) {
        DungeonTeam team = this.getTeam(requester.getUniqueId());
        if (team == null) {
            this.send(requester, "commands.team.not-in-team");
            return true;
        }

        List<String> memberNames = new ArrayList<>();
        for (UUID memberId : team.getMemberIds()) {
            Player member = Bukkit.getPlayer(memberId);
            memberNames.add(member == null ? memberId.toString() : member.getName());
        }

        String state =
                team.isStarted()
                        ? LangUtils.getMessage("commands.team.info.states.running", false)
                        : team.isQueued()
                                ? LangUtils.getMessage("commands.team.info.states.queued", false)
                                : LangUtils.getMessage("commands.team.info.states.idle", false);
        this.send(requester, "commands.team.info.leader", this.getLeaderName(team));
        this.send(requester, "commands.team.info.members", String.join(", ", memberNames));
        this.send(requester, "commands.team.info.state", state);
        if (team.getQueuedDungeonName() != null) {
            String difficulty =
                    team.getQueuedDifficulty() == null || team.getQueuedDifficulty().isEmpty()
                            ? LangUtils.getMessage("commands.team.info.labels.default-difficulty", false)
                            : team.getQueuedDifficulty();
            this.send(requester, "commands.team.info.dungeon", team.getQueuedDungeonName(), difficulty);
        }

        if (team.isLeader(requester.getUniqueId())) {
            if (team.isStarted()) {
                this.send(requester, "commands.team.info.leader-controls-started");
            } else {
                this.send(requester, "commands.team.info.leader-controls-idle");
            }
        } else if (team.isStarted()) {
            this.send(requester, "commands.team.info.member-controls-started");
        } else {
            this.send(requester, "commands.team.info.member-controls-idle");
        }

        return true;
    }

    /** Handles `/team kick` validation and member removal. */
    public boolean handleKickMember(Player leader, Player target) {
        DungeonTeam team = this.getTeam(leader.getUniqueId());
        if (team == null) {
            this.send(leader, "commands.team.no-team-create-hint");
            return true;
        }

        if (!team.isLeader(leader.getUniqueId())) {
            this.send(leader, "commands.team.kick.only-leader");
            return true;
        }

        if (leader.getUniqueId().equals(target.getUniqueId())) {
            this.send(leader, "commands.team.kick.self");
            return true;
        }

        if (!team.hasMember(target.getUniqueId())) {
            this.send(leader, "commands.team.kick.not-member");
            return true;
        }

        if (team.isStarted()) {
            this.send(leader, "commands.team.kick.started");
            return true;
        }

        this.adminRemoveMember(
                target.getUniqueId(),
                "commands.team.kick.removed-self",
                "commands.team.kick.removed-other",
                target.getName());
        return true;
    }

    /** Handles `/team revoke` invite revocation flow. */
    public boolean handleRevokeInvite(Player leader, Player target) {
        DungeonTeam team = this.getTeam(leader.getUniqueId());
        if (team == null) {
            this.send(leader, "commands.team.no-team-create-hint");
            return true;
        }

        if (!team.isLeader(leader.getUniqueId())) {
            this.send(leader, "commands.team.revoke.only-leader");
            return true;
        }

        TeamInvite invite = this.invitesByTarget.get(target.getUniqueId());
        if (invite == null
                || invite.isExpired()
                || !team.getTeamId().equals(invite.teamId())
                || !leader.getUniqueId().equals(invite.inviterId())) {
            if (invite != null && invite.isExpired()) {
                this.invitesByTarget.remove(target.getUniqueId(), invite);
            }

            this.send(leader, "commands.team.revoke.no-invite");
            return true;
        }

        this.invitesByTarget.remove(target.getUniqueId(), invite);
        this.send(leader, "commands.team.revoke.success", target.getName());
        if (target.isOnline()) {
            this.send(target, "commands.team.revoke.notified", leader.getName());
        }

        return true;
    }

    /** Removes a non-leader idle member from their team. */
    public boolean adminRemoveMember(
            UUID memberId, String memberKey, String othersKey, String... othersArgs) {
        DungeonTeam team = this.getTeam(memberId);
        if (team == null || team.isLeader(memberId) || team.isStarted()) {
            return false;
        }

        DungeonPlayerSession memberState = this.playerManager.get(memberId);
        DungeonQueueEntry queue = memberState == null ? null : this.queueManager.getQueue(memberState);
        if (queue != null) {
            this.queueManager.removePlayer(memberId);
        }

        this.removeMember(team, memberId, memberKey, othersKey, othersArgs);
        return true;
    }

    /** Marks a team as queued for a dungeon start. */
    public void markQueued(DungeonTeam team, String dungeonName, String difficulty) {
        team.setQueued(dungeonName, difficulty == null ? "" : difficulty);
        this.notifyTeam(team, "commands.team.queued", dungeonName);
    }

    /** Clears queued-state metadata for a team. */
    public void clearQueued(DungeonTeam team) {
        if (team != null) {
            team.clearQueued();
        }
    }

    /** Clears queued-state for all teams, optionally filtered by dungeon name. */
    public void clearQueuedStarts(@Nullable String dungeonName) {
        for (DungeonTeam team : new ArrayList<>(this.teamsById.values())) {
            if (!team.isQueued() || team.isStarted()) {
                continue;
            }

            if (dungeonName != null && !Objects.equals(dungeonName, team.getQueuedDungeonName())) {
                continue;
            }

            team.clearQueued();
        }
    }

    /** Marks a team as started and binds it to an instance world. */
    public void markStarted(UUID teamId, DungeonInstance instance) {
        DungeonTeam team = this.teamsById.get(teamId);
        if (team == null) {
            return;
        }

        team.setStarted(true);
        instance.setStartedTeamLeaderId(team.getLeaderId());
        team.setInstanceWorldName(instance.getInstanceWorld().getName());
        team.cancelExpiryTask();
        this.teamIdByInstanceWorld.put(instance.getInstanceWorld().getName(), teamId);
        this.notifyTeam(team, "commands.team.started", instance.getInstanceWorld().getName());
        this.notifyTeam(team, "commands.team.started-hint");
    }

    /** Schedules auto-expiry for idle teams that never start. */
    private void scheduleExpiry(DungeonTeam team) {
        team.cancelExpiryTask();
        long expiryTicks = PluginConfigView.getUnstartedTeamExpiryTicks(this.plugin.getConfig());
        if (expiryTicks <= 0L) {
            return;
        }

        team.setExpiryTask(
                Bukkit.getScheduler()
                        .runTaskLater(
                                this.plugin,
                                () -> {
                                    DungeonTeam current = this.teamsById.get(team.getTeamId());
                                    if (current == null || current.isStarted()) {
                                        return;
                                    }

                                    DungeonQueueEntry queue =
                                            this.queueManager.getQueue(this.playerManager.get(current.getLeaderId()));
                                    if (queue != null) {
                                        this.queueManager.unqueue(queue);
                                    }

                                    this.dissolveTeam(current, "commands.team.expired", true);
                                },
                                expiryTicks));
    }

        private boolean isBusyForTeamMembership(DungeonPlayerSession playerSession) {
        return playerSession == null
                || playerSession.getInstance() != null
                || playerSession.isAwaitingDungeon()
                || this.queueManager.getQueue(playerSession) != null;
    }

        private void disbandStartedTeamAndRemoveLeader(
            DungeonTeam team,
            DungeonInstance instance,
            DungeonPlayerSession leaderSession,
            String teamMessageKey) {
        this.disbandStartedTeam(team, instance, teamMessageKey);
        if (leaderSession != null && leaderSession.getInstance() == instance) {
            instance.removePlayer(leaderSession);
        }
    }

        private void disbandStartedTeam(
            DungeonTeam team, DungeonInstance instance, String teamMessageKey) {
        if (team == null || instance == null || instance.getInstanceWorld() == null) {
            return;
        }

        if (!this.dissolvingTeams.add(team.getTeamId())) {
            return;
        }

        Bukkit.getScheduler()
                .runTask(
                        this.plugin,
                        () -> {
                            try {
                                this.dissolveTeam(team, teamMessageKey, true);
                                this.scheduleDisbandShutdown(instance);
                            } finally {
                                this.dissolvingTeams.remove(team.getTeamId());
                            }
                        });
    }

    /** Schedules delayed shutdown of an instance whose backing team disbanded. */
    private void scheduleDisbandShutdown(DungeonInstance instance) {
        if (instance.getInstanceWorld() == null) {
            return;
        }

        String instanceWorldName = instance.getInstanceWorld().getName();
        BukkitTask previousTask = this.disbandShutdownTasksByInstanceWorld.remove(instanceWorldName);
        if (previousTask != null) {
            previousTask.cancel();
        }

        if (instance.getPlayers().isEmpty()) {
            instance.dispose();
            return;
        }

        int delaySeconds =
                Math.max(
                        0, instance.getDungeon().getConfig().getInt("team.disband_shutdown_delay_seconds", 60));
        this.sendDisbandShutdownWarning(instance, delaySeconds);

        if (delaySeconds == 0) {
            this.closeDisbandedInstance(instance, instanceWorldName);
            return;
        }

        Set<Integer> warningSeconds =
                PluginConfigView.getDisbandShutdownWarningSeconds(this.plugin.getConfig());
        BukkitRunnable shutdownTask =
                                new BukkitRunnable() {
                    private int remainingSeconds = delaySeconds;

                    @Override
                                        public void run() {
                        if (instance.getInstanceWorld() == null || instance.getPlayers().isEmpty()) {
                            DungeonTeamService.this.disbandShutdownTasksByInstanceWorld.remove(instanceWorldName);
                            this.cancel();
                            if (instance.getPlayers().isEmpty()) {
                                instance.dispose();
                            }
                            return;
                        }

                        this.remainingSeconds--;
                        if (this.remainingSeconds <= 0) {
                            DungeonTeamService.this.closeDisbandedInstance(instance, instanceWorldName);
                            this.cancel();
                            return;
                        }

                        if (warningSeconds.contains(this.remainingSeconds)) {
                            DungeonTeamService.this.sendDisbandShutdownWarning(instance, this.remainingSeconds);
                        }
                    }
                };

        this.disbandShutdownTasksByInstanceWorld.put(
                instanceWorldName, shutdownTask.runTaskTimer(this.plugin, 20L, 20L));
        this.plugin
                .getSLF4JLogger()
                .info(
                        "Scheduled shutdown of instance '{}' in {} seconds because its team was disbanded.",
                        instanceWorldName,
                        delaySeconds);
    }

        private void sendDisbandShutdownWarning(DungeonInstance instance, int remainingSeconds) {
        String seconds = String.valueOf(Math.max(remainingSeconds, 0));
        for (DungeonPlayerSession playerSession : new ArrayList<>(instance.getPlayers())) {
            Player player = playerSession.getPlayer();
            if (player != null && player.isOnline()) {
                LangUtils.sendMessage(
                        player,
                        "instance.play.events.team-disbanded-shutdown",
                        LangUtils.placeholder("seconds", seconds));
            }
        }
    }

        private void closeDisbandedInstance(DungeonInstance instance, String instanceWorldName) {
        BukkitTask task = this.disbandShutdownTasksByInstanceWorld.remove(instanceWorldName);
        if (task != null) {
            task.cancel();
        }

        if (instance.getPlayers().isEmpty()) {
            instance.dispose();
            return;
        }

        instance.messagePlayers(LangUtils.getMessage("instance.play.events.team-disbanded-timeout"));
        for (DungeonPlayerSession playerSession : new ArrayList<>(instance.getPlayers())) {
            instance.removePlayer(playerSession);
        }
        instance.dispose();
    }

        private void removeMember(
            DungeonTeam team, UUID memberId, String memberKey, String othersKey, String... othersArgs) {
        if (!team.removeMember(memberId)) {
            return;
        }

        this.teamIdByMember.remove(memberId);
        Player target = Bukkit.getPlayer(memberId);
        if (target != null && target.isOnline() && memberKey != null && !memberKey.isEmpty()) {
            this.send(target, memberKey);
        }

        this.notifyTeam(team, othersKey, othersArgs);
    }

        private void dissolveTeam(DungeonTeam team, String messageKey, boolean notifyMembers) {
        if (team == null) {
            return;
        }

        team.cancelExpiryTask();
        this.teamsById.remove(team.getTeamId());
        if (team.getInstanceWorldName() != null) {
            this.teamIdByInstanceWorld.remove(team.getInstanceWorldName());
        }

        for (UUID memberId : team.getMemberIds()) {
            this.teamIdByMember.remove(memberId);
            if (notifyMembers) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null && member.isOnline() && messageKey != null && !messageKey.isEmpty()) {
                    this.send(member, messageKey);
                }
            }
        }

        this.clearInvitesForTeam(team.getTeamId());
    }

        private void clearInvitesForTeam(UUID teamId) {
        this.invitesByTarget.entrySet().removeIf(entry -> entry.getValue().teamId().equals(teamId));
    }

        private void clearInvitesForPlayer(UUID playerId) {
        Iterator<Map.Entry<UUID, TeamInvite>> iterator = this.invitesByTarget.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, TeamInvite> entry = iterator.next();
            TeamInvite invite = entry.getValue();
            if (invite.targetId().equals(playerId) || invite.inviterId().equals(playerId)) {
                iterator.remove();
            }
        }
    }

        private void notifyTeam(DungeonTeam team, String messageKey, String... args) {
        for (UUID memberId : team.getMemberIds()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                this.send(member, messageKey, args);
            }
        }
    }

        private void send(Player player, String messageKey, String... args) {
        // Team messages use named placeholders and this central switch keeps the placeholder
        // mapping explicit so mismatches fail fast during development.
        switch (messageKey) {
            case "commands.team.invite.sent",
                            "commands.team.invite.received",
                            "commands.team.invite.denied-other",
                            "commands.team.member-joined",
                            "commands.team.leave.other",
                            "commands.team.leave.started-other",
                            "commands.team.kick.removed-other",
                            "commands.player.queue.cleared.team" ->
                    LangUtils.sendMessage(player, messageKey, LangUtils.placeholder("player", args[0]));
            case "commands.team.joined" ->
                    LangUtils.sendMessage(player, messageKey, LangUtils.placeholder("leader", args[0]));
            case "commands.team.revoke.notified" ->
                    LangUtils.sendMessage(player, messageKey, LangUtils.placeholder("leader", args[0]));
            case "commands.team.revoke.success" ->
                    LangUtils.sendMessage(player, messageKey, LangUtils.placeholder("player", args[0]));
            case "commands.team.info.leader" ->
                    LangUtils.sendMessage(player, messageKey, LangUtils.placeholder("leader", args[0]));
            case "commands.team.info.members" ->
                    LangUtils.sendMessage(player, messageKey, LangUtils.placeholder("members", args[0]));
            case "commands.team.info.state" ->
                    LangUtils.sendMessage(player, messageKey, LangUtils.placeholder("state", args[0]));
            case "commands.team.info.dungeon" ->
                    LangUtils.sendMessage(
                            player,
                            messageKey,
                            LangUtils.placeholder("dungeon", args[0]),
                            LangUtils.placeholder("difficulty", args[1]));
            case "commands.team.queued" ->
                    LangUtils.sendMessage(player, messageKey, LangUtils.placeholder("dungeon", args[0]));
            case "commands.team.started" ->
                    LangUtils.sendMessage(player, messageKey, LangUtils.placeholder("instance", args[0]));
            default -> {
                if (args.length == 0) {
                    LangUtils.sendMessage(player, messageKey);
                } else {
                                        throw new IllegalArgumentException(
                            "Unhandled named placeholder mapping for team message key: " + messageKey);
                }
            }
        }
    }

        private String getLeaderName(DungeonTeam team) {
        Player leader = Bukkit.getPlayer(team.getLeaderId());
        return leader == null ? team.getLeaderId().toString() : leader.getName();
    }

    /** Removes idle teams or members when players disconnect. */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        this.clearInvitesForPlayer(playerId);
        DungeonTeam team = this.getTeam(playerId);
        if (team == null || team.isStarted()) {
            return;
        }

        DungeonQueueEntry queue = this.queueManager.getQueue(this.playerManager.get(event.getPlayer()));
        if (queue != null && team.isLeader(playerId)) {
            this.queueManager.unqueue(queue);
        }

        if (team.isLeader(playerId)) {
            this.dissolveTeam(team, "commands.team.deleted-leader-offline", true);
        } else {
            if (queue != null) {
                this.queueManager.removePlayer(playerId);
            }
            this.removeMember(
                    team, playerId, null, "commands.team.leave.other", event.getPlayer().getName());
        }
    }

    /** Keeps started-team membership in sync when players leave a dungeon instance. */
    @EventHandler
    public void onPlayerLeaveDungeon(PlayerLeaveDungeonEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Player player = event.getPlayer();
        DungeonTeam team = this.getTeam(player.getUniqueId());
        if (team == null || !team.isStarted()) {
            return;
        }

        if (!Objects.equals(
                team.getInstanceWorldName(), event.getInstance().getInstanceWorld().getName())) {
            return;
        }

        if (team.isLeader(player.getUniqueId())) {
            this.disbandStartedTeam(
                    team, event.getInstance(), "commands.team.deleted-leader-left-dungeon");
            return;
        }

        if (this.dissolvingTeams.contains(team.getTeamId())) {
            return;
        }

        this.removeMember(
                team,
                player.getUniqueId(),
                "commands.team.leave.started-self",
                "commands.team.leave.started-other",
                player.getName());
    }

    /** Dissolves teams once their started instance disposes. */
    @EventHandler
    public void onDungeonDispose(DungeonDisposeEvent event) {
        String instanceWorldName = event.getInstance().getInstanceWorld().getName();
        BukkitTask disbandShutdownTask =
                this.disbandShutdownTasksByInstanceWorld.remove(instanceWorldName);
        if (disbandShutdownTask != null) {
            disbandShutdownTask.cancel();
        }

        UUID teamId = this.teamIdByInstanceWorld.remove(instanceWorldName);
        if (teamId == null) {
            return;
        }

        DungeonTeam team = this.teamsById.get(teamId);
        if (team != null) {
            this.dissolveTeam(team, "commands.team.deleted-dungeon-ended", true);
        }
    }
}
