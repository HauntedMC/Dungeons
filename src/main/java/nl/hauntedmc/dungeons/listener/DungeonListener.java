package nl.hauntedmc.dungeons.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;
import nl.hauntedmc.dungeons.content.dungeon.BranchingDungeon;
import nl.hauntedmc.dungeons.content.function.HologramFunction;
import nl.hauntedmc.dungeons.content.instance.edit.BranchingEditableInstance;
import nl.hauntedmc.dungeons.event.PlayerFinishDungeonEvent;
import nl.hauntedmc.dungeons.event.PlayerLeaveDungeonEvent;
import nl.hauntedmc.dungeons.generation.room.BranchingRoomDefinition;
import nl.hauntedmc.dungeons.gui.framework.GuiService;
import nl.hauntedmc.dungeons.gui.menu.HotbarMenus;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.instance.DungeonInstance;
import nl.hauntedmc.dungeons.plugin.DungeonsPlugin;
import nl.hauntedmc.dungeons.runtime.dungeon.DungeonRepository;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.runtime.player.PlayerSessionRegistry;
import nl.hauntedmc.dungeons.runtime.queue.DungeonQueueEntry;
import nl.hauntedmc.dungeons.runtime.queue.DungeonQueueRegistry;
import nl.hauntedmc.dungeons.util.command.CommandUtils;
import nl.hauntedmc.dungeons.util.entity.EntityUtils;
import nl.hauntedmc.dungeons.util.entity.ParticleUtils;
import nl.hauntedmc.dungeons.util.item.ItemUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import nl.hauntedmc.dungeons.util.text.ComponentUtils;
import nl.hauntedmc.dungeons.util.text.MessageUtils;
import nl.hauntedmc.dungeons.util.world.LocationUtils;
import nl.hauntedmc.dungeons.util.world.WorldUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

/**
 * Global listener coordinating player session recovery and editor tool interactions.
 *
 * <p>This listener handles behavior that is not scoped to one live instance listener, such as
 * queue restore on join, function/room editor selection tools, and world bootstrap hooks.</p>
 */
public class DungeonListener implements Listener {
    private static final double MAX_FUNCTION_DISPLAY_DISTANCE_SQUARED = 0.75D * 0.75D;
    private final DungeonsPlugin plugin;
    private final PlayerSessionRegistry playerManager;
    private final DungeonQueueRegistry queueManager;
    private final DungeonRepository dungeonManager;
    private final GuiService guiService;
    private final Set<UUID> pendingRoomNameInputs = ConcurrentHashMap.newKeySet();

    /**
     * Creates the global dungeon listener with runtime service dependencies.
     */
    public DungeonListener(
            DungeonsPlugin plugin,
            PlayerSessionRegistry playerManager,
            DungeonQueueRegistry queueManager,
            DungeonRepository dungeonManager,
            GuiService guiService) {
        this.plugin = plugin;
        this.playerManager = playerManager;
        this.queueManager = queueManager;
        this.dungeonManager = dungeonManager;
        this.guiService = guiService;
    }

    /**
     * Restores persisted player session state when a player joins the server.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        DungeonPlayerSession playerSession = this.playerManager.get(player);
        if (playerSession != null) {
            playerSession.setPlayer(player);
            Location savedLocation = playerSession.getSavedPosition();
            if (playerSession.getInstance() == null && savedLocation != null) {
                EntityUtils.forceTeleport(player, savedLocation);
                player.setGameMode(playerSession.getSavedGameMode());
                playerSession.setSavedPosition(null);
                playerSession.clearExitLocation();
            }

            DungeonQueueEntry qData = this.queueManager.getQueue(playerSession);
            if (qData != null) {
                playerSession.setAwaitingDungeon(true);
            }

            if (playerSession.isEditMode()) {
                Bukkit.getScheduler().runTask(this.plugin, playerSession::ensureEditModeTools);
            }
        } else {
            playerSession = this.playerManager.put(player);
            Location exitLoc = playerSession.getExitLocation();
            if (exitLoc != null) {
                EntityUtils.forceTeleport(player, exitLoc);
                playerSession.clearExitLocation();
            }
        }
    }

    /**
     * Restores the captured edit hotbar for editors when they disconnect.
     */
    @EventHandler
    public void restoreEditPlayerHotbar(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        DungeonPlayerSession playerSession = this.playerManager.get(player);
        playerSession.setAwaitingDungeon(false);
        if (playerSession.isEditMode()) {
            playerSession.restoreCapturedHotbar();
        }
    }

    /**
     * Handles right-click function tool usage and opens or creates function selections.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onFunctionToolInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            return;
        }

        Player player = event.getPlayer();
        if (!CommandUtils.hasPermissionSilent(player, "dungeons.functioneditor")) {
            return;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(player);
        if (playerSession == null || !playerSession.isEditMode()) {
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (!ItemUtils.isFunctionTool(item)) {
            return;
        }

        event.setCancelled(true);
        if (event.getClickedBlock() == null) {
            return;
        }

        this.handleFunctionSelection(player, playerSession, event.getClickedBlock().getLocation());
    }

    /**
     * Supports selecting a function by interacting with its floating text label.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onFunctionLabelInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            return;
        }

        if (!(event.getRightClicked() instanceof TextDisplay display)) {
            return;
        }

        this.handleFunctionDisplaySelection(event.getPlayer(), display, event);
    }

    /**
     * Supports selecting a function by interacting at its floating text label.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onFunctionLabelInteractAt(PlayerInteractAtEntityEvent event) {
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            return;
        }

        if (!(event.getRightClicked() instanceof TextDisplay display)) {
            return;
        }

        this.handleFunctionDisplaySelection(event.getPlayer(), display, event);
    }

    /**
     * Handles right-click room tool interactions for room selection and room edit entry.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onRoomToolInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                && event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || event.getHand() == EquipmentSlot.OFF_HAND) {
            return;
        }

        Player player = event.getPlayer();
        if (!CommandUtils.hasPermissionSilent(player, "dungeons.roomeditor")) {
            return;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(player);
        if (!playerSession.isEditMode()) {
            return;
        }

        if (!ItemUtils.isRoomTool(item)) {
            return;
        }

        BranchingEditableInstance editSesh =
                playerSession.getInstance().as(BranchingEditableInstance.class);
        if (editSesh == null) {
            return;
        }

        BranchingDungeon dungeon = editSesh.getDungeon();
        event.setCancelled(true);

        if (playerSession.isAwaitingRoomName()) {
            playerSession.setAwaitingRoomName(false);
            playerSession.setPos1(null);
            playerSession.setPos2(null);
            LangUtils.sendMessage(player, "editor.session.room-create-cancelled");
            return;
        }

        Block targetBlock = player.getTargetBlockExact(10);
        Location pos =
                targetBlock == null ? player.getLocation().toBlockLocation() : targetBlock.getLocation();
        BranchingRoomDefinition room = dungeon.getRoom(pos);
        if (room != null) {
            playerSession.setActiveRoom(room);
            playerSession.captureHotbar();
            playerSession.showHotbar(HotbarMenus.getRoomEditMenu(), true);
            this.sendHotbarControls(player);
        } else {
            playerSession.setPos2(pos);
            LangUtils.sendMessage(player, "editor.session.room-select-2");
            if (playerSession.getPos1() != null) {
                playerSession.setAwaitingRoomName(true);
                LangUtils.sendMessage(player, "editor.session.room-name-request");
            }
        }
    }

    /**
     * Handles left-click room tool interactions for the first room-corner selection.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onRoomToolClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK
                && event.getAction() != Action.LEFT_CLICK_AIR) {
            return;
        }

        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            return;
        }

        if (!CommandUtils.hasPermissionSilent(player, "dungeons.roomeditor")) {
            return;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(player);
        if (!playerSession.isEditMode()) {
            return;
        }

        if (!(playerSession.getInstance() instanceof BranchingEditableInstance)) {
            return;
        }

        if (!ItemUtils.isRoomTool(item)) {
            return;
        }

        event.setCancelled(true);

        if (playerSession.isAwaitingRoomName()) {
            playerSession.setAwaitingRoomName(false);
            playerSession.setPos1(null);
            playerSession.setPos2(null);
            LangUtils.sendMessage(player, "editor.session.room-create-cancelled");
            return;
        }

        Block targetBlock = player.getTargetBlockExact(10);
        Location pos =
                targetBlock == null ? player.getLocation().toBlockLocation() : targetBlock.getLocation();
        playerSession.setPos1(pos);
        LangUtils.sendMessage(player, "editor.session.room-select-1");

        if (playerSession.getPos2() != null) {
            playerSession.setAwaitingRoomName(true);
            LangUtils.sendMessage(player, "editor.session.room-name-request");
            ParticleUtils.displayBoundingBox(
                    player,
                    200,
                    LocationUtils.captureOffsetBoundingBox(playerSession.getPos1(), playerSession.getPos2()));
        }
    }

    /**
     * Renders a temporary room selection preview while awaiting room naming.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void previewRoomSelection(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        DungeonPlayerSession playerSession = this.playerManager.get(player);
        if (playerSession.isEditMode()
                && playerSession.getInstance() instanceof BranchingEditableInstance) {
            if (playerSession.isAwaitingRoomName()
                    && playerSession.getPos1() != null
                    && playerSession.getPos2() != null) {
                ParticleUtils.displayBoundingBox(
                        player,
                        200,
                        LocationUtils.captureOffsetBoundingBox(
                                playerSession.getPos1(), playerSession.getPos2()));
            }
        }
    }

    /**
     * Consumes chat input when the player is currently naming a new branching room.
     */
    @EventHandler
    public void onCreateRoomMessage(AsyncChatEvent event) {
        String message = ComponentUtils.plainText(event.message()).trim();
        if (this.handleRoomNameInput(
                event.getPlayer().getUniqueId(), message, event.isAsynchronous())) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevents placing editor tools as normal blocks while in edit mode.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPreventToolPlacement(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        DungeonPlayerSession playerSession = this.playerManager.get(player);
        if (playerSession != null && playerSession.isEditMode()) {
            ItemStack item = event.getItemInHand();
            if (ItemUtils.isFunctionTool(item) || ItemUtils.isRoomTool(item)) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Prevents function/room tools from being used to break blocks in edit mode.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPreventToolBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        DungeonPlayerSession playerSession = this.playerManager.get(player);
        if (playerSession != null && playerSession.isEditMode()) {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (ItemUtils.isFunctionTool(item) || ItemUtils.isRoomTool(item)) {
                if (event.getBlock().getType() != Material.AIR) {
                    event.setCancelled(true);
                }
            }
        }
    }

    /**
     * Reminds players to claim pending rewards after a successful run.
     */
    @EventHandler
    public void onFinishDungeon(PlayerFinishDungeonEvent event) {
        DungeonPlayerSession playerSession = event.getPlayerSession();
        if (!playerSession.getRewardItems().isEmpty()) {
            MessageUtils.sendClickableCommand(
                    playerSession.getPlayer(),
                    LangUtils.getMessage("instance.play.rewards.unclaimed-rewards"),
                    "rewards");
        }
    }

    /**
     * Reminds players to claim pending rewards when they leave a run early.
     */
    @EventHandler
    public void onLeaveDungeon(PlayerLeaveDungeonEvent event) {
        if (!event.isCancelled()) {
            DungeonPlayerSession playerSession = event.getPlayerSession();
            if (!playerSession.getRewardItems().isEmpty()) {
                MessageUtils.sendClickableCommand(
                        playerSession.getPlayer(),
                        LangUtils.getMessage("instance.play.rewards.unclaimed-rewards"),
                        "rewards");
            }
        }
    }

    /**
     * Applies runtime world defaults for dungeon and instance worlds on init.
     */
    @EventHandler
    public void onLoadDungeonWorld(WorldInitEvent event) {
        World world = event.getWorld();
        String instanceWorldName = world.getName();
        Pattern p = Pattern.compile("(_[0-9]*)?$");
        String name = p.matcher(instanceWorldName).replaceFirst("");
        if (this.dungeonManager.get(name) != null) {
            WorldUtils.releaseSpawnChunk(world);
            world.setAutoSave(false);
        }
    }

    /**
     * Selects an existing function at the clicked block or opens function creation UI.
     */
    private void handleFunctionSelection(
            Player player, DungeonPlayerSession playerSession, Location clickedLocation) {
        DungeonInstance instance = playerSession.getInstance();
        if (instance == null) {
            return;
        }

        Location blockLoc = clickedLocation.clone();
        blockLoc.setWorld(null);

        DungeonFunction function = this.findFunctionAt(instance.getDungeon(), blockLoc);
        if (function != null) {
            this.openExistingFunctionEditor(player, playerSession, function);
            return;
        }

        BranchingEditableInstance editInstance = instance.as(BranchingEditableInstance.class);
        if (editInstance != null) {
            BranchingRoomDefinition room = editInstance.getDungeon().getRoom(blockLoc);
            if (room == null) {
                LangUtils.sendMessage(player, "editor.session.function-outside-room");
                return;
            }
        }

        LangUtils.sendMessage(player, "editor.session.tip-edit-existing");
        LangUtils.sendMessage(player, "editor.session.function-select");
        playerSession.setTargetLocation(blockLoc);
        this.guiService.openGui(player, "functionmenu");
    }

    /**
     * Resolves the clicked text display to a function and opens its editor.
     */
    private void handleFunctionDisplaySelection(
            Player player, TextDisplay display, Cancellable event) {
        if (!CommandUtils.hasPermissionSilent(player, "dungeons.functioneditor")) {
            return;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(player);
        if (playerSession == null || !playerSession.isEditMode()) {
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (!ItemUtils.isFunctionTool(item)) {
            return;
        }

        DungeonInstance instance = playerSession.getInstance();
        if (instance == null) {
            return;
        }

        DungeonFunction function = this.findFunctionByDisplay(instance, display);
        if (function == null) {
            return;
        }

        event.setCancelled(true);
        this.openExistingFunctionEditor(player, playerSession, function);
    }

    /**
     * Finds a function at a block location, including hologram-backed function anchors.
     */
    private DungeonFunction findFunctionAt(DungeonDefinition dungeon, Location location) {
        if (dungeon == null || location == null) {
            return null;
        }

        Location blockLoc = location.clone();
        blockLoc.setWorld(null);

        DungeonFunction exact = dungeon.getFunctions().get(blockLoc);
        if (exact != null) {
            return exact;
        }

        for (DungeonFunction function : dungeon.getFunctions().values()) {
            if (function instanceof HologramFunction hologram) {
                Location hologramLoc = hologram.getHologramLoc();
                if (hologramLoc != null) {
                    Location hologramBlock = hologramLoc.clone().toBlockLocation();
                    hologramBlock.setWorld(null);
                    if (hologramBlock.equals(blockLoc)) {
                        return function;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Finds the closest function represented by the clicked text display.
     */
    private DungeonFunction findFunctionByDisplay(DungeonInstance instance, TextDisplay display) {
        if (instance == null || display == null || !display.isValid()) {
            return null;
        }

        Location displayLocation = display.getLocation();
        DungeonFunction closest = null;
        double closestDistance = Double.MAX_VALUE;

        for (DungeonFunction function : instance.getDungeon().getFunctions().values()) {
            Location labelLocation = this.getFunctionLabelLocation(function, displayLocation.getWorld());
            double labelDistance = this.distanceSquared(labelLocation, displayLocation);
            if (labelDistance >= 0.0D
                    && labelDistance <= MAX_FUNCTION_DISPLAY_DISTANCE_SQUARED
                    && labelDistance < closestDistance) {
                closest = function;
                closestDistance = labelDistance;
            }

            if (function instanceof HologramFunction hologram) {
                Location hologramLoc = hologram.getHologramLoc();
                if (hologramLoc != null) {
                    Location actualHologramLoc = hologramLoc.clone();
                    if (actualHologramLoc.getWorld() == null) {
                        actualHologramLoc.setWorld(displayLocation.getWorld());
                    }

                    double hologramDistance = this.distanceSquared(actualHologramLoc, displayLocation);
                    if (hologramDistance >= 0.0D
                            && hologramDistance <= MAX_FUNCTION_DISPLAY_DISTANCE_SQUARED
                            && hologramDistance < closestDistance) {
                        closest = function;
                        closestDistance = hologramDistance;
                    }
                }
            }
        }

        return closest;
    }

    /**
     * Returns the expected text label location for a function inside one world.
     */
    private Location getFunctionLabelLocation(DungeonFunction function, World world) {
        Location labelLocation = function.getLocation().clone();
        labelLocation.setWorld(world);
        labelLocation.setX(labelLocation.getX() + 0.5);
        labelLocation.setY(labelLocation.getY() + 1.2);
        labelLocation.setZ(labelLocation.getZ() + 0.5);
        return labelLocation;
    }

    /**
     * Computes squared distance when both locations belong to the same world.
     */
    private double distanceSquared(Location a, Location b) {
        if (a == null
                || b == null
                || a.getWorld() == null
                || b.getWorld() == null
                || a.getWorld() != b.getWorld()) {
            return -1.0D;
        }

        return a.distanceSquared(b);
    }

    /**
     * Switches the player into function edit mode for the selected function.
     */
    private void openExistingFunctionEditor(
            Player player, DungeonPlayerSession playerSession, DungeonFunction function) {
        LangUtils.sendMessage(
                player,
                "editor.session.function-selected",
                LangUtils.placeholder(
                        "function", "<" + function.getColour() + ">" + function.getNamespace()));
        playerSession.setActiveFunction(function);
        playerSession.captureAndShowHotbar(HotbarMenus.getFunctionEditMenu());
        this.sendHotbarControls(player);
    }

    /**
     * Sends the shared editor hotbar controls hint to a player.
     */
    private void sendHotbarControls(Player player) {
        LangUtils.sendMessageList(player, "editor.session.hotbar-controls");
    }

    /**
     * Routes room-name chat processing onto the main thread when chat is asynchronous.
     */
    private boolean handleRoomNameInput(UUID playerId, String message, boolean asynchronous) {
        if (!asynchronous) {
            return this.handleRoomNameInputSync(playerId, message);
        }

        try {
            // Room creation mutates player session and world-bound state, so the final handler
            // always runs on the Bukkit main thread.
            return Bukkit.getScheduler()
                    .callSyncMethod(this.plugin, () -> this.handleRoomNameInputSync(playerId, message))
                    .get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException exception) {
            Throwable root = exception.getCause() == null ? exception : exception.getCause();
            this.plugin
                    .getSLF4JLogger()
                    .error("Failed to process room-name chat input for player '{}'.", playerId, root);
            return false;
        }
    }

    /**
     * Completes room creation from chat input for players awaiting a room name.
     */
    private boolean handleRoomNameInputSync(UUID playerId, String message) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return false;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(playerId);
        if (playerSession == null
                || !playerSession.isAwaitingRoomName()
                || !(playerSession.getInstance() instanceof BranchingEditableInstance branchingEdit)) {
            return false;
        }

        if (!this.pendingRoomNameInputs.add(playerId)) {
            return true;
        }

        try {
            if (message.isEmpty()) {
                LangUtils.sendMessage(player, "editor.session.room-create-cancelled");
                return true;
            }

            BranchingDungeon dungeon = branchingEdit.getDungeon();
            BoundingBox bounds =
                    LocationUtils.captureOffsetBoundingBox(playerSession.getPos1(), playerSession.getPos2());
            BranchingRoomDefinition room = dungeon.defineRoom(message, bounds);
            if (room == null) {
                LangUtils.sendMessage(player, "editor.session.room-exists");
                return true;
            }

            LangUtils.sendMessage(
                    player,
                    "editor.session.room-created",
                    LangUtils.placeholder("room", room.getNamespace()));
            playerSession.setActiveRoom(room);
            playerSession.setAwaitingRoomName(false);
            playerSession.setPos1(null);
            playerSession.setPos2(null);
            playerSession.captureHotbar();
            playerSession.showHotbar(HotbarMenus.getRoomEditMenu());
            this.sendHotbarControls(player);
            return true;
        } finally {
            this.pendingRoomNameInputs.remove(playerId);
        }
    }
}
