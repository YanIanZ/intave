package de.jpx3.intave.test.client;

import de.jpx3.intave.IntaveBuildConfig;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.annotate.HighOrderService;
import de.jpx3.intave.cleanup.ShutdownTasks;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscriber;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.test.TestCompletionLatch;
import de.jpx3.intave.user.UserLocal;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.bukkit.Bukkit.getServer;

@HighOrderService
public final class ClientTestService implements PluginMessageListener, BukkitEventSubscriber {
	private static final String CHANNEL = "intave:obsidian";
	private final UserLocal<AtomicReference<WorkerState>> workerState = UserLocal.withInitial(
		() -> new AtomicReference<>(WorkerState.UNREGISTERED)
	);
	private final List<GameTest> pendingGameTests = new ArrayList<>();
	private final Map<String, GameTestResult> gameTestResults = new HashMap<>();

	public void setup() {
		IntavePlugin plugin = IntavePlugin.singletonInstance();

		Messenger messenger = getServer().getMessenger();
		messenger.registerIncomingPluginChannel(plugin, CHANNEL, this);
		messenger.registerOutgoingPluginChannel(plugin, CHANNEL);

		Modules.linker().bukkitEvents().registerEventsIn(this);

		TestCompletionLatch.begunTest();


		TestCompletionLatch.finishTest();
		ShutdownTasks.add(this::disable);
	}

	public void disable() {
		Messenger messenger = getServer().getMessenger();
	  messenger.unregisterIncomingPluginChannel(IntavePlugin.singletonInstance(), CHANNEL);
	  messenger.unregisterOutgoingPluginChannel(IntavePlugin.singletonInstance(), CHANNEL);
	}

	@BukkitEventSubscription
	public void on(EntityDamageEvent damage) {
		if (damage.getEntity() instanceof Player) {
			Player player = (Player) damage.getEntity();
			if (workerState.get(player).get() == WorkerState.BUSY) {
				damage.setCancelled(true);
			}
		}
	}

	@BukkitEventSubscription
	public void onWaterFlow(BlockFromToEvent event) {
		Material type = event.getBlock().getType();
		World world = event.getBlock().getWorld();
		if (!world.getName().contains("intave_tempworld_") && !world.hasMetadata("intave_testworld")) {
			return;
		}
		if (type == Material.WATER || type == Material.LAVA) {
			event.setCancelled(true);
		}
	}

	@BukkitEventSubscription
	public void onSandFall(EntityChangeBlockEvent event) {
		World world = event.getBlock().getWorld();
		if (!world.getName().contains("intave_tempworld_") && !world.hasMetadata("intave_testworld")) {
			return;
		}
		if (event.getEntity() instanceof FallingBlock) {
			event.setCancelled(true);
			event.getBlock().getState().update(true, false);
		}
	}

	@BukkitEventSubscription
	public void onBlockBreak(BlockBreakEvent event) {
		World world = event.getBlock().getWorld();
		if (!world.getName().contains("intave_tempworld_") && !world.hasMetadata("intave_testworld")) {
			return;
		}
		event.setCancelled(true);
	}

	@BukkitEventSubscription
	public void on(BlockPhysicsEvent event) {
		World world = event.getBlock().getWorld();
		if (!world.getName().contains("intave_tempworld_") && !world.hasMetadata("intave_testworld")) {
			return;
		}
		event.setCancelled(true);
	}

	@Override
	public void onPluginMessageReceived(String channel, Player player, byte[] message) {
		if (!CHANNEL.equals(channel)) {
			return;
		}
		ObsidianPayload payload = ObsidianPayload.STREAM_CODEC.decode(Unpooled.copiedBuffer(message));
		System.out.println(payload);

		if (!player.isOp() && !IntaveBuildConfig.TEST_BUILD) {
			player.sendMessage(ChatColor.RED + "[Intave/Obsidian] You are not allowed to run tests as you are not an operator.");
			return;
		}

		if (workerState.get(player).get() == WorkerState.BUSY) {
			player.sendMessage(ChatColor.YELLOW + "[Intave/Obsidian] You are currently running a test, please wait until it finishes.");
			return;
		}

		switch (payload.command()) {
			case READY:
				if (pendingGameTests.isEmpty()) {
					workerState.get(player).set(WorkerState.IDLE);
				} else {
					workerState.get(player).set(WorkerState.BUSY);
					GameTest myTestToRun = pendingGameTests.remove(0);
					myTestToRun.setup(player, () -> {
						myTestToRun.execute(() -> {
							workerState.get(player).set(WorkerState.IDLE);
						});
					});
				}
				break;
			default:
				player.sendMessage(ChatColor.RED + "[Intave/Obsidian] Unknown command: " + payload.command());
		}
	}

	void send(Player player, ObsidianPayload payload) {
		ByteBuf buffer = Unpooled.buffer();
		ObsidianPayload.STREAM_CODEC.encode(buffer, payload);
		byte[] bytes = new byte[buffer.readableBytes()];
		buffer.readBytes(bytes);
		player.sendPluginMessage(IntavePlugin.singletonInstance(), CHANNEL, bytes);
	}

	public void queueTest(GameTest gameTest) {
		pendingGameTests.add(gameTest);
	}
}
