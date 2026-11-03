package qouteall.imm_ptl.core.mixin.client.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RawProjectionMatrix;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import net.minecraft.client.render.command.RenderDispatcher;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.profiler.Profilers;
import org.joml.Matrix4f;
import org.joml.Quaternionfc;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.ducks.IEGameRenderer;
import qouteall.imm_ptl.core.portal.animation.ClientPortalAnimationManagement;
import qouteall.imm_ptl.core.portal.animation.StableClientTimer;
import qouteall.imm_ptl.core.render.CrossPortalViewRendering;
import qouteall.imm_ptl.core.render.GuiPortalRendering;
import qouteall.imm_ptl.core.render.MyGameRenderer;
import qouteall.imm_ptl.core.render.MyRenderHelper;
import qouteall.imm_ptl.core.render.TransformationManager;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.renderer.PortalRenderer;
import qouteall.imm_ptl.core.teleportation.ClientTeleportationManager;

@Mixin({GameRenderer.class})
public abstract class MixinGameRenderer implements IEGameRenderer {
   @Shadow
   @Final
   @Mutable
   private LightmapTextureManager lightmapTextureManager;
   @Shadow
   @Final
   @Mutable
   private Camera camera;
   @Shadow
   @Final
   private MinecraftClient client;
   @Shadow
   @Final
   private static Logger LOGGER;
   @Shadow
   @Final
   @Mutable
   private WorldRenderState worldRenderState;
   @Shadow
   @Final
   @Mutable
   private OrderedRenderCommandQueueImpl orderedRenderCommandQueue;
   @Shadow
   @Final
   @Mutable
   private RenderDispatcher entityRenderDispatcher;
   @Shadow
   @Final
   @Mutable
   private FogRenderer fogRenderer;
   @Shadow
   @Final
   private RawProjectionMatrix worldProjectionMatrix;
   @Unique
   private boolean ip_doRenderHand = true;
   private static boolean portal_isRenderingHand = false;

   @Shadow
   protected abstract void bobView(MatrixStack var1, float var2);

   @Inject(
      method = {"render"},
      at = {@At("HEAD")}
   )
   private void onFarBeforeRendering(RenderTickCounter deltaTracker, boolean renderWorldIn, CallbackInfo ci) {
      Profilers.get().push("ip_pre_total_render");
      IPGlobal.PRE_TOTAL_RENDER_TASK_LIST.processTasks();
      Profilers.get().pop();
      if (this.client.world != null) {
         if (renderWorldIn) {
            Profilers.get().push("ip_pre_render");
            float partialTick = deltaTracker.getTickProgress(true);
            RenderStates.updatePreRenderInfo(partialTick);
            StableClientTimer.update(this.client.world.getTime(), partialTick);
            ClientPortalAnimationManagement.update();
            ClientTeleportationManager.manageTeleportation(false);
            IPGlobal.PRE_GAME_RENDER_EVENT.invoker().run();
            if (IPCGlobal.earlyRemoteUpload) {
               MyRenderHelper.earlyRemoteUpload();
            }

            Profilers.get().pop();
            RenderStates.frameIndex++;
         }
      }
   }

   @Inject(
      method = {"render"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/GameRenderer;renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V"
      )}
   )
   private void onBeforeRenderingCenter(RenderTickCounter deltaTracker, boolean bl, CallbackInfo ci) {
      PortalRenderer.switchToCorrectRenderer();
      IPCGlobal.renderer.prepareRendering();
   }

   @Inject(
      method = {"render"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/GameRenderer;renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V",
         shift = Shift.AFTER
      )}
   )
   private void onAfterRenderingCenter(RenderTickCounter deltaTracker, boolean bl, CallbackInfo ci) {
      IPCGlobal.renderer.finishRendering();
      RenderStates.onTotalRenderEnd();
      GuiPortalRendering._onGameRenderEnd();
      MyGameRenderer.onFrameEnd();
      if (IPCGlobal.lateClientLightUpdate) {
         Profilers.get().push("ip_late_update_light");
         MyRenderHelper.lateUpdateLight();
         Profilers.get().pop();
      }
   }

   @Redirect(
      method = {"render"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/GameRenderer;renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V"
      )}
   )
   private void redirectRenderingWorld(GameRenderer gameRenderer, RenderTickCounter deltaTracker) {
      if (!CrossPortalViewRendering.renderCrossPortalView()) {
         gameRenderer.renderWorld(deltaTracker);
      }
   }

   @Inject(
      method = {"renderWorld"},
      at = {@At("TAIL")}
   )
   private void onRenderCenterEnded(RenderTickCounter deltaTracker, CallbackInfo ci) {
      IPCGlobal.renderer.onHandRenderingEnded();
   }

   @WrapOperation(
      method = {"renderWorld"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/util/ObjectAllocator;Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V"
      )}
   )
   private void wrapRenderLevel(
      WorldRenderer instance,
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
      Operation<Void> original
   ) {
      original.call(
         new Object[]{
            instance, resourceAllocator, deltaTracker, renderBlockOutline, camera, modelView, projection, cullingProjection, fog, fogColor, renderSky
         }
      );
      IPCGlobal.renderer.onBeforeHandRendering(modelView);
   }

   @Inject(
      method = {"Lnet/minecraft/client/render/GameRenderer;onResized(II)V"},
      at = {@At("RETURN")}
   )
   private void onOnResized(int int_1, int int_2, CallbackInfo ci) {
      if (ClientWorldLoader.getIsInitialized()) {
         ClientWorldLoader.WORLD_RENDERER_MAP
            .values()
            .stream()
            .filter(worldRenderer -> worldRenderer != this.client.worldRenderer)
            .forEach(worldRenderer -> worldRenderer.onResized(int_1, int_2));
      }
   }

   @Inject(
      method = {"renderHand"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onRenderHandBegins(float partialTick, boolean sleeping, Matrix4f modelView, CallbackInfo ci) {
      if (!this.ip_doRenderHand) {
         ci.cancel();
      } else {
         portal_isRenderingHand = true;
      }
   }

   @Inject(
      method = {"renderHand"},
      at = {@At("RETURN")}
   )
   private void onRenderHandEnds(float partialTick, boolean sleeping, Matrix4f modelView, CallbackInfo ci) {
      portal_isRenderingHand = false;
   }

   @WrapOperation(
      method = {"renderWorld"},
      at = {@At(
         value = "INVOKE",
         target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearDepthTexture(Lcom/mojang/blaze3d/textures/GpuTexture;D)V"
      )}
   )
   private void wrapClearDepthForHand(CommandEncoder encoder, GpuTexture depthTexture, double depth, Operation<Void> original) {
      if (this.ip_doRenderHand) {
         original.call(new Object[]{encoder, depthTexture, depth});
      }
   }

   @WrapOperation(
      method = {"renderWorld"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/hud/InGameOverlayRenderer;renderOverlays(ZFLnet/minecraft/client/render/command/OrderedRenderCommandQueue;)V"
      )}
   )
   private void wrapRenderScreenEffect(
      InGameOverlayRenderer instance, boolean sleeping, float partialTick, OrderedRenderCommandQueue collector, Operation<Void> original
   ) {
      if (this.ip_doRenderHand) {
         original.call(new Object[]{instance, sleeping, partialTick, collector});
      }
   }

   @ModifyArg(
      method = {"bobView"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/util/math/MatrixStack;translate(FFF)V"
      )},
      index = 0
   )
   private float modifyBobViewTranslateX(float f) {
      return portal_isRenderingHand ? f : (float)(f * RenderStates.getViewBobbingOffsetMultiplier());
   }

   @ModifyArg(
      method = {"bobView"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/util/math/MatrixStack;translate(FFF)V"
      )},
      index = 1
   )
   private float modifyBobViewTranslateY(float f) {
      return portal_isRenderingHand ? f : (float)(f * RenderStates.getViewBobbingOffsetMultiplier());
   }

   @ModifyArg(
      method = {"bobView"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/util/math/MatrixStack;translate(FFF)V"
      )},
      index = 2
   )
   private float modifyBobViewTranslateZ(float f) {
      return portal_isRenderingHand ? f : (float)(f * RenderStates.getViewBobbingOffsetMultiplier());
   }

   @Redirect(
      method = {"renderWorld"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/GameRenderer;getBasicProjectionMatrix(F)Lorg/joml/Matrix4f;",
         ordinal = 0
      )}
   )
   private Matrix4f redirectGetBasicProjectionMatrix(GameRenderer instance, float fov) {
      if (PortalRendering.isRendering()) {
         if (RenderStates.basicProjectionMatrix != null) {
            return new Matrix4f(RenderStates.basicProjectionMatrix);
         }

         LOGGER.error("[iPortal] Projection matrix state abnormal");
      }

      Matrix4f result = instance.getBasicProjectionMatrix(fov);
      RenderStates.basicProjectionMatrix = new Matrix4f(result);
      return result;
   }

   @WrapOperation(
      method = {"renderWorld"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/RawProjectionMatrix;set(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"
      )}
   )
   private GpuBufferSlice wrapUploadProjectionMatrix(RawProjectionMatrix instance, Matrix4f projectionMatrix, Operation<GpuBufferSlice> original) {
      RenderStates.projectionMatrix = new Matrix4f(projectionMatrix);
      return (GpuBufferSlice)original.call(new Object[]{instance, projectionMatrix});
   }

   @WrapOperation(
      method = {"renderWorld"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/fog/FogRenderer;applyFog(Lnet/minecraft/client/render/Camera;ILnet/minecraft/client/render/RenderTickCounter;FLnet/minecraft/client/world/ClientWorld;)Lorg/joml/Vector4f;"
      )}
   )
   private Vector4f wrapSetupFog(
      FogRenderer instance,
      Camera camera,
      int renderDistance,
      RenderTickCounter deltaTracker,
      float darkenWorldAmount,
      ClientWorld level,
      Operation<Vector4f> original
   ) {
      Vector4f color = (Vector4f)original.call(new Object[]{instance, camera, renderDistance, deltaTracker, darkenWorldAmount, level});
      RenderStates.currentFogColor = new Vec3d(color.x, color.y, color.z);
      return color;
   }

   @WrapOperation(
      method = {"renderWorld"},
      at = {@At(
         value = "INVOKE",
         target = "Lorg/joml/Matrix4f;rotation(Lorg/joml/Quaternionfc;)Lorg/joml/Matrix4f;",
         remap = false
      )}
   )
   private Matrix4f wrapCameraTransformation(Matrix4f instance, Quaternionfc quat, Operation<Matrix4f> original) {
      Matrix4f r = (Matrix4f)original.call(new Object[]{instance, quat});
      return TransformationManager.processTransformation(this.camera, r);
   }

   @Override
   public void ip_setLightmapTextureManager(LightmapTextureManager manager) {
      this.lightmapTextureManager = manager;
   }

   @Override
   public boolean ip_getDoRenderHand() {
      return this.ip_doRenderHand;
   }

   @Override
   public void ip_setDoRenderHand(boolean cond) {
      this.ip_doRenderHand = cond;
   }

   @Override
   public void ip_setCamera(Camera camera_) {
      this.camera = camera_;
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
      return this.orderedRenderCommandQueue;
   }

   @Override
   public void ip_setSubmitNodeStorage(OrderedRenderCommandQueueImpl arg) {
      this.orderedRenderCommandQueue = arg;
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
   public FogRenderer ip_getFogRenderer() {
      return this.fogRenderer;
   }

   @Override
   public void ip_setFogRenderer(FogRenderer arg) {
      this.fogRenderer = arg;
   }

   @Override
   public void ip_resetProjectionMatrix(Matrix4f matrix, ProjectionType projectionType) {
      RenderSystem.setProjectionMatrix(this.worldProjectionMatrix.set(matrix), projectionType);
      RenderStates.projectionMatrix = new Matrix4f(matrix);
   }
}
