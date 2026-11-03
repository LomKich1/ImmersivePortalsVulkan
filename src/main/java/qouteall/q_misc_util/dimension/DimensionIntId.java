package qouteall.q_misc_util.dimension;

import com.mojang.logging.LogUtils;
import java.util.HashSet;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.World;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.Identifier;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.network.listener.ClientCommonPacketListener;
import net.minecraft.server.MinecraftServer;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import qouteall.dimlib.api.DimensionAPI;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.IPPerServerInfo;
import qouteall.imm_ptl.core.McHelper;
import qouteall.q_misc_util.MiscNetworking;
import qouteall.q_misc_util.platform.event.Event;

public class DimensionIntId {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final Identifier DYNAMIC_UPDATE_EVENT_EARLY_PHASE = McHelper.newResourceLocation("iportal:early_phase");
   public static DimIntIdMap clientRecord;

   public static void init() {
      DimensionAPI.SERVER_DIMENSION_DYNAMIC_UPDATE_EVENT.addPhaseOrdering(DYNAMIC_UPDATE_EVENT_EARLY_PHASE, Event.DEFAULT_PHASE);
      DimensionAPI.SERVER_DIMENSION_DYNAMIC_UPDATE_EVENT.register(DYNAMIC_UPDATE_EVENT_EARLY_PHASE, (server, dimensions) -> onServerDimensionChanged(server));
   }

   @Environment(EnvType.CLIENT)
   public static void initClient() {
      IPCGlobal.CLIENT_EXIT_EVENT.register(DimensionIntId::onClientExit);
   }

   @Environment(EnvType.CLIENT)
   private static void onClientExit() {
      clientRecord = null;
   }

   @Environment(EnvType.CLIENT)
   @NotNull
   public static DimIntIdMap getClientMap() {
      Validate.notNull(clientRecord, "Client dim id record is not yet synced. This should not be used in networking thread.", new Object[0]);
      return clientRecord;
   }

   @NotNull
   public static DimIntIdMap getServerMap(MinecraftServer server) {
      IPPerServerInfo perServerInfo = IPPerServerInfo.of(server);
      DimIntIdMap rec = perServerInfo.dimIntIdMap;
      Validate.notNull(rec, "Server dim id record is not yet initialized", new Object[0]);
      return rec;
   }

   public static void onServerStarted(MinecraftServer server) {
      DimIntIdMap rec = new DimIntIdMap();
      fillInVanillaDimIds(rec);

      for (ServerWorld world : server.getWorlds()) {
         RegistryKey<World> dimId = world.getRegistryKey();
         if (!rec.containsDimId(dimId)) {
            rec.add(dimId, rec.getNextIntegerId());
         }
      }

      IPPerServerInfo perServerInfo = IPPerServerInfo.of(server);
      perServerInfo.dimIntIdMap = rec;
      LOGGER.info("Server dimension integer id mapping:\n{}", rec);
   }

   private static void fillInVanillaDimIds(DimIntIdMap rec) {
      if (!rec.containsDimId(World.OVERWORLD)) {
         rec.add(World.OVERWORLD, 0);
      }

      if (!rec.containsDimId(World.NETHER)) {
         rec.add(World.NETHER, -1);
      }

      if (!rec.containsDimId(World.END)) {
         rec.add(World.END, 1);
      }
   }

   public static void onServerDimensionChanged(MinecraftServer server) {
      DimIntIdMap map = getServerMap(server);

      for (RegistryKey<World> levelKey : server.getWorldRegistryKeys()) {
         if (!map.containsDimId(levelKey)) {
            map.add(levelKey, map.getNextIntegerId());
         }
      }

      HashSet<RegistryKey<World>> usedDimKeys = new HashSet<>(server.getWorldRegistryKeys());
      usedDimKeys.add(World.OVERWORLD);
      usedDimKeys.add(World.NETHER);
      usedDimKeys.add(World.END);
      map.removeUnused(usedDimKeys);
      LOGGER.info("Current dimension integer id mapping:\n{}", map);
      Packet<ClientCommonPacketListener> packet = MiscNetworking.DimIdSyncPacket.createPacket(server);

      for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
         player.networkHandler.sendPacket(packet);
      }
   }
}
