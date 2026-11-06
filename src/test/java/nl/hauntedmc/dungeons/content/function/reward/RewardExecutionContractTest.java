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
    @Test
    void clonedRewardFunctionsKeepClaimHistoryAndMutableRewardStacksIndependent() throws Exception {
        try(var f=new RunFixture(directory,new YamlConfiguration());var menus=new HeadlessMenus();var inventories=new HeadlessInventoryMenus()) {
            var state=new PlayerState(); var session=f.session(state);
            var template=new RewardFunction(Map.of("xp",20,"rewards",List.of(PlayerState.stack(3)),"cooldownDisabled",true));
            template.initializeFields(); template.setInstance(f.run); template.setLocation(new org.bukkit.Location(f.world,0,70,0));
            var left=(RewardFunction)template.clone(); var right=(RewardFunction)template.clone();
            assertNotNull(left); assertNotNull(right);
            left.runFunction(null,List.of(session)); right.runFunction(null,List.of(session));
            verify(state.player,times(2)).giveExp(20);
            left.getRewards().getFirst().setAmount(1);
            assertEquals(3,right.getRewards().getFirst().getAmount());
            assertEquals(3,template.getRewards().getFirst().getAmount());
            template.runFunction(null,List.of(session)); verify(state.player,times(3)).giveExp(20);
        }
    }

}
