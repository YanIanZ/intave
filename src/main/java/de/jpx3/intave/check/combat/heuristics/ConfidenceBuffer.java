package de.jpx3.intave.check.combat.heuristics;

/**
 * A time-decaying evidence accumulator — the heuristic engine's standard building block for
 * "the more recent, corroborating evidence I have, the more confident I am" detection.
 *
 * <p>Many heuristics historically hand-rolled their own ad-hoc "balance" counters that add on a
 * suspicious observation and subtract a little otherwise. {@code ConfidenceBuffer} replaces that
 * pattern with a single, well-defined primitive: evidence is <i>added</i>, and the stored value
 * <i>decays exponentially</i> over wall-clock time with a configurable half-life. A burst of
 * corroborating observations therefore raises confidence quickly, while isolated, sporadic
 * observations fade on their own without any explicit "subtract" bookkeeping.
 *
 * <p>The decay is continuous and lazy: it is applied on read/update from the elapsed time since the
 * last interaction, so callers never need to tick the buffer. The class keeps no thread-safety
 * guarantees of its own; per-player instances are expected to be confined to that player's packet
 * thread (matching the rest of the heuristic state), or guarded by their owner (see
 * {@link ConfidenceLedger}).
 */
public final class ConfidenceBuffer {
  private final double halfLifeMillis;
  private double value;
  private long lastUpdate;

  /**
   * @param halfLifeMillis the time after which un-reinforced confidence halves; must be positive
   */
  public ConfidenceBuffer(double halfLifeMillis) {
    if (!(halfLifeMillis > 0)) {
      throw new IllegalArgumentException("halfLifeMillis must be positive, got " + halfLifeMillis);
    }
    this.halfLifeMillis = halfLifeMillis;
  }

  private void decayTo(long now) {
    if (value != 0) {
      long elapsed = now - lastUpdate;
      if (elapsed > 0) {
        value *= Math.pow(0.5, elapsed / halfLifeMillis);
        if (value < 1.0e-6) {
          value = 0;
        }
      }
    }
    lastUpdate = now;
  }

  /**
   * Adds (reinforces) evidence and returns the decayed value afterwards.
   *
   * @param amount the (non-negative) confidence to add
   * @param now    the current timestamp in milliseconds
   * @return the buffer value after decay and addition
   */
  public double add(double amount, long now) {
    decayTo(now);
    if (amount > 0) {
      value += amount;
    }
    return value;
  }

  /**
   * @param now the current timestamp in milliseconds
   * @return the current decayed confidence
   */
  public double value(long now) {
    decayTo(now);
    return value;
  }

  /**
   * Atomically tests-and-consumes a threshold's worth of confidence. This is the idiomatic way to
   * "release a flag every time enough evidence has piled up" while carrying the remainder forward.
   *
   * @param threshold the confidence required
   * @param now       the current timestamp in milliseconds
   * @return {@code true} if the (decayed) value reached the threshold, in which case that much
   * confidence is subtracted; {@code false} otherwise
   */
  public boolean consumeIfAtLeast(double threshold, long now) {
    decayTo(now);
    if (value >= threshold) {
      value -= threshold;
      return true;
    }
    return false;
  }

  /** Clears all accumulated confidence. */
  public void reset() {
    value = 0;
    lastUpdate = 0;
  }
}
