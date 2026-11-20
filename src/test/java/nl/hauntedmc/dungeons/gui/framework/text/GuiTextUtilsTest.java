package nl.hauntedmc.dungeons.gui.framework.text;

import static org.junit.jupiter.api.Assertions.*;

import net.kyori.adventure.text.ComponentIteratorType;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import nl.hauntedmc.dungeons.util.text.ComponentUtils;
import org.junit.jupiter.api.Test;

class GuiTextUtilsTest {
    @Test
    void inventoryTitlesKeepHighContrastEvenWhenTheSourceUsesChatColors() {
        var title = GuiTextUtils.title("&fDungeon &bGuide");
        assertEquals("◆ Dungeon Guide", ComponentUtils.plainText(title));
        assertEquals(NamedTextColor.DARK_AQUA, title.color());
        for (var part : title.children().getFirst().iterable(ComponentIteratorType.DEPTH_FIRST))
            assertEquals(NamedTextColor.DARK_GRAY, part.color());
    }

    @Test
    void tooltipsKeepExplicitAccentsAndOptOutOfVanillaItalicDefaults() {
        assertEquals(
                TextDecoration.State.FALSE,
                GuiTextUtils.itemName("Compass").decoration(TextDecoration.ITALIC));
        assertEquals(
                TextDecoration.State.FALSE,
                GuiTextUtils.itemLore("Click to open").decoration(TextDecoration.ITALIC));
    }
}
