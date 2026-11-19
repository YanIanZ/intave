/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.check.bedrock;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.Check;
import de.jpx3.intave.check.CheckConfiguration;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.violation.Violation;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The checks for Bedrock players (clients that come in through Geyser).
 *
 * <p>What reaches this server from a Bedrock client is not what that client did: Geyser translates another
 * game's inputs into Java packets, corrects positions against its own collision model, may send two movement
 * packets in one client tick, and passes on a game with other reach, no attack cooldown, touch aiming and
 * other movement constants. The Java checks model a Java client packet by packet, so they stay off for these
 * players ({@code TrustFactor.BYPASS}). This check judges the translated result with limits that no Bedrock
 * input and no translation step explains:</p>
 * <ul>
 *   <li><b>speed</b>: ground covered per second, against sprint-jumping on ice scaled by the speed attribute;</li>
 *   <li><b>hover</b>: seconds without anything to stand on while not falling;</li>
 *   <li><b>timer</b>: movement updates per second over five seconds (Geyser forwards 20 per second);</li>
 *   <li><b>reach</b>: melee distance from the eyes to the target's box, with a ping allowance;</li>
 *   <li><b>attack rate</b>: melee hits per second, sustained;</li>
 *   <li><b>aim</b>: hits on targets behind the player, and hits on several targets inside one tick.</li>
 * </ul>
 * <p>Every sub-check needs its condition in consecutive windows before it reports, and nothing here moves,
 * cancels or slows a player: the result is a violation level and the thresholds of the {@code bedrock} section.</p>
 */
public final class BedrockSentinel extends Check {
  private static final double BASE_SPRINT_ATTRIBUTE = 0.13;
  private static final Method IS_PASSABLE = method(Block.class, "isPassable");
  private static final Method IS_SWIMMING = method(Player.class, "isSwimming");
  private static final Method IS_RIPTIDING = method(Player.class, "isRiptiding");
  private static final Method IS_CLIMBING = method(Player.class, "isClimbing");
  private static final Method GET_PING = method(Player.class, "getPing");
  private static final Object SPEED_ATTRIBUTE = speedAttribute();
  private static final Method GET_ATTRIBUTE = attributeGetter();
  private static final PotionEffectType SLOW_FALLING = PotionEffectType.getByName("SLOW_FALLING");
  private static final PotionEffectType DOLPHINS_GRACE = PotionEffectType.getByName("DOLPHINS_GRACE");
  private static final PotionEffectType LEVITATION = PotionEffectType.getByName("LEVITATION");

  private static final class Track {
    long quietUntil;
    // speed
    long speedWindowStart;
    double speedDistance;
    int speedOver;
    // hover
    long leftGroundAt;
    double leftGroundY;
    long hoverFlaggedAt;
    // timer
    long timerWindowStart;
    int timerMoves;
    int timerOver;
    // attack rate
    long attackWindowStart;
    int attacks;
    int attackOver;
    // aim
    long lastHitAt;
    int lastHitEntity;
    int multiHits;
    int behindHits;
    long behindWindowStart;
  }

  private final Map<UUID, Track> tracks = new ConcurrentHashMap<UUID, Track>();
  private final double maxBlocksPerSecond;
  private final int maxAirMillis;
  private final double maxMovesPerSecond;
  private final double maxReach;
  private final double maxPingAllowance;
  private final int maxAttacksPerSecond;
  private final int violationLevel;

  public BedrockSentinel(IntavePlugin plugin) {
    super("BedrockSentinel", "bedrock");
    CheckConfiguration.CheckSettings settings = configuration().settings();
    this.maxBlocksPerSecond = settings.intInBoundsBy("max-blocks-per-second", 10, 60, 18);
    this.maxAirMillis = settings.intInBoundsBy("max-air-ms", 1000, 10000, 2500);
    this.maxMovesPerSecond = settings.intInBoundsBy("max-moves-per-second", 21, 60, 24);
    this.maxReach = settings.intInBoundsBy("max-reach-tenths", 30, 80, 42) / 10.0;
    this.maxPingAllowance = settings.intInBoundsBy("max-ping-allowance-tenths", 0, 40, 20) / 10.0;
    this.maxAttacksPerSecond = settings.intInBoundsBy("max-attacks-per-second", 10, 60, 22);
    this.violationLevel = settings.intInBoundsBy("violation-level", 1, 50, 10);
  }

  // ------------------------------------------------------------------ movement

  @BukkitEventSubscription(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onMove(PlayerMoveEvent event) {
    Player player = event.getPlayer();
    if (!enabled() || !BedrockPlayers.is(player)) {
      return;
    }
    Location from = event.getFrom();
    Location to = event.getTo();
    if (to == null || from.getWorld() != to.getWorld()) {
      return;
    }
    Track track = track(player);
    long now = System.currentTimeMillis();
    if (unjudged(player, track, now)) {
      track.speedWindowStart = now;
      track.speedDistance = 0;
      track.speedOver = 0;
      track.leftGroundAt = 0;
      track.timerWindowStart = now;
      track.timerMoves = 0;
      return;
    }
    speed(player, track, from, to, now);
    hover(player, track, to, now);
    timer(player, track, from, to, now);
  }

  private void speed(Player player, Track track, Location from, Location to, long now) {
    double dx = to.getX() - from.getX();
    double dz = to.getZ() - from.getZ();
    track.speedDistance += Math.sqrt(dx * dx + dz * dz);
    if (track.speedWindowStart == 0) {
      track.speedWindowStart = now;
      return;
    }
    long elapsed = now - track.speedWindowStart;
    if (elapsed < 1000) {
      return;
    }
    double speed = track.speedDistance / (elapsed / 1000.0);
    track.speedWindowStart = now;
    track.speedDistance = 0;
    double limit = maxBlocksPerSecond * Math.max(1.0, speedAttributeOf(player) / BASE_SPRINT_ATTRIBUTE);
    if (speed <= limit) {
      track.speedOver = 0;
      return;
    }
    if (++track.speedOver < 2) {
      return;
    }
    track.speedOver = 0;
    report(player, "moved faster than a Bedrock client can", round(speed) + " blocks/s, limit " + round(limit));
  }

  private void hover(Player player, Track track, Location to, long now) {
    if (supported(to)) {
      track.leftGroundAt = 0;
      return;
    }
    if (track.leftGroundAt == 0) {
      track.leftGroundAt = now;
      track.leftGroundY = to.getY();
      return;
    }
    // Judged from the top of the arc: a jump or a throw upwards ends within about a second.
    if (to.getY() > track.leftGroundY && now - track.leftGroundAt < 1200) {
      track.leftGroundY = to.getY();
      return;
    }
    if (now - track.leftGroundAt < maxAirMillis || to.getY() < track.leftGroundY - 0.5 || now - track.hoverFlaggedAt < 2000) {
      return;
    }
    track.hoverFlaggedAt = now;
    report(player, "stayed in the air without falling", (now - track.leftGroundAt) + " ms above nothing, y " + round(to.getY()));
  }

  private void timer(Player player, Track track, Location from, Location to, long now) {
    // Only position changes count; Geyser also sends rotation-only updates.
    if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) {
      return;
    }
    track.timerMoves++;
    if (track.timerWindowStart == 0) {
      track.timerWindowStart = now;
      return;
    }
    long elapsed = now - track.timerWindowStart;
    if (elapsed < 5000) {
      return;
    }
    double rate = track.timerMoves / (elapsed / 1000.0);
    track.timerWindowStart = now;
    track.timerMoves = 0;
    if (rate <= maxMovesPerSecond) {
      track.timerOver = 0;
      return;
    }
    // One burst after a lag spike is normal; two five-second windows in a row are not.
    if (++track.timerOver < 2) {
      return;
    }
    track.timerOver = 0;
    report(player, "sent movement faster than the game ticks", round(rate) + " position updates/s, limit " + round(maxMovesPerSecond));
  }

  // ------------------------------------------------------------------ combat

  @BukkitEventSubscription(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onHit(EntityDamageByEntityEvent event) {
    if (!enabled() || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK || !(event.getDamager() instanceof Player)) {
      return;
    }
    Player player = (Player) event.getDamager();
    if (!BedrockPlayers.is(player) || player.getGameMode() == GameMode.CREATIVE) {
      return;
    }
    Entity target = event.getEntity();
    Location eyes = player.getEyeLocation();
    if (eyes.getWorld() != target.getWorld()) {
      return;
    }
    Track track = track(player);
    long now = System.currentTimeMillis();

    // reach: eyes to the nearest point of the target's box
    Location base = target.getLocation();
    double half = width(target) / 2.0;
    double x = clamp(eyes.getX(), base.getX() - half, base.getX() + half);
    double y = clamp(eyes.getY(), base.getY(), base.getY() + height(target));
    double z = clamp(eyes.getZ(), base.getZ() - half, base.getZ() + half);
    double distance = Math.sqrt(square(eyes.getX() - x) + square(eyes.getY() - y) + square(eyes.getZ() - z));
    double limit = maxReach + Math.min(maxPingAllowance, Math.max(0, pingOf(player)) / 1000.0 * 8.0);
    if (distance > limit) {
      report(player, "hit from too far away", round(distance) + " blocks, limit " + round(limit) + ", ping " + pingOf(player));
    }

    // aim: a touch player hits what is on the screen, never what is behind the camera
    if (distance > 1.5) {
      Vector toTarget = new Vector(base.getX() - eyes.getX(), 0, base.getZ() - eyes.getZ());
      Vector look = eyes.getDirection().setY(0);
      if (toTarget.lengthSquared() > 0.01 && look.lengthSquared() > 0.01) {
        double angle = Math.toDegrees(toTarget.angle(look));
        if (now - track.behindWindowStart > 10000) {
          track.behindWindowStart = now;
          track.behindHits = 0;
        }
        if (angle > 120.0 && ++track.behindHits >= 3) {
          track.behindHits = 0;
          report(player, "hit targets behind the camera", "3 hits beyond 120 degrees within 10 s, last " + round(angle));
        }
      }
    }
    // several different targets inside one tick
    if (now - track.lastHitAt < 45 && track.lastHitEntity != target.getEntityId()) {
      if (++track.multiHits >= 4) {
        track.multiHits = 0;
        report(player, "hit several targets within one tick", "4 times a second target in under 45 ms");
      }
    }
    track.lastHitAt = now;
    track.lastHitEntity = target.getEntityId();

    // attack rate
    track.attacks++;
    if (track.attackWindowStart == 0) {
      track.attackWindowStart = now;
    } else if (now - track.attackWindowStart >= 1000) {
      double rate = track.attacks / ((now - track.attackWindowStart) / 1000.0);
      track.attackWindowStart = now;
      track.attacks = 0;
      if (rate <= maxAttacksPerSecond) {
        track.attackOver = 0;
      } else if (++track.attackOver >= 2) {
        track.attackOver = 0;
        report(player, "attacked faster than a hand can", round(rate) + " hits/s, limit " + maxAttacksPerSecond);
      }
    }
  }

  // ------------------------------------------------------------------ quiet periods

  @BukkitEventSubscription(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onTeleport(PlayerTeleportEvent event) {
    quiet(event.getPlayer(), 3000);
  }

  @BukkitEventSubscription(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onVelocity(PlayerVelocityEvent event) {
    quiet(event.getPlayer(), 2500);
  }

  @BukkitEventSubscription(priority = EventPriority.MONITOR)
  public void onRespawn(PlayerRespawnEvent event) {
    quiet(event.getPlayer(), 3000);
  }

  @BukkitEventSubscription(priority = EventPriority.MONITOR)
  public void onQuit(PlayerQuitEvent event) {
    tracks.remove(event.getPlayer().getUniqueId());
  }

  private void quiet(Player player, long millis) {
    if (BedrockPlayers.is(player)) {
      Track track = track(player);
      track.quietUntil = Math.max(track.quietUntil, System.currentTimeMillis() + millis);
    }
  }

  private Track track(Player player) {
    Track track = tracks.get(player.getUniqueId());
    if (track == null) {
      track = new Track();
      Track raced = tracks.putIfAbsent(player.getUniqueId(), track);
      if (raced != null) {
        track = raced;
      }
    }
    return track;
  }

  /** States in which the server itself moves the player in ways these limits do not describe. */
  private static boolean unjudged(Player player, Track track, long now) {
    GameMode mode = player.getGameMode();
    return track.quietUntil > now || mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR
      || player.getAllowFlight() || player.isFlying() || player.isGliding() || player.isInsideVehicle()
      || player.isDead() || player.isSleeping() || flag(IS_SWIMMING, player) || flag(IS_RIPTIDING, player)
      || flag(IS_CLIMBING, player) || has(player, LEVITATION) || has(player, SLOW_FALLING) || has(player, DOLPHINS_GRACE);
  }

  /** Something to stand on, or a block that carries or slows a player, within about one block below the feet. */
  private static boolean supported(Location at) {
    if (at.getWorld() == null) {
      return true;
    }
    for (double dx = -0.31; dx <= 0.31; dx += 0.31) {
      for (double dz = -0.31; dz <= 0.31; dz += 0.31) {
        for (double dy = 0.1; dy >= -1.1; dy -= 0.6) {
          Block block = at.clone().add(dx, dy, dz).getBlock();
          Material type = block.getType();
          if (block.isLiquid() || !passable(block, type) || carries(type)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static boolean passable(Block block, Material type) {
    if (IS_PASSABLE != null) {
      try {
        return (Boolean) IS_PASSABLE.invoke(block);
      } catch (Exception ignored) {
        // fall through to the old API
      }
    }
    return !type.isSolid();
  }

  private static boolean carries(Material type) {
    String name = type.name();
    return name.equals("COBWEB") || name.equals("WEB") || name.equals("POWDER_SNOW") || name.equals("SCAFFOLDING")
      || name.equals("BUBBLE_COLUMN") || name.equals("HONEY_BLOCK") || name.equals("SLIME_BLOCK") || name.equals("LADDER")
      || name.endsWith("VINE") || name.endsWith("VINES") || name.endsWith("VINES_PLANT") || name.contains("BERRY")
      || name.contains("CARPET") || name.equals("SNOW") || name.contains("LILY");
  }

  private void report(Player player, String message, String details) {
    Violation violation = Violation.builderFor(BedrockSentinel.class)
      .forPlayer(player)
      .withMessage(message)
      .withDetails(details)
      .withVL(violationLevel)
      .build();
    Modules.violationProcessor().processViolation(violation);
  }

  // ------------------------------------------------------------------ API differences between server versions

  private static Method method(Class<?> owner, String name) {
    try {
      return owner.getMethod(name);
    } catch (Exception missing) {
      return null;
    }
  }

  private static boolean flag(Method method, Player player) {
    if (method == null) {
      return false;
    }
    try {
      return (Boolean) method.invoke(player);
    } catch (Exception failed) {
      return false;
    }
  }

  private static boolean has(Player player, PotionEffectType type) {
    return type != null && player.hasPotionEffect(type);
  }

  private static int pingOf(Player player) {
    if (GET_PING == null) {
      return 0;
    }
    try {
      return (Integer) GET_PING.invoke(player);
    } catch (Exception failed) {
      return 0;
    }
  }

  private static Object speedAttribute() {
    try {
      Class<?> attribute = Class.forName("org.bukkit.attribute.Attribute");
      for (String name : new String[] {"MOVEMENT_SPEED", "GENERIC_MOVEMENT_SPEED"}) {
        try {
          return attribute.getField(name).get(null);
        } catch (NoSuchFieldException next) {
          // the other name
        }
      }
    } catch (Exception missing) {
      // no attribute API
    }
    return null;
  }

  private static Method attributeGetter() {
    try {
      return Class.forName("org.bukkit.attribute.Attributable").getMethod("getAttribute", Class.forName("org.bukkit.attribute.Attribute"));
    } catch (Exception missing) {
      return null;
    }
  }

  private static double speedAttributeOf(Player player) {
    if (SPEED_ATTRIBUTE == null || GET_ATTRIBUTE == null) {
      return BASE_SPRINT_ATTRIBUTE;
    }
    try {
      Object instance = GET_ATTRIBUTE.invoke(player, SPEED_ATTRIBUTE);
      return instance == null ? BASE_SPRINT_ATTRIBUTE : (Double) instance.getClass().getMethod("getValue").invoke(instance);
    } catch (Exception failed) {
      return BASE_SPRINT_ATTRIBUTE;
    }
  }

  private static double width(Entity entity) {
    try {
      return ((Number) Entity.class.getMethod("getWidth").invoke(entity)).doubleValue();
    } catch (Exception missing) {
      return 0.6;
    }
  }

  private static double height(Entity entity) {
    try {
      return ((Number) Entity.class.getMethod("getHeight").invoke(entity)).doubleValue();
    } catch (Exception missing) {
      return 1.8;
    }
  }

  private static double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(value, max));
  }

  private static double square(double value) {
    return value * value;
  }

  private static double round(double value) {
    return Math.round(value * 10.0) / 10.0;
  }
}
