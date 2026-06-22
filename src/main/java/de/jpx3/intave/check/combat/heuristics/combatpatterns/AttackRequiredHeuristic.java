package de.jpx3.intave.check.combat.heuristics.combatpatterns;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.packet.reader.EntityUseReader;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.*;
import de.jpx3.intave.world.raytrace.Raytrace;
import de.jpx3.intave.world.raytrace.Raytracing;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import static de.jpx3.intave.check.movement.physics.MoveMetric.TELEPORT;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_8;

/**
 * Detects 1.8 kill-aura that swings at an entity but omits the mandatory attack packet.
 *
 * <p>On the 1.8 protocol a real hit is always two packets: an arm-animation (swing) <i>and</i> a
 * use-entity/attack. Some aura implementations only replay the swing while injecting the actual
 * damage elsewhere, or drop the attack packet under load. When the cursor demonstrably rests on
 * the entity (confirmed by ray-trace) yet a swing arrives without the paired attack, the
 * heuristic builds violation level and flags once the pattern repeats within a plausible combat
 * cadence ({@link #MIN_REPEAT_GAP_MS}–{@link #MAX_REPEAT_GAP_MS} between occurrences).
 *
 * <p><b>Version scope:</b> this check only runs for genuine 1.8 clients
 * ({@code protocolVersion() == VER_1_8}). From 1.9 onwards the attack-cooldown means players
 * frequently swing without attacking on purpose, which would make this signal unreliable; the
 * modern equivalent is handled by {@link PreAttackHeuristic}. On a 26.2 server, 1.8 players
 * connecting through ViaVersion are still covered.
 */
public final class AttackRequiredHeuristic extends ClassicHeuristic<AttackRequiredHeuristic.AttackRequiredMeta> {
  // Plausible spacing (ms) between two missed-attack occurrences for them to count as a pattern.
  private static final long MIN_REPEAT_GAP_MS = 1500;
  private static final long MAX_REPEAT_GAP_MS = 20_000;

  public AttackRequiredHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.ATTACK_REQUIRED, AttackRequiredMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      ARM_ANIMATION
    }
  )
  public void receiveSwing(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    metaOf(user).didSwing = true;
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      ATTACK_ENTITY, USE_ENTITY
    }
  )
  public void receiveAttack(
    Player player, EntityUseReader reader
  ) {
    if (reader.isAttackPacket()) {
      metaOf(player).didAttack = true;
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      HELD_ITEM_SLOT_IN
    }
  )
  public void receiveSlotSwitch(PacketEvent event) {
    Player player = event.getPlayer();
    AttackRequiredMeta meta = metaOf(player);
    PacketContainer packet = event.getPacket();
    Integer slot = packet.getIntegers().read(0);

    ItemStack item = player.getInventory().getItem(slot);
    if (item == null) {
      return;
    }
    meta.ticksSinceFishingRodItemSwitch = 0;
  }

  @PacketSubscription(
    priority = ListenerPriority.NORMAL,
    packetsIn = {
      FLYING, LOOK, POSITION, POSITION_LOOK, VEHICLE_MOVE
    }
  )
  public void receiveMovement(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    ProtocolMetadata clientData = user.meta().protocol();
    AttackMetadata attackData = user.meta().attack();
    SimulationEnvironment movementData = user.meta().movement();
    Entity entity = attackData.lastAttackedEntity();
    if (entity == null || !entity.clientSynchronized || movementData.ticksPast(TELEPORT) < 5) {
      return;
    }
    // todo: test if it works on versions > 1.8
    if (clientData.protocolVersion() != VER_1_8 || clientData.outdatedClient()) {
      return;
    }

    boolean dead = entity.fakeDead || entity.dead;
    if (dead) {
      return;
    }
    AttackRequiredMeta meta = metaOf(player);

    // FishingRod overrides onItemRightClick and sends an arm-animation packet
    boolean recentlyUsedRot = meta.ticksSinceFishingRodItemSwitch < 5;

    if (!recentlyUsedRot && meta.didSwing && !meta.didAttack) {
      // Raytrace if cursor is upon entity
      boolean cursorUponEntity = cursorUponEntity(user, entity);
      if (cursorUponEntity) {
        long timeToLastFlag = System.currentTimeMillis() - meta.lastFlag;
        if (timeToLastFlag < MAX_REPEAT_GAP_MS && timeToLastFlag > MIN_REPEAT_GAP_MS) {
          int vl = (meta.vl += 200) / 200;
          if (vl >= 2) {
            flag(player, "missed attack packet vl:" + vl);
          }
        }
        meta.lastFlag = System.currentTimeMillis();
      }
    }

    if (meta.didSwing && meta.didAttack && meta.vl > 0) {
      meta.vl--;
    }

    meta.ticksSinceFishingRodItemSwitch++;

    meta.expectedAttack = false;
    meta.didAttack = false;
    meta.didSwing = false;
  }

  private boolean cursorUponEntity(
    User user,
    Entity entity
  ) {
    Player player = user.player();
    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();
    ProtocolMetadata clientData = meta.protocol();
    float expandHitbox = 0.05f;
    double blockReachDistance = reachDistance(player.getGameMode() == GameMode.CREATIVE);
    float lastRotationYaw = movementData.lastRotationYaw % 360;
    float rotationYaw = movementData.rotationYaw % 360;
    boolean alternativePositionY = clientData.protocolVersion() == VER_1_8;
    boolean hasAlwaysMouseDelayFix = clientData.protocolVersion() >= 314;
    // mouse delay fix
    Raytrace distanceOfResult = Raytracing.blockConstraintEntityRaytrace(
      player,
      entity, alternativePositionY,
      movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ,
      rotationYaw, movementData.rotationPitch,
      expandHitbox
    );
    if (distanceOfResult.reach() > blockReachDistance) {
      return false;
    }
    if (!hasAlwaysMouseDelayFix) {
      // normal
      distanceOfResult = Raytracing.blockConstraintEntityRaytrace(
        player,
        entity, true,
        movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ,
        lastRotationYaw, movementData.rotationPitch,
        expandHitbox
      );
    }
    return distanceOfResult.reach() <= blockReachDistance;
  }

  private float reachDistance(boolean creative) {
    return (creative ? 5.0F : 3.0F) - 0.005f;
  }

  public static final class AttackRequiredMeta extends CheckCustomMetadata {
    public boolean expectedAttack;
    public boolean didAttack, didSwing;
    public long lastFlag;
    public int vl;
    public int ticksSinceFishingRodItemSwitch;
  }
}