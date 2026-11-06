package nl.hauntedmc.dungeons.content.function.reward;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.Path;
import java.util.*;
import nl.hauntedmc.dungeons.support.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RewardExecutionContractTest {
    @TempDir Path directory;
    @Test
    void experienceRewardsGrantOncePerPlayerAndDoNotLeakClaimsToAnotherFunction() throws Exception {
        try(var f=new RunFixture(directory,new YamlConfiguration());var menus=new HeadlessMenus();var inventories=new HeadlessInventoryMenus()) {
            var first=new PlayerState(); var firstSession=f.session(first); var second=new PlayerState(); var secondSession=f.session(second);
            var config=Map.<String,Object>of("xp",20,"levels",2,"cooldownDisabled",true);
            var reward=new RewardFunction(config); reward.initializeFields(); reward.setInstance(f.run); reward.setLocation(new org.bukkit.Location(f.world,0,70,0));
            reward.runFunction(null,List.of(firstSession)); reward.runFunction(null,List.of(firstSession));
            verify(first.player,times(1)).giveExp(20); verify(first.player,times(1)).giveExpLevels(2);
            verify(second.player,never()).giveExp(anyInt());
            reward.runFunction(null,List.of(secondSession)); verify(second.player).giveExp(20);
            var other=new RewardFunction(config); other.initializeFields(); other.setInstance(f.run); other.setLocation(new org.bukkit.Location(f.world,1,70,0));
            other.runFunction(null,List.of(firstSession)); verify(first.player,times(2)).giveExp(20);
        }
    }
}
