package qouteall.q_misc_util;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.World;
import net.minecraft.registry.Registry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.util.Identifier;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.world.dimension.DimensionTypes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.network.listener.ClientCommonPacketListener;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload.class_9154;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.McHelper;
import qouteall.q_misc_util.dimension.DimIntIdMap;
import qouteall.q_misc_util.dimension.DimensionIntId;
import qouteall.q_misc_util.platform.PlatformNetworking;

public class MiscNetworking {
   private static final Logger LOGGER = LogUtils.getLogger();

   @Environment(EnvType.CLIENT)
   public static void initClient() {
      PlatformNetworking.get().registerClientReceiver(MiscNetworking.DimIdSyncPacket.TYPE, (p, c) -> p.handle());
   }

   public static void init() {
      PlatformNetworking.get().registerPlayS2C(MiscNetworking.DimIdSyncPacket.TYPE, MiscNetworking.DimIdSyncPacket.CODEC);
   }

   public record DimIdSyncPacket(NbtCompound dimIntIdTag, NbtCompound dimTypeTag) implements CustomPayload {
      public static final class_9154<MiscNetworking.DimIdSyncPacket> TYPE = new class_9154(McHelper.newResourceLocation("imm_ptl:dim_int_id_sync"));
      public static final PacketCodec<PacketByteBuf, MiscNetworking.DimIdSyncPacket> CODEC = PacketCodec.ofStatic(
         (b, p) -> p.write(b), MiscNetworking.DimIdSyncPacket::read
      );

      public static MiscNetworking.DimIdSyncPacket createFromServer(MinecraftServer server) {
         DimIntIdMap rec = DimensionIntId.getServerMap(server);
         NbtCompound dimIntIdTag = rec.toTag(dim -> true);
         DynamicRegistryManager registryManager = server.getRegistryManager();
         Registry<DimensionType> dimensionTypes = registryManager.get(RegistryKeys.field_41241);
         NbtCompound dimIdToDimTypeIdTag = new NbtCompound();

         for (ServerWorld world : server.getWorlds()) {
            RegistryKey<World> dimId = world.getRegistryKey();
            DimensionType dimType = world.getDimension();
            Identifier dimTypeId = dimensionTypes.getId(dimType);
            if (dimTypeId == null) {
               MiscNetworking.LOGGER.error("Cannot find dimension type for {}", dimId.getValue());
               MiscNetworking.LOGGER.error("Registered dimension types {}", dimensionTypes.getIds());
               dimTypeId = DimensionTypes.field_37666.getValue();
            }

            dimIdToDimTypeIdTag.putString(dimId.getValue().toString(), dimTypeId.toString());
         }

         return new MiscNetworking.DimIdSyncPacket(dimIntIdTag, dimIdToDimTypeIdTag);
      }

      public static Packet<ClientCommonPacketListener> createPacket(MinecraftServer server) {
         return PlatformNetworking.get().createS2CPacket(createFromServer(server));
      }

      public void write(PacketByteBuf buf) {
         buf.writeNbt(this.dimIntIdTag);
         buf.writeNbt(this.dimTypeTag);
      }

      public static MiscNetworking.DimIdSyncPacket read(PacketByteBuf buf) {
         NbtCompound idMapTag = buf.readNbt();
         NbtCompound typeTag = buf.readNbt();
         return new MiscNetworking.DimIdSyncPacket(idMapTag, typeTag);
      }

      public void handle() {
         DimIntIdMap rec = DimIntIdMap.fromTag(this.dimIntIdTag);
         MiscNetworking.LOGGER.info("Client received dim id sync packet\n{}", rec);
         DimensionIntId.clientRecord = rec;
         Builder<RegistryKey<World>, RegistryKey<DimensionType>> builder = new Builder();

         for (String key : this.dimTypeTag.getKeys()) {
            RegistryKey<World> dimId = RegistryKey.of(RegistryKeys.WORLD, McHelper.newResourceLocation(key));
            String dimTypeId = this.dimTypeTag.getString(key, "");
            RegistryKey<DimensionType> dimType = RegistryKey.of(RegistryKeys.field_41241, McHelper.newResourceLocation(dimTypeId));
            builder.put(dimId, dimType);
         }

         ImmutableMap<RegistryKey<World>, RegistryKey<DimensionType>> dimTypeMap = builder.build();
         ClientWorldLoader.dimIdToDimTypeId = dimTypeMap;
         MiscNetworking.LOGGER.info("Client accepted dimension type mapping {}", dimTypeMap);
      }

      @NotNull
      public class_9154<? extends CustomPayload> getId() {
         return TYPE;
      }
   }
}
