package de.jpx3.intave.check.combat.heuristics;

import de.jpx3.intave.user.meta.CheckCustomMetadata;

/**
 * Per-player, cross-heuristic evidence ledger — the engine's shared view of "how suspicious is this
 * player right now, and how many independent heuristics agree".
 *
 * <p>Every classic heuristic shares a single instance of this ledger per player (it is keyed by
 * class in the user's metadata pool, so all {@link ClassicHeuristic} parts resolve the same object,
 * and it is released automatically when the player leaves). Each {@link HeuristicsClassicType} gets
 * its own {@link ConfidenceBuffer}, so the ledger can answer two questions the individual checks
 * cannot answer on their own:
 *
 * <ul>
 *   <li><b>Corroboration</b> — how many <i>distinct</i> heuristics have flagged this player within a
 *       recent window. Independent detectors agreeing is far stronger evidence than one noisy
 *       detector repeating, which is exactly the signal a robust engine should weigh.</li>
 *   <li><b>Aggregate confidence</b> — the combined, time-decayed weight of all recent flags.</li>
 * </ul>
 *
 * <p>The ledger is populated automatically by {@link ClassicHeuristic#flag}. It deliberately does
 * not, by itself, change any violation level or threshold — it is an additive intelligence layer
 * that detectors (and verbose/cloud output) can consult. All access is synchronised on the instance
 * so flags arriving from different packet threads for the same player remain consistent.
 */
public final class ConfidenceLedger extends CheckCustomMetadata {
  /** Half-life of a heuristic's per-type confidence; tuned so corroboration spans a short fight. */
  private static final double HALF_LIFE_MILLIS = 8_000d;
  /** Default window within which flags from different heuristics count as corroborating. */
  public static final long DEFAULT_CORROBORATION_WINDOW_MILLIS = 4_000L;

  private static final int TYPE_COUNT = HeuristicsClassicType.values().length;

  private final ConfidenceBuffer[] perType = new ConfidenceBuffer[TYPE_COUNT];
  private final long[] lastFlagMillis = new long[TYPE_COUNT];

  private ConfidenceBuffer bufferOf(HeuristicsClassicType type) {
    int index = type.ordinal();
    ConfidenceBuffer buffer = perType[index];
    if (buffer == null) {
      buffer = perType[index] = new ConfidenceBuffer(HALF_LIFE_MILLIS);
    }
    return buffer;
  }

  /**
   * Records a flag of the given type and confidence at the current time.
   *
   * @return the decayed confidence accumulated for that type afterwards
   */
  public synchronized double note(HeuristicsClassicType type, double confidence) {
    long now = System.currentTimeMillis();
    lastFlagMillis[type.ordinal()] = now;
    return bufferOf(type).add(Math.max(0, confidence), now);
  }

  /** @return the current decayed confidence accumulated for a single heuristic type */
  public synchronized double confidenceOf(HeuristicsClassicType type) {
    return bufferOf(type).value(System.currentTimeMillis());
  }

  /**
   * @param windowMillis how recently a heuristic must have flagged to count
   * @return the number of <i>distinct</i> heuristic types that flagged this player within the window
   */
  public synchronized int corroboratingHeuristics(long windowMillis) {
    return corroboratingHeuristics(windowMillis, null);
  }

  /**
   * As {@link #corroboratingHeuristics(long)}, but ignores one heuristic type — used by a
   * corroboration meta-detector so it does not count its own flags as corroboration.
   *
   * @param windowMillis how recently a heuristic must have flagged to count
   * @param exclude      a type to ignore, or {@code null} to count all
   * @return the number of distinct, non-excluded heuristics that flagged within the window
   */
  public synchronized int corroboratingHeuristics(long windowMillis, HeuristicsClassicType exclude) {
    long now = System.currentTimeMillis();
    int excludeIndex = exclude == null ? -1 : exclude.ordinal();
    int count = 0;
    for (int i = 0; i < lastFlagMillis.length; i++) {
      if (i == excludeIndex) {
        continue;
      }
      long last = lastFlagMillis[i];
      if (last != 0 && now - last <= windowMillis) {
        count++;
      }
    }
    return count;
  }

  /**
   * The confidence-weighted counterpart to {@link #corroboratingHeuristics(long, HeuristicsClassicType)}:
   * instead of merely counting the distinct heuristics that flagged within the window, it sums their
   * current decayed confidence. This lets a meta-detector weigh not just <i>how many</i> heuristics
   * agree but <i>how strongly</i> — broad agreement among confident detectors scores far higher than the
   * same number of borderline ones, which is the basis of a more powerful evidence fusion.
   *
   * @param windowMillis how recently a heuristic must have flagged to contribute
   * @param exclude      a type to ignore, or {@code null} to weigh all
   * @return the summed decayed confidence of the distinct, non-excluded heuristics that flagged in window
   */
  public synchronized double weightedCorroboration(long windowMillis, HeuristicsClassicType exclude) {
    long now = System.currentTimeMillis();
    int excludeIndex = exclude == null ? -1 : exclude.ordinal();
    double weighted = 0;
    for (int i = 0; i < lastFlagMillis.length; i++) {
      if (i == excludeIndex) {
        continue;
      }
      long last = lastFlagMillis[i];
      if (last != 0 && now - last <= windowMillis) {
        ConfidenceBuffer buffer = perType[i];
        if (buffer != null) {
          weighted += buffer.value(now);
        }
      }
    }
    return weighted;
  }

  /**
   * As {@link #corroboratingHeuristics(long)}, but ignores up to two heuristic types — used by the
   * ghost-client meta-detector to count distinct <i>base</i> module tells while excluding both itself
   * and the corroboration meta-detector (which is derived from the base detectors).
   *
   * @param windowMillis how recently a heuristic must have flagged to count
   * @param excludeA     a type to ignore, or {@code null}
   * @param excludeB     a second type to ignore, or {@code null}
   * @return the number of distinct, non-excluded heuristics that flagged within the window
   */
  public synchronized int corroboratingHeuristics(long windowMillis, HeuristicsClassicType excludeA, HeuristicsClassicType excludeB) {
    long now = System.currentTimeMillis();
    int excludeIndexA = excludeA == null ? -1 : excludeA.ordinal();
    int excludeIndexB = excludeB == null ? -1 : excludeB.ordinal();
    int count = 0;
    for (int i = 0; i < lastFlagMillis.length; i++) {
      if (i == excludeIndexA || i == excludeIndexB) {
        continue;
      }
      long last = lastFlagMillis[i];
      if (last != 0 && now - last <= windowMillis) {
        count++;
      }
    }
    return count;
  }

  /** @return the combined, time-decayed confidence across every heuristic type */
  public synchronized double aggregateConfidence() {
    long now = System.currentTimeMillis();
    double sum = 0;
    for (ConfidenceBuffer buffer : perType) {
      if (buffer != null) {
        sum += buffer.value(now);
      }
    }
    return sum;
  }
}
