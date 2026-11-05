package de.raindancer.modules.skills.service;

import de.raindancer.core.ui.actionbar.ActionBars;
import de.raindancer.core.ui.effect.Cues;
import de.raindancer.core.ui.effect.Effects;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.skills.model.Hero;
import de.raindancer.modules.skills.model.SkillCatalog;
import de.raindancer.modules.skills.rules.FightRule;
import de.raindancer.modules.skills.rules.MobRule;
import de.raindancer.modules.skills.rules.SkillRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The branches' features as the service works them on a player, with what Bukkit lends mocked. */
class CombatServiceTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final HeroService heroes = mock(HeroService.class);
    private final Effects effects = mock(Effects.class);
    private final World world = mock(World.class);
    private final UUID id = UUID.randomUUID();
    private final Player player = mock(Player.class);
    private CombatService combat;

    @BeforeEach
    void setUp() {
        SkillRule perks = new SkillRule(SkillCatalog.all(), () -> 100, () -> Duration.ofHours(24));
        combat = new CombatService(mock(Plugin.class), mock(Messages.class), mock(ActionBars.class), effects, heroes,
                perks, new FightRule(), new MobRule(), now::get);
        when(world.getName()).thenReturn("world");
        when(player.getUniqueId()).thenReturn(id);
        when(player.getWorld()).thenReturn(world);
        when(player.getInventory()).thenReturn(mock(PlayerInventory.class));
        when(player.getLocation()).thenAnswer(call -> new Location(world, 0, 64, 0, 0, 0));
        holds(Map.of());
    }

    private void holds(Map<String, Integer> ranks) {
        when(heroes.of(id)).thenReturn(Optional.of(new Hero(id, 50, 0, ranks, 0)));
    }

    @Test
    @DisplayName("a mastery feature is had once its badge is taken, and only then")
    void masteryFeatures() {
        assertThat(combat.pure(player)).isFalse();
        assertThat(combat.fireproof(player)).isFalse();
        assertThat(combat.aegis(player)).isFalse();
        holds(Map.of("defense:defense_mastery", 1, "defense:guardian_mastery", 1, "defense:warden_mastery", 1));
        assertThat(combat.pure(player)).isTrue();
        assertThat(combat.fireproof(player)).isTrue();
        assertThat(combat.aegis(player)).isTrue();
    }

    @Test
    @DisplayName("Velocity speeds an arrow up, Skewer lets it through two more foes")
    void arrows() {
        AbstractArrow arrow = mock(AbstractArrow.class);
        when(arrow.getVelocity()).thenReturn(new Vector(2, 0, 0));
        combat.loosed(player, arrow);
        verify(arrow, never()).setPierceLevel(anyInt());
        verify(arrow, never()).setVelocity(any());

        holds(Map.of("archery:strong_draw", 3, "archery:trickshot_mastery", 1));
        combat.loosed(player, arrow);
        ArgumentCaptor<Vector> velocity = ArgumentCaptor.forClass(Vector.class);
        verify(arrow).setVelocity(velocity.capture());
        assertThat(velocity.getValue().getX()).isCloseTo(2.18, within(1e-9));
        verify(arrow).setPierceLevel(2);
    }

    @Test
    @DisplayName("Dash throws a sprinting player ahead, then waits four seconds")
    void dash() {
        when(player.isSprinting()).thenReturn(true);
        assertThat(combat.dash(player)).as("without the badge").isFalse();
        holds(Map.of("agility:acrobatics_mastery", 1));
        assertThat(combat.dash(player)).isTrue();
        ArgumentCaptor<Vector> push = ArgumentCaptor.forClass(Vector.class);
        verify(player).setVelocity(push.capture());
        assertThat(push.getValue().getZ()).as("yaw 0 looks along +z").isCloseTo(1.5, within(1e-6));
        assertThat(push.getValue().getY()).isCloseTo(0.3, within(1e-9));
        verify(effects).play(id, Cues.ABILITY);
        assertThat(combat.dash(player)).as("again at once").isFalse();
        now.addAndGet(CombatService.DASH_MILLIS + 1);
        assertThat(combat.dash(player)).isTrue();
        when(player.isSprinting()).thenReturn(false);
        now.addAndGet(CombatService.DASH_MILLIS + 1);
        assertThat(combat.dash(player)).as("only from a sprint").isFalse();
    }

    @Test
    @DisplayName("Mana Shield takes its share of a hurt out of the player's experience, while it lasts")
    void manaShield() {
        holds(Map.of("magic:mana_shield", 1));
        when(player.calculateTotalExperiencePoints()).thenReturn(100);
        assertThat(combat.taken(player, 10, false, FightRule.Ward.NONE)).isCloseTo(8.0, within(1e-9));
        verify(player).setExperienceLevelAndProgress(96);

        when(player.calculateTotalExperiencePoints()).thenReturn(0);
        assertThat(combat.taken(player, 10, false, FightRule.Ward.NONE)).as("nothing to pay with").isEqualTo(10.0);
    }

    @Test
    @DisplayName("a ward softens its own hurt for the player")
    void wards() {
        holds(Map.of("defense:salamander", 1));
        assertThat(combat.taken(player, 10, false, FightRule.Ward.FIRE)).isCloseTo(7.5, within(1e-9));
        assertThat(combat.taken(player, 10, false, FightRule.Ward.BLAST)).isEqualTo(10.0);
    }
}
