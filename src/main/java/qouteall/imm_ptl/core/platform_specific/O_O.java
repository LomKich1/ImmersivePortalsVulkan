package qouteall.imm_ptl.core.platform_specific;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlClientChunkMap;
import qouteall.imm_ptl.core.network.ImmPtlNetworkConfig;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.custom_portal_gen.PortalGenInfo;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.platform.ClientPlatform;
import qouteall.q_misc_util.platform.ModInfo;
import qouteall.q_misc_util.platform.Platform;

public class O_O {
   public static boolean isDimensionalThreadingPresent = false;
   private static final BlockState obsidianState = Blocks.OBSIDIAN.getDefaultState();

   public static boolean isForge() {
      return Platform.get().isForgeLike();
   }

   @Environment(EnvType.CLIENT)
   public static void onPlayerChangeDimensionClient(RegistryKey<World> from, RegistryKey<World> to) {
      RequiemCompat.onPlayerTeleportedClient();
      ClientPlatform.get().onPlayerChangedDimensionClient(from, to);
   }

   public static void onPlayerTravelOnServer(ServerPlayerEntity player, ServerWorld fromWorld, ServerWorld toWorld) {
      RequiemCompat.onPlayerTeleportedServer(player);
   }

   public static Path getGameDir() {
      return Platform.get().getGameDir();
   }

   public static boolean isObsidian(BlockState blockState) {
      return blockState == obsidianState;
   }

   public static void postClientChunkLoadEvent(WorldChunk chunk) {
      ClientPlatform.get().postClientChunkLoadEvent((ClientWorld)chunk.getWorld(), chunk);
   }

   public static void postClientChunkUnloadEvent(WorldChunk chunk) {
      ClientPlatform.get().postClientChunkUnloadEvent((ClientWorld)chunk.getWorld(), chunk);
   }

   public static boolean isDedicatedServer() {
      return Platform.get().isDedicatedServer();
   }

   public static void postPortalSpawnEventForge(PortalGenInfo info) {
   }

   @Environment(EnvType.CLIENT)
   public static ClientChunkManager createMyClientChunkManager(ClientWorld world, int loadDistance) {
      return new ImmPtlClientChunkMap(world, loadDistance);
   }

   public static boolean getIsPehkuiPresent() {
      return Platform.get().isModLoaded("pehkui");
   }

   @Nullable
   public static String getImmPtlModInfoUrl() {
      String gameVersion = SharedConstants.getGameVersion().name();
      return isForge()
         ? "https://qouteall.fun/immptl_info/forge-%s.json".formatted(gameVersion)
         : "https://qouteall.fun/immptl_info/%s.json".formatted(gameVersion);
   }

   public static boolean isModLoadedWithinVersion(String modId, @Nullable String startVersion, @Nullable String endVersion) {
      Optional<ModInfo> modContainer = Platform.get().getModContainer(modId);
      if (modContainer.isPresent()) {
         String version = modContainer.get().version();

         try {
            if (startVersion != null) {
               int i = Platform.get().compareVersions(startVersion, version);
               if (i > 0) {
                  return false;
               }
            }

            if (endVersion != null) {
               int i = Platform.get().compareVersions(endVersion, version);
               if (i < 0) {
                  return false;
               }
            }
         } catch (IllegalArgumentException var6) {
            var6.printStackTrace();
         }

         return true;
      } else {
         return false;
      }
   }

   @NotNull
   public static ImmPtlNetworkConfig.ModVersion getImmPtlVersion() {
      ModInfo modInfo = Platform.get().getModContainer("iportal").orElseThrow();
      int[] versionComponents = modInfo.semanticVersionComponents();
      if (versionComponents == null) {
         return ImmPtlNetworkConfig.ModVersion.OTHER;
      } else if (versionComponents.length != 3) {
         Helper.LOGGER.error("immersive portals version {} is not in regular form", modInfo.version());
         return ImmPtlNetworkConfig.ModVersion.OTHER;
      } else {
         return new ImmPtlNetworkConfig.ModVersion(versionComponents[0], versionComponents[1], versionComponents[2]);
      }
   }

   public static String getImmPtlVersionStr() {
      return Platform.get().getModContainer("iportal").orElseThrow().version();
   }

   public static boolean shouldUpdateImmPtl(String latestReleaseVersion) {
      if (Platform.get().isDevelopmentEnvironment()) {
         return false;
      } else {
         String currentVersion = Platform.get().getModContainer("iportal").get().version();

         try {
            if (Platform.get().compareVersions(latestReleaseVersion, currentVersion) > 0) {
               return true;
            }
         } catch (IllegalArgumentException var3) {
            var3.printStackTrace();
         }

         return false;
      }
   }

   public static String getModDownloadLink() {
      return "https://modrinth.com/mod/immersiveportals";
   }

   public static String getIssueLink() {
      return "https://github.com/iPortalTeam/ImmersivePortalsMod/discussions";
   }

   @Nullable
   public static Identifier getModIconLocation(String modid) {
      String path = Platform.get().getModContainer(modid).flatMap(ModInfo::iconPath).orElse(null);
      if (path == null) {
         return null;
      } else {
         if (path.startsWith("/")) {
            path = path.substring(1);
         }

         if (path.startsWith("assets")) {
            path = path.substring("assets".length());
         }

         if (path.startsWith("/")) {
            path = path.substring(1);
         }

         String[] parts = path.split("/");
         return parts.length != 2 ? null : McHelper.newResourceLocation(parts[0], parts[1]);
      }
   }

   @Nullable
   public static String getModName(String modid) {
      return Platform.get().getModContainer(modid).map(ModInfo::name).orElse(null);
   }

   public static boolean isQuilt() {
      return Platform.get().isModLoaded("quilted_fabric_api");
   }

   public static List<String> getLoadedModIds() {
      return Platform.get().getAllMods().stream().map(ModInfo::id).sorted().toList();
   }

   public static boolean allowTeleportingEntity(Entity entity, Portal portal) {
      return true;
   }

   public static boolean isDevEnv() {
      return Platform.get().isDevelopmentEnvironment();
   }
}
