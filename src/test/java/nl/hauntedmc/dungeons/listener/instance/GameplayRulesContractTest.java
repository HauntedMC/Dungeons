package nl.hauntedmc.dungeons.listener.instance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.Path;
import java.util.*;
import nl.hauntedmc.dungeons.support.*;
import nl.hauntedmc.dungeons.content.function.AllowBlockFunction;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.block.*;
import org.bukkit.event.player.*;
import org.bukkit.event.entity.*;
import org.bukkit.entity.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GameplayRulesContractTest {
    @TempDir Path directory;
    static Block block(RunFixture f) {
        var block=mock(Block.class); when(block.getWorld()).thenReturn(f.world);
        when(block.getLocation()).thenReturn(new Location(f.world,1,70,1));
        when(block.getType()).thenReturn(Material.STONE); return block;
    }
    @Test
    void breakRulesHonorWhitelistBlacklistAndActivatedLocationOverridesWithoutLeakingToOtherWorlds() throws Exception {
        var settings=new YamlConfiguration(); settings.set("rules.building.break_blocks",false);
        settings.set("rules.building.break_whitelist",List.of("STONE"));
        settings.set("rules.building.break_blacklist",List.of("STONE"));
        try(var f=new RunFixture(directory,settings)) {
            var listener=(PlayListener)f.run.getListener(); var state=new PlayerState();
            var block=block(f); var event=new BlockBreakEvent(block,state.player);
            listener.onBreakBlock(event); assertTrue(event.isCancelled());
            f.dungeon.getBreakBlacklist().clear(); listener.onBreakBlock(event); assertFalse(event.isCancelled());
            f.dungeon.getBreakWhitelist().clear(); listener.onBreakBlock(event); assertTrue(event.isCancelled());
            var override=new AllowBlockFunction(Map.of("allowBreak",true)); override.initializeFields();
            f.run.getFunctions().put(block.getLocation(),override);
            listener.onBreakBlock(event); assertTrue(event.isCancelled());
            override.runFunction(null,List.of()); listener.onBreakBlock(event); assertFalse(event.isCancelled());
            override.onDisable(); listener.onBreakBlock(event); assertTrue(event.isCancelled());
            when(block.getWorld()).thenReturn(state.originWorld); event.setCancelled(false);
            listener.onBreakBlock(event); assertFalse(event.isCancelled());
        }
    }
}
