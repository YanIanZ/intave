package de.jpx3.intave.check.combat.heuristics;

/**
 * Online (single-pass) Pearson correlation of a stream of paired samples — the engine's reusable
 * accumulator for "do these two measurements move together on a straight line?".
 *
 * <p>It is the two-variable companion to {@link RollingStatistics}: each {@code (x, y)} pair is
 * folded into running means and co-moments with Welford's numerically-stable, constant-memory
 * algorithm, so a check can {@link #accept(double, double) accept} pairs as packets arrive and read
 * the {@linkplain #correlation() correlation} when its window is full — no growing lists and no
 * second pass. A correlation magnitude near {@code 1} means the samples lie on a straight line; near
 * {@code 0} means no linear relationship.
 *
 * <p>The degenerate case where either axis barely varies (so a Pearson correlation is undefined)
 * deliberately reports {@code 0} rather than a spurious extreme, so a <i>high</i> magnitude
 * unambiguously means "robotically collinear". It keeps no thread-safety guarantees of its own; like
 * the rest of the per-player heuristic state it is confined to that player's packet thread.
 */
public final class RollingCorrelation {
  private long count;
  private double meanX;
  private double meanY;
  /** Sum of squared deviations of x and y (Welford's {@code M2}). */
  private double m2x;
  private double m2y;
  /** Co-moment: sum of products of paired deviations. */
  private double comoment;

  /** Folds one paired sample into the running correlation. */
  public void accept(double x, double y) {
    count++;
    double deltaXOld = x - meanX;
    double deltaYOld = y - meanY;
    meanX += deltaXOld / count;
    meanY += deltaYOld / count;
    double deltaXNew = x - meanX;
    double deltaYNew = y - meanY;
    m2x += deltaXOld * deltaXNew;
    m2y += deltaYOld * deltaYNew;
    comoment += deltaXOld * deltaYNew;
  }

  /** @return how many pairs have been accepted since construction or the last {@link #reset()} */
  public long count() {
    return count;
  }

  /**
   * @return the Pearson correlation coefficient in {@code [-1, 1]} of the accepted pairs, or
   * {@code 0} when fewer than two pairs have been seen or either axis has (near-)zero variance.
   */
  public double correlation() {
    if (count < 2) {
      return 0.0d;
    }
    double denominator = Math.sqrt(m2x * m2y);
    if (denominator <= 1.0e-12d) {
      return 0.0d;
    }
    return comoment / denominator;
  }

  /** Clears all accumulated pairs. */
  public void reset() {
    count = 0;
    meanX = 0.0d;
    meanY = 0.0d;
    m2x = 0.0d;
    m2y = 0.0d;
    comoment = 0.0d;
  }
}
