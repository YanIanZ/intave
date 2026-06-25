package de.jpx3.intave.check.world.placementanalysis;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.PlayerCheckPart;
import de.jpx3.intave.check.world.PlacementAnalysis;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.check.world.PlacementAnalysis.COMMON_FLAG_MESSAGE;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.BLOCK_PLACE;
import static de.jpx3.intave.module.violation.Violation.ViolationFlags.DISPLAY_IN_ALL_VERBOSE_MODES;

/**
 * Targets the {@code KeepY} / {@code SameY} scaffold behaviour shared by LiquidBounce, Wurst and Vape:
 * pinning the look pitch so blocks are bridged at a constant level.
 *
 * <p>To place block after block while crossing a gap, a human looks around — their pitch wanders as
 * they glance ahead, adjust and re-aim. A {@code KeepY} scaffold instead holds the pitch almost
 * perfectly still (its whole purpose is to keep the same Y), so across a run of placements the pitch
 * variance collapses to something a person bridging cannot reproduce.
 *
 * <p>Over a burst of consecutive placements (gaps under {@link #BURST_GAP_MILLIS}) the pitch variance
 * is measured. A flag requires three things together, which a legitimate builder does not satisfy at
 * once: the pitch is robotically static ({@link #MAX_PITCH_VARIANCE}); it is aimed clearly downward
 * ({@code > }{@value #MIN_DOWNWARD_PITCH}{@code °}, i.e. bridging rather than building a wall at eye
 * level); and the player actually traversed ground ({@code > }{@value #MIN_BRIDGE_DISTANCE}{@code }
 * blocks), so a stationary wall-builder holding a steady aim is never flagged.
 *
 * <p>It is shipped as a verbose-only signal (violation level {@code 0}, {@link
 * Violation.ViolationFlags#DISPLAY_IN_ALL_VERBOSE_MODES}) because fast legitimate bridging can hold a
 * fairly steady look; it surfaces the {@code KeepY} fingerprint for triage and corroboration without
 * punishing on its own. Raise its weight once confirmed against your bridging players.
 */
public final class KeepYPlace extends PlayerCheckPart<PlacementAnalysis> {
  /** Placements within this gap count as one bridging burst. */
  private static final long BURST_GAP_MILLIS = 600L;
  /** Consecutive placements evaluated per burst. */
  private static final int WINDOW = 12;
  /** Pitch variance (deg²) below which the look is robotically static — ~0.5° standard deviation. */
  private static final double MAX_PITCH_VARIANCE = 0.25d;
  /** Minimum downward pitch (degrees) — bridging looks down, an eye-level wall build does not. */
  private static final double MIN_DOWNWARD_PITCH = 20.0d;
  /** Minimum horizontal distance (blocks) traversed across the burst — proves actual bridging. */
  private static final double MIN_BRIDGE_DISTANCE = 2.5d;

  private int count;
  private double pitchSum;
  private double pitchSquareSum;
  private double startX;
  private double startZ;
  private long lastPlaceMillis;

  public KeepYPlace(User user, PlacementAnalysis parentCheck) {
    super(user, parentCheck);
  }

  @PacketSubscription(
    priority = ListenerPriority.LOW,
    packetsIn = {BLOCK_PLACE}
  )
  public void onPlace(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    MovementMetadata movement = user.meta().movement();
    float pitch = movement.rotationPitch;
    double x = movement.positionX;
    double z = movement.positionZ;
    long now = System.currentTimeMillis();

    if (now - lastPlaceMillis > BURST_GAP_MILLIS) {
      reset();
    }
    lastPlaceMillis = now;

    if (count == 0) {
      startX = x;
      startZ = z;
    }
    count++;
    pitchSum += pitch;
    pitchSquareSum += (double) pitch * pitch;

    if (count < WINDOW) {
      return;
    }

    double mean = pitchSum / count;
    double variance = Math.max(0.0d, pitchSquareSum / count - mean * mean);
    double horizontalDistance = Math.hypot(x - startX, z - startZ);
    reset();

    if (variance < MAX_PITCH_VARIANCE
      && mean > MIN_DOWNWARD_PITCH
      && horizontalDistance > MIN_BRIDGE_DISTANCE) {
      Violation violation = Violation.builderFor(PlacementAnalysis.class)
        .forPlayer(player).withMessage(COMMON_FLAG_MESSAGE)
        .appendFlags(DISPLAY_IN_ALL_VERBOSE_MODES)
        .withDetails("keep-Y bridging: static pitch (sd "
          + MathHelper.formatDouble(Math.sqrt(variance), 2) + "° over " + WINDOW + " places)")
        .withCustomThreshold(PlacementAnalysis.legacyConfigurationLayout() ? "thresholds" : "cloud-thresholds.on-premise")
        .withVL(0).build();
      Modules.violationProcessor().processViolation(violation);
    }
  }

  private void reset() {
    count = 0;
    pitchSum = 0.0d;
    pitchSquareSum = 0.0d;
  }
}
