package qouteall.dimlib;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.network.listener.ClientCommonPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.registry.entry.RegistryEntryInfo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.SaveProperties;
import net.minecraft.world.World;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.border.WorldBorder.Properties;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.level.ServerWorldProperties;
import net.minecraft.world.level.UnmodifiableLevelProperties;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import qouteall.dimlib.api.DimensionAPI;
import qouteall.dimlib.ducks.IMappedRegistry;
import qouteall.dimlib.ducks.IMinecraftServer;
import qouteall.q_misc_util.platform.PlatformNetworking;

public class DynamicDimensionsImpl {
   private static final Logger LOGGER = LogManager.getLogger();
   public static boolean isRemovingDimension = false;

   public static void init() {
   }

   public static void addDimensionDynamically(MinecraftServer server, Identifier dimensionId, DimensionOptions levelStem) {
      RegistryKey<World> dimensionResourceKey = RegistryKey.of(RegistryKeys.WORLD, dimensionId);
      Validate.isTrue(server.isOnThread(), "this should be called in server main thread", new Object[0]);
      Validate.isTrue(server.isRunning(), "Server is not running", new Object[0]);
      if (server.getWorld(dimensionResourceKey) != null) {
         throw new RuntimeException("Dimension " + dimensionId + " already exists.");
      } else {
         ServerWorld overworld = server.getWorld(World.OVERWORLD);
         Validate.notNull(overworld, "Overworld is null", new Object[0]);
         WorldBorder worldBorder = overworld.getWorldBorder();
         Validate.notNull(worldBorder, "Overworld world border is null", new Object[0]);
         SaveProperties worldData = server.getSaveProperties();
         ServerWorldProperties serverLevelData = worldData.getMainWorldProperties();
         long seed = worldData.getGeneratorOptions().getSeed();
         long obfuscatedSeed = BiomeAccess.hashSeed(seed);
         UnmodifiableLevelProperties derivedLevelData = new UnmodifiableLevelProperties(worldData, serverLevelData);
         ServerWorld newWorld = new ServerWorld(
            server,
            ((IMinecraftServer)server).dimlib_getExecutor(),
            ((IMinecraftServer)server).dimlib_getStorageSource(),
            derivedLevelData,
            dimensionResourceKey,
            levelStem,
            false,
            obfuscatedSeed,
            ImmutableList.of(),
            false,
            overworld.getRandomSequences()
         );
         ((IMinecraftServer)server).dimlib_addDimensionToWorldMap(dimensionResourceKey, newWorld);
         Registry<DimensionOptions> levelStemRegistry = server.getRegistryManager().getOrThrow(RegistryKeys.DIMENSION);
         ((IMappedRegistry)levelStemRegistry).dimlib_setIsFrozen(false);
         ((SimpleRegistry)levelStemRegistry).add(RegistryKey.of(RegistryKeys.DIMENSION, dimensionId), levelStem, RegistryEntryInfo.DEFAULT);
         ((IMappedRegistry)levelStemRegistry).dimlib_setIsFrozen(true);
         Optional<Properties> legacyBorderSettings = serverLevelData.getWorldBorder();
         if (legacyBorderSettings.isPresent()) {
            Properties legacySettings = legacyBorderSettings.get();
            PersistentStateManager dataStorage = newWorld.getPersistentStateManager();
            if (dataStorage.get(WorldBorder.TYPE) == null) {
               double coordinateScale = newWorld.getDimension().coordinateScale();
               Properties scaledSettings = new Properties(
                  legacySettings.centerX() / coordinateScale,
                  legacySettings.centerZ() / coordinateScale,
                  legacySettings.damagePerBlock(),
                  legacySettings.safeZone(),
                  legacySettings.warningBlocks(),
                  legacySettings.warningTime(),
                  legacySettings.size(),
                  legacySettings.lerpTime(),
                  legacySettings.lerpTarget()
               );
               WorldBorder newBorder = new WorldBorder(scaledSettings);
               newBorder.ensureInitialized(newWorld.getTime());
               dataStorage.set(WorldBorder.TYPE, newBorder);
            }
         }

         newWorld.getWorldBorder().setMaxRadius(server.getMaxWorldBorderRadius());
         server.getPlayerManager().setMainWorld(newWorld);
         LOGGER.info("Added Dimension {}", dimensionId);
         Packet<ClientCommonPacketListener> dimSyncPacket = PlatformNetworking.get().createS2CPacket(DimLibNetworking.DimSyncPacket.createPacket(server));

         for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.networkHandler.sendPacket(dimSyncPacket);
         }

         DimensionAPI.SERVER_DIMENSION_DYNAMIC_UPDATE_EVENT.invoker().run(server, server.getWorldRegistryKeys());
      }
   }

   public static void removeDimensionDynamically(ServerWorld world) {
      MinecraftServer server = world.getServer();
      Validate.isTrue(server.isOnThread());
      RegistryKey<World> dimension = world.getRegistryKey();
      if (dimension != World.OVERWORLD && dimension != World.NETHER && dimension != World.END) {
         Validate.isTrue(server.isRunning(), "Server is not running", new Object[0]);
         LOGGER.info("Started Removing Dimension {}", dimension.getValue());
         ((IMinecraftServer)server)
            .dimlib_addTask(
               () -> {
                  DimensionAPI.SERVER_PRE_REMOVE_DIMENSION_EVENT.invoker().accept(world);
                  evacuatePlayersFromDimension(world);
                  long startTime = System.nanoTime();
                  long lastLogTime = System.nanoTime();
                  isRemovingDimension = true;
                  ((IMinecraftServer)server).dimlib_removeDimensionFromWorldMap(dimension);

                  try {
                     while (world.getChunkManager().chunkLoadingManager.shouldDelayShutdown()) {
                        world.getChunkManager().shutdown();
                        world.getChunkManager().tick(() -> true, false);
                        world.getChunkManager().executeQueuedTasks();
                        server.runTask();
                        if (System.nanoTime() - lastLogTime > DimLibUtil.secondToNano(1.0)) {
                           lastLogTime = System.nanoTime();
                           LOGGER.info("waiting for chunk tasks to finish");
                        }

                        if (System.nanoTime() - startTime > DimLibUtil.secondToNano(15.0)) {
                           LOGGER.error("Waited too long for chunk tasks");
                           break;
                        }

                        ((IMinecraftServer)server).dimlib_waitUntilNextTick();
                     }
                  } catch (Throwable var12) {
                     LOGGER.error("Error when waiting for chunk tasks", var12);
                  }

                  isRemovingDimension = false;
                  LOGGER.info("Finished chunk tasks in {} seconds", DimLibUtil.nanoToSecond(System.nanoTime() - startTime));
                  LOGGER.info(
                     "Chunk num: {}     Has entities: {}",
                     world.getChunkManager().chunkLoadingManager.getLoadedChunkCount(),
                     world.iterateEntities().iterator().hasNext()
                  );
                  server.save(false, true, false);

                  try {
                     world.close();
                  } catch (IOException var11) {
                     LOGGER.error("Error when closing world", var11);
                  }

                  Registry<DimensionOptions> levelStemRegistry = server.getRegistryManager().getOrThrow(RegistryKeys.DIMENSION);
                  ((IMappedRegistry)levelStemRegistry).dimlib_forceRemove(dimension.getValue());
                  LOGGER.info("Removed Dimension {}", dimension.getValue());
                  Packet<ClientCommonPacketListener> dimSyncPacket = PlatformNetworking.get()
                     .createS2CPacket(DimLibNetworking.DimSyncPacket.createPacket(server));

                  for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                     player.networkHandler.sendPacket(dimSyncPacket);
                  }

                  DimensionAPI.SERVER_DIMENSION_DYNAMIC_UPDATE_EVENT.invoker().run(server, server.getWorldRegistryKeys());
               }
            );
      } else {
         throw new RuntimeException("Cannot remove vanilla dimension");
      }
   }

   private static void evacuatePlayersFromDimension(ServerWorld world) {
      MinecraftServer server = world.getServer();
      ServerWorld overworld = server.getWorld(World.OVERWORLD);
      Validate.notNull(overworld, "Overworld is null", new Object[0]);
      List<ServerPlayerEntity> players = world.getPlayers(p -> true);
      BlockPos sharedSpawnPos = overworld.getSpawnPoint().getPos();

      for (ServerPlayerEntity player : players) {
         player.teleport(overworld, sharedSpawnPos.getX(), sharedSpawnPos.getY(), sharedSpawnPos.getZ(), Set.of(), 0.0F, 0.0F, true);
         player.sendMessage(Text.literal("Teleported to spawn pos because dimension %s had been removed".formatted(world.getRegistryKey().getValue())));
      }
   }
}
