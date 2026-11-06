package nl.hauntedmc.dungeons.content.reward;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import nl.hauntedmc.dungeons.support.*;
import org.junit.jupiter.api.Test;

class LootExecutionContractTest {
    @Test
    void duplicateEnabledLootCanDrawTheConfiguredCountBeyondTheNumberOfDistinctEntries() {
        try(var paper=new PaperBoundary();var menus=new HeadlessInventoryMenus()) {
            var base=PlayerState.stack(3); var entry=new LootTableItem(base);
            var table=new LootTable(Map.of("namespace","cache","minItems",4,"maxItems",4,
                "allowDuplicates",true,"lootItems",Map.of(9,entry)));
            var result=table.randomizeItems(0);
            assertEquals(4,result.size());
            assertTrue(result.stream().allMatch(item -> item.getAmount()==3));
            result.getFirst().setAmount(1);
            assertEquals(3,base.getAmount()); assertEquals(3,result.getLast().getAmount());
        }
    }
    @Test
    void drawsWithoutReplacementTerminateWhenDisabledEntriesOutnumberEligibleLoot() {
        try(var paper=new PaperBoundary();var menus=new HeadlessInventoryMenus()) {
            var first=new LootTableItem(Map.of("item",PlayerState.stack(1),"weight",1));
            var second=new LootTableItem(Map.of("item",PlayerState.stack(2),"weight",1));
            var disabled=new LootTableItem(Map.of("item",PlayerState.stack(3),"weight",0));
            int[] draws={0};
            Random random=new Random() {
                @Override public double nextDouble() {
                    if(++draws[0]>3) throw new AssertionError("Selection did not exhaust eligible entries");
                    return 0;
                }
            };
            var table=new LootTable(Map.of("namespace","finite","minItems",4,"maxItems",4,
                "allowDuplicates",false,"lootItems",Map.of(9,first,10,second,11,disabled))) {
                @Override nl.hauntedmc.dungeons.util.math.RandomCollection<LootTableItem> createRandomizer() {
                    return new nl.hauntedmc.dungeons.util.math.RandomCollection<>(random);
                }
            };
            var result=table.randomizeItems(0);
            assertEquals(2,result.size());
            assertEquals(Set.of(1,2),result.stream().map(item -> item.getAmount()).collect(java.util.stream.Collectors.toSet()));
            assertTrue(draws[0]<=3);
        }
    }

}
