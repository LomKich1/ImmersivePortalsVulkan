package qouteall.q_misc_util.platform;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.brigadier.CommandDispatcher;
import java.util.ServiceLoader;
import java.util.function.Consumer;
import net.minecraft.world.World;
import net.minecraft.text.Text;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.render.WorldRenderer;

public interface ClientPlatform {
   static ClientPlatform get() {
      return ClientPlatform.Holder.INSTANCE;
   }

   void onClientTickEnd(Consumer<MinecraftClient> var1);

   void onClientPlayJoin(Consumer<ClientPlayNetworkHandler> var1);

   void onClientLoginInit(Runnable var1);

   void postClientChunkLoadEvent(ClientWorld var1, WorldChunk var2);

   void postClientChunkUnloadEvent(ClientWorld var1, WorldChunk var2);

   default GpuDevice unwrapGpuDevice(GpuDevice device) {
      return device;
   }

   default GpuTexture unwrapGpuTexture(GpuTexture texture) {
      return texture;
   }

   default GpuTextureView unwrapGpuTextureView(GpuTextureView view) {
      return view;
   }

   default boolean hasNativeStencilSupport() {
      return false;
   }

   default boolean isStencilEnabled(Framebuffer target) {
      throw new UnsupportedOperationException();
   }

   default void setStencilEnabled(Framebuffer target, boolean enabled) {
      throw new UnsupportedOperationException();
   }

   default void onRenderTargetBuffersCreated(Framebuffer target) {
   }

   default SimpleFramebuffer createSecondaryTextureTarget(String label, int width, int height, boolean useDepth) {
      return new SimpleFramebuffer(label, width, height, useDepth);
   }

   void onRegisterClientCommands(ClientPlatform.ClientCommandRegistrationHandler var1);

   default Object beforeNestedWorldRender(WorldRenderer outerRenderer) {
      return null;
   }

   default void afterNestedWorldRender(WorldRenderer outerRenderer, Object token) {
   }

   default void onPlayerChangedDimensionClient(RegistryKey<World> from, RegistryKey<World> to) {
   }

   public interface ClientCommandRegistrationHandler {
      <S> void register(CommandDispatcher<S> var1, ClientPlatform.ClientCommandSupport<S> var2);
   }

   public interface ClientCommandSupport<S> {
      void sendFeedback(S var1, Text var2);
   }

   public static class Holder {
      private static final ClientPlatform INSTANCE = ServiceLoader.load(ClientPlatform.class)
         .findFirst()
         .orElseThrow(() -> new IllegalStateException("No ClientPlatform implementation found"));
   }
}
