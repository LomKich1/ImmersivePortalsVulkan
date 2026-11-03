package qouteall.imm_ptl.core;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.world.ClientWorld.Properties;
import net.minecraft.item.map.MapState;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.profiler.Profilers;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.dimension.DimensionType;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qouteall.dimlib.api.DimensionAPI;
import qouteall.imm_ptl.core.ducks.IECamera;
import qouteall.imm_ptl.core.ducks.IEClientPlayNetworkHandler;
import qouteall.imm_ptl.core.ducks.IEClientWorld;
import qouteall.imm_ptl.core.ducks.IEMinecraftClient;
import qouteall.imm_ptl.core.ducks.IEParticleManager;
import qouteall.imm_ptl.core.ducks.IEWorld;
import qouteall.imm_ptl.core.ducks.IEWorldRenderer;
import qouteall.imm_ptl.core.mixin.client.accessor.IEClientLevelData;
import qouteall.imm_ptl.core.mixin.client.accessor.IEClientLevel_Accessor;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.context_management.DimensionRenderHelper;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.CountDownInt;
import qouteall.q_misc_util.platform.event.Event;

@Environment(EnvType.CLIENT)
public class ClientWorldLoader {
   private static final Logger LOGGER = LoggerFactory.getLogger(ClientWorldLoader.class);
   private static final CountDownInt LOG_LIMIT = new CountDownInt(20);
   public static final Event<Consumer<RegistryKey<World>>> CLIENT_DIMENSION_DYNAMIC_REMOVE_EVENT = Helper.createConsumerEvent();
   public static final Event<Consumer<ClientWorld>> CLIENT_WORLD_LOAD_EVENT = Helper.createConsumerEvent();
   private static final Map<RegistryKey<World>, ClientWorld> CLIENT_WORLD_MAP = new Object2ObjectOpenHashMap();
   public static final Map<RegistryKey<World>, WorldRenderer> WORLD_RENDERER_MAP = new Object2ObjectOpenHashMap();
   public static final Map<RegistryKey<World>, DimensionRenderHelper> RENDER_HELPER_MAP = new Object2ObjectOpenHashMap();
   @Nullable
   public static Map<RegistryKey<World>, RegistryKey<DimensionType>> dimIdToDimTypeId;
   private static final MinecraftClient CLIENT = MinecraftClient.getInstance();
   private static boolean isInitialized = false;
   private static boolean isCreatingClientWorld = false;
   public static boolean isClientRemoteTicking = false;
   private static boolean isWorldSwitched = false;
   private static boolean isReloadingOtherWorldRenderers = false;

   public static void init() {
      DimensionAPI.CLIENT_DIMENSION_UPDATE_EVENT.register(serverDimensions -> {
         if (getIsInitialized()) {
            for (RegistryKey<World> dim : CLIENT_WORLD_MAP.keySet().stream().filter(dimx -> !serverDimensions.contains(dimx)).toList()) {
               disposeDimensionDynamically(dim);
            }
         }
      });
      IPCGlobal.CLIENT_EXIT_EVENT.register(() -> dimIdToDimTypeId = null);
   }

   public static boolean getIsInitialized() {
      return isInitialized;
   }

   public static boolean getIsCreatingClientWorld() {
      return isCreatingClientWorld;
   }

   public static void tick() {
      if (IPCGlobal.isClientRemoteTickingEnabled) {
         isClientRemoteTicking = true;
         CLIENT_WORLD_MAP.values().forEach(world -> {
            if (CLIENT.world != world) {
               tickRemoteWorld(world);
            }
         });
         WORLD_RENDERER_MAP.values().forEach(worldRenderer -> {
            if (worldRenderer != CLIENT.worldRenderer) {
               worldRenderer.tick(CLIENT.gameRenderer.getCamera());
            }
         });
         isClientRemoteTicking = false;
      }

      boolean lightmapTextureConflict = false;

      for (DimensionRenderHelper helper : RENDER_HELPER_MAP.values()) {
         helper.tick();
         if (helper.world != CLIENT.world && helper.lightmapTexture == CLIENT.gameRenderer.getLightmapTextureManager()) {
            assert CLIENT.world != null;

            LOGGER.info("Lightmap Texture Conflict {} {}", helper.world.getRegistryKey().getValue(), CLIENT.world.getRegistryKey().getValue());
            lightmapTextureConflict = true;
         }
      }

      if (lightmapTextureConflict) {
         disposeRenderHelpers();
         LOGGER.info("Refreshed Lightmaps");
      }
   }

   public static void disposeRenderHelpers() {
      RENDER_HELPER_MAP.values().forEach(DimensionRenderHelper::cleanUp);
      RENDER_HELPER_MAP.clear();
   }

   private static void tickRemoteWorld(ClientWorld newWorld) {
      List<Portal> nearbyPortals = CHelper.getClientNearbyPortals(10.0).collect(Collectors.toList());
      withSwitchedWorld(newWorld, () -> {
         try {
            newWorld.tickEntities();
            newWorld.tick(() -> true);
            if (!CLIENT.isPaused()) {
               tickRemoteWorldRandomTicksClient(newWorld, nearbyPortals);
            }

            newWorld.runQueuedChunkUpdates();
         } catch (Throwable var3) {
            if (LOG_LIMIT.tryDecrement()) {
               LOGGER.error("", var3);
            }
         }
      });
   }

   private static void tickRemoteWorldRandomTicksClient(ClientWorld newWorld, List<Portal> nearbyPortals) {
      nearbyPortals.stream().filter(portal -> portal.getDestDim() == newWorld.getRegistryKey()).findFirst().ifPresent(portal -> {
         assert CLIENT.player != null;

         Vec3d playerPos = CLIENT.player.getEntityPos();
         Vec3d center = portal.transformPoint(playerPos);
         Camera camera = CLIENT.gameRenderer.getCamera();
         Vec3d oldCameraPos = camera.getCameraPos();
         ((IECamera)camera).portal_setPos(center);
         if (newWorld.getTime() % 2L == 0L) {
            newWorld.doRandomBlockDisplayTicks((int)center.x, (int)center.y, (int)center.z);
         }

         CLIENT.particleManager.tick();
         ((IECamera)camera).portal_setPos(oldCameraPos);
      });
   }

   public static void cleanUp() {
      WORLD_RENDERER_MAP.values().forEach(ClientWorldLoader::disposeWorldRenderer);

      for (ClientWorld clientWorld : CLIENT_WORLD_MAP.values()) {
         ((IEClientWorld)clientWorld).ip_resetWorldRendererRef();
      }

      CLIENT_WORLD_MAP.clear();
      WORLD_RENDERER_MAP.clear();
      disposeRenderHelpers();
      isInitialized = false;
   }

   private static void disposeWorldRenderer(WorldRenderer worldRenderer) {
      worldRenderer.setWorld(null);
      if (worldRenderer != CLIENT.worldRenderer) {
         worldRenderer.close();
         ((IEWorldRenderer)worldRenderer).portal_fullyDispose();
      }
   }

   private static void disposeDimensionDynamically(RegistryKey<World> dimension) {
      Validate.notNull(CLIENT.player, "player is null", new Object[0]);
      Validate.notNull(CLIENT.world, "level is null", new Object[0]);
      Validate.isTrue(CLIENT.world.getRegistryKey() != dimension, "Cannot dispose current dimension", new Object[0]);
      Validate.isTrue(CLIENT.player.getEntityWorld().getRegistryKey() != dimension, "Cannot dispose current dimension", new Object[0]);
      Validate.isTrue(CLIENT.isOnThread(), "not on client thread", new Object[0]);
      WorldRenderer worldRenderer = WORLD_RENDERER_MAP.get(dimension);
      disposeWorldRenderer(worldRenderer);
      WORLD_RENDERER_MAP.remove(dimension);
      Validate.isTrue(CLIENT.worldRenderer != worldRenderer);
      ClientWorld clientWorld = CLIENT_WORLD_MAP.get(dimension);
      ((IEClientWorld)clientWorld).ip_resetWorldRendererRef();
      CLIENT_WORLD_MAP.remove(dimension);
      DimensionRenderHelper renderHelper = RENDER_HELPER_MAP.remove(dimension);
      if (renderHelper != null) {
         renderHelper.cleanUp();
      }

      LOGGER.info("Client Dynamically Removed Dimension {}", dimension.getValue());
      if (clientWorld.getChunkManager().getLoadedChunkCount() > 0) {
         LOGGER.error("The chunks of that dimension was not cleared before removal");
      }

      if (clientWorld.getRegularEntityCount() > 0) {
         LOGGER.error("The entities of that dimension was not cleared before removal");
      }

      CLIENT.gameRenderer.reset();
      CLIENT_DIMENSION_DYNAMIC_REMOVE_EVENT.invoker().accept(dimension);
   }

   @NotNull
   public static WorldRenderer getWorldRenderer(RegistryKey<World> dimension) {
      initializeIfNeeded();
      WorldRenderer result = WORLD_RENDERER_MAP.get(dimension);
      if (result == null) {
         LOGGER.warn("Acquiring LevelRenderer before acquiring Level. Something is probably wrong. {}", dimension.getValue(), new Throwable());
         getWorld(dimension);
         result = WORLD_RENDERER_MAP.get(dimension);
         if (result == null) {
            throw new RuntimeException("Unable to get LevelRenderer of " + dimension.getValue());
         }
      }

      return result;
   }

   @NotNull
   public static ClientWorld getWorld(RegistryKey<World> dimension) {
      Validate.notNull(dimension, "dimension is null", new Object[0]);
      Validate.isTrue(CLIENT.isOnThread());
      initializeIfNeeded();
      if (!CLIENT_WORLD_MAP.containsKey(dimension)) {
         return createSecondaryClientWorld(dimension);
      } else {
         ClientWorld result = CLIENT_WORLD_MAP.get(dimension);
         Validate.notNull(result, "null value in world map", new Object[0]);
         return result;
      }
   }

   @Nullable
   public static ClientWorld getOptionalWorld(RegistryKey<World> dimension) {
      Validate.notNull(dimension, "dimension is null", new Object[0]);
      Validate.isTrue(CLIENT.isOnThread(), "not on client thread", new Object[0]);
      return getServerDimensions().contains(dimension) ? getWorld(dimension) : null;
   }

   public static DimensionRenderHelper getDimensionRenderHelper(RegistryKey<World> dimension) {
      initializeIfNeeded();
      DimensionRenderHelper result = RENDER_HELPER_MAP.computeIfAbsent(dimension, dimensionType -> new DimensionRenderHelper(getWorld(dimension)));
      Validate.isTrue(result.world.getRegistryKey() == dimension);
      return result;
   }

   public static void initializeIfNeeded() {
      if (!isInitialized) {
         Validate.isTrue(CLIENT.world != null, "level is null", new Object[0]);
         Validate.isTrue(CLIENT.worldRenderer != null, "levelRenderer is null", new Object[0]);
         Validate.notNull(
            CLIENT.player, "player is null. This may be caused by prior initialization failure. The log may provide useful information.", new Object[0]
         );
         Validate.isTrue(CLIENT.player.getEntityWorld() == CLIENT.world, "The player level is not the same as client level", new Object[0]);
         RegistryKey<World> playerDimension = CLIENT.world.getRegistryKey();
         CLIENT_WORLD_MAP.put(playerDimension, CLIENT.world);
         WORLD_RENDERER_MAP.put(playerDimension, CLIENT.worldRenderer);
         RENDER_HELPER_MAP.put(CLIENT.world.getRegistryKey(), new DimensionRenderHelper(CLIENT.world));
         isInitialized = true;
      }
   }

   private static ClientWorld createSecondaryClientWorld(RegistryKey<World> dimension) {
      Validate.notNull(CLIENT.player, "player is null", new Object[0]);
      Validate.isTrue(CLIENT.isOnThread(), "not on client thread", new Object[0]);
      Set<RegistryKey<World>> dimIds = getServerDimensions();
      if (!dimIds.contains(dimension)) {
         throw new RuntimeException("Cannot create invalid client dimension " + dimension.getValue());
      } else {
         isCreatingClientWorld = true;
         Profilers.get().push("create_world");
         int chunkLoadDistance = 3;
         WorldRenderer worldRenderer = new WorldRenderer(
            CLIENT,
            CLIENT.getEntityRenderDispatcher(),
            CLIENT.getBlockEntityRenderDispatcher(),
            CLIENT.getBufferBuilders(),
            CLIENT.gameRenderer.getEntityRenderStates(),
            CLIENT.gameRenderer.getEntityRenderDispatcher()
         );

         ClientWorld newWorld;
         try {
            ClientPlayNetworkHandler mainNetHandler = CLIENT.player.networkHandler;

            assert CLIENT.world != null;

            Map<String, MapState> mapData = ((IEClientLevel_Accessor)CLIENT.world).ip_getMapData();
            Validate.notNull(dimIdToDimTypeId, "dimension type mapping is missing", new Object[0]);
            RegistryKey<DimensionType> dimensionTypeKey = dimIdToDimTypeId.get(dimension);
            if (dimensionTypeKey == null) {
               throw new IllegalStateException("Cannot find dimension type for %s in %s".formatted(dimension.getValue(), dimIdToDimTypeId));
            }

            Properties currentProperty = (Properties)((IEWorld)CLIENT.world).ip_getLevelData();
            DynamicRegistryManager registryManager = mainNetHandler.getRegistryManager();
            int simulationDistance = CLIENT.world.getSimulationDistance();
            RegistryEntry<DimensionType> dimensionType = registryManager.getOrThrow(RegistryKeys.DIMENSION_TYPE).getOrThrow(dimensionTypeKey);
            Properties properties = new Properties(
               currentProperty.getDifficulty(), currentProperty.isHardcore(), ((IEClientLevelData)currentProperty).ip_getIsFlat()
            );
            newWorld = new ClientWorld(
               mainNetHandler,
               properties,
               dimension,
               dimensionType,
               chunkLoadDistance,
               simulationDistance,
               worldRenderer,
               CLIENT.world.isDebugWorld(),
               CLIENT.world.getBiomeAccess().seed,
               CLIENT.world.getSeaLevel()
            );
            ((IEClientLevel_Accessor)newWorld).ip_setMapData(mapData);
            ((IEClientWorld)newWorld).ip_setTickRateManager(CLIENT.world.getTickManager());
            worldRenderer.setWorld(newWorld);
            worldRenderer.reload(CLIENT.getResourceManager());
            CLIENT_WORLD_MAP.put(dimension, newWorld);
            WORLD_RENDERER_MAP.put(dimension, worldRenderer);
            LOGGER.info("Client World Created {}", dimension.getValue());
         } catch (Exception var16) {
            throw new IllegalStateException("Creating Client World " + dimension.getValue() + " " + CLIENT_WORLD_MAP.keySet(), var16);
         } finally {
            isCreatingClientWorld = false;
            Profilers.get().pop();
         }

         CLIENT_WORLD_LOAD_EVENT.invoker().accept(newWorld);
         return newWorld;
      }
   }

   public static Set<RegistryKey<World>> getServerDimensions() {
      assert CLIENT.player != null;

      return CLIENT.player.networkHandler.getWorldKeys();
   }

   public static Collection<ClientWorld> getClientWorlds() {
      Validate.isTrue(isInitialized);
      return CLIENT_WORLD_MAP.values();
   }

   public static void _onWorldRendererReloaded() {
      Validate.isTrue(CLIENT.isOnThread());
      if (CLIENT.world != null) {
         LOGGER.info("WorldRenderer reloaded {}", CLIENT.world.getRegistryKey().getValue());
      }

      if (!isReloadingOtherWorldRenderers) {
         if (!PortalRendering.isRendering()) {
            if (!getIsCreatingClientWorld()) {
               isReloadingOtherWorldRenderers = true;

               for (RegistryKey<World> dim : WORLD_RENDERER_MAP.keySet().stream().filter(d -> d != CLIENT.world.getRegistryKey()).toList()) {
                  ClientWorld world = CLIENT_WORLD_MAP.get(dim);
                  Validate.notNull(world, "missing client world %s", new Object[]{dim.getValue()});
                  withSwitchedWorld(world, () -> CLIENT.worldRenderer.reload());
               }

               isReloadingOtherWorldRenderers = false;
            }
         }
      }
   }

   public static <T> T withSwitchedWorld(ClientWorld newWorld, Supplier<T> supplier) {
      Validate.isTrue(CLIENT.isOnThread(), "not on client thread", new Object[0]);
      Validate.isTrue(CLIENT.player != null, "player is null", new Object[0]);
      ClientPlayNetworkHandler networkHandler = CLIENT.getNetworkHandler();

      assert networkHandler != null;

      ClientWorld originalWorld = CLIENT.world;
      WorldRenderer originalWorldRenderer = CLIENT.worldRenderer;
      ClientWorld originalNetHandlerWorld = networkHandler.getWorld();
      boolean originalIsWorldSwitched = isWorldSwitched;
      WorldRenderer newWorldRenderer = getWorldRenderer(newWorld.getRegistryKey());
      Validate.notNull(newWorldRenderer, "new world renderer is null", new Object[0]);
      CLIENT.world = newWorld;
      ((IEParticleManager)CLIENT.particleManager).ip_setWorld(newWorld);
      ((IEMinecraftClient)CLIENT).ip_setWorldRenderer(newWorldRenderer);
      ((IEClientPlayNetworkHandler)networkHandler).ip_setWorld(newWorld);
      isWorldSwitched = true;

      Object var8;
      try {
         var8 = supplier.get();
      } finally {
         if (CLIENT.world != newWorld) {
            LOGGER.error("Respawn packet should not be redirected");
            originalWorld = CLIENT.world;
            originalWorldRenderer = CLIENT.worldRenderer;
         }

         CLIENT.world = originalWorld;
         ((IEMinecraftClient)CLIENT).ip_setWorldRenderer(originalWorldRenderer);
         ((IEParticleManager)CLIENT.particleManager).ip_setWorld(originalWorld);
         ((IEClientPlayNetworkHandler)networkHandler).ip_setWorld(originalNetHandlerWorld);
         isWorldSwitched = originalIsWorldSwitched;
      }

      return (T)var8;
   }

   public static void withSwitchedWorld(ClientWorld newWorld, Runnable runnable) {
      withSwitchedWorld(newWorld, () -> {
         runnable.run();
         return null;
      });
   }

   public static void withSwitchedWorldFailSoft(RegistryKey<World> dim, Runnable runnable) {
      ClientWorld world = getOptionalWorld(dim);
      if (world == null) {
         LOGGER.error("Ignoring redirected task of invalid dimension {}", dim.getValue(), new Throwable());
      } else {
         withSwitchedWorld(world, runnable);
      }
   }

   public static boolean getIsWorldSwitched() {
      return isWorldSwitched;
   }

   public static class RemoteCallables {
      public static void checkBiomeRegistry(Map<String, Integer> idMap) {
         ClientPlayerEntity player = MinecraftClient.getInstance().player;

         assert player != null;

         DynamicRegistryManager registryAccess = player.networkHandler.getRegistryManager();
         Registry<Biome> biomes = registryAccess.getOrThrow(RegistryKeys.BIOME);

         for (Entry<String, Integer> entry : idMap.entrySet()) {
            Identifier id = McHelper.newResourceLocation(entry.getKey());
            int expectedId = entry.getValue();
            if (biomes.getRawId((Biome)biomes.get(id)) != expectedId) {
               ClientWorldLoader.LOGGER.error("Biome id mismatch: {} {}", id, expectedId);
            }
         }

         if (idMap.size() != biomes.getIds().size()) {
            ClientWorldLoader.LOGGER.error("Biome id mismatch: size not equal");
         }

         ClientWorldLoader.LOGGER.info("Biome id check finished");
      }
   }
}
