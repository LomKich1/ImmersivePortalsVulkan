package qouteall.imm_ptl.core.render.renderer;

import qouteall.imm_ptl.core.compat.RenderBackendCompat;
import com.mojang.blaze3d.opengl.GlStateManager;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.compat.IPModInfoChecking;


import qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface;

import qouteall.imm_ptl.core.portal.Mirror;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.global_portals.GlobalPortalStorage;
import qouteall.imm_ptl.core.render.MyGameRenderer;
import qouteall.imm_ptl.core.render.MyRenderHelper;
import qouteall.imm_ptl.core.render.TransformationManager;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.platform.event.Event;
import qouteall.q_misc_util.platform.event.EventFactory;

public abstract class PortalRenderer {
   public static final Event<Predicate<Portal>> PORTAL_RENDERING_PREDICATE = EventFactory.createArrayBacked(Predicate.class, listeners -> portal -> {
      for (Predicate<Portal> listener : listeners) {
         if (!listener.test(portal)) {
            return false;
         }
      }

      return true;
   });
   public static final MinecraftClient client = MinecraftClient.getInstance();
   private static boolean fabulousWarned = false;

   public abstract void onBeforeTranslucentRendering(Matrix4f var1);

   public abstract void onAfterTranslucentRendering(Matrix4f var1);

   public abstract void onHandRenderingEnded();

   public void onBeforeHandRendering(Matrix4f modelView) {
   }

   public abstract void prepareRendering();

   public abstract void finishRendering();

   public abstract void renderPortalInEntityRenderer(Portal var1);

   public abstract boolean replaceFrameBufferClearing();

   protected List<Portal> getPortalsToRender(Matrix4f modelView) {
      Supplier<Frustum> frustumSupplier = Helper.cached(() -> {
         Frustum frustum = new Frustum(modelView, MyRenderHelper.getProjectionMatrix());
         Vec3d cameraPos = client.gameRenderer.getCamera().getCameraPos();
         frustum.setPosition(cameraPos.x, cameraPos.y, cameraPos.z);
         return frustum;
      });
      ObjectArrayList<Portal> renderables = new ObjectArrayList();
      ClientWorld world = client.world;

      assert world != null;

      for (Portal globalPortal : GlobalPortalStorage.getGlobalPortals(world)) {
         if (!shouldSkipRenderingPortal(globalPortal, frustumSupplier)) {
            renderables.add(globalPortal);
         }
      }

      world.getEntities().forEach(e -> {
         if (e instanceof Portal portal && !shouldSkipRenderingPortal(portal, frustumSupplier)) {
            renderables.add(portal);
         }
      });
      Vec3d cameraPos = CHelper.getCurrentCameraPos();
      renderables.sort(Comparator.comparingDouble(e -> e.getDistanceToNearestPointInPortal(cameraPos)));
      return renderables;
   }

   private static boolean shouldSkipRenderingPortal(Portal portal, Supplier<Frustum> frustumSupplier) {
      if (!portal.isPortalValid()) {
         return true;
      } else if (!portal.isVisible() && IPGlobal.maxPortalLayer != 0) {
         return true;
      } else if (RenderStates.getRenderedPortalNum() >= IPGlobal.portalRenderLimit) {
         return true;
      } else {
         Vec3d cameraPos = TransformationManager.getIsometricAdjustedCameraPos();
         if (!portal.isRoughlyVisibleTo(cameraPos)) {
            return true;
         } else {
            if (PortalRendering.isRendering()) {
               Portal outerPortal = PortalRendering.getRenderingPortal();
               if (outerPortal.cannotRenderInMe(portal)) {
                  return true;
               }
            }

            double distance = portal.getDistanceToNearestPointInPortal(cameraPos);
            if (distance > getRenderRange()) {
               return true;
            } else {
               if (IPCGlobal.earlyFrustumCullingPortal && distance > 0.1) {
                  Frustum frustum = frustumSupplier.get();
                  if (!frustum.isVisible(portal.getThinBoundingBox())) {
                     return true;
                  }
               }

               if (PortalRendering.isInvalidRecursionRendering(portal)) {
                  return true;
               } else {
                  boolean predicateTest = PORTAL_RENDERING_PREDICATE.invoker().test(portal);
                  return !predicateTest;
               }
            }
         }
      }
   }

   public static double getRenderRange() {
      double range = client.options.getClampedViewDistance() * 16;
      if (RenderStates.isLaggy || IPGlobal.reducedPortalRendering) {
         range = 16.0;
      }

      if (PortalRendering.getPortalLayer() > 1) {
         range /= PortalRendering.getPortalLayer();
      }

      if (PortalRendering.getPortalLayer() >= 1) {
         double outerPortalScale = PortalRendering.getRenderingPortal().getScale();
         if (outerPortalScale > 2.0) {
            range *= outerPortalScale;
            range = Math.min(range, 512.0);
         }
      }

      return range;
   }

   protected final void renderPortalContent(Portal portal) {
      if (PortalRendering.getPortalLayer() <= PortalRendering.getMaxPortalLayer()) {
         ClientWorld newWorld = ClientWorldLoader.getWorld(portal.getDestDim());
         PortalRendering.onBeginPortalWorldRendering();
         int renderDistance = getPortalRenderDistance(portal);
         this.invokeWorldRendering(
            new WorldRenderInfo.Builder()
               .setWorld(newWorld)
               .setCameraPos(PortalRendering.getRenderingCameraPos())
               .setCameraTransformation(portal.getAdditionalCameraTransformation())
               .setOverwriteCameraTransformation(false)
               .setDescription(portal.getDiscriminator())
               .setRenderDistance(renderDistance)
               .setDoRenderHand(false)
               .setEnableViewBobbing(true)
               .setDoRenderSky(!portal.isFuseView())
               .build()
         );
         PortalRendering.onEndPortalWorldRendering();
         GlStateManager._enableDepthTest();
         MyRenderHelper.restoreViewPort();
      }
   }

   private static int getPortalRenderDistance(Portal portal) {
      int mcRenderDistance = client.options.getClampedViewDistance();
      if (portal.getScale() > 2.0) {
         double radiusBlocks = portal.getDestAreaRadiusEstimation() * 1.4;
         radiusBlocks = Math.min(radiusBlocks, 512.0);
         return Math.max((int)(radiusBlocks / 16.0), mcRenderDistance);
      } else {
         return IPGlobal.reducedPortalRendering ? mcRenderDistance / 3 : mcRenderDistance;
      }
   }

   public void invokeWorldRendering(WorldRenderInfo worldRenderInfo) {
      MyGameRenderer.renderWorldNew(worldRenderInfo, Runnable::run);
   }

   @Nullable
   public static Matrix4f getPortalTransformation(Portal portal) {
      Matrix4f rot = getPortalRotationMatrix(portal);
      Matrix4f mirror = portal instanceof Mirror ? TransformationManager.getMirrorTransformation(portal.getNormal()) : null;
      Matrix4f scale = getPortalScaleMatrix(portal);
      return combineNullable(rot, combineNullable(mirror, scale));
   }

   @Nullable
   public static Matrix4f getPortalRotationMatrix(Portal portal) {
      if (portal.getRotation() == null) {
         return null;
      } else {
         Quaternionf rot = portal.getRotation().toMcQuaternion();
         rot.conjugate();
         return rot.get(new Matrix4f());
      }
   }

   @Nullable
   public static Matrix4f combineNullable(@Nullable Matrix4f a, @Nullable Matrix4f b) {
      return Helper.combineNullable(a, b, (m1, m2) -> {
         m1.mul(m2);
         return m1;
      });
   }

   @Nullable
   public static Matrix4f getPortalScaleMatrix(Portal portal) {
      if (shouldApplyScaleToModelView(portal)) {
         float v = (float)(1.0 / portal.getScale());
         return new Matrix4f().scale(v, v, v);
      } else {
         return null;
      }
   }

   public static boolean shouldApplyScaleToModelView(Portal portal) {
      return portal.hasScaling() && portal.isFuseView();
   }

   public void onBeginIrisTranslucentRendering(Matrix4f modelView) {
   }

   public static void switchToCorrectRenderer() {
      if (!PortalRendering.isRendering()) {
         if (MinecraftClient.getInstance().options.getPreset().getValue() == GraphicsMode.FABULOUS && !fabulousWarned) {
            fabulousWarned = true;
            CHelper.printChat(Text.translatable("imm_ptl.fabulous_warning"));
         }

         IPModInfoChecking.checkShaderpack();
         switch (IPGlobal.renderMode) {
            case normal:
               switchRenderer(RenderBackendCompat.supportsStencil() ? IPCGlobal.rendererUsingStencil : IPCGlobal.rendererUsingFrameBuffer);
               break;
            case compatibility:
               switchRenderer(IPCGlobal.rendererUsingFrameBuffer);
               break;
            case debug:
               switchRenderer(IPCGlobal.rendererDebug);
               break;
            case none:
               switchRenderer(IPCGlobal.rendererDummy);
         }
      }
   }

   private static void switchRenderer(PortalRenderer renderer) {
      if (IPCGlobal.renderer != renderer) {
         Helper.log("switched to renderer " + renderer.getClass());
         IPCGlobal.renderer = renderer;
         if (IrisInterface.invoker.isShaders()) {
            IrisInterface.invoker.reloadPipelines();
         }
      }
   }
}
