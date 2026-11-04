package qouteall.imm_ptl.core.render;

import qouteall.imm_ptl.core.compat.RenderBackendCompat;
import com.mojang.blaze3d.opengl.GlStateManager;
import java.util.WeakHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.q_misc_util.my_util.Plane;

public class FrontClipping {
   private static final MinecraftClient client = MinecraftClient.getInstance();
   private static double[] activeClipPlaneEquationBeforeModelView;
   private static double[] activeClipPlaneAfterModelView;
   public static boolean isClippingEnabled = false;
   public static final double ADJUSTMENT = 0.01;
   private static final WeakHashMap<ShaderProgram, Integer> uniformLocationCache = new WeakHashMap<>();

   public static void disableClipping() {
      if (IPGlobal.enableClippingMechanism && isClippingEnabled) {
         GL11.glDisable(12288);
         isClippingEnabled = false;
      }
   }

   private static void enableClipping() {
      if (IPGlobal.enableClippingMechanism && !isClippingEnabled) {
         GL11.glEnable(12288);
         isClippingEnabled = true;
      }
   }

   public static void updateInnerClipping(MatrixStack matrixStack) {
      Matrix4f modelView = matrixStack.peek().getPositionMatrix();
      updateInnerClipping(modelView);
   }

   public static void updateInnerClipping(Matrix4f modelView) {
      if (PortalRendering.isRendering()) {
         setupInnerClipping(PortalRendering.getActiveClippingPlane(), modelView, 0.0);
      } else {
         disableClipping();
      }
   }

   public static void setupInnerClipping(Plane clipping, Matrix4f modelView, double adjustment) {
      if (IPCGlobal.useFrontClipping) {
         if (clipping != null) {
            activeClipPlaneEquationBeforeModelView = getClipEquationInner(clipping.pos(), clipping.normal(), adjustment);
            activeClipPlaneAfterModelView = transformClipEquation(activeClipPlaneEquationBeforeModelView, modelView);
            enableClipping();
         } else {
            activeClipPlaneEquationBeforeModelView = null;
            disableClipping();
         }
      }
   }

   private static double[] transformClipEquation(double[] equation, Matrix4f modelView) {
      Vector4f eq = new Vector4f((float)equation[0], (float)equation[1], (float)equation[2], (float)equation[3]);
      Matrix4f m = new Matrix4f(modelView);
      m.invert();
      m.transpose();
      m.transform(eq);
      return new double[]{eq.x(), eq.y(), eq.z(), eq.w()};
   }

   private static double[] getClipEquationInner(Vec3d clippingPoint, Vec3d clippingDirection, double correction) {
      Vec3d cameraPos = CHelper.getCurrentCameraPos();
      Vec3d portalPos = clippingPoint.add(clippingDirection.multiply(correction)).subtract(cameraPos);
      double c = clippingDirection.multiply(-1.0).dotProduct(portalPos);
      return new double[]{clippingDirection.x, clippingDirection.y, clippingDirection.z, c};
   }

   public static void setupOuterClipping(MatrixStack matrixStack, Portal portal) {
      if (IPCGlobal.useFrontClipping) {
         double[] clipEquationOuter = getClipEquationOuter(portal);
         if (clipEquationOuter != null) {
            activeClipPlaneEquationBeforeModelView = clipEquationOuter;
            activeClipPlaneAfterModelView = transformClipEquation(activeClipPlaneEquationBeforeModelView, matrixStack.peek().getPositionMatrix());
            enableClipping();
         } else {
            activeClipPlaneEquationBeforeModelView = null;
            disableClipping();
         }
      }
   }

   private static double[] getClipEquationOuter(Portal portal) {
      Plane outerClipping = portal.getPortalShape().getOuterClipping(portal.getThisSideState());
      if (outerClipping == null) {
         return null;
      } else {
         Vec3d planeNormal = outerClipping.normal();
         Vec3d cameraPos = client.gameRenderer.getCamera().getCameraPos();
         Vec3d portalPos = outerClipping.pos().subtract(cameraPos);
         double c = planeNormal.multiply(-1.0).dotProduct(portalPos);
         return new double[]{planeNormal.x, planeNormal.y, planeNormal.z, c};
      }
   }

   public static double[] getActiveClipPlaneEquationBeforeModelView() {
      return activeClipPlaneEquationBeforeModelView;
   }

   public static double[] getActiveClipPlaneEquationAfterModelView() {
      return activeClipPlaneAfterModelView;
   }

   public static void onProgramBound(ShaderProgram program) {
      if (IPGlobal.enableClippingMechanism && RenderBackendCompat.isRawGlAvailable()) {
         int location = uniformLocationCache.computeIfAbsent(program, p -> GlStateManager._glGetUniformLocation(p.getGlRef(), "iportal_ClippingEquation"));
         if (location != -1) {
            if (isClippingEnabled && shouldApplyClippingToCurrentDraw() && activeClipPlaneEquationBeforeModelView != null) {
               double[] equation = activeClipPlaneEquationBeforeModelView;
               GL20.glUniform4f(location, (float)equation[0], (float)equation[1], (float)equation[2], (float)equation[3]);
            } else {
               GL20.glUniform4f(location, 0.0F, 0.0F, 0.0F, 1.0F);
            }
         }
      }
   }

   private static boolean shouldApplyClippingToCurrentDraw() {
      if (ViewAreaRenderer.isRenderingPortalArea || RenderStates.isRenderingTerrainLayer) {
         return true;
      } else {
         return IrisInterface.invoker.isIrisPresent()
            ? false
            : CrossPortalEntityRenderer.isRenderingEntityNormally
               || CrossPortalEntityRenderer.isRenderingEntityProjection
               || RenderStates.isRenderingPortalWeather;
      }
   }
}
