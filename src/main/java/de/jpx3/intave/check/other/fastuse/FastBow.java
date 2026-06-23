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
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.BLOCK_PLACE;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.USE_ITEM;

/**
 * Flags firing a bow at (near) full power without drawing it long enough — the "fast-bow" cheat.
 *
 * <p>A bow's power is a fixed function of how long it was drawn: full charge needs ~1s (20 ticks),
 * and an arrow is launched at a speed of {@code force * 3.0} blocks/tick, so a full-charge shot leaves
 * the bow at ~3.0. The launch speed is read from {@link EntityShootBowEvent#getProjectile()}'s velocity
 * (universally available API), deliberately avoiding the version-fragile {@code getForce()}, which is
 * absent on the older servers Intave still supports.
 *
 * <p>The draw timer mirrors {@link FastConsume}: a right-click ({@code USE_ITEM} / {@code BLOCK_PLACE})
 * begins the draw and the timer only (re)starts on a genuinely new draw — never mid-draw — so repeated
 * use-packets can never advance it and fake a short draw. A near-full launch speed combined with a draw
 * shorter than {@link #MINIMUM_FULL_DRAW_MILLIS} cannot be produced legitimately. Crossbows are excluded
 * (they fire at full power instantly by design), and flags are gated behind a decaying {@code balance}
 * so only sustained fast-bow is reported.
 */
public final class FastBow extends MetaCheckPart<FastUse, FastBow.FastBowMeta> {
  /** Below this draw time a (near) full-charge shot is impossible (full charge needs ~1000ms). */
  private static final long MINIMUM_FULL_DRAW_MILLIS = 800L;
  /** Launch speed (blocks/tick) treated as near-full charge — force ~0.97, which needs ~975ms of draw. */
  private static final double FULL_CHARGE_VELOCITY = 2.9d;
  /** No bow draw lasts this long; an older "drawing" state is stale and a new draw may start. */
  private static final long STALE_USE_MILLIS = 3_000L;
  /** Tolerance: each genuine use action repays a little of the balance built up by fast shots. */
  private static final double BALANCE_REPAID_PER_USE = 0.25d;

  public FastBow(FastUse parentCheck) {
    super(parentCheck, FastBowMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.LOW,
    packetsIn = {USE_ITEM, BLOCK_PLACE}
  )
  public void onDrawStart(PacketEvent event) {
    User user = userOf(event.getPlayer());
    FastBowMeta meta = metaOf(user);
    long now = System.currentTimeMillis();
    // Only start the timer on a genuinely new draw — never mid-draw — so repeated use-packets can
    // never advance drawStart and fake a short draw.
    if (!meta.drawing || now - meta.drawStart > STALE_USE_MILLIS) {
      meta.drawStart = now;
      meta.drawing = true;
    }
    meta.balance = Math.max(0d, meta.balance - BALANCE_REPAID_PER_USE);
  }

  @BukkitEventSubscription
  public void onBowShoot(EntityShootBowEvent event) {
    if (!(event.getEntity() instanceof Player)) {
      return; // skeletons etc. also shoot bows
    }
    Player player = (Player) event.getEntity();
    User user = userOf(player);
    FastBowMeta meta = metaOf(user);
    long now = System.currentTimeMillis();
    long drawStart = meta.drawStart;
    meta.drawing = false;
    if (drawStart <= 0L) {
      return; // never observed the start of this draw
    }
    ItemStack bow = event.getBow();
    if (bow != null && bow.getType().name().contains("CROSSBOW")) {
      return; // crossbows fire at full power instantly by design
    }
    Entity projectile = event.getProjectile();
    if (projectile == null) {
      return;
    }
    double velocity = projectile.getVelocity().length();
    long drawTime = now - drawStart;
    if (velocity >= FULL_CHARGE_VELOCITY && drawTime < MINIMUM_FULL_DRAW_MILLIS && meta.balance++ >= 2) {
      String details = "full-power shot (v=" + MathHelper.formatDouble(velocity, 2)
        + ") after only " + drawTime + "ms draw";
      Violation violation = Violation.builderFor(FastUse.class)
        .forPlayer(player).withMessage("fired a bow too quickly")
        .withDetails(details)
        .withVL(10).build();
      Modules.violationProcessor().processViolation(violation);
    }
  }

  public static final class FastBowMeta extends CheckCustomMetadata {
    public long drawStart;
    public boolean drawing;
    public double balance;
  }
}
