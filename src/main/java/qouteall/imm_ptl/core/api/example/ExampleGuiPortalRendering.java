package qouteall.imm_ptl.core.api.example;

import java.util.WeakHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.chunk_loading.ChunkLoader;
import qouteall.imm_ptl.core.chunk_loading.DimensionalChunkPos;
import qouteall.imm_ptl.core.render.GuiPortalRendering;
import qouteall.imm_ptl.core.render.MyRenderHelper;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.q_misc_util.api.McRemoteProcedureCall;
import qouteall.q_misc_util.my_util.DQuaternion;

public class ExampleGuiPortalRendering {
   @Environment(EnvType.CLIENT)
   private static Framebuffer frameBuffer;
   private static final WeakHashMap<ServerPlayerEntity, ChunkLoader> chunkLoaderMap = new WeakHashMap<>();

   private static void removeChunkLoaderFor(ServerPlayerEntity player) {
      ChunkLoader chunkLoader = chunkLoaderMap.remove(player);
      if (chunkLoader != null) {
         PortalAPI.removeChunkLoaderForPlayer(player, chunkLoader);
      }
   }

   public static void onCommandExecuted(ServerPlayerEntity player, ServerWorld world, Vec3d pos) {
      removeChunkLoaderFor(player);
      ChunkLoader chunkLoader = new ChunkLoader(new DimensionalChunkPos(world.getRegistryKey(), new ChunkPos(BlockPos.ofFloored(pos))), 8);
      PortalAPI.addChunkLoaderForPlayer(player, chunkLoader);
      chunkLoaderMap.put(player, chunkLoader);
      McRemoteProcedureCall.tellClientToInvoke(
         player, "qouteall.imm_ptl.core.api.example.ExampleGuiPortalRendering.RemoteCallables.clientActivateExampleGuiPortal", world.getRegistryKey(), pos
      );
   }

   @Environment(EnvType.CLIENT)
   public static class GuiPortalScreen extends Screen {
      private final RegistryKey<World> viewingDimension;
      private final Vec3d viewingPosition;

      public GuiPortalScreen(RegistryKey<World> viewingDimension, Vec3d viewingPosition) {
         super(Text.literal("GUI Portal Example"));
         this.viewingDimension = viewingDimension;
         this.viewingPosition = viewingPosition;
      }

      public void close() {
         super.close();
         McRemoteProcedureCall.tellServerToInvoke("qouteall.imm_ptl.core.api.example.ExampleGuiPortalRendering.RemoteCallables.serverRemoveChunkLoader");
      }

      public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
         super.render(context, mouseX, mouseY, deltaTicks);
         double t1 = CHelper.getSmoothCycles(503L);
         double t2 = CHelper.getSmoothCycles(197L);
         Matrix4f cameraTransformation = new Matrix4f();
         cameraTransformation.identity();
         cameraTransformation.mul(DQuaternion.rotationByDegrees(new Vec3d(1.0, 1.0, 1.0).normalize(), t1 * 360.0).toMatrix());
         Vec3d cameraPosition = this.viewingPosition.add(new Vec3d(Math.cos(t2 * 2.0 * Math.PI), 0.0, Math.sin(t2 * 2.0 * Math.PI)).multiply(30.0));
         WorldRenderInfo worldRenderInfo = new WorldRenderInfo.Builder()
            .setWorld(ClientWorldLoader.getWorld(this.viewingDimension))
            .setCameraPos(cameraPosition)
            .setCameraTransformation(cameraTransformation)
            .setOverwriteCameraTransformation(true)
            .setDescription(null)
            .setRenderDistance(this.client.options.getClampedViewDistance())
            .setDoRenderHand(false)
            .setEnableViewBobbing(false)
            .setDoRenderSky(false)
            .setHasFog(false)
            .build();
         GuiPortalRendering.submitNextFrameRendering(worldRenderInfo, ExampleGuiPortalRendering.frameBuffer);
         int h = this.height;
         int w = this.width;
         MyRenderHelper.drawFramebufferInGui(context, ExampleGuiPortalRendering.frameBuffer, (int)(w * 0.2F), (int)(h * 0.2F), (int)(w * 0.8F), (int)(h * 0.8F));
         context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 70, 16777215);
      }

      public boolean shouldPause() {
         return false;
      }

      public boolean keyPressed(KeyInput input) {
         if (super.keyPressed(input)) {
            return true;
         } else if (this.client.options.inventoryKey.matchesKey(input)) {
            this.close();
            return true;
         } else {
            return false;
         }
      }
   }

   public static class RemoteCallables {
      @Environment(EnvType.CLIENT)
      public static void clientActivateExampleGuiPortal(RegistryKey<World> dimension, Vec3d position) {
         if (ExampleGuiPortalRendering.frameBuffer == null) {
            ExampleGuiPortalRendering.frameBuffer = new SimpleFramebuffer("imm_ptl_example_gui_portal", 2, 2, true);
         }

         MinecraftClient.getInstance().setScreen(new ExampleGuiPortalRendering.GuiPortalScreen(dimension, position));
      }

      public static void serverRemoveChunkLoader(ServerPlayerEntity player) {
         ExampleGuiPortalRendering.removeChunkLoaderFor(player);
      }
   }
}
