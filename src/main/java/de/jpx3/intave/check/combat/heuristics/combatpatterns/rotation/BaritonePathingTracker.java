package de.jpx3.intave.check.combat.heuristics.combatpatterns.rotation;

import java.util.HashSet;
import java.util.Set;

/**
 * Pure, windowed accumulator behind {@link BaritonePathingRotationHeuristic} — measures the
 * fingerprint a pathfinding bot leaves on its rotation stream while it travels.
 *
 * <p>A pathing bot (e.g. Baritone) does not look around the way a human does: it computes one pitch
 * for the path segment it is walking and holds it dead still, steering purely in yaw to follow the
 * node chain. Over a window of per-tick rotation deltas this leaves three jointly tell-tale marks:
 * the pitch barely moves (a near-zero {@link #pitchRange()}), only a handful of distinct pitch steps
 * occur ({@link #distinctPitchDeltas()}), yet a large amount of yaw turning accumulates
 * ({@link #yawTurnSum()}). A human who sweeps that much yaw inevitably tilts the view, so their pitch
 * range and distinct-step count are far higher — that asymmetry is what makes the tell hard to fake
 * without giving up the bot's path-following precision.
 *
 * <p>This is the heuristic-engine-native port of the open-source MX project's {@code BaritoneCheck}
 * rotation analysis, adapted to intave's pure-core idiom and, crucially, evaluated on travel rather
 * than combat — closing the gap that intave's combat-gated rotation heuristics leave open for a bot
 * that paths without fighting. The class is deliberately free of any Bukkit/packet dependency so the
 * decision logic can be unit-tested directly.
 */
public final class BaritonePathingTracker {
  /** Quantisation step (degrees) for binning pitch deltas before counting distinct values. */
  private static final double PITCH_BIN = 0.01d;

  private int count;
  private double yawTurnSum;
  private double minPitchDelta;
  private double maxPitchDelta;
  private final Set<Long> pitchBins = new HashSet<>();

  /**
   * Feeds one travelling tick of rotation into the window.
   *
   * @param absYawDelta the (already wrapped) absolute yaw change this tick, in degrees
   * @param pitchDelta  the signed pitch change this tick, in degrees
   */
  public void accept(double absYawDelta, double pitchDelta) {
    if (count == 0) {
      minPitchDelta = pitchDelta;
      maxPitchDelta = pitchDelta;
    } else {
      if (pitchDelta < minPitchDelta) {
        minPitchDelta = pitchDelta;
      }
      if (pitchDelta > maxPitchDelta) {
        maxPitchDelta = pitchDelta;
      }
    }
    yawTurnSum += Math.abs(absYawDelta);
    pitchBins.add(Math.round(pitchDelta / PITCH_BIN));
    count++;
  }

  /** @return how many ticks have been gathered into the current window. */
  public int count() {
    return count;
  }

  /** @return the spread of pitch deltas in the window {@code (max - min)}; {@code 0} before any sample. */
  public double pitchRange() {
    return count == 0 ? 0d : maxPitchDelta - minPitchDelta;
  }

  /** @return how many distinct (quantised) pitch-delta values occurred in the window. */
  public int distinctPitchDeltas() {
    return pitchBins.size();
  }

  /** @return the total amount of yaw turning accumulated across the window, in degrees. */
  public double yawTurnSum() {
    return yawTurnSum;
  }

  /** Clears the window so the next {@link #accept(double, double)} starts a fresh one. */
  public void reset() {
    count = 0;
    yawTurnSum = 0d;
    minPitchDelta = 0d;
    maxPitchDelta = 0d;
    pitchBins.clear();
  }
}
