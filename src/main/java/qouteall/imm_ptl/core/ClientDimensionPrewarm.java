package qouteall.imm_ptl.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.world.LevelLoadingScreen;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface;
import qouteall.imm_ptl.core.compat.voxy_compatibility.VoxyInterface;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.renderer.PortalRenderer;

@Environment(EnvType.CLIENT)
public class ClientDimensionPrewarm {
   private static final Logger LOGGER = LoggerFactory.getLogger(ClientDimensionPrewarm.class);
   public static final String DIMENSIONS_PROPERTY = "immptl.prewarmPortalDimensions";
   private static final int GRACE_TICKS = 40;
   private static final MinecraftClient CLIENT = MinecraftClient.getInstance();
   private static boolean armed = false;
   @Nullable
   private static ClientWorld seenLevel = null;
   private static boolean decidedForLevel = true;
   private static int ticksForLevel = 0;
   private static boolean waitReported = false;

   public static void init() {
      IPGlobal.POST_CLIENT_TICK_EVENT.register(ClientDimensionPrewarm::tick);
   }

   public static void onVanillaLevelSet() {
      armed = true;
   }

   private static String screenName() {
      return CLIENT.currentScreen == null ? "none" : CLIENT.currentScreen.getClass().getName();
   }

   private static void tick() {
      ClientWorld level = CLIENT.world;
      if (level != null && CLIENT.player != null) {
         if (level != seenLevel) {
            seenLevel = level;
            ticksForLevel = 0;
            waitReported = false;
            decidedForLevel = !armed;
            if (armed) {
               armed = false;
               LOGGER.info(
                  "Portal dimension prewarm: level {} set by the vanilla path, screen {}, dimension data {}",
                  new Object[]{level.getRegistryKey().getValue(), screenName(), ClientWorldLoader.dimIdToDimTypeId == null ? "not yet received" : "received"}
               );
            }
         }

         ticksForLevel++;
         if (!decidedForLevel) {
            if (!PortalRendering.isRendering() && !ClientWorldLoader.getIsWorldSwitched()) {
               boolean loadingScreenUp = CLIENT.currentScreen instanceof LevelLoadingScreen;
               if (ClientWorldLoader.dimIdToDimTypeId != null && CLIENT.player.getEntityWorld() == level) {
                  if (!loadingScreenUp && ticksForLevel > 40) {
                     decidedForLevel = true;
                     LOGGER.info(
                        "Portal dimensions not prewarmed: the loading screen closed before the mod's dimension data was usable (tick {}, screen {})",
                        ticksForLevel,
                        screenName()
                     );
                  } else {
                     decidedForLevel = true;
                     LOGGER.info("Portal dimension prewarm at tick {} after the level was set, screen {}", ticksForLevel, screenName());

                     try {
                        run(level);
                     } catch (Throwable var3) {
                        LOGGER.error("Prewarming the portal dimensions failed", var3);
                     }
                  }
               } else {
                  if (!waitReported) {
                     waitReported = true;
                     LOGGER.info(
                        "Portal dimension prewarm waiting at tick {}: dimension data {}, player level {}, screen {}",
                        new Object[]{
                           ticksForLevel,
                           ClientWorldLoader.dimIdToDimTypeId == null ? "missing" : "received",
                           CLIENT.player.getEntityWorld() == level ? "ok" : "not the client level",
                           screenName()
                        }
                     );
                  }

                  if (!loadingScreenUp && ticksForLevel > 40) {
                     decidedForLevel = true;
                     LOGGER.info("Portal dimensions not prewarmed: the loading screen closed before the mod's dimension data arrived");
                  }
               }
            }
         }
      } else {
         seenLevel = null;
      }
   }

   private static void run(ClientWorld level) {
      List<RegistryKey<World>> targets = targetDimensions(level);
      boolean shaders = IrisInterface.invoker.isShaders();
      boolean voxy = VoxyInterface.invoker.isVoxyPresent();
      if (!targets.isEmpty() && (shaders || voxy)) {
         if (voxy && VoxyInterface.invoker.isLowOnVideoMemory()) {
            LOGGER.info("Portal dimensions not prewarmed: Voxy's free-VRAM policy keeps one LOD render system, following the player");
         } else {
            long start = System.nanoTime();
            PortalRenderer.switchToCorrectRenderer();
            long mainMs = 0L;
            if (shaders) {
               long mainStart = System.nanoTime();
               IrisInterface.invoker.preparePipelineForCurrentLevel();
               IrisInterface.invoker.initializeCurrentPipelineForRendering();
               mainMs = (System.nanoTime() - mainStart) / 1000000L;
            }

            List<ClientWorld> prewarmed = new ArrayList<>();

            for (RegistryKey<World> dimension : targets) {
               String name = dimension.getValue().toString();

               try {
                  long worldStart = System.nanoTime();
                  ClientWorld world = ClientWorldLoader.getWorld(dimension);
                  ClientWorldLoader.getDimensionRenderHelper(dimension);
                  long worldMs = (System.nanoTime() - worldStart) / 1000000L;
                  long[] ms = new long[]{0L, 0L};
                  VoxyInterface.prewarmingNow = true;

                  try {
                     ClientWorldLoader.withSwitchedWorld(world, () -> {
                        if (shaders) {
                           long pipelineStart = System.nanoTime();
                           IrisInterface.invoker.preparePipelineForCurrentLevel();
                           ms[0] = (System.nanoTime() - pipelineStart) / 1000000L;
                           long initStart = System.nanoTime();
                           IrisInterface.invoker.initializeCurrentPipelineForRendering();
                           ms[1] = (System.nanoTime() - initStart) / 1000000L;
                        }

                        if (voxy) {
                           VoxyInterface.invoker.onRendererAboutToRender(CLIENT.worldRenderer);
                        }
                     });
                  } finally {
                     VoxyInterface.prewarmingNow = false;
                  }

                  prewarmed.add(world);
                  LOGGER.info(
                     "Prewarmed {} for portal views behind the loading screen in {} ms (client world {} ms, Iris pipeline {} ms, its first-render work {} ms)",
                     new Object[]{name, (System.nanoTime() - worldStart) / 1000000L, worldMs, ms[0], ms[1]}
                  );
               } catch (Throwable var31) {
                  LOGGER.error("Prewarming {} for portal views failed; it is built at its first portal view instead", name, var31);
               }
            }

            if (voxy) {
               for (ClientWorld world : prewarmed) {
                  VoxyInterface.prewarmingNow = true;

                  try {
                     ClientWorldLoader.withSwitchedWorld(world, () -> {
                        if (shaders) {
                           IrisInterface.invoker.preparePipelineForCurrentLevel();
                        }

                        VoxyInterface.invoker.onRendererAboutToRender(CLIENT.worldRenderer);
                     });
                  } catch (Throwable var28) {
                     LOGGER.error("Re-creating the LOD render system of {} failed", world.getRegistryKey().getValue(), var28);
                  } finally {
                     VoxyInterface.prewarmingNow = false;
                  }
               }
            }

            if (shaders) {
               IrisInterface.invoker.preparePipelineForCurrentLevel();
            }

            if (voxy) {
               VoxyInterface.invoker.onRendererAboutToRender(CLIENT.worldRenderer);
            }

            LOGGER.info(
               "Prewarmed {} of {} portal dimensions in {} ms behind the loading screen (the player's own pipeline {} ms; {})",
               new Object[]{
                  prewarmed.size(),
                  targets.size(),
                  (System.nanoTime() - start) / 1000000L,
                  mainMs,
                  (shaders ? "shader pipelines" : "no shader pack") + (voxy ? ", Voxy LOD render systems" : "")
               }
            );
         }
      }
   }

   private static List<RegistryKey<World>> targetDimensions(ClientWorld level) {
      List<RegistryKey<World>> wanted = new ArrayList<>();
      String property = System.getProperty("immptl.prewarmPortalDimensions");
      if (property == null || property.isBlank()) {
         wanted.add(World.OVERWORLD);
         wanted.add(World.NETHER);
         wanted.add(World.END);
      } else if (!property.equalsIgnoreCase("false") && !property.equalsIgnoreCase("none")) {
         for (String token : property.split(",")) {
            Identifier id = Identifier.tryParse(token.trim());
            if (id == null) {
               LOGGER.warn("{}: not a dimension id, ignored: {}", "immptl.prewarmPortalDimensions", token);
            } else {
               wanted.add(RegistryKey.of(RegistryKeys.WORLD, id));
            }
         }
      }

      Set<RegistryKey<World>> serverDimensions = ClientWorldLoader.getServerDimensions();
      List<RegistryKey<World>> result = new ArrayList<>();

      for (RegistryKey<World> dimension : wanted) {
         if (!dimension.equals(level.getRegistryKey()) && serverDimensions.contains(dimension) && !result.contains(dimension)) {
            result.add(dimension);
         }
      }

      return result;
   }
}
