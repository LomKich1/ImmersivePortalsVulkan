package qouteall.imm_ptl.core.render.context_management;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Stack;
import java.util.stream.Collectors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.portal.Mirror;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalLike;
import qouteall.imm_ptl.core.portal.shape.BoxPortalShape;
import qouteall.imm_ptl.core.render.renderer.PortalRenderer;
import qouteall.q_misc_util.my_util.Plane;

@Environment(EnvType.CLIENT)
public class PortalRendering {
   private static final Stack<Portal> portalLayers = new Stack<>();
   private static boolean isRenderingCache = false;
   private static boolean isRenderingOddNumberOfMirrorsCache = false;

   public static void pushPortalLayer(Portal portal) {
      portalLayers.push(portal);
      updateCache();
   }

   public static void popPortalLayer() {
      portalLayers.pop();
      updateCache();
   }

   private static void updateCache() {
      isRenderingCache = getPortalLayer() != 0;
      int mirrorNum = 0;

      for (Portal portal : portalLayers) {
         if (portal instanceof Mirror) {
            mirrorNum++;
         }
      }

      isRenderingOddNumberOfMirrorsCache = mirrorNum % 2 == 1;
   }

   public static int getPortalLayer() {
      return portalLayers.size();
   }

   public static boolean isRendering() {
      return isRenderingCache;
   }

   public static boolean isRenderingOddNumberOfMirrors() {
      return isRenderingOddNumberOfMirrorsCache;
   }

   public static int getMaxPortalLayer() {
      return RenderStates.isLaggy ? 1 : IPGlobal.maxPortalLayer;
   }

   @NotNull
   public static Portal getRenderingPortal() {
      return portalLayers.peek();
   }

   public static void onBeginPortalWorldRendering() {
      List<WeakReference<Portal>> currRenderInfo = portalLayers.stream().map(WeakReference::new).collect(Collectors.toList());
      RenderStates.portalRenderInfos.add(currRenderInfo);
      RenderStates.portalsRenderedThisFrame++;
      if (portalLayers.stream().anyMatch(PortalLike::hasScaling)) {
         RenderStates.renderedScalingPortal = true;
      }

      CHelper.checkGlError();
   }

   public static void onEndPortalWorldRendering() {
      RenderStates.renderedDimensions.add(portalLayers.peek().getDestDim());
   }

   public static Vec3d getRenderingCameraPos() {
      Vec3d pos = RenderStates.originalCamera.getCameraPos();

      for (Portal portal : portalLayers) {
         pos = portal.transformPoint(pos);
      }

      return pos;
   }

   public static double getExtraModelViewScaling() {
      double scale = 1.0;

      for (Portal portal : portalLayers) {
         if (!PortalRenderer.shouldApplyScaleToModelView(portal)) {
            scale *= portal.getScale();
         }
      }

      return scale;
   }

   public static boolean shouldEnableSodiumCaveCulling() {
      if (isRendering()) {
         Portal renderingPortal = getRenderingPortal();
         if (renderingPortal.getPortalShape() instanceof BoxPortalShape) {
            return false;
         } else {
            Vec3d currentCameraPos = CHelper.getCurrentCameraPos();
            Vec3d originalCameraPos = renderingPortal.inverseTransformPoint(currentCameraPos);
            double distance = renderingPortal.getDistanceToNearestPointInPortal(originalCameraPos);
            return distance < 5.0;
         }
      } else {
         return false;
      }
   }

   public static boolean shouldRenderHitResult() {
      if (isRendering()) {
         HitResult hitResult = MinecraftClient.getInstance().crosshairTarget;
         if (hitResult == null) {
            return false;
         }

         Portal renderingPortal = getRenderingPortal();
         if (renderingPortal instanceof Mirror) {
            return false;
         }

         if (hitResult instanceof BlockHitResult blockHitResult && hitResult.getType() == Type.BLOCK) {
            BlockPos blockPos = blockHitResult.getBlockPos();
            Plane innerClipping = renderingPortal.getInnerClipping();
            if (innerClipping != null) {
               return innerClipping.move(-0.1).isPointOnPositiveSide(Vec3d.ofCenter(blockPos));
            }
         }
      }

      return true;
   }

   @Nullable
   public static Plane getActiveClippingPlane() {
      Portal renderingPortal = getRenderingPortal();
      Plane plane = renderingPortal.getInnerClipping();
      if (plane == null && portalLayers.size() >= 2) {
         for (int i = portalLayers.size() - 2; i >= 0; i--) {
            Portal portal = portalLayers.get(i);
            Plane outerPlane = portal.getInnerClipping();
            if (outerPlane != null) {
               for (int j = i + 1; j < portalLayers.size(); j++) {
                  Portal portal1 = portalLayers.get(j);
                  outerPlane = new Plane(portal1.transformPoint(outerPlane.pos()), portal1.transformLocalVecNonScale(outerPlane.normal()));
               }

               return outerPlane;
            }
         }
      }

      return plane;
   }

   public static boolean isInvalidRecursionRendering(Portal toRender) {
      if (portalLayers.size() < 2) {
         return false;
      } else {
         Portal last = portalLayers.get(portalLayers.size() - 1);
         Portal secondLast = portalLayers.get(portalLayers.size() - 2);
         return toRender == secondLast && Portal.isReversePortal(toRender, last);
      }
   }
}
