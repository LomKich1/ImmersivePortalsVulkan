package qouteall.imm_ptl.core.render;

import qouteall.imm_ptl.core.compat.RenderBackendCompat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderSystem.ShapeIndexBuffer;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import com.mojang.blaze3d.vertex.VertexFormat.IndexType;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlBackend;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.TexturedQuadGuiElementRenderState;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RawProjectionMatrix;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.texture.GlTextureView;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.miscellaneous.IPVanillaCopy;
import qouteall.imm_ptl.core.mixin.client.render.IEGuiGraphics;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.q_misc_util.platform.ClientPlatform;

public class MyRenderHelper {
   public static final MinecraftClient client = MinecraftClient.getInstance();
   private static final Matrix4f IDENTITY = new Matrix4f();
   private static final int SCREEN_SIZE_UBO_SIZE = Math.max(16, new Std140SizeCalculator().putFloat().putFloat().get());
   @Nullable
   private static RawProjectionMatrix projectionBuffer;
   @Nullable
   private static RawProjectionMatrix identityProjectionBuffer;
   @Nullable
   private static GpuBufferSlice identityProjectionSlice;
   @Nullable
   private static GpuBuffer screenSizeUniformBuffer;
   private static boolean debugEnabled = false;

   public static void init() {
   }

   public static GpuBufferSlice getProjectionBuffer(Matrix4f projectionMatrix) {
      if (projectionBuffer == null) {
         projectionBuffer = new RawProjectionMatrix("imm_ptl");
      }

      return projectionBuffer.set(projectionMatrix);
   }

   public static GpuBufferSlice getIdentityProjectionBuffer() {
      if (identityProjectionBuffer == null) {
         identityProjectionBuffer = new RawProjectionMatrix("imm_ptl identity");
         identityProjectionSlice = identityProjectionBuffer.set(new Matrix4f());
      }

      return identityProjectionSlice;
   }

   public static Matrix4f getProjectionMatrix() {
      Matrix4f matrix = RenderStates.projectionMatrix;
      return matrix == null ? new Matrix4f() : matrix;
   }

   public static int getFbo(Framebuffer target) {
      GlBackend device = (GlBackend)ClientPlatform.get().unwrapGpuDevice(RenderSystem.getDevice());
      GlTextureView colorView = (GlTextureView)ClientPlatform.get().unwrapGpuTextureView(target.getColorAttachmentView());
      GpuTexture depth = ClientPlatform.get().unwrapGpuTexture(target.getDepthAttachment());
      return colorView.getOrCreateFramebuffer(device.getBufferManager(), depth);
   }

   public static void bindRenderTargetFbo(Framebuffer target) {
      if (!RenderBackendCompat.isRawGlAvailable()) {
         return;
      }

      GlStateManager._glBindFramebuffer(36160, getFbo(target));
   }

   @IPVanillaCopy
   public static void drawMesh(
      RenderPipeline pipeline,
      BuiltBuffer meshData,
      Framebuffer target,
      Matrix4fc modelViewMatrix,
      GpuBufferSlice projection,
      @Nullable Consumer<RenderPass> extraSetup
   ) {
      try {
         GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
            .write(modelViewMatrix, new Vector4f(1.0F, 1.0F, 1.0F, 1.0F), new Vector3f(), new Matrix4f());
         VertexFormat format = pipeline.getVertexFormat();
         GpuBuffer vertexBuffer = format.uploadImmediateVertexBuffer(meshData.getBuffer());
         GpuBuffer indexBuffer;
         IndexType indexType;
         if (meshData.getSortedBuffer() == null) {
            ShapeIndexBuffer sequentialBuffer = RenderSystem.getSequentialBuffer(meshData.getDrawParameters().mode());
            indexBuffer = sequentialBuffer.getIndexBuffer(meshData.getDrawParameters().indexCount());
            indexType = sequentialBuffer.getIndexType();
         } else {
            indexBuffer = format.uploadImmediateIndexBuffer(meshData.getSortedBuffer());
            indexType = meshData.getDrawParameters().indexType();
         }

         RenderPass renderPass = RenderSystem.getDevice()
            .createCommandEncoder()
            .createRenderPass(
               () -> "ImmPtl " + pipeline.getLocation(),
               target.getColorAttachmentView(),
               OptionalInt.empty(),
               target.useDepthAttachment ? target.getDepthAttachmentView() : null,
               OptionalDouble.empty()
            );

         try {
            renderPass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("Projection", projection);
            renderPass.setUniform("DynamicTransforms", transforms);
            renderPass.setVertexBuffer(0, vertexBuffer);
            renderPass.setIndexBuffer(indexBuffer, indexType);
            if (extraSetup != null) {
               extraSetup.accept(renderPass);
            }

            renderPass.drawIndexed(0, 0, meshData.getDrawParameters().indexCount(), 1);
         } catch (Throwable var19) {
            if (renderPass != null) {
               try {
                  renderPass.close();
               } catch (Throwable var18) {
                  var19.addSuppressed(var18);
               }
            }

            throw var19;
         }

         if (renderPass != null) {
            renderPass.close();
         }
      } finally {
         meshData.close();
      }
   }

   private static void writeScreenSize(int width, int height) {
      CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
      if (screenSizeUniformBuffer == null) {
         screenSizeUniformBuffer = RenderSystem.getDevice().createBuffer(() -> "ImmPtl screen size UBO", 136, SCREEN_SIZE_UBO_SIZE);
      }

      MemoryStack stack = MemoryStack.stackPush();

      try {
         ByteBuffer buffer = Std140Builder.onStack(stack, SCREEN_SIZE_UBO_SIZE).putFloat(width).putFloat(height).get();
         encoder.writeToBuffer(screenSizeUniformBuffer.slice(), buffer);
      } catch (Throwable var7) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var6) {
               var7.addSuppressed(var6);
            }
         }

         throw var7;
      }

      if (stack != null) {
         stack.close();
      }
   }

   public static void drawPortalAreaWithFramebuffer(
      Portal portal, Framebuffer textureProvider, Matrix4f modelViewMatrix, Matrix4f projectionMatrix, Framebuffer target
   ) {
      writeScreenSize(textureProvider.textureWidth, textureProvider.textureHeight);
      BuiltBuffer mesh = ViewAreaRenderer.buildPortalViewAreaTrianglesMesh(Vec3d.ZERO, portal, CHelper.getCurrentCameraPos(), RenderStates.getPartialTick());
      drawMesh(IPRenderPipelines.DRAW_FB_IN_AREA, mesh, target, modelViewMatrix, getProjectionBuffer(projectionMatrix), renderPass -> {
         renderPass.bindTexture("DiffuseSampler", textureProvider.getColorAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.NEAREST));
         renderPass.setUniform("IPScreenSize", screenSizeUniformBuffer);
      });
   }

   public static void renderScreenTriangle(RenderPipeline pipeline, Framebuffer target) {
      renderScreenTriangle(pipeline, 0.0F, 255, 255, 255, 255, target);
   }

   public static void renderScreenTriangle(RenderPipeline pipeline, Vec3d color, Framebuffer target) {
      renderScreenTriangle(pipeline, 0.0F, (int)(color.x * 255.0), (int)(color.y * 255.0), (int)(color.z * 255.0), 255, target);
   }

   @IPVanillaCopy
   public static void renderScreenTriangle(RenderPipeline pipeline, float z, int r, int g, int b, int a, Framebuffer target) {
      BufferBuilder bufferBuilder = Tessellator.getInstance().begin(DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
      bufferBuilder.vertex(1.0F, -1.0F, z).color(r, g, b, a);
      bufferBuilder.vertex(1.0F, 1.0F, z).color(r, g, b, a);
      bufferBuilder.vertex(-1.0F, 1.0F, z).color(r, g, b, a);
      bufferBuilder.vertex(-1.0F, 1.0F, z).color(r, g, b, a);
      bufferBuilder.vertex(-1.0F, -1.0F, z).color(r, g, b, a);
      bufferBuilder.vertex(1.0F, -1.0F, z).color(r, g, b, a);
      drawMesh(pipeline, bufferBuilder.end(), target, IDENTITY, getIdentityProjectionBuffer(), null);
   }

   public static void drawScreenFrameBuffer(Framebuffer textureProvider, boolean doUseAlphaBlend, boolean doEnableModifyAlpha, Framebuffer target) {
      int x = 0;
      int y = 0;
      int viewportWidth = textureProvider.textureWidth;
      int viewportHeight = textureProvider.textureHeight;
      drawFramebufferWithCoordinatesAndDimensions(textureProvider, doUseAlphaBlend, doEnableModifyAlpha, x, y, viewportWidth, viewportHeight, target);
   }

   public static void drawFramebuffer(
      Framebuffer textureProvider, boolean doUseAlphaBlend, boolean doEnableModifyAlpha, float xMin, float xMax, float yMin, float yMax, Framebuffer target
   ) {
      drawFramebufferWithCoordinatesAndDimensions(
         textureProvider,
         doUseAlphaBlend,
         doEnableModifyAlpha,
         0,
         0,
         client.getWindow().getFramebufferWidth(),
         client.getWindow().getFramebufferHeight(),
         target
      );
   }

   public static void drawFramebufferWithViewport(
      Framebuffer textureProvider,
      boolean doUseAlphaBlend,
      boolean doEnableModifyAlpha,
      float left,
      float right,
      float bottom,
      float up,
      int viewportWidth,
      int viewportHeight,
      Framebuffer target
   ) {
      drawFramebufferWithCoordinatesAndDimensions(textureProvider, doUseAlphaBlend, doEnableModifyAlpha, 0, 0, viewportWidth, viewportHeight, target);
   }

   public static void drawFramebufferWithBounds(
      Framebuffer textureProvider, boolean doUseAlphaBlend, boolean doEnableModifyAlpha, int xMin, int xMax, int yMin, int yMax, Framebuffer target
   ) {
      drawFramebufferWithCoordinatesAndDimensions(
         textureProvider, doUseAlphaBlend, doEnableModifyAlpha, xMin, yMin, MathHelper.abs(xMax - xMin), MathHelper.abs(yMax - yMin), target
      );
   }

   @IPVanillaCopy
   public static void drawFramebufferWithCoordinatesAndDimensions(
      Framebuffer textureProvider,
      boolean doUseAlphaBlend,
      boolean doEnableModifyAlpha,
      int x,
      int y,
      int viewportWidth,
      int viewportHeight,
      Framebuffer target
   ) {
      CHelper.checkGlError();
      RenderPipeline pipeline = IPRenderPipelines.blitScreen(doUseAlphaBlend, doEnableModifyAlpha);
      RenderPass renderPass = RenderSystem.getDevice()
         .createCommandEncoder()
         .createRenderPass(
            () -> "ImmPtl draw framebuffer",
            target.getColorAttachmentView(),
            OptionalInt.empty(),
            target.useDepthAttachment ? target.getDepthAttachmentView() : null,
            OptionalDouble.empty()
         );

      try {
         GlStateManager._viewport(x, textureProvider.textureHeight - viewportHeight - y, viewportWidth, viewportHeight);
         renderPass.setPipeline(pipeline);
         RenderSystem.bindDefaultUniforms(renderPass);
         renderPass.bindTexture("InSampler", textureProvider.getColorAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.NEAREST));
         renderPass.draw(0, 3);
      } catch (Throwable var13) {
         if (renderPass != null) {
            try {
               renderPass.close();
            } catch (Throwable var12) {
               var13.addSuppressed(var12);
            }
         }

         throw var13;
      }

      if (renderPass != null) {
         renderPass.close();
      }

      CHelper.checkGlError();
   }

   public static void drawFramebufferInGui(DrawContext guiGraphics, Framebuffer framebuffer, int x0, int y0, int x1, int y1) {
      GuiRenderState guiRenderState = ((IEGuiGraphics)(Object)guiGraphics).ip_getGuiRenderState();
      guiRenderState.addSimpleElement(
         new TexturedQuadGuiElementRenderState(
            RenderPipelines.GUI_TEXTURED,
            TextureSetup.of(framebuffer.getColorAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.NEAREST)),
            new Matrix3x2f(guiGraphics.getMatrices()),
            x0,
            y0,
            x1,
            y1,
            0.0F,
            1.0F,
            1.0F,
            0.0F,
            -1,
            null
         )
      );
   }

   public static void lateUpdateLight() {
      if (ClientWorldLoader.getIsInitialized()) {
         ClientWorldLoader.getClientWorlds().forEach(world -> {
            if (!RenderStates.isDimensionRendered(world.getRegistryKey())) {
               world.getChunkManager().getLightingProvider().doLightUpdates();
            }
         });
      }
   }

   public static void earlyRemoteUpload() {
      if (ClientWorldLoader.getIsInitialized()) {
         ClientWorldLoader.WORLD_RENDERER_MAP.forEach((dim, worldRenderer) -> {
            if (client.world.getRegistryKey() != dim) {
               ChunkBuilder dispatcher = worldRenderer.getChunkBuilder();
               if (dispatcher != null) {
                  dispatcher.upload();
               }
            }
         });
      }
   }

   public static void applyMirrorFaceCulling() {
      if (!RenderBackendCompat.isRawGlAvailable()) {
         qouteall.imm_ptl.core.compat.vulkanmod_compatibility.VulkanTerrainBridge.setMirrorFrontFace(true);
         return;
      }

      GL11.glCullFace(1028);
   }

   public static void recoverFaceCulling() {
      if (!RenderBackendCompat.isRawGlAvailable()) {
         qouteall.imm_ptl.core.compat.vulkanmod_compatibility.VulkanTerrainBridge.setMirrorFrontFace(false);
         return;
      }

      GL11.glCullFace(1029);
   }

   public static void clearAlphaTo1(Framebuffer mcFrameBuffer) {
      renderScreenTriangle(IPRenderPipelines.SCREEN_TRIANGLE_ALPHA_ONLY, 0.0F, 0, 0, 0, 255, mcFrameBuffer);
   }

   public static void restoreViewPort() {
      MinecraftClient client = MinecraftClient.getInstance();
      GlStateManager._viewport(0, 0, client.getWindow().getFramebufferWidth(), client.getWindow().getFramebufferHeight());
   }

   public static float transformFogDistance(float value) {
      if (!WorldRenderInfo.isFogEnabled()) {
         return value * 23333.0F;
      } else {
         if (PortalRendering.isRendering()) {
            Portal renderingPortal = PortalRendering.getRenderingPortal();
            if (renderingPortal.isFuseView()) {
               return value * 23333.0F;
            }
         }

         return value;
      }
   }

   public static void debugFramebufferDepth() {
      if (debugEnabled && RenderBackendCompat.isRawGlAvailable()) {
         debugEnabled = false;
         int width = client.getFramebuffer().textureWidth;
         int height = client.getFramebuffer().textureHeight;
         GlStateManager._glBindFramebuffer(36008, getFbo(client.getFramebuffer()));
         ByteBuffer directBuffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.LITTLE_ENDIAN);
         FloatBuffer floatBuffer = directBuffer.asFloatBuffer();
         GL11.glReadPixels(0, 0, width, height, 6402, 5126, floatBuffer);
         float[] data = new float[width * height];
         floatBuffer.rewind();
         floatBuffer.get(data);
         float maxValue = (float)IntStream.range(0, data.length).mapToDouble(i -> data[i]).max().getAsDouble();
         float minValue = (float)IntStream.range(0, data.length).mapToDouble(i -> data[i]).min().getAsDouble();
         byte[] grayData = new byte[width * height];

         for (int i = 0; i < data.length; i++) {
            float datum = data[i];
            datum = (datum - minValue) / (maxValue - minValue);
            grayData[i] = (byte)(datum * 255.0F);
         }

         BufferedImage bufferedImage = new BufferedImage(width, height, 10);
         bufferedImage.setData(Raster.createRaster(bufferedImage.getSampleModel(), new DataBufferByte(grayData, grayData.length), new Point()));
         System.out.println("oops");
      }
   }

   public static void debugFramebufferColorRed() {
      if (debugEnabled) {
         debugEnabled = false;
         int width = client.getFramebuffer().textureWidth;
         int height = client.getFramebuffer().textureHeight;
         GlStateManager._glBindFramebuffer(36008, getFbo(client.getFramebuffer()));
         ByteBuffer directBuffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.LITTLE_ENDIAN);
         FloatBuffer floatBuffer = directBuffer.asFloatBuffer();
         GL11.glReadPixels(0, 0, width, height, 6403, 5126, floatBuffer);
         float[] data = new float[width * height];
         floatBuffer.rewind();
         floatBuffer.get(data);
         float maxValue = (float)IntStream.range(0, data.length).mapToDouble(i -> data[i]).max().getAsDouble();
         float minValue = (float)IntStream.range(0, data.length).mapToDouble(i -> data[i]).min().getAsDouble();
         byte[] grayData = new byte[width * height];

         for (int i = 0; i < data.length; i++) {
            float datum = data[i];
            datum = (datum - minValue) / (maxValue - minValue);
            grayData[i] = (byte)(datum * 255.0F);
         }

         BufferedImage bufferedImage = new BufferedImage(width, height, 10);
         bufferedImage.setData(Raster.createRaster(bufferedImage.getSampleModel(), new DataBufferByte(grayData, grayData.length), new Point()));
         System.out.println("oops");
      }
   }
}
