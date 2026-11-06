package nl.hauntedmc.dungeons.content.function.meta;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.element.DungeonTrigger;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.support.PaperBoundary;
import org.junit.jupiter.api.Test;

class FunctionCompositionContractTest {
    @Test
    void compositesPreserveOrderIsolateTargetsSkipTriggeredChildrenAndContinueAfterAChildFails() {
        try (var paper = new PaperBoundary()) {
            List<String> effects = new ArrayList<>();
            var first = new Effect("first", effects);
            first.clearTargets = true;
            var broken = new Effect("broken", effects);
            broken.fail = true;
            var last = new Effect("last", effects);
            var independent = new Effect("independent", effects);
            independent.setTrigger(mock(DungeonTrigger.class));
            var composite = new CompositeFunction();
            composite.addFunction(first);
            composite.addFunction(broken);
            composite.addFunction(independent);
            composite.addFunction(last);
            var source = mock(DungeonPlayerSession.class);
            var targets = new ArrayList<>(List.of(source));

            composite.runFunction(mock(TriggerFireEvent.class), targets);

            assertEquals(List.of("first", "broken", "last"), effects);
            assertEquals(List.of(source), targets);
            assertEquals(List.of(source), last.received);
        }
    }

    static class Effect extends DungeonFunction {
        final String name;
        final List<String> effects;
        boolean clearTargets;
        boolean fail;
        List<DungeonPlayerSession> received;
        Effect(String name, List<String> effects) { super(name); this.name = name; this.effects = effects; }
        @Override public void runFunction(TriggerFireEvent event, List<DungeonPlayerSession> targets) {
            effects.add(name);
            received = new ArrayList<>(targets);
            if (clearTargets) targets.clear();
            if (fail) throw new IllegalStateException("broken effect");
        }
        @Override public MenuButton buildMenuButton() { return null; }
        @Override public void buildHotbarMenu() {}
    }
}
