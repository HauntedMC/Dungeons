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
}
