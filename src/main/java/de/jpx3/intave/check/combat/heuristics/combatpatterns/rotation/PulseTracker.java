package de.jpx3.intave.check.combat.heuristics.combatpatterns.rotation;

import de.jpx3.intave.check.combat.heuristics.RollingStatistics;

/**
 * Pure detector for the "artificial fail" fingerprint that aim-assist clients (e.g. LiquidBounce's
 * {@code FailRotationProcessor}) leave behind.
 *
 * <p>A fail processor sits on top of an otherwise robotically tight aim and, at a low rate, injects a
 * deliberate miss: it nudges the rotation off the target by a bounded amount (a few degrees) for one
 * to a few ticks and then snaps straight back. Fed the per-tick angular residual to the perfect angle
 * while the player is tracking a target, this tracker measures that exact signature:
 *
 * <ul>
 *   <li><b>a tight baseline</b> — most ticks sit below {@link #TINY_THRESHOLD} degrees; and</li>
 *   <li><b>clean revert pulses</b> — discrete excursions that rise to {@link #RISE_THRESHOLD}, peak no
 *       higher than {@link #MAX_PEAK} (above that it is a genuine turn / target acquisition, not an
 *       injected fail) and return below the tiny baseline within {@link #MAX_PULSE_TICKS} ticks.</li>
 * </ul>
 *
 * <p>Human aim is the opposite: continuous mid-range motor noise rather than a tight baseline broken
 * by bounded, self-reverting pulses. The peak magnitudes of injected fails are also tightly clustered
 * (the processor draws from a small strength range), which {@link #peakMagnitudeCv()} surfaces.
 *
 * <p>Side-effect free and Bukkit-free, so it is unit-tested directly. It keeps no thread-safety
 * guarantees; per-player instances stay on that player's packet thread like the rest of the engine
 * state.
 */
public final class PulseTracker {
  /** A residual below this (degrees) counts as on-target — the tight aim-assist baseline. */
  public static final double TINY_THRESHOLD = 1.5d;
  /** A residual at/above this (degrees) can begin an excursion. */
  public static final double RISE_THRESHOLD = 5.0d;
  /** Excursions peaking above this (degrees) are genuine turns/acquisitions, not injected fails. */
  public static final double MAX_PEAK = 14.0d;
  /** An excursion must return to the tight baseline within this many ticks to count as a fail pulse. */
  public static final int MAX_PULSE_TICKS = 4;

  private long totalTicks;
  private long tinyTicks;
  private int pulseCount;
  private final RollingStatistics peakMagnitudes = new RollingStatistics();

  // active-excursion state
  private boolean inPulse;
  private double currentPeak;
  private int currentDuration;

  /** Folds one tick's absolute angular residual (degrees to the perfect angle) into the tracker. */
  public void accept(double residual) {
    totalTicks++;
    boolean tiny = residual < TINY_THRESHOLD;
    if (tiny) {
      tinyTicks++;
    }

    if (!inPulse) {
      // a clean pulse only starts from a tight baseline jumping into the [RISE, MAX_PEAK] band;
      // a jump straight past MAX_PEAK is a genuine large turn and never opens a pulse
      if (!tiny && residual >= RISE_THRESHOLD && residual <= MAX_PEAK) {
        inPulse = true;
        currentPeak = residual;
        currentDuration = 1;
      }
      return;
    }

    // inside an excursion
    if (tiny) {
      // reverted to the baseline within the allowed window -> a fail pulse
      if (currentDuration <= MAX_PULSE_TICKS) {
        pulseCount++;
        peakMagnitudes.accept(currentPeak);
      }
      inPulse = false;
      return;
    }

    currentDuration++;
    currentPeak = Math.max(currentPeak, residual);
    if (currentDuration > MAX_PULSE_TICKS || currentPeak > MAX_PEAK) {
      // too long or too large to be an injected fail — abandon without recording
      inPulse = false;
    }
  }

  public long totalTicks() {
    return totalTicks;
  }

  /** Fraction of ticks that sat on-target — the tight baseline a fail processor rides on top of. */
  public double tinyTickRatio() {
    return totalTicks == 0 ? 0.0d : (double) tinyTicks / totalTicks;
  }

  public int pulseCount() {
    return pulseCount;
  }

  /** Coefficient of variation of the recorded pulse peaks — low means uniform, processor-bounded. */
  public double peakMagnitudeCv() {
    return peakMagnitudes.coefficientOfVariation();
  }

  public void reset() {
    totalTicks = 0;
    tinyTicks = 0;
    pulseCount = 0;
    peakMagnitudes.reset();
    inPulse = false;
    currentPeak = 0.0d;
    currentDuration = 0;
  }
}
