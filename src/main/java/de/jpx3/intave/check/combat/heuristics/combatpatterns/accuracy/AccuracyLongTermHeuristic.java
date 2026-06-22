package de.jpx3.intave.check.combat.heuristics.combatpatterns.accuracy;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.packet.reader.EntityUseReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AttackMetadata;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

/**
 * Detects kill-aura / auto-clicker by the long-term ratio of <i>missed</i> swings to landed hits.
 *
 * <p>When a human fights a moving target a meaningful share of arm swings miss — the cursor drifts
 * off the hit-box between clicks. Aura keeps the cursor glued to the target, so almost every swing
 * connects. Over a window of {@link #ATTACK_SAMPLE_SIZE} registered attacks the heuristic computes
 * the fail rate (swings that did not turn into an attack) and flags when it stays under
 * {@link #MAX_FAIL_RATE_PERCENT}% — an accuracy no legitimate player sustains against a moving foe.
 *
 * <p>The window only counts swings/attacks against a long-lived ({@code ticksAlive >= 200}),
 * moving, recently-attacked entity, which filters out target dummies and stationary mobs.
 */
public final class AccuracyLongTermHeuristic extends ClassicHeuristic<AccuracyLongTermHeuristic.ClickAccuracyMeta> {
  // Window size (registered attacks) and the fail-rate ceiling (%) below which combat is flagged.
  private static final int ATTACK_SAMPLE_SIZE = 80;
  private static final double MAX_FAIL_RATE_PERCENT = 3.0;

  public AccuracyLongTermHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.ATTACK_ACCURACY, ClickAccuracyMeta.class);
  }

  @PacketSubscription(
    packetsIn = {
      ATTACK_ENTITY, USE_ENTITY, ARM_ANIMATION
    }
  )
  public void evaluateFightAccuracy(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    AttackMetadata attackData = user.meta().attack();
    ClickAccuracyMeta heuristicMeta = metaOf(user);
    PacketType packetType = event.getPacketType();
    PacketContainer packet = event.getPacket();
    Entity entity = attackData.lastAttackedEntity();
    if (entity == null || !entity.moving(0.05) || entity.ticksAlive < 200) {
      return;
    }
    if (!attackData.recentlyAttacked(500) || attackData.recentlySwitchedEntity(1000)) {
      return;
    }
    if (packetType == PacketType.Play.Client.ARM_ANIMATION) {
      heuristicMeta.swings++;
    } else {
      boolean isAttack;
      try (EntityUseReader reader = PacketReaders.readerOf(packet)) {
        isAttack = reader.isAttackPacket();
      } catch (Exception e) {
	      throw new RuntimeException(e);
      }
	    if (isAttack) {
        heuristicMeta.attacks++;
        heuristicMeta.swings--;
        double failRate = (heuristicMeta.swings / heuristicMeta.attacks) * 100.0;
//        Synchronizer.synchronize(() -> player.sendMessage(String.valueOf(failRate)));
        if (heuristicMeta.attacks > ATTACK_SAMPLE_SIZE) {
          if (failRate >= 0 && failRate < MAX_FAIL_RATE_PERCENT) {
            flag(player, "player maintains high attack accuracy (failRate: " + MathHelper.formatDouble(failRate, 2) + "%)");
          }
          heuristicMeta.attacks = 0;
          heuristicMeta.swings = 0;
        }
      }
    }
  }

  public static class ClickAccuracyMeta extends CheckCustomMetadata {
    public double attacks;
    public double swings;
  }
}
