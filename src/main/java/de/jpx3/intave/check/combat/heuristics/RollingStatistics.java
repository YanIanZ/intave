package de.jpx3.intave.check.combat.heuristics;

/**
 * Online (single-pass) running statistics — the engine's reusable accumulator for the mean,
 * variance, standard deviation and coefficient of variation of a stream of samples.
 *
 * <p>Several heuristics need to ask "how uniform is this sequence of measurements?" (turn speeds,
 * step ratios, inter-click intervals, …). They historically hand-rolled the same two-pass loop over
 * a {@code List<Float>}: sum for the mean, then a second sum of squared deviations for the variance.
 * This primitive folds that into Welford's numerically-stable, constant-memory online algorithm so a
 * check simply {@link #accept(double) accepts} samples as packets arrive and reads the derived
 * statistic when its window is full — no growing list, no second pass, and no repeated boilerplate.
 *
 * <p>It keeps no thread-safety guarantees of its own; like the rest of the per-player heuristic
 * state it is expected to be confined to that player's packet thread.
 */
public final class RollingStatistics {
  private long count;
  private double mean;
  /** Sum of squares of differences from the running mean (Welford's {@code M2}). */
  private double sumOfSquaredDeltas;

  /** Folds one sample into the running mean and variance. */
  public void accept(double value) {
    count++;
    double delta = value - mean;
    mean += delta / count;
    sumOfSquaredDeltas += delta * (value - mean);
  }

  /** @return how many samples have been accepted since construction or the last {@link #reset()} */
  public long count() {
    return count;
  }

  /** @return the arithmetic mean of the accepted samples ({@code 0} when none) */
  public double mean() {
    return mean;
  }

  /** @return the population variance of the accepted samples ({@code 0} with fewer than two) */
  public double variance() {
    return count > 1 ? sumOfSquaredDeltas / count : 0.0d;
  }

  /** @return the population standard deviation of the accepted samples */
  public double standardDeviation() {
    return Math.sqrt(variance());
  }

  /**
   * @return the coefficient of variation (standard deviation / mean), or {@link Double#MAX_VALUE}
   * for an empty buffer or a non-positive mean. The "treat a non-positive mean as maximally
   * non-uniform" convention matches the rotation heuristics, so a <i>low</i> value unambiguously
   * means "robotically uniform".
   */
  public double coefficientOfVariation() {
    if (count == 0 || mean <= 0.0d) {
      return Double.MAX_VALUE;
    }
    return standardDeviation() / mean;
  }

  /** Clears all accumulated samples. */
  public void reset() {
    count = 0;
    mean = 0.0d;
    sumOfSquaredDeltas = 0.0d;
  }
}
