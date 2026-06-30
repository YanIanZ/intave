package de.jpx3.intave.check.movement.pathfinder;

import de.jpx3.intave.check.Check;
import de.jpx3.intave.check.CheckViolationLevelDecrementer;

/**
 * Movement-domain detection cluster for pathfinding bots (e.g. Baritone).
 *
 * <p>A pathfinding bot is not a combat cheat, so the combat heuristics never see it. It betrays itself
 * in how it <i>travels</i>: with {@code antiCheatCompatibility} on (Baritone's default) it keeps the
 * transmitted yaw locked to the direction it is walking so it never sprints sideways, and it follows
 * computed routes that curve at block boundaries. A human constantly decouples view from travel —
 * glancing around, over-/under-shooting turns — so a yaw that stays welded to the movement heading
 * <i>through</i> turns over a sustained sprint is the tell {@link HeadingLock} measures.
 *
 * <p>This check raises its violations through the normal pipeline, so the per-player violation level it
 * accumulates is automatically folded into {@code GhostClientHeuristic}'s cross-domain breadth (a true
 * cheat client that also auto-paths trips this alongside its combat tells). The detection is the same
 * across every Baritone branch (1.13.2 → 1.21.11) because it reads only motion and yaw. It is gated by
 * {@code check.pathfinder.enabled} and, like the other behavioural tells, ships notify/log-only (no
 * kick) until tuned per server.
 */
public final class Pathfinder extends Check {
  private final CheckViolationLevelDecrementer decrementer;

  public Pathfinder() {
    super("Pathfinder", "pathfinder");
    this.decrementer = new CheckViolationLevelDecrementer(this, 0.05);
    appendCheckPart(new HeadingLock(this));
  }

  public CheckViolationLevelDecrementer decrementer() {
    return decrementer;
  }
}
