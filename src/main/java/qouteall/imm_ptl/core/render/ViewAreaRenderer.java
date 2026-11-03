package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.class_5596;
import java.util.Objects;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BuiltBuffer;
import org.joml.Matrix4f;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.q_misc_util.my_util.TriangleConsumer;

public class ViewAreaRenderer {
   public static boolean isRenderingPortalArea = false;

   public static void renderPortalArea(
      Portal portal,
      Vec3d fogColor,
      Matrix4f modelViewMatrix,
      Matrix4f projectionMatrix,
      boolean doFaceCulling,
      boolean doModifyColor,
      boolean doModifyDepth,
      boolean doClip,
      Framebuffer target
   ) {
      renderPortalArea(
         portal, fogColor, modelViewMatrix, projectionMatrix, doFaceCulling, doModifyColor, doModifyDepth, doClip, DepthTestFunction.LEQUAL_DEPTH_TEST, target
      );
   }

   public static void renderPortalArea(
      Portal portal,
      Vec3d fogColor,
      Matrix4f modelViewMatrix,
      Matrix4f projectionMatrix,
      boolean doFaceCulling,
      boolean doModifyColor,
      boolean doModifyDepth,
      boolean doClip,
      DepthTestFunction depthTest,
      Framebuffer target
   ) {
      boolean writeColor;
      if (portal.isFuseView() && IPGlobal.maxPortalLayer != 0) {
         writeColor = false;
      } else {
         writeColor = doModifyColor;
      }

      boolean writeDepth;
      if (doModifyDepth) {
         writeDepth = !portal.isFuseView();
      } else {
         writeDepth = false;
      }

      boolean shouldReverseCull = PortalRendering.isRenderingOddNumberOfMirrors();
      if (shouldReverseCull) {
         MyRenderHelper.applyMirrorFaceCulling();
      }

      if (doClip) {
         if (PortalRendering.isRendering()) {
            FrontClipping.setupInnerClipping(PortalRendering.getActiveClippingPlane(), modelViewMatrix, 0.0);
         }
      } else {
         FrontClipping.disableClipping();
      }

      CHelper.enableDepthClamp();
      RenderPipeline pipeline = IPRenderPipelines.portalArea(doFaceCulling, writeColor, writeDepth, depthTest);
      BuiltBuffer mesh = buildPortalViewAreaTrianglesMesh(fogColor, portal, CHelper.getCurrentCameraPos(), RenderStates.getPartialTick());
      isRenderingPortalArea = true;

      try {
         MyRenderHelper.drawMesh(pipeline, mesh, target, modelViewMatrix, MyRenderHelper.getProjectionBuffer(projectionMatrix), null);
      } finally {
         isRenderingPortalArea = false;
      }

      CHelper.disableDepthClamp();
      if (shouldReverseCull) {
         MyRenderHelper.recoverFaceCulling();
      }

      if (PortalRendering.isRendering()) {
         FrontClipping.disableClipping();
      }

      CHelper.checkGlError();
   }

   public static BuiltBuffer buildPortalViewAreaTrianglesMesh(Vec3d fogColor, Portal portal, Vec3d cameraPos, float partialTick) {
      Tessellator tessellator = Tessellator.getInstance();
      BufferBuilder bufferBuilder = tessellator.begin(class_5596.field_27379, VertexFormats.POSITION_COLOR);
      Vec3d originRelativeToCamera = portal.getOriginPos().subtract(cameraPos);
      TriangleConsumer vertexOutput = (p0x, p0y, p0z, p1x, p1y, p1z, p2x, p2y, p2z) -> {
         bufferBuilder.vertex((float)p0x, (float)p0y, (float)p0z)
            .color((float)fogColor.x, (float)fogColor.y, (float)fogColor.z, 1.0F);
         bufferBuilder.vertex((float)p1x, (float)p1y, (float)p1z)
            .color((float)fogColor.x, (float)fogColor.y, (float)fogColor.z, 1.0F);
         bufferBuilder.vertex((float)p2x, (float)p2y, (float)p2z)
            .color((float)fogColor.x, (float)fogColor.y, (float)fogColor.z, 1.0F);
      };
      portal.renderViewAreaMesh(originRelativeToCamera, vertexOutput);
      return Objects.requireNonNull(bufferBuilder.endNullable());
   }

   public static void outputTriangle(
      TriangleConsumer vertexOutput,
      Vec3d center,
      Vec3d localXAxis,
      Vec3d localYAxis,
      double p0x,
      double p0y,
      double p1x,
      double p1y,
      double p2x,
      double p2y
   ) {
      vertexOutput.accept(
         center.x + p0x * localXAxis.getX() + p0y * localYAxis.getX(),
         center.y + p0x * localXAxis.getY() + p0y * localYAxis.getY(),
         center.z + p0x * localXAxis.getZ() + p0y * localYAxis.getZ(),
         center.x + p1x * localXAxis.getX() + p1y * localYAxis.getX(),
         center.y + p1x * localXAxis.getY() + p1y * localYAxis.getY(),
         center.z + p1x * localXAxis.getZ() + p1y * localYAxis.getZ(),
         center.x + p2x * localXAxis.getX() + p2y * localYAxis.getX(),
         center.y + p2x * localXAxis.getY() + p2y * localYAxis.getY(),
         center.z + p2x * localXAxis.getZ() + p2y * localYAxis.getZ()
      );
   }

   @Deprecated
   private static void generateTriangleForNormalShape(TriangleConsumer vertexOutput, Portal portal, Vec3d posInPlayerCoordinate) {
      double w = Math.min(portal.getWidth(), 23333.0);
      double h = Math.min(portal.getHeight(), 23333.0);
      Vec3d localXAxis = portal.getAxisW().multiply(w / 2.0);
      Vec3d localYAxis = portal.getAxisH().multiply(h / 2.0);
      outputFullQuad(vertexOutput, posInPlayerCoordinate, localXAxis, localYAxis);
   }

   @Deprecated
   private static void generateTriangleForGlobalPortal(TriangleConsumer vertexOutput, Portal portal, Vec3d portalOriginLocal) {
      Vec3d cameraPosFromPortalOrigin = portalOriginLocal.multiply(-1.0);
      Vec3d cameraPosFromPortalOriginProjected = portal.getLocalVecProjectedToPlane(cameraPosFromPortalOrigin);
      Vec3d localCenter = portalOriginLocal.add(cameraPosFromPortalOriginProjected);
      double r = MinecraftClient.getInstance().options.getClampedViewDistance() * 16 - 16;
      if (TransformationManager.isIsometricView) {
         r *= 2.0;
      }

      double distance = Math.abs(cameraPosFromPortalOrigin.dotProduct(portal.getNormal()));
      if (distance > 200.0) {
         r = r * 200.0 / distance;
      }

      Vec3d localXAxis = portal.getAxisW().multiply(r);
      Vec3d localYAxis = portal.getAxisH().multiply(r);
      outputFullQuad(vertexOutput, localCenter, localXAxis, localYAxis);
   }

   public static void outputFullQuad(TriangleConsumer vertexOutput, Vec3d posInPlayerCoordinate, Vec3d localXAxis, Vec3d localYAxis) {
      outputTriangle(vertexOutput, posInPlayerCoordinate, localXAxis, localYAxis, 1.0, 1.0, -1.0, 1.0, 1.0, -1.0);
      outputTriangle(vertexOutput, posInPlayerCoordinate, localXAxis, localYAxis, -1.0, 1.0, -1.0, -1.0, 1.0, -1.0);
   }
}
