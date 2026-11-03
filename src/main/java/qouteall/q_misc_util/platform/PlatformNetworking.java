package qouteall.q_misc_util.platform;

import com.mojang.authlib.GameProfile;
import java.util.ServiceLoader;
import java.util.function.Consumer;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerConfigurationTask;
import net.minecraft.network.listener.ClientCommonPacketListener;
import net.minecraft.network.listener.ServerCommonPacketListener;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.server.network.ServerPlayerConfigurationTask.class_8606;
import net.minecraft.network.packet.CustomPayload.class_9154;
import net.minecraft.server.MinecraftServer;

public interface PlatformNetworking {
   static PlatformNetworking get() {
      return PlatformNetworking.Holder.INSTANCE;
   }

   <T extends CustomPayload> void registerPlayS2C(class_9154<T> var1, PacketCodec<? super RegistryByteBuf, T> var2);

   <T extends CustomPayload> void registerPlayC2S(class_9154<T> var1, PacketCodec<? super RegistryByteBuf, T> var2);

   <T extends CustomPayload> void registerServerReceiver(class_9154<T> var1, PlatformNetworking.ServerPlayReceiver<T> var2);

   <T extends CustomPayload> void registerClientReceiver(class_9154<T> var1, PlatformNetworking.ClientPlayReceiver<T> var2);

   Packet<ClientCommonPacketListener> createS2CPacket(CustomPayload var1);

   Packet<ServerCommonPacketListener> createC2SPacket(CustomPayload var1);

   <T extends CustomPayload> void registerConfigurationS2C(class_9154<T> var1, PacketCodec<? super PacketByteBuf, T> var2);

   <T extends CustomPayload> void registerConfigurationC2S(class_9154<T> var1, PacketCodec<? super PacketByteBuf, T> var2);

   <T extends CustomPayload> void registerServerConfigurationReceiver(class_9154<T> var1, PlatformNetworking.ServerConfigurationReceiver<T> var2);

   <T extends CustomPayload> void registerClientConfigurationReceiver(class_9154<T> var1, PlatformNetworking.ClientConfigurationReceiver<T> var2);

   void onServerConfigure(PlatformNetworking.ServerConfigureHandler var1);

   Packet<ClientCommonPacketListener> createConfigurationS2CPacket(CustomPayload var1);

   default Packet<?> decorateChunkPacket(WorldChunk chunk, ChunkDataS2CPacket packet) {
      return packet;
   }

   void onChunkSentToPlayer(ServerPlayNetworkHandler var1, ServerWorld var2, WorldChunk var3);

   @FunctionalInterface
   public interface ClientConfigurationReceiver<T extends CustomPayload> {
      void receive(T var1, Consumer<CustomPayload> var2);
   }

   @FunctionalInterface
   public interface ClientPlayReceiver<T extends CustomPayload> {
      void receive(T var1, MinecraftClient var2);
   }

   public static class Holder {
      private static final PlatformNetworking INSTANCE = ServiceLoader.load(PlatformNetworking.class)
         .findFirst()
         .orElseThrow(() -> new IllegalStateException("No PlatformNetworking implementation found"));
   }

   public interface ServerConfigurationContext {
      GameProfile gameProfile();

      void completeTask(class_8606 var1);

      void disconnect(Text var1);
   }

   public interface ServerConfigurationControl {
      boolean canSend(class_9154<?> var1);

      void addTask(ServerPlayerConfigurationTask var1);

      void disconnect(Text var1);

      GameProfile gameProfile();
   }

   @FunctionalInterface
   public interface ServerConfigurationReceiver<T extends CustomPayload> {
      void receive(T var1, PlatformNetworking.ServerConfigurationContext var2);
   }

   @FunctionalInterface
   public interface ServerConfigureHandler {
      void onConfigure(PlatformNetworking.ServerConfigurationControl var1, MinecraftServer var2);
   }

   @FunctionalInterface
   public interface ServerPlayReceiver<T extends CustomPayload> {
      void receive(T var1, ServerPlayerEntity var2);
   }
}
