package de.jpx3.intave.check.combat.heuristics;

/**
 * Catalogue of the "classic" (on-premise, non-cloud) combat heuristics.
 *
 * <p>Each constant maps one-to-one to a configuration key under {@code heuristics.classic.*}
 * in {@code advanced.yml}. The configured integer is the violation-level increase applied
 * whenever the matching heuristic flags; {@code -1} disables the heuristic entirely while
 * {@code 0} keeps it running (so combat nerfers still apply) without adding violation levels.
 *
 * <p>Heuristics intentionally describe <i>statistical tendencies</i> of cheat software rather
 * than hard physics violations: a single flag is rarely conclusive, which is why every type
 * feeds a shared, decaying violation bucket that only escalates to mitigations or a kick once
 * the {@code heuristics.classic.thresholds} are crossed.
 *
 * <p>All types listed here apply across the supported protocol range (1.7 through 26.2). Where
 * a heuristic is restricted to a narrower range — usually because the relevant packet only
 * exists on legacy clients — the limitation is documented on the implementing class.
 */
public enum HeuristicsClassicType {
  /** Auto-clicker / kill-aura: maintains an implausibly high hit-to-swing ratio over time. */
  ATTACK_ACCURACY("attack-accuracy"),
  /** Kill-aura (1.8): registers a swing on an entity yet never sends the required attack packet. */
  ATTACK_REQUIRED("attack-required"),
  /** Kill-aura: attacks fire without the player's cursor ever resting on the target first. */
  PRE_ATTACK("pre-attack"),
  /** Aim-assist: rotation tracks the target's perfect angle far more tightly than a human can. */
  ROTATION_ACCURACY("rotation-accuracy"),
  /** Aimbot: rotation lands <i>exactly</i> on the target's perfect yaw/pitch (zero residual). */
  ROTATION_EXACT("rotation-exact"),
  /** Aimbot: instantaneous, large yaw "snap" onto the target that is not mirrored by movement. */
  ROTATION_SNAP("rotation-snap"),
  /** Aimbot: rotation deltas stop sharing the constant GCD that a real mouse sensitivity yields. */
  ROTATION_SENSITIVITY("rotation-sensitivity"),
  /** Silent-aim: rotation snaps to the target then resets back to the player's view angle. */
  ROTATION_MODULO_RESET("rotation-reset"),
  /** Linear-aim / aimbot: angular (yaw) velocity stays robotically constant while tracking. */
  ROTATION_CONSTANT_SPEED("rotation-constant-speed"),
  /** Inventory-aura: sends look packets carrying rotation while an inventory screen is open. */
  INVENTORY_ROTATIONS("inventory-rotations"),
  /** Block-hit / fast-use (1.8): abuses sword blocking timing to gain defensive frames. */
  BLOCKING("blocking"),
  /** No-swing / no-hand-swing: lands attacks without the corresponding arm-animation packet. */
  NO_SWING("no-swing"),
  /** Packet-order spoofing: the swing/attack packet ordering does not match the vanilla client. */
  SWING_ORDER("swing-order"),
  /** W-tap / sprint-reset bots: toggles sprint or sneak multiple times within a single tick. */
  SPRINT_TOGGLES("sprint-toggles"),
  /** Auto-tool / fast-break aura: swaps the held slot mid block-break in an automated pattern. */
  TOOL_SWITCH("tool-switch"),
  /** Auto-swap / weapon-combo macro: swaps the held weapon faster than one slot change per tick. */
  FAST_SWAP("fast-swap"),
  /**
   * Meta-detector: several <i>distinct</i> heuristics corroborate on the same player within a short
   * window. Independent detectors agreeing is far stronger evidence than one repeating, so this
   * escalates only when the shared {@link ConfidenceLedger} shows broad agreement.
   */
  CORROBORATION("corroboration");

  private final String configurationName;

  HeuristicsClassicType(String configurationName) {
    this.configurationName = configurationName;
  }

  public String configurationName() {
    return configurationName;
  }

  public String verboseName() {
    return configurationName;
  }
}
