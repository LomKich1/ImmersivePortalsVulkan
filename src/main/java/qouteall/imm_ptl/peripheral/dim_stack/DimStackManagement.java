package qouteall.imm_ptl.peripheral.dim_stack;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.command.DefaultPermissions;
import net.minecraft.world.World;
import net.minecraft.block.Blocks;
import net.minecraft.text.Text;
import net.minecraft.block.BlockState;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos.class_2339;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.dimlib.api.DimensionAPI;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.platform_specific.IPConfig;
import qouteall.imm_ptl.core.platform_specific.O_O;
import qouteall.imm_ptl.core.portal.global_portals.GlobalPortalStorage;
import qouteall.imm_ptl.core.portal.global_portals.VerticalConnectingPortal;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.api.McRemoteProcedureCall;

public class DimStackManagement {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static DimStackInfo dimStackToApply = null;
   public static Map<RegistryKey<World>, BlockState> bedrockReplacementMap = new HashMap<>();

   public static void init() {
      if (isDimStackEnabled()) {
         DimensionAPI.SERVER_DIMENSIONS_LOAD_EVENT.register(server -> {
            if (dimStackToApply != null) {
               DimensionStackAPI.DIMENSION_STACK_PRE_UPDATE_EVENT.invoker().run(server, dimStackToApply);
            }
         });
      }
   }

   private static boolean isDimStackEnabled() {
      return true;
   }

   public static void onServerEarlyInit(MinecraftServer server) {
      if (isDimStackEnabled()) {
         updateDimStackFromPresetInDedicatedServer(server);
         Map<RegistryKey<World>, BlockState> bedrockReplacementMap = new HashMap<>();
         if (dimStackToApply != null) {
            for (DimStackEntry entry : dimStackToApply.entries) {
               bedrockReplacementMap.put(entry.getDimension(), DimStackInfo.parseBlockString(entry.bedrockReplacementStr));
            }
         }

         DimStackManagement.bedrockReplacementMap = bedrockReplacementMap;
      }
   }

   public static void onServerCreatedWorlds(MinecraftServer server) {
      if (isDimStackEnabled()) {
         if (dimStackToApply != null) {
            dimStackToApply.apply(server);
            dimStackToApply = null;
         } else {
            updateBedrockReplacementFromStorage(server);
         }
      }
   }

   private static void updateDimStackFromPresetInDedicatedServer(MinecraftServer server) {
      if (O_O.isDedicatedServer()) {
         DimStackInfo dimStackPreset = getDimStackPreset();
         if (dimStackPreset != null) {
            if (!hasDimStackPortal(server)) {
               LOGGER.info("Applying dimension stack preset in dedicated server");
               dimStackToApply = dimStackPreset;
            } else {
               LOGGER.info("There are already dim stack portals, so the preset is not applied");
            }
         } else {
            LOGGER.info("The server has no dimension stack preset.");
         }
      }
   }

   private static void updateBedrockReplacementFromStorage(MinecraftServer server) {
      Map<RegistryKey<World>, BlockState> newMap = new HashMap<>();

      for (ServerWorld world : server.getWorlds()) {
         BlockState replacement = GlobalPortalStorage.get(world).bedrockReplacement;
         newMap.put(world.getRegistryKey(), replacement);
         LOGGER.info(
            "Bedrock Replacement {} {}",
            world.getRegistryKey().getValue(),
            replacement != null ? Registries.BLOCK.getId(replacement.getBlock()) : "null"
         );
      }

      bedrockReplacementMap = newMap;
   }

   public static void replaceBedrock(ServerWorld world, Chunk chunk) {
      if (isDimStackEnabled()) {
         if (bedrockReplacementMap == null) {
            LOGGER.error("Dimension Stack Bedrock Replacement Abnormal");
         } else {
            BlockState replacement = bedrockReplacementMap.get(world.getRegistryKey());
            if (replacement != null) {
               class_2339 mutable = new class_2339();

               for (int x = 0; x < 16; x++) {
                  for (int z = 0; z < 16; z++) {
                     for (int y = chunk.getBottomY(); y < chunk.getTopYInclusive() + 1; y++) {
                        mutable.set(x, y, z);
                        BlockState blockState = chunk.getBlockState(mutable);
                        if (blockState.getBlock() == Blocks.field_9987) {
                           chunk.setBlockState(mutable, replacement, 1);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static Collection<RegistryKey<World>> collectDimStackCandidateWhenServerRunning(MinecraftServer server) {
      LinkedHashSet<RegistryKey<World>> result = new LinkedHashSet<>(server.getWorldRegistryKeys());
      Collection<RegistryKey<World>> extra = DimensionStackAPI.DIMENSION_STACK_CANDIDATE_COLLECTION_EVENT
         .invoker()
         .getExtraDimensionKeys(server.getRegistryManager(), server.getSaveProperties().getGeneratorOptions());
      result.addAll(extra);
      return result;
   }

   public static void onDimensionStackCommandExecute(ServerPlayerEntity player) {
      List<String> dimIdList = collectDimStackCandidateWhenServerRunning(player.server).stream().map(k -> k.getValue().toString()).toList();
      McRemoteProcedureCall.tellClientToInvoke(player, "qouteall.imm_ptl.peripheral.dim_stack.DimStackManagement.RemoteCallables.clientOpenScreen", dimIdList);
   }

   private static void updateDimStack(MinecraftServer server, DimStackInfo dimStackInfo) {
      DimensionStackAPI.DIMENSION_STACK_PRE_UPDATE_EVENT.invoker().run(server, dimStackInfo);
      clearDimStackPortals(server);
      dimStackInfo.apply(server);
      if (O_O.isDedicatedServer()) {
         setDimStackPreset(dimStackInfo);
      }
   }

   public static boolean hasDimStackPortal(MinecraftServer server) {
      for (ServerWorld world : server.getWorlds()) {
         GlobalPortalStorage gps = GlobalPortalStorage.get(world);
         if (Helper.indexOf(gps.data, p -> p instanceof VerticalConnectingPortal) != -1) {
            return true;
         }
      }

      return false;
   }

   private static void clearDimStackPortals(MinecraftServer server) {
      for (ServerWorld world : server.getWorlds()) {
         GlobalPortalStorage gps = GlobalPortalStorage.get(world);
         gps.data.removeIf(p -> p instanceof VerticalConnectingPortal);
         gps.bedrockReplacement = null;
         gps.onDataChanged();
      }

      updateBedrockReplacementFromStorage(server);
   }

   @Nullable
   public static DimStackInfo getDimStackPreset() {
      JsonObject json = IPConfig.getConfig().dimStackPreset;
      if (json == null) {
         return null;
      } else {
         try {
            return (DimStackInfo)IPGlobal.gson.fromJson(json, DimStackInfo.class);
         } catch (Exception var2) {
            LOGGER.error("Cannot parse dimension stack preset JSON {}", json, var2);
            return null;
         }
      }
   }

   public static void setDimStackPreset(@Nullable DimStackInfo preset) {
      JsonObject json = null;
      if (preset != null) {
         json = IPGlobal.gson.toJsonTree(preset).getAsJsonObject();
      }

      IPConfig config = IPConfig.getConfig();
      config.dimStackPreset = json;
      config.saveConfigFile();
   }

   public static class RemoteCallables {
      @Environment(EnvType.CLIENT)
      public static void clientOpenScreen(List<String> dimensions) {
         List<RegistryKey<World>> dimensionList = dimensions.stream().map(Helper::dimIdToKey).collect(Collectors.toList());
         DimStackGuiController controller = new DimStackGuiController(
            null,
            () -> dimensionList,
            dimStackInfo -> {
               if (dimStackInfo != null) {
                  McRemoteProcedureCall.tellServerToInvoke(
                     "qouteall.imm_ptl.peripheral.dim_stack.DimStackManagement.RemoteCallables.serverSetupDimStack", dimStackInfo
                  );
               } else {
                  McRemoteProcedureCall.tellServerToInvoke("qouteall.imm_ptl.peripheral.dim_stack.DimStackManagement.RemoteCallables.serverRemoveDimStack");
               }

               MinecraftClient.getInstance().setScreen(null);
            }
         );
         controller.initializeAsDefault();
         MinecraftClient.getInstance().setScreen(controller.view);
      }

      public static void serverSetupDimStack(ServerPlayerEntity player, DimStackInfo dimStackInfo) {
         if (!player.getPermissions().hasPermission(DefaultPermissions.GAMEMASTERS)) {
            player.sendMessage(Text.literal("You don't have permission to change dimension stack"));
         } else if (!DimStackManagement.isDimStackEnabled()) {
            player.sendMessage(Text.literal("Dimension stack feature is not enabled"));
         } else {
            MinecraftServer server = player.getServerWorld().getServer();
            DimStackManagement.updateDimStack(server, dimStackInfo);
            player.sendMessage(Text.translatable("imm_ptl.dim_stack_established"), false);
         }
      }

      public static void serverRemoveDimStack(ServerPlayerEntity player) {
         if (!player.getPermissions().hasPermission(DefaultPermissions.GAMEMASTERS)) {
            Helper.err("one player without permission tries to change dimension stack");
         } else {
            MinecraftServer server = player.getServerWorld().getServer();
            DimStackManagement.clearDimStackPortals(server);
            player.sendMessage(Text.translatable("imm_ptl.dim_stack_removed"), false);
            if (O_O.isDedicatedServer()) {
               DimStackManagement.setDimStackPreset(null);
            }
         }
      }
   }
}
