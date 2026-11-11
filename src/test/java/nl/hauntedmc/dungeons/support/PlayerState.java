package nl.hauntedmc.dungeons.support;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyDouble;
import static org.mockito.Mockito.anyFloat;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Stateful Paper boundary: inventory reads copy arrays as Bukkit does, without hiding alias bugs.
 */
public final class PlayerState {
    public final Player player = mock(Player.class);
    public final PlayerInventory inventory = mock(PlayerInventory.class);
    public ItemStack[] contents = new ItemStack[43];
    public ItemStack[] armor = new ItemStack[4];
    public double health = 13;
    public int food = 9, level = 17, heldSlot = 2;
    public float exp = .4f;
    public GameMode gameMode = GameMode.SURVIVAL;
    public final World originWorld = mock(World.class);
    public Location location = new Location(originWorld, 10, 65, 20);
    public boolean online = true;
    public boolean vanillaDead;

    public PlayerState() {
        when(location.getWorld().key()).thenReturn(new NamespacedKey("minecraft", "overworld"));
        when(location.getWorld().getName()).thenReturn("overworld");
        when(location.getWorld().getUID()).thenReturn(UUID.randomUUID());
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        var persistent = mock(PersistentDataContainer.class);
        var data = new HashMap<NamespacedKey, String>();
        when(player.getPersistentDataContainer()).thenReturn(persistent);
        when(persistent.get(any(NamespacedKey.class), eq(PersistentDataType.STRING)))
                .thenAnswer(c -> data.get(c.getArgument(0)));
        doAnswer(
                        c -> {
                            data.put(c.getArgument(0), c.getArgument(2));
                            return null;
                        })
                .when(persistent)
                .set(any(NamespacedKey.class), eq(PersistentDataType.STRING), any(String.class));
        doAnswer(
                        c -> {
                            data.remove(c.getArgument(0));
                            return null;
                        })
                .when(persistent)
                .remove(any(NamespacedKey.class));
        when(player.getName()).thenReturn("Explorer");
        when(player.isOnline()).thenAnswer(c -> online);
        when(player.isDead()).thenAnswer(c -> vanillaDead);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getHealth()).thenAnswer(c -> health);
        when(player.getFoodLevel()).thenAnswer(c -> food);
        when(player.getExp()).thenAnswer(c -> exp);
        when(player.getLevel()).thenAnswer(c -> level);
        when(player.getGameMode()).thenAnswer(c -> gameMode);
        when(player.getLocation()).thenAnswer(c -> location.clone());
        when(player.getWorld()).thenAnswer(c -> location.getWorld());
        when(player.teleport(any(Location.class), any(PlayerTeleportEvent.TeleportCause.class)))
                .thenAnswer(
                        c -> {
                            if (vanillaDead) return false;
                            location = ((Location) c.getArgument(0)).clone();
                            return true;
                        });
        when(player.getActivePotionEffects()).thenReturn(List.of());
        doAnswer(
                        c -> {
                            health = c.getArgument(0);
                            return null;
                        })
                .when(player)
                .setHealth(anyDouble());
        doAnswer(
                        c -> {
                            food = c.getArgument(0);
                            return null;
                        })
                .when(player)
                .setFoodLevel(anyInt());
        doAnswer(
                        c -> {
                            exp = c.getArgument(0);
                            return null;
                        })
                .when(player)
                .setExp(anyFloat());
        doAnswer(
                        c -> {
                            level = c.getArgument(0);
                            return null;
                        })
                .when(player)
                .setLevel(anyInt());
        doAnswer(
                        c -> {
                            gameMode = c.getArgument(0);
                            return null;
                        })
                .when(player)
                .setGameMode(any());
        when(inventory.getSize()).thenAnswer(c -> contents.length);
        doAnswer(
                        c -> {
                            ItemStack requested = c.getArgument(0);
                            for (int slot = 0; slot < contents.length; slot++) {
                                ItemStack item = contents[slot];
                                if (item == requested
                                        || item != null
                                                && item.getAmount() == requested.getAmount()
                                                && item.isSimilar(requested)) contents[slot] = null;
                            }
                            return null;
                        })
                .when(inventory)
                .remove(any(ItemStack.class));
        when(inventory.getContents()).thenAnswer(c -> contents.clone());
        when(inventory.getArmorContents()).thenAnswer(c -> armor.clone());
        when(inventory.getItem(anyInt())).thenAnswer(c -> contents[(int) c.getArgument(0)]);
        when(inventory.getItemInOffHand()).thenAnswer(c -> contents[40]);
        when(inventory.getHeldItemSlot()).thenAnswer(c -> heldSlot);
        when(inventory.iterator()).thenAnswer(c -> Arrays.asList(contents).listIterator());
        doAnswer(
                        c -> {
                            contents = ((ItemStack[]) c.getArgument(0)).clone();
                            return null;
                        })
                .when(inventory)
                .setContents(any());
        doAnswer(
                        c -> {
                            armor = ((ItemStack[]) c.getArgument(0)).clone();
                            return null;
                        })
                .when(inventory)
                .setArmorContents(any());
        doAnswer(
                        c -> {
                            contents[(int) c.getArgument(0)] = c.getArgument(1);
                            return null;
                        })
                .when(inventory)
                .setItem(anyInt(), any());
        doAnswer(
                        c -> {
                            heldSlot = c.getArgument(0);
                            return null;
                        })
                .when(inventory)
                .setHeldItemSlot(anyInt());
        doAnswer(
                        c -> {
                            Arrays.fill(contents, null);
                            Arrays.fill(armor, null);
                            return null;
                        })
                .when(inventory)
                .clear();
    }

    public static ItemStack stack(int amount) {
        ItemStack item = mock(ItemStack.class);
        Material material = mock(Material.class);
        when(item.getType()).thenReturn(material);
        int[] count = {amount};
        when(item.getAmount()).thenAnswer(c -> count[0]);
        doAnswer(
                        c -> {
                            count[0] = c.getArgument(0);
                            return null;
                        })
                .when(item)
                .setAmount(anyInt());
        when(item.clone()).thenAnswer(c -> stack(count[0]));
        when(item.isSimilar(any(ItemStack.class))).thenReturn(true);
        NativeItemBytes.support(item);
        return item;
    }
}
