package de.jpx3.intave.check.other.fastuse;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.other.FastUse;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemConsumeEvent;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.BLOCK_PLACE;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.USE_ITEM;

/**
 * Flags consuming an item (food, potion, milk) faster than the game permits.
 *
 * <p>A right-click ({@code USE_ITEM} on modern, {@code BLOCK_PLACE} on 1.8) starts the timer; the
 * resulting {@link PlayerItemConsumeEvent} stops it. The quickest legitimate consumable — dried kelp —
 * still takes ~865ms, so a completion under {@link #MINIMUM_CONSUME_MILLIS} is physically impossible.
 *
 * <p>The timer only (re)starts on a genuinely new use action — never mid-use (see {@code using} /
 * {@link #STALE_USE_MILLIS}). This is the crux of the false-positive guard: a client that re-sends
 * use-packets every tick during the eating animation cannot advance the start time and thereby shrink
 * the measured duration, so {@code useStart} is always at or before the true start and the measured
 * duration is never falsely short. Flags are additionally gated behind a decaying {@code balance}
 * (mirroring {@link de.jpx3.intave.check.world.breakspeedlimiter.CompletionDurationCheck}) so only
 * sustained fast-use is reported.
 */
public final class FastConsume extends MetaCheckPart<FastUse, FastConsume.FastConsumeMeta> {
  /** Hard physical floor (ms): below this no consumable can legitimately finish (dried kelp ~865ms). */
  private static final long MINIMUM_CONSUME_MILLIS = 500L;
  /** No consumable use lasts this long; an older "using" state is stale and a new use may start. */
  private static final long STALE_USE_MILLIS = 3_000L;
  /** Tolerance: each genuine use action repays a little of the balance built up by fast completions. */
  private static final double BALANCE_REPAID_PER_USE = 0.25d;

  public FastConsume(FastUse parentCheck) {
    super(parentCheck, FastConsumeMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.LOW,
    packetsIn = {USE_ITEM, BLOCK_PLACE}
  )
  public void onUseStart(PacketEvent event) {
    User user = userOf(event.getPlayer());
    FastConsumeMeta meta = metaOf(user);
    long now = System.currentTimeMillis();
    // Only start the timer on a genuinely new use action — never mid-use — so repeated use-packets
    // during the eating animation can never advance useStart and fake a short duration.
    if (!meta.using || now - meta.useStart > STALE_USE_MILLIS) {
      meta.useStart = now;
      meta.using = true;
    }
    meta.balance = Math.max(0d, meta.balance - BALANCE_REPAID_PER_USE);
  }

  @BukkitEventSubscription
  public void onConsume(PlayerItemConsumeEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    FastConsumeMeta meta = metaOf(user);
    long now = System.currentTimeMillis();
    long useStart = meta.useStart;
    meta.using = false;
    if (useStart <= 0L) {
      return; // start of this use was never observed — cannot measure it
    }
    long actualDuration = now - useStart;
    if (actualDuration < MINIMUM_CONSUME_MILLIS && meta.balance++ >= 2) {
      String details = MathHelper.formatDouble((MINIMUM_CONSUME_MILLIS - actualDuration) / 50d, 2)
        + " ticks faster than possible (" + actualDuration + "ms)";
      Violation violation = Violation.builderFor(FastUse.class)
        .forPlayer(player).withMessage("consumed an item too quickly")
        .withDetails(details)
        .withVL(10).build();
      Modules.violationProcessor().processViolation(violation);
    }
  }

  public static final class FastConsumeMeta extends CheckCustomMetadata {
    public long useStart;
    public boolean using;
    public double balance;
  }
}
