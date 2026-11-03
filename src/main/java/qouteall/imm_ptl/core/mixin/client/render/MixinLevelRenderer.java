package qouteall.imm_ptl.core.mixin.client.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderPass.RenderObject;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat.IndexType;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.DynamicUniforms.ChunkSectionsValue;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.client.render.BlockRenderLayerGroup;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.DefaultFramebufferSet;
import net.minecraft.client.render.FramePass;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.SectionRenderState;
import net.minecraft.client.render.SubmittableBatch;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WeatherRendering;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.DiffuseLighting.Type;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.render.chunk.AbstractChunkRenderData;
import net.minecraft.client.render.chunk.Buffers;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.render.chunk.ChunkRenderData;
import net.minecraft.client.render.chunk.ChunkBuilder.BuiltChunk;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import net.minecraft.client.render.command.RenderDispatcher;
import net.minecraft.client.render.debug.DebugRenderer;
import net.minecraft.client.render.debug.GameTestDebugRenderer;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.gizmo.GizmoDrawerImpl;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.render.state.WeatherRenderState;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.profiler.Profilers;
import net.minecraft.world.World;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface;
import qouteall.imm_ptl.core.compat.sodium_compatibility.SodiumInterface;
import qouteall.imm_ptl.core.ducks.IEWorldRenderer;
import qouteall.imm_ptl.core.miscellaneous.IPVanillaCopy;
import qouteall.imm_ptl.core.render.CrossPortalEntityRenderer;
import qouteall.imm_ptl.core.render.FrontClipping;
import qouteall.imm_ptl.core.render.ImmPtlViewArea;
import qouteall.imm_ptl.core.render.MyGameRenderer;
import qouteall.imm_ptl.core.render.MyRenderHelper;
import qouteall.imm_ptl.core.render.VisibleSectionDiscovery;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.q_misc_util.Helper;

@Mixin({WorldRenderer.class})
public abstract class MixinLevelRenderer implements IEWorldRenderer {
   @Shadow
   private ClientWorld world;
   @Shadow
   @Final
   private EntityRenderManager entityRenderManager;
   @Shadow
   @Final
   private MinecraftClient client;
   @Shadow
   private BuiltChunkStorage chunks;
   @Mutable
   @Shadow
   @Final
   private BufferBuilderStorage bufferBuilders;
   @Shadow
   private int viewDistance;
   @Shadow
   @Nullable
   private ChunkBuilder chunkBuilder;
   @Shadow
   @Final
   @Mutable
   private ObjectArrayList<BuiltChunk> builtChunks;
   @Shadow
   @Final
   @Mutable
   private WorldRenderState worldRenderState;
   @Shadow
   @Final
   @Mutable
   private OrderedRenderCommandQueueImpl entityRenderCommandQueue;
   @Shadow
   @Final
   @Mutable
   private RenderDispatcher entityRenderDispatcher;
   @Shadow
   @Final
   @Mutable
   private SubmittableBatch particleBatch;
   @Shadow
   @Final
   private DefaultFramebufferSet framebufferSet;
   @Unique
   private int ip_nestedRenderSerialAtPrepare = 0;

   @Shadow
   public abstract boolean isRenderingReady(BlockPos var1);

   @Inject(
      method = {"renderBlockLayers"},
      at = {@At("RETURN")}
   )
   private void onPrepareChunkRenders(Matrix4fc modelView, double camX, double camY, double camZ, CallbackInfoReturnable<SectionRenderState> cir) {
      this.ip_nestedRenderSerialAtPrepare = MyGameRenderer.nestedRenderSerial;
   }

   @Unique
   @IPVanillaCopy
   private SectionRenderState ip_prepareChunkRenders(Matrix4fc modelView, double camX, double camY, double camZ) {
      ObjectListIterator<BuiltChunk> iterator = this.builtChunks.listIterator(0);
      EnumMap<BlockRenderLayer, List<RenderObject<GpuBufferSlice[]>>> drawsPerLayer = new EnumMap<>(BlockRenderLayer.class);
      int maxIndicesRequired = 0;

      for (BlockRenderLayer layer : BlockRenderLayer.values()) {
         drawsPerLayer.put(layer, new ArrayList<>());
      }

      List<ChunkSectionsValue> sectionInfos = new ArrayList<>();
      GpuTextureView atlasView = this.client.getTextureManager().getTexture(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).getGlTextureView();
      int atlasWidth = atlasView.getWidth(0);
      int atlasHeight = atlasView.getHeight(0);

      while (iterator.hasNext()) {
         BuiltChunk section = (BuiltChunk)iterator.next();
         AbstractChunkRenderData sectionMesh = section.getCurrentRenderData();
         BlockPos origin = section.getOrigin();
         long now = Util.getMeasuringTimeMs();
         int infoIndex = -1;

         for (BlockRenderLayer layer : BlockRenderLayer.values()) {
            Buffers buffers = sectionMesh.getBuffersForLayer(layer);
            if (buffers != null) {
               if (infoIndex == -1) {
                  infoIndex = sectionInfos.size();
                  sectionInfos.add(
                     new ChunkSectionsValue(
                        new Matrix4f(modelView), origin.getX(), origin.getY(), origin.getZ(), section.method_76298(now), atlasWidth, atlasHeight
                     )
                  );
               }

               GpuBuffer indexBuffer;
               IndexType indexType;
               if (buffers.getIndexBuffer() == null) {
                  if (buffers.getIndexCount() > maxIndicesRequired) {
                     maxIndicesRequired = buffers.getIndexCount();
                  }

                  indexBuffer = null;
                  indexType = null;
               } else {
                  indexBuffer = buffers.getIndexBuffer();
                  indexType = buffers.getIndexType();
               }

               int sliceIndex = infoIndex;
               drawsPerLayer.get(layer)
                  .add(
                     new RenderObject(
                        0,
                        buffers.getVertexBuffer(),
                        indexBuffer,
                        indexType,
                        0,
                        buffers.getIndexCount(),
                        (slicesx, uploader) -> uploader.upload("ChunkSection", slicesx[sliceIndex])
                     )
                  );
            }
         }
      }

      GpuBufferSlice[] slices = RenderSystem.getDynamicUniforms().writeChunkSections(sectionInfos.toArray(new ChunkSectionsValue[0]));
      return new SectionRenderState(atlasView, drawsPerLayer, maxIndicesRequired, slices);
   }

   @WrapOperation(
      method = {"lambda$addMainPass$1", "method_62214"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/SectionRenderState;renderSection(Lnet/minecraft/client/render/BlockRenderLayerGroup;Lnet/minecraft/client/gl/GpuSampler;)V"
      )},
      require = 1
   )
   private void wrapRenderSectionLayerGroup(
      SectionRenderState instance,
      BlockRenderLayerGroup group,
      GpuSampler sampler,
      Operation<Void> original,
      @Local(argsOnly = true) Matrix4f modelView,
      @Local LocalRef<SectionRenderState> chunkSectionsToRender
   ) {
      if (MyGameRenderer.nestedRenderSerial != this.ip_nestedRenderSerialAtPrepare && !SodiumInterface.invoker.isSodiumPresent()) {
         Vec3d cameraPos = this.worldRenderState.cameraRenderState.pos;
         instance = this.ip_prepareChunkRenders(modelView, cameraPos.x, cameraPos.y, cameraPos.z);
         chunkSectionsToRender.set(instance);
         this.ip_nestedRenderSerialAtPrepare = MyGameRenderer.nestedRenderSerial;
      }

      this.onBeforeRenderingLayer(modelView);
      RenderStates.isRenderingTerrainLayer = true;

      try {
         original.call(new Object[]{instance, group, sampler});
      } finally {
         RenderStates.isRenderingTerrainLayer = false;
      }

      this.onAfterRenderingLayer();
      if (group == BlockRenderLayerGroup.OPAQUE) {
         this.onAfterCutoutRendering(modelView);
      }
   }

   private void onAfterCutoutRendering(Matrix4f modelView) {
      CrossPortalEntityRenderer.onBeginRenderingEntitiesAndBlockEntities(modelView);
   }

   private void onBeforeRenderingLayer(Matrix4f modelView) {
      if (PortalRendering.isRendering()) {
         FrontClipping.setupInnerClipping(PortalRendering.getActiveClippingPlane(), modelView, -0.01);
         if (PortalRendering.isRenderingOddNumberOfMirrors()) {
            MyRenderHelper.applyMirrorFaceCulling();
         }

         if (IPGlobal.enableDepthClampForPortalRendering) {
            CHelper.enableDepthClamp();
         }
      }
   }

   private void onAfterRenderingLayer() {
      if (PortalRendering.isRendering()) {
         FrontClipping.disableClipping();
         MyRenderHelper.recoverFaceCulling();
         if (IPGlobal.enableDepthClampForPortalRendering) {
            CHelper.disableDepthClamp();
         }
      }
   }

   @Inject(
      method = {"lambda$addMainPass$1", "method_62214"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/TexturedRenderLayers;getItemTranslucentCull()Lnet/minecraft/client/render/RenderLayer;"
      )},
      require = 1
   )
   private void onMyBeforeTranslucentRendering(CallbackInfo ci, @Local(argsOnly = true) Matrix4f modelView) {
      IPCGlobal.renderer.onBeforeTranslucentRendering(modelView);
      MyGameRenderer.updateFogColor();
      MyGameRenderer.resetFogState();
      MyGameRenderer.resetDiffuseLighting();
      FrontClipping.disableClipping();
   }

   @Inject(
      method = {"lambda$addMainPass$1", "method_62214"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;drawCurrentLayer()V",
         ordinal = 0,
         shift = Shift.AFTER
      )},
      require = 1
   )
   @IPVanillaCopy
   private void onEndRenderingEntities(CallbackInfo ci, @Local MatrixStack poseStack) {
      CrossPortalEntityRenderer.onEndRenderingEntitiesAndBlockEntities(poseStack);
   }

   @WrapOperation(
      method = {"lambda$addMainPass$1", "method_62214"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/WorldRenderer;collectGizmos()V"
      )},
      require = 1
   )
   private void wrapFinalizeGizmoCollection(WorldRenderer instance, Operation<Void> original) {
      if (!WorldRenderInfo.isRendering()) {
         original.call(new Object[]{instance});
      }
   }

   @WrapOperation(
      method = {"lambda$addMainPass$1", "method_62214"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/gizmo/GizmoDrawerImpl;draw(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/render/state/CameraRenderState;Lorg/joml/Matrix4f;)V"
      )},
      require = 1
   )
   private void wrapRenderGizmos(
      GizmoDrawerImpl instance,
      MatrixStack poseStack,
      VertexConsumerProvider bufferSource,
      CameraRenderState cameraRenderState,
      Matrix4f projection,
      Operation<Void> original
   ) {
      if (!WorldRenderInfo.isRendering()) {
         original.call(new Object[]{instance, poseStack, bufferSource, cameraRenderState, projection});
      }
   }

   @WrapOperation(
      method = {"render"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/debug/DebugRenderer;render(Lnet/minecraft/client/render/Frustum;DDDF)V"
      )}
   )
   private void wrapEmitDebugGizmos(DebugRenderer instance, Frustum frustum, double x, double y, double z, float partialTick, Operation<Void> original) {
      if (!WorldRenderInfo.isRendering()) {
         original.call(new Object[]{instance, frustum, x, y, z, partialTick});
      }
   }

   @WrapOperation(
      method = {"render"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/debug/GameTestDebugRenderer;render()V"
      )}
   )
   private void wrapEmitGameTestGizmos(GameTestDebugRenderer instance, Operation<Void> original) {
      if (!WorldRenderInfo.isRendering()) {
         original.call(new Object[]{instance});
      }
   }

   @Inject(
      method = {"renderLateDebug"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onAddLateDebugPass(CallbackInfo ci) {
      if (WorldRenderInfo.isRendering()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"render"},
      at = {@At("RETURN")}
   )
   private void onAfterTranslucentRendering(
      ObjectAllocator resourceAllocator,
      RenderTickCounter deltaTracker,
      boolean renderBlockOutline,
      Camera camera,
      Matrix4f modelView,
      Matrix4f projection,
      Matrix4f cullingProjection,
      GpuBufferSlice fog,
      Vector4f fogColor,
      boolean renderSky,
      CallbackInfo ci
   ) {
      IPCGlobal.renderer.onAfterTranslucentRendering(modelView);
      this.client.gameRenderer.getDiffuseLighting().setShaderLights(Type.LEVEL);
   }

   @WrapOperation(
      method = {"render"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/FramePass;setRenderer(Ljava/lang/Runnable;)V",
         ordinal = 0
      )}
   )
   private void wrapClearPass(FramePass pass, Runnable runnable, Operation<Void> original) {
      original.call(new Object[]{pass, (Runnable)() -> {
         if (!IPCGlobal.renderer.replaceFrameBufferClearing()) {
            runnable.run();
         }
      }});
   }

   @Redirect(
      method = {"render"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/world/ClientWorld;runQueuedChunkUpdates()V"
      )}
   )
   private void redirectRunQueuedChunkUpdates(ClientWorld world) {
      ClientWorldLoader.withSwitchedWorld(world, world::runQueuedChunkUpdates);
   }

   @Inject(
      method = {"renderSky(Lnet/minecraft/client/render/FrameGraphBuilder;Lnet/minecraft/client/render/Camera;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V", "addSkyPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/Camera;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Matrix4f;)V"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 1
   )
   private void onRenderSkyBegin(CallbackInfo ci) {
      if (WorldRenderInfo.isRendering() && !WorldRenderInfo.getTopRenderInfo().doRenderSky && !IrisInterface.invoker.isShaders()) {
         ci.cancel();
      }
   }

   @WrapOperation(
      method = {"renderSky(Lnet/minecraft/client/render/FrameGraphBuilder;Lnet/minecraft/client/render/Camera;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V", "addSkyPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/Camera;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Matrix4f;)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/FramePass;setRenderer(Ljava/lang/Runnable;)V"
      )},
      require = 1
   )
   private void wrapSkyPass(FramePass pass, Runnable runnable, Operation<Void> original) {
      original.call(new Object[]{pass, (Runnable)() -> {
         if (PortalRendering.isRenderingOddNumberOfMirrors()) {
            MyRenderHelper.applyMirrorFaceCulling();
         }

         runnable.run();
         MyRenderHelper.recoverFaceCulling();
      }});
   }

   @WrapOperation(
      method = {"lambda$addWeatherPass$4", "method_62216"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/WeatherRendering;renderPrecipitation(Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/client/render/state/WeatherRenderState;)V"
      )},
      require = 1
   )
   private void wrapRenderWeather(
      WeatherRendering instance, VertexConsumerProvider bufferSource, Vec3d cameraPos, WeatherRenderState weatherRenderState, Operation<Void> original
   ) {
      if (PortalRendering.isRendering()) {
         FrontClipping.setupInnerClipping(PortalRendering.getActiveClippingPlane(), new Matrix4f(RenderSystem.getModelViewMatrix()), 0.0);
         RenderStates.isRenderingPortalWeather = true;
      }

      original.call(new Object[]{instance, bufferSource, cameraPos, weatherRenderState});
      if (PortalRendering.isRendering()) {
         FrontClipping.disableClipping();
         RenderStates.isRenderingPortalWeather = false;
      }
   }

   @Inject(
      method = {"updateCamera"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onSetupTerrainBegin(Camera camera, Frustum frustum, boolean spectator, CallbackInfo ci) {
      if (WorldRenderInfo.isRendering() && this.world.getRegistryKey() != RenderStates.originalPlayerDimension) {
         this.chunkBuilder.setCameraPosition(camera.getCameraPos());
      }

      if (this.ip_allowOverrideTerrainSetup() && WorldRenderInfo.isRendering()) {
         Profilers.get().push("ip_terrain_setup");
         VisibleSectionDiscovery.discoverVisibleSections(
            this.world, (ImmPtlViewArea)this.chunks, camera, new Frustum(frustum).coverBoxAroundSetPosition(8), this.builtChunks
         );
         Profilers.get().pop();
         ci.cancel();
      }
   }

   private boolean ip_allowOverrideTerrainSetup() {
      return !SodiumInterface.invoker.isSodiumPresent() && !IrisInterface.invoker.isRenderingShadowMap();
   }

   @Inject(
      method = {"updateCamera"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void onSetupTerrainEnd(Camera camera, Frustum frustum, boolean spectator, CallbackInfo ci) {
      if (!WorldRenderInfo.isRendering() && this.ip_allowOverrideTerrainSetup()) {
         if (MyGameRenderer.vanillaTerrainSetupOverride > 0) {
            MyGameRenderer.vanillaTerrainSetupOverride--;
            Profilers.get().push("ip_terrain_setup");
            VisibleSectionDiscovery.discoverVisibleSections(
               this.world, (ImmPtlViewArea)this.chunks, camera, new Frustum(frustum).coverBoxAroundSetPosition(8), this.builtChunks
            );
            Profilers.get().pop();
         } else if (IPGlobal.alwaysOverrideTerrainSetup) {
            Profilers.get().push("ip_terrain_setup_debug");
            VisibleSectionDiscovery.discoverVisibleSections(
               this.world, (ImmPtlViewArea)this.chunks, camera, new Frustum(frustum).coverBoxAroundSetPosition(8), this.builtChunks
            );
            Profilers.get().pop();
         }
      }
   }

   @ModifyVariable(
      method = {"updateCamera"},
      at = {@At("HEAD")},
      argsOnly = true,
      ordinal = 0
   )
   private boolean modifyIsSpectator(boolean value) {
      return WorldRenderInfo.isRendering() ? true : value;
   }

   @Redirect(
      method = {"reload()V"},
      at = {@At(
         value = "NEW",
         target = "(Lnet/minecraft/client/render/chunk/ChunkBuilder;Lnet/minecraft/world/World;ILnet/minecraft/client/render/WorldRenderer;)Lnet/minecraft/client/render/BuiltChunkStorage;"
      )}
   )
   private BuiltChunkStorage redirectConstructingBuildChunkStorage(ChunkBuilder chunkBuilder_1, World world_1, int int_1, WorldRenderer worldRenderer_1) {
      return (BuiltChunkStorage)(IPCGlobal.useHackedChunkRenderDispatcher
         ? new ImmPtlViewArea(chunkBuilder_1, world_1, int_1, worldRenderer_1)
         : new BuiltChunkStorage(chunkBuilder_1, world_1, int_1, worldRenderer_1));
   }

   @Inject(
      method = {"fillEntityRenderStates"},
      at = {@At("HEAD")}
   )
   private void onBeginExtractingEntities(Camera camera, Frustum frustum, RenderTickCounter deltaTracker, WorldRenderState levelRenderState, CallbackInfo ci) {
      CrossPortalEntityRenderer.onBeginExtractingEntities();
   }

   @WrapOperation(
      method = {"fillEntityRenderStates"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/entity/EntityRenderManager;shouldRender(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/Frustum;DDD)Z"
      )}
   )
   private boolean wrapShouldRenderForDeferring(
      EntityRenderManager dispatcher,
      Entity entity,
      Frustum frustum,
      double cameraX,
      double cameraY,
      double cameraZ,
      Operation<Boolean> original,
      @Local(argsOnly = true) Camera camera
   ) {
      boolean result = (Boolean)original.call(new Object[]{dispatcher, entity, frustum, cameraX, cameraY, cameraZ});
      if (result && CrossPortalEntityRenderer.shouldDeferEntityRendering(entity) && this.ip_vanillaWouldExtract(entity, camera)) {
         CrossPortalEntityRenderer.deferEntity(entity);
         return false;
      } else {
         return result;
      }
   }

   @WrapOperation(
      method = {"fillEntityRenderStates"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/entity/Entity;hasPassengerDeep(Lnet/minecraft/entity/Entity;)Z"
      )}
   )
   private boolean wrapHasIndirectPassengerForDeferring(Entity entity, Entity passenger, Operation<Boolean> original) {
      return CrossPortalEntityRenderer.isLastDeferredEntity(entity) ? false : (Boolean)original.call(new Object[]{entity, passenger});
   }

   @IPVanillaCopy
   private boolean ip_vanillaWouldExtract(Entity entity, Camera camera) {
      BlockPos blockPos = entity.getBlockPos();
      return (this.world.isOutOfHeightLimit(blockPos.getY()) || this.isRenderingReady(blockPos))
         && (
            entity != camera.getFocusedEntity()
               || camera.isThirdPerson()
               || camera.getFocusedEntity() instanceof LivingEntity livingEntity && livingEntity.isSleeping()
         )
         && (!(entity instanceof ClientPlayerEntity) || camera.getFocusedEntity() == entity);
   }

   @Inject(
      method = {"reload()V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onReloadStarted(CallbackInfo ci) {
      if (WorldRenderInfo.isRendering()) {
         Helper.log("world renderer reloading cancelled during portal rendering");
         ci.cancel();
      }
   }

   @Inject(
      method = {"reload()V"},
      at = {@At("TAIL")}
   )
   private void onReloadFinished(CallbackInfo ci) {
      WorldRenderer this_ = (WorldRenderer)this;
      if (!ClientWorldLoader.getIsCreatingClientWorld()) {
         Validate.isTrue(MinecraftClient.getInstance().worldRenderer == this_);
         ClientWorldLoader._onWorldRendererReloaded();
      }
   }

   @Inject(
      method = {"isRenderingReady"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onIsChunkCompiled(BlockPos blockPos, CallbackInfoReturnable<Boolean> cir) {
      if (PortalRendering.isRendering() && !SodiumInterface.invoker.isSodiumPresent() && this.chunks instanceof ImmPtlViewArea immPtlViewArea) {
         cir.setReturnValue(this.ip_isChunkCompiled(immPtlViewArea, blockPos));
      }
   }

   private boolean ip_isChunkCompiled(ImmPtlViewArea immPtlViewArea, BlockPos blockPos) {
      ChunkSectionPos sectionPos = ChunkSectionPos.from(blockPos);
      BuiltChunk renderChunk = immPtlViewArea.rawGet(sectionPos.getSectionX(), sectionPos.getSectionY(), sectionPos.getSectionZ());
      return renderChunk != null && renderChunk.currentRenderData.get() != ChunkRenderData.HIDDEN;
   }

   @Override
   public EntityRenderManager ip_getEntityRenderDispatcher() {
      return this.entityRenderManager;
   }

   @Override
   public BuiltChunkStorage ip_getBuiltChunkStorage() {
      return this.chunks;
   }

   @Override
   public void ip_renderSingleEntity(
      Entity entity, double cameraX, double cameraY, double cameraZ, float partialTick, MatrixStack matrixStack, Immediate bufferSource
   ) {
      EntityRenderState renderState = this.entityRenderManager.getAndUpdateRenderState(entity, partialTick);
      this.entityRenderManager
         .render(
            renderState,
            this.worldRenderState.cameraRenderState,
            renderState.x - cameraX,
            renderState.y - cameraY,
            renderState.z - cameraZ,
            matrixStack,
            this.entityRenderCommandQueue
         );
      this.entityRenderDispatcher.render();
   }

   @Override
   public BufferBuilderStorage ip_getRenderBuffers() {
      return this.bufferBuilders;
   }

   @Override
   public void ip_setRenderBuffers(BufferBuilderStorage arg) {
      this.bufferBuilders = arg;
   }

   @Override
   public void portal_fullyDispose() {
      this.world = null;
   }

   @Override
   public void portal_setChunkInfoList(ObjectArrayList<BuiltChunk> arg) {
      this.builtChunks = arg;
   }

   @Override
   public ObjectArrayList<BuiltChunk> portal_getChunkInfoList() {
      return this.builtChunks;
   }

   @Override
   public WorldRenderState ip_getLevelRenderState() {
      return this.worldRenderState;
   }

   @Override
   public void ip_setLevelRenderState(WorldRenderState arg) {
      this.worldRenderState = arg;
   }

   @Override
   public OrderedRenderCommandQueueImpl ip_getSubmitNodeStorage() {
      return this.entityRenderCommandQueue;
   }

   @Override
   public void ip_setSubmitNodeStorage(OrderedRenderCommandQueueImpl arg) {
      this.entityRenderCommandQueue = arg;
   }

   @Override
   public RenderDispatcher ip_getFeatureRenderDispatcher() {
      return this.entityRenderDispatcher;
   }

   @Override
   public void ip_setFeatureRenderDispatcher(RenderDispatcher arg) {
      this.entityRenderDispatcher = arg;
   }

   @Override
   public SubmittableBatch ip_getParticlesRenderState() {
      return this.particleBatch;
   }

   @Override
   public void ip_setParticlesRenderState(SubmittableBatch arg) {
      this.particleBatch = arg;
   }

   @Override
   public DefaultFramebufferSet ip_getTargets() {
      return this.framebufferSet;
   }
}
