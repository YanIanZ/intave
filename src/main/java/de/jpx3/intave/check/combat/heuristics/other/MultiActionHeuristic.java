package de.jpx3.intave.check.combat.heuristics.other;

import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.ConfidenceBuffer;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AbilityMetadata;
import de.jpx3.intave.user.meta.AttackMetadata;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.InventoryMetadata;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.BLOCK_DIG;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.BLOCK_PLACE;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.USE_ITEM_ON;

/**
 * Detects the "multi-action" exploit (e.g. LiquidBounce's {@code MultiActions}) — performing hand
 * actions vanilla physically allows only one of per tick.
 *
 * <p>A vanilla client drives a single hand action at a time: you cannot place a block while a
 * block-break is mid-progress (you must abort/stop the break first), and you cannot begin breaking a
 * block while an item is in active use (eating, drinking, drawing a bow, or shield-blocking occupy the
 * hand). The exploit fires both at once. Two overlaps are caught:
 *
 * <ul>
 *   <li><b>place-while-break</b> — a block place ({@code USE_ITEM_ON}/{@code BLOCK_PLACE}) arrives
 *       while {@link AttackMetadata#inBreakProcess} is still set. The legitimate sequence sends a
 *       {@code STOP}/{@code ABORT_DESTROY_BLOCK} first, which clears that flag before the place, so a
 *       place during an un-aborted break is the tell.</li>
 *   <li><b>break-while-use</b> — a {@code START_DESTROY_BLOCK} arrives while
 *       {@link InventoryMetadata#handActive()} reports an item in active use.</li>
 * </ul>
 *
 * <p>A single overlap can race the tick boundary, and break-while-use can legitimately occur on the one
 * click that interrupts a use, so each overlap feeds a decaying {@link ConfidenceBuffer}: an isolated
 * edge fades and only a <b>sustained</b> run crosses {@link #RELEASE_THRESHOLD} and flags, at a
 * {@linkplain ClassicHeuristic#flag(Player, String, double) graded confidence} that scales with the
 * streak. Creative mode (instant break) is exempt. It is a hard invariant when sustained, so it ships
 * enforced (see {@code heuristics.classic.multi-action}).
 */
public final class MultiActionHeuristic extends ClassicHeuristic<MultiActionHeuristic.MultiActionMeta> {
  private static final double BUFFER_HALF_LIFE_MILLIS = 5_000d;
  /** Accumulated overlap evidence required before a flag is released. */
  private static final double RELEASE_THRESHOLD = 4.0d;
  /** Overlaps closer together than this continue a streak (otherwise it restarts). */
  private static final long STREAK_GAP_MILLIS = 1_500L;
  /** Streak length at which the run is unambiguously a multi-action exploit. */
  private static final double SUSTAINED_STREAK = 6.0d;

  public MultiActionHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.MULTI_ACTION, MultiActionMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {USE_ITEM_ON, BLOCK_PLACE}
  )
  public void onPlace(User user) {
    if (isCreative(user)) {
      return;
    }
    // a place while a block-break is still in progress: vanilla would have stopped/aborted the break
    if (user.meta().attack().inBreakProcess) {
      note(user, "placed a block while still breaking one");
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {BLOCK_DIG}
  )
  public void onDig(User user, PacketEvent event) {
    if (isCreative(user)) {
      return;
    }
    EnumWrappers.PlayerDigType digType = event.getPacket().getPlayerDigTypes().readSafely(0);
    // a block-break starting while an item is in active use: the hand is occupied
    if (digType == EnumWrappers.PlayerDigType.START_DESTROY_BLOCK && user.meta().inventory().handActive()) {
      note(user, "started breaking a block while using an item");
    }
  }

  private void note(User user, String details) {
    long now = System.currentTimeMillis();
    MultiActionMeta meta = metaOf(user);
    meta.streak = now - meta.lastOverlapMillis < STREAK_GAP_MILLIS ? meta.streak + 1 : 1;
    meta.lastOverlapMillis = now;
    meta.evidence.add(1.0d, now);
    if (meta.evidence.consumeIfAtLeast(RELEASE_THRESHOLD, now)) {
      double confidence = MathHelper.minmax(0.4d, meta.streak / SUSTAINED_STREAK, 1.0d);
      flag(user.player(), details + " (streak " + meta.streak + ") — multi-action exploit", confidence);
    }
  }

  private boolean isCreative(User user) {
    AbilityMetadata abilities = user.meta().abilities();
    return abilities.inGameMode(GameMode.CREATIVE);
  }

  public static final class MultiActionMeta extends CheckCustomMetadata {
    private long lastOverlapMillis;
    private int streak;
    private final ConfidenceBuffer evidence = new ConfidenceBuffer(BUFFER_HALF_LIFE_MILLIS);
  }
}
