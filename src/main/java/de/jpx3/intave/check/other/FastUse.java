package de.jpx3.intave.check.other;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.Check;
import de.jpx3.intave.check.CheckViolationLevelDecrementer;
import de.jpx3.intave.check.other.fastuse.FastBow;
import de.jpx3.intave.check.other.fastuse.FastConsume;
import de.jpx3.intave.executor.task.Tasks;
import de.jpx3.intave.user.UserRepository;

/**
 * Detection cluster for using items faster than the game physically allows ("fast-use").
 *
 * <p>The use duration of a consumable is fixed by the item, not the player: the quickest legitimate
 * consumable (dried kelp) still takes ~0.865s, and ordinary food/potions take ~1.6s. A fast-use cheat
 * finishes in a tick or two regardless. {@link FastConsume} measures the time between the right-click
 * that begins a use and the resulting {@link org.bukkit.event.player.PlayerItemConsumeEvent}, and a
 * completion below the hard physical floor cannot be produced by a legitimate client. {@link FastBow}
 * applies the same idea to bows: a (near) full-power shot — read from the launched arrow's velocity —
 * after too short a draw is likewise impossible.
 *
 * <p>Like {@link de.jpx3.intave.check.world.BreakSpeedLimiter} this is shipped notify-only with a
 * decaying violation level, so only sustained fast-use accumulates toward the staff notification.
 */
public final class FastUse extends Check {
  private final CheckViolationLevelDecrementer decrementer;

  public FastUse(IntavePlugin plugin) {
    super("FastUse", "fastuse");
    setupParts();
    decrementer = new CheckViolationLevelDecrementer(this, 0.15);
    startDecrementTask();
  }

  private void startDecrementTask() {
    Tasks.periodicNamed("FastUse.decrementer", () -> {
      UserRepository.applyOnAll(user -> decrementer.decrement(user, 0.05));
    }, 40, 40).startAsync();
  }

  private void setupParts() {
    appendCheckPart(new FastConsume(this));
    appendCheckPart(new FastBow(this));
  }
}
