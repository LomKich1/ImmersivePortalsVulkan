package qouteall.q_misc_util.platform.fabric;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents.Configure;
import net.fabricmc.fabric.impl.attachment.AttachmentTargetImpl;
import net.fabricmc.fabric.impl.attachment.sync.AttachmentChange;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.network.packet.Packet;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerConfigurationTask;
import net.minecraft.server.network.ServerConfigurationNetworkHandler;
import net.minecraft.network.listener.ClientCommonPacketListener;
import net.minecraft.network.listener.ServerCommonPacketListener;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.server.network.ServerPlayerConfigurationTask.class_8606;
import net.minecraft.network.packet.CustomPayload.class_9154;
import qouteall.imm_ptl.core.mixin.common.other_sync.IEServerConfigurationPacketListenerImpl;
import qouteall.q_misc_util.platform.PlatformNetworking;

public class FabricPlatformNetworking implements PlatformNetworking {
   @Override
   public <T extends CustomPayload> void registerPlayS2C(class_9154<T> type, PacketCodec<? super RegistryByteBuf, T> codec) {
      PayloadTypeRegistry.playS2C().register(type, codec);
   }

   @Override
   public <T extends CustomPayload> void registerPlayC2S(class_9154<T> type, PacketCodec<? super RegistryByteBuf, T> codec) {
      PayloadTypeRegistry.playC2S().register(type, codec);
   }

   @Override
   public <T extends CustomPayload> void registerServerReceiver(class_9154<T> type, PlatformNetworking.ServerPlayReceiver<T> handler) {
      ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) -> handler.receive((T)payload, context.player()));
   }

   @Environment(EnvType.CLIENT)
   @Override
   public <T extends CustomPayload> void registerClientReceiver(class_9154<T> type, PlatformNetworking.ClientPlayReceiver<T> handler) {
      ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) -> handler.receive((T)payload, context.client()));
   }

   @Override
   public Packet<ClientCommonPacketListener> createS2CPacket(CustomPayload payload) {
      return ServerPlayNetworking.createS2CPacket(payload);
   }

   @Environment(EnvType.CLIENT)
   @Override
   public Packet<ServerCommonPacketListener> createC2SPacket(CustomPayload payload) {
      return ClientPlayNetworking.createC2SPacket(payload);
   }

   @Override
   public <T extends CustomPayload> void registerConfigurationS2C(class_9154<T> type, PacketCodec<? super PacketByteBuf, T> codec) {
      PayloadTypeRegistry.configurationS2C().register(type, codec);
   }

   @Override
   public <T extends CustomPayload> void registerConfigurationC2S(class_9154<T> type, PacketCodec<? super PacketByteBuf, T> codec) {
      PayloadTypeRegistry.configurationC2S().register(type, codec);
   }

   @Override
   public <T extends CustomPayload> void registerServerConfigurationReceiver(class_9154<T> type, PlatformNetworking.ServerConfigurationReceiver<T> handler) {
      ServerConfigurationNetworking.registerGlobalReceiver(
         type, (payload, context) -> handler.receive((T)payload, new FabricPlatformNetworking.FabricServerConfigurationContext(context.networkHandler()))
      );
   }

   @Environment(EnvType.CLIENT)
   @Override
   public <T extends CustomPayload> void registerClientConfigurationReceiver(class_9154<T> type, PlatformNetworking.ClientConfigurationReceiver<T> handler) {
      ClientConfigurationNetworking.registerGlobalReceiver(type, (payload, context) -> handler.receive((T)payload, context.responseSender()::sendPacket));
   }

   @Override
   public void onServerConfigure(PlatformNetworking.ServerConfigureHandler handler) {
      ServerConfigurationConnectionEvents.CONFIGURE
         .register(
            (Configure)(networkHandler, server) -> handler.onConfigure(new FabricPlatformNetworking.FabricServerConfigurationControl(networkHandler), server)
         );
   }

   @Override
   public Packet<ClientCommonPacketListener> createConfigurationS2CPacket(CustomPayload payload) {
      return ServerConfigurationNetworking.createS2CPacket(payload);
   }

   @Override
   public void onChunkSentToPlayer(ServerPlayNetworkHandler listener, ServerWorld level, WorldChunk chunk) {
      ServerPlayerEntity player = listener.player;
      List<AttachmentChange> changes = new ArrayList<>();
      ((AttachmentTargetImpl)chunk).fabric_computeInitialSyncChanges(player, changes::add);
      if (!changes.isEmpty()) {
         AttachmentChange.partitionAndSendPackets(changes, player);
      }
   }

   private record FabricServerConfigurationContext(ServerConfigurationNetworkHandler networkHandler) implements PlatformNetworking.ServerConfigurationContext {
      @Override
      public GameProfile gameProfile() {
         return ((IEServerConfigurationPacketListenerImpl)this.networkHandler).ip_getGameProfile();
      }

      @Override
      public void completeTask(class_8606 type) {
         this.networkHandler.completeTask(type);
      }

      @Override
      public void disconnect(Text reason) {
         this.networkHandler.disconnect(reason);
      }
   }

   private record FabricServerConfigurationControl(ServerConfigurationNetworkHandler handler) implements PlatformNetworking.ServerConfigurationControl {
      @Override
      public boolean canSend(class_9154<?> type) {
         return ServerConfigurationNetworking.canSend(this.handler, type);
      }

      @Override
      public void addTask(ServerPlayerConfigurationTask task) {
         this.handler.addTask(task);
      }

      @Override
      public void disconnect(Text reason) {
         this.handler.disconnect(reason);
      }

      @Override
      public GameProfile gameProfile() {
         return ((IEServerConfigurationPacketListenerImpl)this.handler).ip_getGameProfile();
      }
   }
}
