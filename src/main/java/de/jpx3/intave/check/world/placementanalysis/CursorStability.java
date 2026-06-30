package de.jpx3.intave.check.world.placementanalysis;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import de.jpx3.intave.check.PlayerCheckPart;
import de.jpx3.intave.check.world.PlacementAnalysis;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.user.User;
import org.bukkit.entity.Player;

import static de.jpx3.intave.check.world.PlacementAnalysis.COMMON_FLAG_MESSAGE;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.BLOCK_PLACE;

/**
 * Hard-invariant scaffold tell: a fabricated <i>constant</i> placement cursor.
 *
 * <p>When a block is placed, the client sends the precise point on the clicked face that the look ray
 * hit — a "cursor" vector with each component in {@code [0, 1]} relative to the clicked block. Because
 * a real player's view micro-moves every tick, that hit point is different on essentially every
 * placement. A scaffold that fabricates placements (rather than truly aiming at each face) commonly
 * sends a <i>fixed</i> cursor — the exact same {@code (x, y, z)} block after block. Reproducing the
 * natural per-placement variation of a human hit point is what makes this hard to bypass.
 *
 * <p>This check is purely about the cursor <i>distribution</i> (the bounds {@code [0, 1]} are
 * {@link Facing}'s job) and is deliberately false-positive proof: it only reacts to a run of
 * <i>byte-identical</i> cursor vectors longer than {@link #MIN_IDENTICAL_STREAK}, which a human's
 * continuously-varying aim cannot produce. The cursor floats are only carried on the legacy
 * placement-packet path (pre-1.14 / ViaVersion-translated clients), so on newer clients the check
 * safely no-ops.
 */
public final class CursorStability extends PlayerCheckPart<PlacementAnalysis> {
  /** Cursor components closer than this are treated as identical. */
  private static final float EPSILON = 1.0e-4f;
  /** Consecutive identical cursors beyond this are a fabricated constant cursor, not human aim. */
  private static final int MIN_IDENTICAL_STREAK = 8;

  private boolean hasLast;
  private float lastX, lastY, lastZ;
  private int identicalStreak;

  public CursorStability(User user, PlacementAnalysis parentCheck) {
    super(user, parentCheck);
  }

  @PacketSubscription(
    priority = ListenerPriority.LOW,
    packetsIn = {BLOCK_PLACE}
  )
  public void onPlace(PacketEvent event) {
    PacketContainer packet = event.getPacket();
    StructureModifier<Float> floats = packet.getFloat();
    if (floats.size() < 3) {
      // cursor vector not carried on this version's placement packet — nothing to judge
      return;
    }
    float x = floats.read(0);
    float y = floats.read(1);
    float z = floats.read(2);
    if (x < 0 || y < 0 || z < 0 || x > 1 || y > 1 || z > 1) {
      // out-of-range cursors are Facing's responsibility
      return;
    }

    if (hasLast
      && Math.abs(x - lastX) < EPSILON
      && Math.abs(y - lastY) < EPSILON
      && Math.abs(z - lastZ) < EPSILON) {
      identicalStreak++;
    } else {
      identicalStreak = 0;
    }
    hasLast = true;
    lastX = x;
    lastY = y;
    lastZ = z;

    if (identicalStreak >= MIN_IDENTICAL_STREAK) {
      identicalStreak = 0; // re-arm so a sustained scaffold keeps contributing
      Player player = event.getPlayer();
      Violation violation = Violation.builderFor(PlacementAnalysis.class)
        .forPlayer(player).withMessage(COMMON_FLAG_MESSAGE)
        .withDetails("constant placement cursor x" + (MIN_IDENTICAL_STREAK + 1))
        .withCustomThreshold(PlacementAnalysis.legacyConfigurationLayout() ? "thresholds" : "cloud-thresholds.on-premise")
        .withVL(5).build();
      Modules.violationProcessor().processViolation(violation);
    }
  }
}
