package de.jpx3.intave.check.combat.heuristics;

import de.jpx3.intave.math.MathHelper;

/**
 * Reusable "a single edge is noise, a sustained run is a cheat" accumulator.
 *
 * <p>Several hard-invariant heuristics share the exact same release logic: a suspicious event can
 * legitimately race a tick boundary once, so each event feeds a decaying {@link ConfidenceBuffer} and
 * only a sustained run crosses a release threshold and flags, at a graded confidence that scales with
 * the consecutive streak. {@code InventoryCloseAttackHeuristic}, {@code BaritoneHeuristic} and
 * {@code MultiActionHeuristic} all hand-rolled identical copies of it; this folds that into one
 * primitive so the decision is defined and unit-tested once.
 *
 * <p>It is side-effect free apart from its own state and Bukkit-free, so it is tested directly. Like
 * the rest of the per-player heuristic state it is confined to that player's packet thread.
 */
public final class SustainedStreakDetector {
  /** Sentinel returned by {@link #note(long)} when the run has not yet earned a flag. */
  public static final double NO_FLAG = -1.0d;

  private final ConfidenceBuffer evidence;
  private final long streakGapMillis;
  private final double releaseThreshold;
  private final double sustainedStreak;
  private final double minConfidence;

  private long lastEventMillis;
  private int streak;

  /**
   * @param halfLifeMillis   decay half-life of the evidence buffer
   * @param releaseThreshold accumulated evidence required before a flag is released
   * @param streakGapMillis  events closer together than this continue a streak; otherwise it restarts
   * @param sustainedStreak  streak length mapped to full confidence
   * @param minConfidence    floor confidence a released flag carries
   */
  public SustainedStreakDetector(double halfLifeMillis, double releaseThreshold,
                                 long streakGapMillis, double sustainedStreak, double minConfidence) {
    this.evidence = new ConfidenceBuffer(halfLifeMillis);
    this.releaseThreshold = releaseThreshold;
    this.streakGapMillis = streakGapMillis;
    this.sustainedStreak = sustainedStreak;
    this.minConfidence = minConfidence;
  }

  /**
   * Records one suspicious event at {@code now}.
   *
   * @return the graded confidence in {@code [minConfidence, 1]} when the sustained run has earned a
   * flag this event, or {@link #NO_FLAG} when it has not yet.
   */
  public double note(long now) {
    streak = now - lastEventMillis < streakGapMillis ? streak + 1 : 1;
    lastEventMillis = now;
    evidence.add(1.0d, now);
    if (evidence.consumeIfAtLeast(releaseThreshold, now)) {
      return MathHelper.minmax(minConfidence, streak / sustainedStreak, 1.0d);
    }
    return NO_FLAG;
  }

  /** @return the current consecutive-event streak length. */
  public int streak() {
    return streak;
  }
}
