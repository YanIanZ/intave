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

import java.util.ConcurrentModificationException;
import java.util.Map;

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
 * <p>The breadth is read across domains, not just combat: a real cheat client also trips non-combat
 * modules, so the player's current violation level on the movement, packet and world checks (the
 * shared per-player VL the violation processor already maintains) is consulted read-only and folded in
 * as <i>cross-domain</i> corroboration. It never lowers the combat gate above — it only sharpens a
 * verdict already reached when other domains agree, so a pure-combat flag is unchanged while a true
 * multi-module client (aim + movement + packet) is surfaced more confidently.
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
  /** A non-combat check counts as a cross-domain module tell once its current VL clears this. */
  private static final double MIN_CROSS_DOMAIN_VL = 5.0d;
  /** Confidence added per corroborating cross-domain (movement/packet/world) module. */
  private static final double CROSS_DOMAIN_BOOST_PER_TELL = 0.10d;
  /** Cap on the cross-domain confidence boost, so breadth sharpens but never solely decides a verdict. */
  private static final double MAX_CROSS_DOMAIN_BOOST = 0.30d;
  /** The combat heuristic cluster's own VL bucket — excluded, since its breadth is counted via the ledger. */
  private static final String COMBAT_CLUSTER_CHECK = "heuristics";

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
    // Cross-domain breadth: a real cheat client also trips non-combat modules (movement, packet, world).
    // Read-only — it never lowers the combat gate above, it only sharpens a verdict already reached when
    // other domains corroborate, so a pure-combat flag is unchanged while a true multi-module client is
    // surfaced more confidently.
    int crossDomainTells = crossDomainTells(user);
    double crossBoost = Math.min(MAX_CROSS_DOMAIN_BOOST, crossDomainTells * CROSS_DOMAIN_BOOST_PER_TELL);
    double confidence = MathHelper.minmax(0.4d, (hidesBrand ? base + 0.15d : base) + crossBoost, 1.0d);

    String brandDisplay = (brand == null || brand.isEmpty()) ? "<none>" : brand;
    String details = "cheat client: " + moduleTells + " combat module tells"
      + (crossDomainTells > 0 ? " + " + crossDomainTells + " cross-domain (movement/packet/world)" : "")
      + ", brand=" + brandDisplay + (hidesBrand ? " (claims vanilla)" : "");
    flag(user.player(), details, confidence);
  }

  /**
   * Counts how many <i>non-combat</i> checks (movement, packet, world, …) currently hold a meaningful
   * violation level on this player. Read-only: it reads the shared per-player VL ledger the violation
   * processor already maintains, so it never changes any check. The combat cluster's own VL bucket is
   * excluded because its breadth is already weighed via the {@link ConfidenceLedger}.
   */
  private int crossDomainTells(User user) {
    Map<String, Map<String, Double>> violationLevels = user.meta().violationLevel().violationLevel;
    if (violationLevels == null || violationLevels.isEmpty()) {
      return 0;
    }
    int tells = 0;
    // The outer map is concurrent (safe to iterate); the inner per-check maps are not, and this runs on
    // the packet thread, so a rare concurrent write is swallowed per check rather than thrown.
    for (Map.Entry<String, Map<String, Double>> checkEntry : violationLevels.entrySet()) {
      if (COMBAT_CLUSTER_CHECK.equalsIgnoreCase(checkEntry.getKey()) || checkEntry.getValue() == null) {
        continue;
      }
      try {
        double total = 0d;
        for (Double value : checkEntry.getValue().values()) {
          if (value != null) {
            total += value;
          }
        }
        if (total >= MIN_CROSS_DOMAIN_VL) {
          tells++;
        }
      } catch (ConcurrentModificationException ignored) {
        // a violation landed on this check mid-read — skip it this pass
      }
    }
    return tells;
  }

  public static final class GhostClientMeta extends CheckCustomMetadata {
    private final ConfidenceBuffer evidence = new ConfidenceBuffer(BUFFER_HALF_LIFE_MILLIS);
  }
}
