package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.Stack;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.option.TextureFilteringMode;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.DefaultFramebufferSet;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.SubmittableBatch;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.DiffuseLighting.Type;
import net.minecraft.client.render.chunk.BlockBufferAllocatorStorage;
import net.minecraft.client.render.chunk.ChunkBuilder.BuiltChunk;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import net.minecraft.client.render.command.RenderDispatcher;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.render.fog.FogRenderer.FogType;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.Handle;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.profiler.Profilers;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.block_manipulation.BlockManipulationClient;

import qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface;
import qouteall.imm_ptl.core.compat.sodium_compatibility.SodiumInterface;
import qouteall.imm_ptl.core.ducks.IEGameRenderer;
import qouteall.imm_ptl.core.ducks.IEMinecraftClient;
import qouteall.imm_ptl.core.ducks.IEParticleManager;
import qouteall.imm_ptl.core.ducks.IEWorldRenderer;
import qouteall.imm_ptl.core.miscellaneous.IPVanillaCopy;
import qouteall.imm_ptl.core.mixin.client.render.IERenderSystem;
import qouteall.imm_ptl.core.mixin.client.render.IESectionRenderDispatcher;
import qouteall.imm_ptl.core.render.context_management.DimensionRenderHelper;
import qouteall.imm_ptl.core.render.context_management.FogRendererContext;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.q_misc_util.my_util.LimitedLogger;
import qouteall.q_misc_util.platform.ClientPlatform;

@Environment(EnvType.CLIENT)
public class MyGameRenderer {
   public static final MinecraftClient client = MinecraftClient.getInstance();
   private static final LimitedLogger limitedLogger = new LimitedLogger(10);
   private static final ArrayList<FogRenderer> fogRendererPool = new ArrayList<>();
   private static final ArrayList<FogRenderer> fogRenderersOfThisFrame = new ArrayList<>();
   private static final Stack<MyGameRenderer.NestedRenderContext> contextPool = new Stack<>();
   private static int usingContextNum = 0;
   public static int vanillaTerrainSetupOverride = 0;
   public static boolean enablePortalCaveCulling = true;
   public static int nestedRenderSerial = 0;

   private static FogRenderer acquireFogRenderer() {
      FogRenderer fogRenderer;
      if (!fogRendererPool.isEmpty()) {
         fogRenderer = fogRendererPool.remove(fogRendererPool.size() - 1);
      } else {
         GpuBufferSlice currentFog = RenderSystem.getShaderFog();
         fogRenderer = new FogRenderer();
         RenderSystem.setShaderFog(currentFog);
      }

      fogRenderersOfThisFrame.add(fogRenderer);
      return fogRenderer;
   }

   public static void onFrameEnd() {
      for (FogRenderer fogRenderer : fogRenderersOfThisFrame) {
         fogRenderer.rotate();
         fogRendererPool.add(fogRenderer);
      }

      fogRenderersOfThisFrame.clear();
   }

   public static void init() {
      IPCGlobal.CLIENT_CLEANUP_EVENT.register(() -> {
         for (MyGameRenderer.NestedRenderContext context : contextPool) {
            context.close();
         }

         contextPool.clear();
         fogRendererPool.forEach(FogRenderer::close);
         fogRendererPool.clear();
         fogRenderersOfThisFrame.forEach(FogRenderer::close);
         fogRenderersOfThisFrame.clear();
      });
   }

   private static MyGameRenderer.NestedRenderContext acquireContext() {
      usingContextNum++;
      boolean useSecondaryRenderBuffers = IPGlobal.useSecondaryEntityVertexConsumer;

      while (!contextPool.isEmpty()) {
         MyGameRenderer.NestedRenderContext context = contextPool.pop();
         if (context.renderBuffers != null == useSecondaryRenderBuffers) {
            return context;
         }

         context.close();
      }

      return new MyGameRenderer.NestedRenderContext(useSecondaryRenderBuffers);
   }

   private static void returnContext(MyGameRenderer.NestedRenderContext context) {
      usingContextNum--;
      contextPool.push(context);
   }

   public static void renderWorldNew(WorldRenderInfo worldRenderInfo, Consumer<Runnable> invokeWrapper) {
      WorldRenderInfo.pushRenderInfo(worldRenderInfo);
      nestedRenderSerial++;
      switchAndRenderTheWorld(
         worldRenderInfo.world,
         worldRenderInfo.cameraPos,
         worldRenderInfo.cameraPos,
         invokeWrapper,
         worldRenderInfo.renderDistance,
         worldRenderInfo.doRenderHand
      );
      nestedRenderSerial++;
      WorldRenderInfo.popRenderInfo();
   }

   private static void switchAndRenderTheWorld(
      ClientWorld newWorld, Vec3d thisTickCameraPos, Vec3d lastTickCameraPos, Consumer<Runnable> invokeWrapper, int renderDistance, boolean doRenderHand
   ) {
      if (!enablePortalCaveCulling) {
         client.chunkCullingEnabled = false;
      }

      if (!PortalRendering.shouldEnableSodiumCaveCulling()) {
         client.chunkCullingEnabled = false;
      }

      RegistryKey<World> newDimension = newWorld.getRegistryKey();
      WorldRenderer worldRenderer = ClientWorldLoader.getWorldRenderer(newDimension);
      CHelper.checkGlError();
      IEGameRenderer ieGameRenderer = (IEGameRenderer)client.gameRenderer;
      IEWorldRenderer ieWorldRenderer = (IEWorldRenderer)worldRenderer;
      DimensionRenderHelper helper = ClientWorldLoader.getDimensionRenderHelper(newDimension);
      Camera newCamera = new Camera();
      ClientWorld oldWorld = client.world;
      WorldRenderer oldWorldRenderer = client.worldRenderer;
      LightmapTextureManager oldLightmap = client.gameRenderer.getLightmapTextureManager();
      boolean oldNoClip = client.player.noClip;
      boolean oldDoRenderHand = ieGameRenderer.ip_getDoRenderHand();
      ObjectArrayList<BuiltChunk> oldChunkInfoList = ((IEWorldRenderer)oldWorldRenderer).portal_getChunkInfoList();
      HitResult oldCrosshairTarget = client.crosshairTarget;
      Camera oldCamera = client.gameRenderer.getCamera();
      BufferBuilderStorage oldRenderBuffers = ieWorldRenderer.ip_getRenderBuffers();
      BufferBuilderStorage oldClientRenderBuffers = client.getBufferBuilders();
      BlockBufferAllocatorStorage oldSectionRenderDispatcherFixedBuffers = ((IESectionRenderDispatcher)worldRenderer.getChunkBuilder()).ip_getFixedBuffers();
      Matrix4f oldProjectionMatrix = RenderStates.projectionMatrix == null ? null : new Matrix4f(RenderStates.projectionMatrix);
      ProjectionType oldProjectionType = RenderSystem.getProjectionType();
      Matrix4fStack oldModelViewStack = IERenderSystem.ip_getModelViewStack();
      WorldRenderState oldGameLevelRenderState = ieGameRenderer.ip_getLevelRenderState();
      OrderedRenderCommandQueueImpl oldGameSubmitNodeStorage = ieGameRenderer.ip_getSubmitNodeStorage();
      RenderDispatcher oldGameFeatureRenderDispatcher = ieGameRenderer.ip_getFeatureRenderDispatcher();
      FogRenderer oldFogRenderer = ieGameRenderer.ip_getFogRenderer();
      WorldRenderState oldWorldLevelRenderState = ieWorldRenderer.ip_getLevelRenderState();
      OrderedRenderCommandQueueImpl oldWorldSubmitNodeStorage = ieWorldRenderer.ip_getSubmitNodeStorage();
      RenderDispatcher oldWorldFeatureRenderDispatcher = ieWorldRenderer.ip_getFeatureRenderDispatcher();
      SubmittableBatch oldParticlesRenderState = ieWorldRenderer.ip_getParticlesRenderState();
      DefaultFramebufferSet targets = ieWorldRenderer.ip_getTargets();
      Handle<Framebuffer> oldTargetMain = targets.mainFramebuffer;
      Handle<Framebuffer> oldTargetTranslucent = targets.translucentFramebuffer;
      Handle<Framebuffer> oldTargetItemEntity = targets.itemEntityFramebuffer;
      Handle<Framebuffer> oldTargetParticles = targets.particlesFramebuffer;
      Handle<Framebuffer> oldTargetWeather = targets.weatherFramebuffer;
      Handle<Framebuffer> oldTargetClouds = targets.cloudsFramebuffer;
      Handle<Framebuffer> oldTargetEntityOutline = targets.entityOutlineFramebuffer;
      GpuBufferSlice oldShaderFog = RenderSystem.getShaderFog();
      GpuBufferSlice oldShaderLights = RenderSystem.getShaderLights();
      Vec3d oldFogColor = RenderStates.currentFogColor;
      ObjectArrayList<BuiltChunk> newChunkInfoList = VisibleSectionDiscovery.takeList();
      ((IEWorldRenderer)oldWorldRenderer).portal_setChunkInfoList(newChunkInfoList);
      Object irisPipeline = IrisInterface.invoker.getPipeline(worldRenderer);
      ((IEMinecraftClient)client).ip_setWorldRenderer(worldRenderer);
      client.world = newWorld;
      ieGameRenderer.ip_setLightmapTextureManager(helper.lightmapTexture);
      client.player.noClip = true;
      ieGameRenderer.ip_setDoRenderHand(doRenderHand);
      FogRendererContext.swappingManager.pushSwapping(newDimension);
      ((IEParticleManager)client.particleManager).ip_setWorld(newWorld);
      if (BlockManipulationClient.remotePointedDim == newDimension) {
         client.crosshairTarget = BlockManipulationClient.remoteHitResult;
      }

      if (!PortalRendering.shouldRenderHitResult()) {
         client.crosshairTarget = null;
      }

      ieGameRenderer.ip_setCamera(newCamera);
      MyGameRenderer.NestedRenderContext context = acquireContext();
      ieGameRenderer.ip_setLevelRenderState(context.levelRenderState);
      ieGameRenderer.ip_setSubmitNodeStorage(context.submitNodeStorage);
      ieGameRenderer.ip_setFeatureRenderDispatcher(context.featureRenderDispatcher);
      ieGameRenderer.ip_setFogRenderer(acquireFogRenderer());
      ieWorldRenderer.ip_setLevelRenderState(context.levelRenderState);
      ieWorldRenderer.ip_setSubmitNodeStorage(context.submitNodeStorage);
      ieWorldRenderer.ip_setFeatureRenderDispatcher(context.featureRenderDispatcher);
      ieWorldRenderer.ip_setParticlesRenderState(context.particlesRenderState);
      BufferBuilderStorage newRenderBuffers = null;
      if (IPGlobal.useSecondaryEntityVertexConsumer) {
         newRenderBuffers = context.renderBuffers;
         if (newRenderBuffers != null) {
            ieWorldRenderer.ip_setRenderBuffers(newRenderBuffers);
            ((IEMinecraftClient)client).ip_setRenderBuffers(newRenderBuffers);
            ((IESectionRenderDispatcher)worldRenderer.getChunkBuilder()).ip_setFixedBuffers(newRenderBuffers.getBlockBufferBuilders());
         } else {
            client.getBufferBuilders().getEntityVertexConsumers().draw();
         }
      }

      Object newSodiumContext = SodiumInterface.invoker.createNewContext(renderDistance);
      SodiumInterface.invoker.switchContextWithCurrentWorldRenderer(newSodiumContext);
      IERenderSystem.ip_setModelViewStack(new Matrix4fStack(16));
      IrisInterface.invoker.setPipeline(worldRenderer, null);
      client.gameRenderer.method_76295(client.getRenderTickCounter());
      updateGlobalSettingsUniform(client.gameRenderer.getCamera());
      if (!RenderStates.isDimensionRendered(newDimension)) {
         helper.lightmapTexture.update(0.0F);
      }

      client.gameRenderer.getDiffuseLighting().updateLevelBuffer(newWorld.getDimension().cardinalLightType());
      Object nestedRenderToken = ClientPlatform.get().beforeNestedWorldRender(oldWorldRenderer);
      Object[] dhRenderState = DhRenderStateGuard.snapshot();
      invokeWrapper.accept(() -> {
         Profilers.get().push("render_portal_content");
         client.gameRenderer.renderWorld(client.getRenderTickCounter());
         Profilers.get().pop();
      });
      DhRenderStateGuard.restore(dhRenderState);
      ClientPlatform.get().afterNestedWorldRender(oldWorldRenderer, nestedRenderToken);
      SodiumInterface.invoker.switchContextWithCurrentWorldRenderer(newSodiumContext);
      ((IEMinecraftClient)client).ip_setWorldRenderer(oldWorldRenderer);
      client.world = oldWorld;
      ieGameRenderer.ip_setLightmapTextureManager(oldLightmap);
      client.player.noClip = oldNoClip;
      ieGameRenderer.ip_setDoRenderHand(oldDoRenderHand);
      ((IEParticleManager)client.particleManager).ip_setWorld(oldWorld);
      client.crosshairTarget = oldCrosshairTarget;
      ieGameRenderer.ip_setCamera(oldCamera);
      updateGlobalSettingsUniform(oldCamera);
      FogRendererContext.swappingManager.popSwapping();
      ((IEWorldRenderer)oldWorldRenderer).portal_setChunkInfoList(oldChunkInfoList);
      VisibleSectionDiscovery.returnList(newChunkInfoList);
      ieWorldRenderer.ip_setRenderBuffers(oldRenderBuffers);
      ((IEMinecraftClient)client).ip_setRenderBuffers(oldClientRenderBuffers);
      ((IESectionRenderDispatcher)worldRenderer.getChunkBuilder()).ip_setFixedBuffers(oldSectionRenderDispatcherFixedBuffers);
      ieGameRenderer.ip_setLevelRenderState(oldGameLevelRenderState);
      ieGameRenderer.ip_setSubmitNodeStorage(oldGameSubmitNodeStorage);
      ieGameRenderer.ip_setFeatureRenderDispatcher(oldGameFeatureRenderDispatcher);
      ieGameRenderer.ip_setFogRenderer(oldFogRenderer);
      ieWorldRenderer.ip_setLevelRenderState(oldWorldLevelRenderState);
      ieWorldRenderer.ip_setSubmitNodeStorage(oldWorldSubmitNodeStorage);
      ieWorldRenderer.ip_setFeatureRenderDispatcher(oldWorldFeatureRenderDispatcher);
      ieWorldRenderer.ip_setParticlesRenderState(oldParticlesRenderState);
      targets.mainFramebuffer = oldTargetMain;
      targets.translucentFramebuffer = oldTargetTranslucent;
      targets.itemEntityFramebuffer = oldTargetItemEntity;
      targets.particlesFramebuffer = oldTargetParticles;
      targets.weatherFramebuffer = oldTargetWeather;
      targets.cloudsFramebuffer = oldTargetClouds;
      targets.entityOutlineFramebuffer = oldTargetEntityOutline;
      returnContext(context);
      if (oldProjectionMatrix != null) {
         ieGameRenderer.ip_resetProjectionMatrix(oldProjectionMatrix, oldProjectionType);
      }

      RenderSystem.setShaderFog(oldShaderFog);
      RenderSystem.setShaderLights(oldShaderLights);
      RenderStates.currentFogColor = oldFogColor;
      IERenderSystem.ip_setModelViewStack(oldModelViewStack);
      IrisInterface.invoker.setPipeline(worldRenderer, irisPipeline);
      client.getEntityRenderDispatcher().configure(oldCamera, client.targetedEntity);
      client.getBlockEntityRenderDispatcher().configure(oldCamera);
      CHelper.checkGlError();
      client.chunkCullingEnabled = true;
   }

   @IPVanillaCopy
   private static void updateGlobalSettingsUniform(Camera camera) {
      ClientWorld level = client.world;
      client.gameRenderer
         .getGlobalSettings()
         .set(
            client.getWindow().getFramebufferWidth(),
            client.getWindow().getFramebufferHeight(),
            (Double)client.options.getGlintStrength().getValue(),
            level == null ? 0L : level.getTime(),
            client.getRenderTickCounter(),
            client.options.getMenuBackgroundBlurrinessValue(),
            camera,
            client.options.getTextureFiltering().getValue() == TextureFilteringMode.RGSS
         );
   }

   @IPVanillaCopy
   public static void resetFogState() {
      FogRenderer fogRenderer = ((IEGameRenderer)client.gameRenderer).ip_getFogRenderer();
      RenderSystem.setShaderFog(fogRenderer.getFogBuffer(FogType.WORLD));
   }

   public static void updateFogColor() {
   }

   @IPVanillaCopy
   public static void resetDiffuseLighting() {
      ClientWorld world = client.world;

      assert world != null;

      DiffuseLighting lighting = client.gameRenderer.getDiffuseLighting();
      lighting.updateLevelBuffer(world.getDimension().cardinalLightType());
      lighting.setShaderLights(Type.LEVEL);
   }

   public static class NestedRenderContext {
      @Nullable
      public final BufferBuilderStorage renderBuffers;
      public final OrderedRenderCommandQueueImpl submitNodeStorage;
      public final RenderDispatcher featureRenderDispatcher;
      public final WorldRenderState levelRenderState;
      public final SubmittableBatch particlesRenderState;

      NestedRenderContext(boolean useSecondaryRenderBuffers) {
         this.renderBuffers = useSecondaryRenderBuffers ? new BufferBuilderStorage(0) : null;
         BufferBuilderStorage buffers = this.renderBuffers != null ? this.renderBuffers : MyGameRenderer.client.getBufferBuilders();
         this.submitNodeStorage = new OrderedRenderCommandQueueImpl();
         this.featureRenderDispatcher = new RenderDispatcher(
            this.submitNodeStorage,
            MyGameRenderer.client.getBlockRenderManager(),
            buffers.getEntityVertexConsumers(),
            MyGameRenderer.client.getAtlasManager(),
            buffers.getOutlineVertexConsumers(),
            buffers.getEffectVertexConsumers(),
            MyGameRenderer.client.textRenderer
         );
         this.levelRenderState = new WorldRenderState();
         this.particlesRenderState = new SubmittableBatch();
      }

      void close() {
         this.featureRenderDispatcher.close();
      }
   }
}
