package de.jpx3.intave.test.client;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.world.TemporaryWorld;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class GameTest {
	private final String name;
	private final Replay replay;
	private Player player;
	private TemporaryWorld temporaryWorld;
	private Location startingLocation;

	private boolean wasStopped;

	public GameTest(String name, Replay replay) {
		this.name = name;
		this.replay = replay;
	}

	public void setup(
		Player player,
		Runnable callback
	) {
		if (!MinecraftVersions.VER1_13_0.atOrAbove()) {
			throw new UnsupportedOperationException("Minecraft 1.13 or above is required to run Intave tests");
		}
		this.player = player;
		this.temporaryWorld = TemporaryWorld.createTemporaryWorld();

		System.out.println("Setting up test " + name + " in world " + temporaryWorld.bukkitWorld().getName() + " for player " + player.getName());
		System.out.println("Frames: " + replay.frames.size());

		World world = temporaryWorld.bukkitWorld();

		try {
			Set<Position> addedBlocks = new HashSet<>();
			for (ReplayFrame frame : replay.frames) {
				for (TouchedBlock addedBlock : frame.addedBlocks()) {
					if (addedBlocks.contains(addedBlock.position())) {
						continue;
					}
					addedBlocks.add(addedBlock.position());
					Block block = world.getBlockAt(addedBlock.x(), addedBlock.y(), addedBlock.z());
					setBlockToBlockData(block, addedBlock.blockData());
				}
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}

		Synchronizer.synchronizeDelayed(() -> {
			System.out.println("Teleporting player " + player.getName() + " to start position " + replay.start.posMoveRot);
			startingLocation = player.getLocation();
			player.teleport(replay.start.posMoveRot.toLocationIn(world));
			UserRepository.userOf(player).tickFeedback(() ->
				Synchronizer.synchronizeDelayed(callback, 40)
			);
		}, 10);
	}

	public void execute(Runnable completionListener) {
		byte[] replayBytes = ReplayCodec.toBytes(replay);
		int bytesToSend = replayBytes.length;
		int maxPacketSize = 1024 * 256;
		int chunksToSend = (bytesToSend / maxPacketSize) + 1;
		System.out.println("Sending replay data in " + chunksToSend + " chunks");

		byte[][] byteChunks = new byte[chunksToSend][];
		for (int i = 0; i < chunksToSend; i++) {
			int start = i * maxPacketSize;
			int end = Math.min(start + maxPacketSize, bytesToSend);
			byte[] chunk = new byte[end - start];
			System.arraycopy(replayBytes, start, chunk, 0, end - start);
			byteChunks[i] = chunk;
		}

		UUID requestId = UUID.randomUUID();
		ClientTestService service = IntavePlugin.singletonInstance().clientTestService();
		int chunkIndex = 0;
		for (byte[] byteChunk : byteChunks) {
			service.send(
				player,
				new ObsidianPayload(
					ObsidianCommand.WORK,
					requestId,
					chunkIndex,
					chunksToSend,
					replayBytes.length,
					1.0f,
					byteChunk
				)
			);
			chunkIndex++;
		}
		Synchronizer.synchronizeDelayed(
			() -> {
				if (exit()) {
					Synchronizer.synchronizeDelayed(completionListener, 10);
				}
			},
			replay.frames.get(replay.frames.size() - 1).tick() + 100
		);
	}

	public boolean exit() {
		if (wasStopped) {
			return false;
		}
		wasStopped = true;
		player.teleport(startingLocation);
		Synchronizer.synchronizeDelayed(temporaryWorld::destroy, 40);
		return true;
	}

	private void setBlockToBlockData(Block block, BlockData blockData) {
		try {
			block.getClass()
				.getMethod("setBlockData", BlockData.class)
				.invoke(block, blockData);
		} catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
			throw new RuntimeException(e);
		}
	}

	public String name() {
		return name;
	}

	public Player player() {
		return player;
	}

	public World world() {
		return temporaryWorld.bukkitWorld();
	}
}
