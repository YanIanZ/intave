package de.jpx3.intave.check.combat.heuristics.other;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.CombatItems;
import de.jpx3.intave.check.combat.heuristics.ConfidenceBuffer;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import static de.jpx3.intave.check.movement.physics.MoveMetric.TELEPORT;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.FLYING;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.LOOK;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION_LOOK;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.VEHICLE_MOVE;

/**
 * Detects mace "smash" hits whose fall distance is physically impossible — i.e. mace fall-distance
 * spoofing used to amplify smash damage without actually falling.
 *
 * <p>The mace deals bonus damage proportional to the attacker's fall distance, so a popular exploit
 * inflates the server-side fall distance while the player barely moves. This can't be caught by
 * comparing Intave's tracked fall distance, because Intave seeds its own value from the server's.
 * Instead this check uses a hard physical invariant: a player cannot fall faster than terminal
 * velocity (~3.92 blocks/tick). The validated airtime since the player was last on the ground is
 * tracked from movement packets; when a mace melee hit lands with a server fall distance that would
 * have required more airtime than has actually elapsed ({@code fallDistance / }{@value
 * #MAX_FALL_PER_TICK}{@code  > ticksSinceGround}), the fall distance is fabricated.
 *
 * <p>Legitimate falls — including elytra dive-bombs — always have airtime proportional to their
 * fall distance, so they never trip the invariant. Evidence is gathered in a decaying
 * {@link ConfidenceBuffer} and guarded against teleports and vehicles, flagging only a sustained,
 * physically-impossible pattern at a confidence scaled by how impossible it was.
 */
public final class MaceFallDistanceHeuristic extends ClassicHeuristic<MaceFallDistanceHeuristic.MaceMeta> {
  /** Server fall distance (blocks) above which the mace applies a meaningful smash bonus. */
  private static final float SMASH_FALL_MIN = 1.5f;
  /** Upper bound on blocks fallen per tick (terminal velocity ≈ 3.92; 4.0 keeps the check strict). */
  private static final double MAX_FALL_PER_TICK = 4.0d;
  private static final double BUFFER_HALF_LIFE_MILLIS = 8_000d;
  /** Impossible smashes required before a flag is released. */
  private static final double RELEASE_THRESHOLD = 2.0d;

  public MaceFallDistanceHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.MACE_FALL_DISTANCE, MaceMeta.class);
  }

  @PacketSubscription(
    packetsIn = {FLYING, POSITION, POSITION_LOOK, LOOK, VEHICLE_MOVE}
  )
  public void receiveMovement(PacketEvent event) {
    User user = userOf(event.getPlayer());
    MovementMetadata movementData = user.meta().movement();
    MaceMeta meta = metaOf(user);
    if (movementData.onGround()) {
      meta.ticksSinceGround = 0;
    } else if (meta.ticksSinceGround < Integer.MAX_VALUE) {
      meta.ticksSinceGround++;
    }
  }

  @BukkitEventSubscription
  public void on(EntityDamageByEntityEvent event) {
    if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
      || !(event.getDamager() instanceof Player)) {
      return;
    }
    Player player = (Player) event.getDamager();
    User user = userOf(player);

    Material held = user.meta().inventory().heldItemType();
    if (!CombatItems.isMace(held)) {
      return;
    }

    MovementMetadata movementData = user.meta().movement();
    if (movementData.isInVehicle() || movementData.ticksPast(TELEPORT) < 10) {
      return;
    }

    float serverFall = player.getFallDistance();
    if (serverFall < SMASH_FALL_MIN) {
      return;
    }

    MaceMeta meta = metaOf(user);
    double minAirtimeTicks = serverFall / MAX_FALL_PER_TICK;
    if (meta.ticksSinceGround >= minAirtimeTicks) {
      // The elapsed airtime can account for the fall distance — legitimate smash.
      return;
    }

    long now = System.currentTimeMillis();
    meta.evidence.add(1.0d, now);
    if (meta.evidence.consumeIfAtLeast(RELEASE_THRESHOLD, now)) {
      double shortfall = (minAirtimeTicks - meta.ticksSinceGround) / minAirtimeTicks;
      double confidence = MathHelper.minmax(0.4d, shortfall, 1.0d);
      flag(player, "mace fall-distance spoof (fall "
        + MathHelper.formatDouble(serverFall, 1) + " in " + meta.ticksSinceGround + "t)", confidence);
    }
  }

  public static final class MaceMeta extends CheckCustomMetadata {
    private int ticksSinceGround;
    private final ConfidenceBuffer evidence = new ConfidenceBuffer(BUFFER_HALF_LIFE_MILLIS);
  }
}
