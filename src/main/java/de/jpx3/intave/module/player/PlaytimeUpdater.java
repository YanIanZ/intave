package de.jpx3.intave.module.player;

import de.jpx3.intave.executor.task.Task;
import de.jpx3.intave.executor.task.Tasks;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.storage.PlaytimeStorage;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlaytimeUpdater extends Module {
  private Task playtimeUpdateTask;

  @Override
  public void enable() {
    playtimeUpdateTask = Tasks.periodic(() -> {
      UserRepository.applyOnAll(user -> {
        PlaytimeStorage playtimeStorage = user.storageOf(PlaytimeStorage.class);
        if (System.currentTimeMillis() - user.joined() < 30000) {
          return;
        }
        if (System.currentTimeMillis() - user.meta().movement().lastRotation > 1000 * 60 * 2) {
          playtimeStorage.incrementMinutesAfkBy(1);
        } else {
          playtimeStorage.incrementMinutesPlayedBy(1);
        }
      });
    }, 20 * 60, 20 * 60).startAsync();
  }

  @Override
  public void disable() {
    if (playtimeUpdateTask != null) {
      playtimeUpdateTask.cancel();
    }
  }

  @BukkitEventSubscription
  public void on(PlayerQuitEvent quit) {
    Player player = quit.getPlayer();
    User user = UserRepository.userOf(player);
    PlaytimeStorage playtimeStorage =
      user.storageOf(PlaytimeStorage.class);
    playtimeStorage.incrementJoins();
  }
}
