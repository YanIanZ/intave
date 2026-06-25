package de.jpx3.intave.check.combat.heuristics.other;

import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.ConfidenceBuffer;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.module.tracker.entity.EntityTracker;
import de.jpx3.intave.packet.reader.EntityUseReader;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.ATTACK_ENTITY;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.USE_ENTITY;

/**
 * Detects crystal-aura — automated end-crystal combat that detonates crystals faster than a human
 * can react to or aim at them.
 *
 * <p>Crystal PvP revolves around placing an end crystal and immediately breaking (detonating) it to
 * damage a nearby enemy. A crystal aura automates the break, so it strikes a crystal in the same — or
 * the very next — tick that the crystal appears. This check resolves the entity each attack targets
 * from the shared {@link EntityTracker}; when it is an end crystal whose {@linkplain Entity#ticksAlive
 * tracked age} is at most {@link #MAX_REACTION_TICKS}, the player reacted to it faster than humanly
 * possible. The shorter the gap, the stronger the evidence.
 *
 * <p>Because skilled players legitimately place-and-break crystals quickly, a single fast break is not
 * conclusive: evidence accumulates in a decaying {@link ConfidenceBuffer} so isolated fast breaks
 * fade and only a <i>sustained</i> super-human cadence flags, at a
 * {@linkplain ClassicHeuristic#flag(org.bukkit.entity.Player, String, double) graded confidence}. It
 * ships at violation level {@code 0} (see {@code heuristics.classic.crystal-aura}) so out of the box
 * it only feeds cross-heuristic corroboration and verbose output; raise it after confirming no false
 * positives on your crystal-PvP players.
 */
public final class CrystalAuraHeuristic extends ClassicHeuristic<CrystalAuraHeuristic.CrystalAuraMeta> {
  /** A crystal detonated within this many ticks of appearing is beyond human reaction/placement time. */
  private static final int MAX_REACTION_TICKS = 2;
  private static final double BUFFER_HALF_LIFE_MILLIS = 8_000d;
  /** Accumulated super-human breaks required before a flag is released (i.e. a sustained cadence). */
  private static final double RELEASE_THRESHOLD = 4.0d;

  public CrystalAuraHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.CRYSTAL_AURA, CrystalAuraMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {ATTACK_ENTITY, USE_ENTITY}
  )
  public void onAttack(User user, EntityUseReader reader) {
    if (!reader.isAttackPacket()) {
      return;
    }
    Entity target = EntityTracker.entityByIdentifier(user, reader.entityId());
    if (target == null || target.typeData() == null) {
      return;
    }
    String name = target.typeData().name();
    if (name == null || !name.toLowerCase().contains("crystal")) {
      return;
    }

    int age = target.ticksAlive;
    if (age > MAX_REACTION_TICKS) {
      // enough time elapsed for a human to see and aim at the crystal
      return;
    }

    long now = System.currentTimeMillis();
    CrystalAuraMeta meta = metaOf(user);
    // The faster the detonation after spawn, the stronger the evidence.
    double weight = 1.0d + (MAX_REACTION_TICKS - age) / (double) (MAX_REACTION_TICKS + 1);
    meta.evidence.add(weight, now);
    if (meta.evidence.consumeIfAtLeast(RELEASE_THRESHOLD, now)) {
      double confidence = MathHelper.minmax(0.3d,
        (MAX_REACTION_TICKS + 1 - age) / (double) (MAX_REACTION_TICKS + 1), 1.0d);
      flag(user.player(), "crystal detonated " + age + "t after spawn (super-human reaction)", confidence);
    }
  }

  public static final class CrystalAuraMeta extends CheckCustomMetadata {
    private final ConfidenceBuffer evidence = new ConfidenceBuffer(BUFFER_HALF_LIFE_MILLIS);
  }
}
