package de.raindancer.modules.dnd.service;

import de.raindancer.modules.dnd.model.HeroClass;
import de.raindancer.modules.dnd.store.HeroBook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("The top screen reads the database once a minute, and names who it can")
class AdventurerScreensTest {

    private final UUID ada = UUID.randomUUID();
    private final UUID ghost = UUID.randomUUID();
    private final AtomicLong now = new AtomicLong(1_000_000);
    private final AtomicInteger reads = new AtomicInteger();

    private AdventurerScreens screens() {
        return new AdventurerScreens(null, null, most -> {
            reads.incrementAndGet();
            return List.of(new HeroBook.Standing(ada, HeroClass.SORCERER, 7, 23_500),
                    new HeroBook.Standing(ghost, HeroClass.SORCERER, 2, 400));
        }, player -> player.equals(ada) ? Optional.of("Ada") : Optional.empty(), now::get);
    }

    @Test
    @DisplayName("the top is shown with names, 'somebody' for one never seen, and read again after a minute")
    void top() {
        AdventurerScreens screens = screens();
        assertThat(screens.topText()).contains("1. Ada").contains("2. somebody");
        screens.topText();
        assertThat(reads).hasValue(1);
        now.addAndGet(AdventurerScreens.TOP_TTL_MILLIS);
        screens.topText();
        assertThat(reads).hasValue(2);
    }
}
