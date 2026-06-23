package de.jpx3.intave.check.combat.heuristics.combatpatterns;

import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.ConfidenceBuffer;
import de.jpx3.intave.check.combat.heuristics.ConfidenceLedger;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.reader.EntityUseReader;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.ATTACK_ENTITY;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.USE_ENTITY;

/**
 * Ghost-/cheat-client verdict — identifies that a player is running a cheat <i>client</i> (e.g. a
 * "ghost" client such as Vape) rather than a single, isolated cheat.
 *
 * <p>A server-side anticheat cannot inspect an external ghost client's process, so it is never caught
 * by one magic signature; it is caught by the <i>aggregate</i> of its modules' behavioural tells. A
 * cheat client runs several modules at once — kill-aura, reach, velocity, auto-clicker — and each one
 * leaks into a different heuristic. This detector consults the shared {@link ConfidenceLedger} on
 * every attack and reacts to that <i>breadth</i>: when at least {@link #MIN_MODULE_TELLS} distinct
 * <b>base</b> heuristics (the corroboration and ghost meta-detectors themselves excluded, so the
 * count reflects genuine module coverage) have flagged the same player within a short window, the
 * combination is the fingerprint of a cheat client, not of a borderline legitimate player.
 *
 * <p>The client brand Intave records from the {@code minecraft:brand} payload is folded in for
 * attribution: a ghost client that spoofs a vanilla brand (or hides it) while clearly running several
 * cheat modules is a strong "fake-vanilla" tell, so a hidden/vanilla brand raises the confidence and
 * is surfaced on the violation. The brand never triggers on its own — an honest Forge/Lunar/Badlion
 * brand is not penalised — it only colours a verdict the behavioural breadth already reached.
 *
 * <p>It is deliberately false-positive resistant: it requires several <i>independent</i> detectors to
 * agree, then fuses their {@linkplain ConfidenceLedger#weightedCorroboration confidence-weighted}
 * evidence — weak-but-broad agreement barely moves the needle, strong module coverage escalates
 * quickly — accumulates that in a decaying {@link ConfidenceBuffer} so a one-off coincidence fades,
 * and flags with a {@linkplain ClassicHeuristic#flag(org.bukkit.entity.Player, String, double) graded
 * confidence}. Its violation weight is configured under {@code heuristics.classic.ghost-client}.
 */
public final class GhostClientHeuristic extends ClassicHeuristic<GhostClientHeuristic.GhostClientMeta> {
  private static final long WINDOW_MILLIS = ConfidenceLedger.DEFAULT_CORROBORATION_WINDOW_MILLIS;
  /** Distinct base heuristics that must agree — a cheat client runs several modules simultaneously. */
  private static final int MIN_MODULE_TELLS = 4;
  /** Accumulated agreement required before a verdict is released (i.e. sustained, not a coincidence). */
  private static final double RELEASE_THRESHOLD = 4.0d;
  private static final double BUFFER_HALF_LIFE_MILLIS = 8_000d;
  /** Per-heuristic confidence treated as "minimally meaningful"; baselines the weighted agreement. */
  private static final double MIN_FLAG_CONFIDENCE = 0.3d;
  /** Weighted module-tell agreement that maps to full reported confidence. */
  private static final double FULL_CONFIDENCE_WEIGHT = 8.0d;

  public GhostClientHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.GHOST_CLIENT, GhostClientMeta.class);
  }

  @PacketSubscription(
    // Run late so the attack-driven heuristics for this packet have already recorded their flags.
    priority = ListenerPriority.LOW,
    packetsIn = {ATTACK_ENTITY, USE_ENTITY}
  )
  public void onAttack(User user, EntityUseReader reader) {
    if (!reader.isAttackPacket()) {
      return;
    }
    ConfidenceLedger ledger = ledgerOf(user);
    int moduleTells = ledger.corroboratingHeuristics(
      WINDOW_MILLIS, HeuristicsClassicType.GHOST_CLIENT, HeuristicsClassicType.CORROBORATION);
    if (moduleTells < MIN_MODULE_TELLS) {
      return; // breadth gate — a cheat client runs several modules at once (FP-resistant, unchanged)
    }

    long now = System.currentTimeMillis();
    double weighted = ledger.weightedCorroboration(
      WINDOW_MILLIS, HeuristicsClassicType.GHOST_CLIENT, HeuristicsClassicType.CORROBORATION);
    GhostClientMeta meta = metaOf(user);
    // Confidence-weighted fusion: reinforce by how strongly the modules leaked, above the baseline the
    // minimum breadth contributes at trivial confidence. Weak-but-broad agreement barely moves the
    // buffer (more conservative than a raw count); strong, broad module coverage escalates quickly.
    double reinforcement = Math.max(0d, weighted - MIN_MODULE_TELLS * MIN_FLAG_CONFIDENCE);
    meta.evidence.add(reinforcement, now);
    if (!meta.evidence.consumeIfAtLeast(RELEASE_THRESHOLD, now)) {
      return;
    }

    String brand = user.meta().protocol().clientBrand();
    boolean hidesBrand = brand == null || brand.isEmpty()
      || brand.equalsIgnoreCase("Unknown") || brand.equalsIgnoreCase("vanilla");
    double base = MathHelper.minmax(0.4d, weighted / FULL_CONFIDENCE_WEIGHT, 1.0d);
    double confidence = MathHelper.minmax(0.4d, hidesBrand ? base + 0.15d : base, 1.0d);

    String brandDisplay = (brand == null || brand.isEmpty()) ? "<none>" : brand;
    String details = "cheat client: " + moduleTells + " module tells, brand=" + brandDisplay
      + (hidesBrand ? " (claims vanilla)" : "");
    flag(user.player(), details, confidence);
  }

  public static final class GhostClientMeta extends CheckCustomMetadata {
    private final ConfidenceBuffer evidence = new ConfidenceBuffer(BUFFER_HALF_LIFE_MILLIS);
  }
}
