package de.jpx3.intave.check.combat.heuristics.other;

import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.ConfidenceBuffer;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;

/**
 * Detects bow-aura / projectile-aimbot by arrow hit-accuracy.
 *
 * <p>Every rotation/aim heuristic in this engine gates on recent <i>melee</i> attacks, so the whole
 * aim suite goes dark while a player fights with a bow — a bow-aura that auto-aims its arrows slips
 * past all of them. This check closes that gap the same way {@code attack-accuracy} closes it for
 * melee kill-aura: it measures how often the player's arrows actually land on another player. A human
 * firing at a moving opponent lands only a fraction of their shots; an aimbot lands almost all of them.
 *
 * <p>Over a window of {@link #WINDOW} bow shots the share that hit a player is measured; a ratio at or
 * above {@link #SUSPICIOUS_RATIO} feeds a decaying {@link ConfidenceBuffer}, so a single lucky volley
 * fades and only a <i>sustained</i> super-human hit-rate flags, at a
 * {@linkplain ClassicHeuristic#flag(Player, String, double) graded confidence}. It ships at violation
 * level {@code 0} (see {@code heuristics.classic.bow-aim-accuracy}) so out of the box it only feeds
 * cross-heuristic corroboration and verbose output; raise it after confirming no false positives.
 */
public final class BowAimAccuracyHeuristic extends ClassicHeuristic<BowAimAccuracyHeuristic.BowMeta> {
  /** Bow shots evaluated per accuracy window (so only an active bow fight is judged). */
  private static final int WINDOW = 10;
  /** Player-hit share at or above which the window is a super-human hit-rate. */
  private static final double SUSPICIOUS_RATIO = 0.8d;
  private static final double BUFFER_HALF_LIFE_MILLIS = 12_000d;
  /** Accumulated super-human windows required before a flag is released (a sustained hit-rate). */
  private static final double RELEASE_THRESHOLD = 2.5d;

  public BowAimAccuracyHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.BOW_AIM_ACCURACY, BowMeta.class);
  }

  @BukkitEventSubscription
  public void onShoot(EntityShootBowEvent event) {
    if (!(event.getEntity() instanceof Player)) {
      return;
    }
    Player player = (Player) event.getEntity();
    BowMeta meta = metaOf(player);
    meta.shots++;
    if (meta.shots < WINDOW) {
      return;
    }

    int shots = meta.shots;
    int hits = meta.hits;
    double ratio = MathHelper.minmax(0.0d, hits / (double) shots, 1.0d);
    meta.shots = 0;
    meta.hits = 0;

    long now = System.currentTimeMillis();
    if (ratio >= SUSPICIOUS_RATIO) {
      meta.evidence.add(ratio, now);
      if (meta.evidence.consumeIfAtLeast(RELEASE_THRESHOLD, now)) {
        double confidence = MathHelper.minmax(0.3d,
          (ratio - SUSPICIOUS_RATIO) / (1.0d - SUSPICIOUS_RATIO), 1.0d);
        flag(player, "super-human bow accuracy (" + hits + "/" + shots + " arrows hit players)", confidence);
      }
    } else {
      // a normal, human-variable window — let the accumulated evidence decay
      meta.evidence.value(now);
    }
  }

  @BukkitEventSubscription
  public void onArrowDamage(EntityDamageByEntityEvent event) {
    if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Arrow)) {
      return; // only an arrow landing on a player counts toward bow hit-accuracy
    }
    Object shooter = ((Arrow) event.getDamager()).getShooter();
    if (shooter instanceof Player) {
      metaOf((Player) shooter).hits++;
    }
  }

  public static final class BowMeta extends CheckCustomMetadata {
    private int shots;
    private int hits;
    private final ConfidenceBuffer evidence = new ConfidenceBuffer(BUFFER_HALF_LIFE_MILLIS);
  }
}
