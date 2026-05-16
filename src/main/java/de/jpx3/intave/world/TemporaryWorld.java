package de.jpx3.intave.world;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.block.type.MaterialSearch;
import de.jpx3.intave.cleanup.ShutdownTasks;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.metadata.LazyMetadataValue;

import java.io.File;
import java.util.Random;

public final class TemporaryWorld {
	private final String worldName;
	private boolean exists = false;

	private TemporaryWorld(String worldName) {
		this.worldName = worldName;
	}

	public void create() {
		if (exists) {
			return;
		}
		exists = true;
		Material floorMat = MaterialSearch.firstOf("BLACK_CONCRETE", "COAL_BLOCK", "OBSIDIAN");
		Material borderMat = MaterialSearch.firstOf("WARPED_WART_BLOCK", "SEA_LANTERN", "GLOWSTONE", "GLASS");
		Material originMat = MaterialSearch.firstOf("REDSTONE_BLOCK", "EMERALD_BLOCK", "DIAMOND_BLOCK");

		WorldCreator creator = new WorldCreator(worldName);
		creator.generator(new ChunkGenerator() {
			@Override
			public ChunkData generateChunkData(World world, Random random, int cx, int cz, BiomeGrid biome) {
				ChunkData chunkData = createChunkData(world);
				boolean isOriginChunk = (cx == 0 && cz == 0);
				for (int x = 0; x < 16; x++) {
					for (int z = 0; z < 16; z++) {
						if (x == 0 || z == 0) {
							if (isOriginChunk && x == 0 && z == 0) {
								chunkData.setBlock(x, WorldHeight.LOWER_WORLD_LIMIT + 1, z, originMat);
							} else {
								chunkData.setBlock(x, WorldHeight.LOWER_WORLD_LIMIT + 1, z, borderMat);
							}
						} else {
							chunkData.setBlock(x, WorldHeight.LOWER_WORLD_LIMIT + 1, z, floorMat);
						}
					}
				}
				return chunkData;
			}
		});
		World world = creator.createWorld();
		if (world == null) {
			throw new RuntimeException("Failed to create temporary world");
		}
		world.setGameRuleValue("doMobSpawning", "false");
		world.setGameRuleValue("doDaylightCycle", "false");
		world.setGameRuleValue("doWeatherCycle", "false");
		world.setMetadata("intave_testworld", new LazyMetadataValue(IntavePlugin.singletonInstance(), () -> true));
		world.setTime(6000);

		ShutdownTasks.add(this::destroy);
	}

	public World bukkitWorld() {
		return Bukkit.getWorld(worldName);
	}

	public void destroy() {
		World world = Bukkit.getWorld(worldName);
		if (world == null) {
			return;
		}
		for (Player player : world.getPlayers()) {
			player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
		}
		Bukkit.unloadWorld(world, false);
		File worldFolder = world.getWorldFolder();
		deleteDirectory(worldFolder);
	}

	public static TemporaryWorld createTemporaryWorld() {
		String worldName = "intave_tempworld_" + System.currentTimeMillis();
		TemporaryWorld tempWorld = new TemporaryWorld(worldName);
		tempWorld.create();
		return tempWorld;
	}

	private static void deleteDirectory(File directoryToBeDeleted) {
		File[] allContents = directoryToBeDeleted.listFiles();
		if (allContents != null) {
			for (File file : allContents) {
				deleteDirectory(file);
			}
		}
		directoryToBeDeleted.delete();
	}
}
