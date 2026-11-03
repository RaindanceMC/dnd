package de.raindancer.modules.skills.screen;

import de.raindancer.core.ui.menu.Backdrop;
import de.raindancer.modules.skills.store.SkillsPack;
import de.raindancer.core.ui.chat.Brand;
import de.raindancer.core.ui.menu.ConfirmMenu;
import de.raindancer.core.ui.menu.Menu;
import org.bukkit.entity.Player;

import java.util.List;

/** Core's confirmation, so every irreversible button here asks the same way as everywhere else. */
public final class ConfirmScreen extends ConfirmMenu implements ISkillsScreen {

    public ConfirmScreen(Player viewer, Brand brand, Menu parent, String question, List<String> consequences,
                         Runnable onYes) {
        super(viewer, brand, parent, question, consequences, onYes);
    }

    @Override
    public String describe() {
        return "a yes or no before something that cannot be taken back";
    }

    @Override
    protected Backdrop backdrop() {
        return SkillsPack.parchment(layout().rows());
    }
}
