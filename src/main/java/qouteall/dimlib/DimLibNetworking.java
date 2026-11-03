package qouteall.dimlib;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.dimension.DimensionTypes;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qouteall.dimlib.api.DimensionAPI;
import qouteall.dimlib.mixin.client.IClientPacketListener;
import qouteall.q_misc_util.platform.PlatformNetworking;

public class DimLibNetworking {
   public static final Logger LOGGER = LoggerFactory.getLogger(DimLibNetworking.class);

   public static void init() {
      PlatformNetworking.get().registerPlayS2C(DimLibNetworking.DimSyncPacket.TYPE, DimLibNetworking.DimSyncPacket.CODEC);
   }

   @Environment(EnvType.CLIENT)
   public static void initClient() {
      PlatformNetworking.get().registerClientReceiver(DimLibNetworking.DimSyncPacket.TYPE, (p, c) -> p.handle(c.getNetworkHandler()));
   }

   public record DimSyncPacket(NbtCompound dimIdToTypeIdTag) implements CustomPayload {
      public static final Id<DimLibNetworking.DimSyncPacket> TYPE = new Id(Identifier.of("dimlib:dim_sync"));
      public static final PacketCodec<PacketByteBuf, DimLibNetworking.DimSyncPacket> CODEC = PacketCodec.ofStatic(
         (b, p) -> p.write(b), DimLibNetworking.DimSyncPacket::read
      );

      public static DimLibNetworking.DimSyncPacket read(PacketByteBuf buf) {
         NbtCompound compoundTag = buf.readNbt();
         return new DimLibNetworking.DimSyncPacket(compoundTag);
      }

      public void write(PacketByteBuf buf) {
         buf.writeNbt(this.dimIdToTypeIdTag);
      }

      public static DimLibNetworking.DimSyncPacket createPacket(MinecraftServer server) {
         DynamicRegistryManager registryManager = server.getRegistryManager();
         Registry<DimensionType> dimensionTypes = registryManager.getOrThrow(RegistryKeys.DIMENSION_TYPE);
         NbtCompound dimIdToDimTypeId = new NbtCompound();

         for (ServerWorld world : server.getWorlds()) {
            RegistryKey<World> dimId = world.getRegistryKey();
            DimensionType dimType = world.getDimension();
            Identifier dimTypeId = dimensionTypes.getId(dimType);
            if (dimTypeId == null) {
               DimLibNetworking.LOGGER.error("Cannot find dimension type for {}", dimId.getValue());
               DimLibNetworking.LOGGER.error("Registered dimension types {}", dimensionTypes.getIds());
               dimTypeId = DimensionTypes.OVERWORLD.getValue();
            }

            dimIdToDimTypeId.putString(dimId.getValue().toString(), dimTypeId.toString());
         }

         return new DimLibNetworking.DimSyncPacket(dimIdToDimTypeId);
      }

      public ImmutableMap<RegistryKey<World>, RegistryKey<DimensionType>> toMap() {
         NbtCompound tag = this.dimIdToTypeIdTag();
         Builder<RegistryKey<World>, RegistryKey<DimensionType>> builder = new Builder();

         for (String key : tag.getKeys()) {
            RegistryKey<World> dimId = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(key));
            String dimTypeId = tag.getString(key, "");
            RegistryKey<DimensionType> dimType = RegistryKey.of(RegistryKeys.DIMENSION_TYPE, Identifier.of(dimTypeId));
            builder.put(dimId, dimType);
         }

         return builder.build();
      }

      @Environment(EnvType.CLIENT)
      public void handle(ClientPlayPacketListener listener) {
         Validate.isTrue(MinecraftClient.getInstance().isOnThread(), "Not running in client thread", new Object[0]);
         DimLibNetworking.LOGGER.info("Client received dimension info\n{}", String.join("\n", this.dimIdToTypeIdTag.getKeys()));
         ImmutableMap<RegistryKey<World>, RegistryKey<DimensionType>> dimIdToDimType = this.toMap();
         ClientDimensionInfo.accept(dimIdToDimType);
         ((IClientPacketListener)listener).ip_setLevels(dimIdToDimType.keySet());
         DimensionAPI.CLIENT_DIMENSION_UPDATE_EVENT.invoker().run(ClientDimensionInfo.getDimensionIds());
      }

      @NotNull
      public Id<? extends CustomPayload> getId() {
         return TYPE;
      }
   }
}
