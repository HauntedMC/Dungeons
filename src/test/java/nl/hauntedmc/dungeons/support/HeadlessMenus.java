package nl.hauntedmc.dungeons.support;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;

import nl.hauntedmc.dungeons.gui.hotbar.PlayerHotbarMenu;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

/**
 * Substitutes GUI rendering so real mechanic initialization can run without a Paper item factory.
 */
public final class HeadlessMenus implements AutoCloseable {
    private final MockedConstruction<MenuButton> buttons = mockConstruction(MenuButton.class);
    private final MockedStatic<PlayerHotbarMenu> menus = mockStatic(PlayerHotbarMenu.class);

    public HeadlessMenus() {
        menus.when(PlayerHotbarMenu::createMenu).thenAnswer(c -> mock(PlayerHotbarMenu.class));
    }

    @Override
    public void close() {
        menus.close();
        buttons.close();
    }
}
