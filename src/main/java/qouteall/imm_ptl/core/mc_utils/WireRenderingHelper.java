package qouteall.imm_ptl.core.mc_utils;

import java.util.Random;
import java.util.function.IntSupplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.MatrixStack.Entry;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.animation.StableClientTimer;
import qouteall.imm_ptl.core.portal.animation.UnilateralPortalState;
import qouteall.imm_ptl.core.portal.shape.SpecialFlatPortalShape;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.q_misc_util.my_util.Circle;
import qouteall.q_misc_util.my_util.DQuaternion;
import qouteall.q_misc_util.my_util.Plane;
import qouteall.q_misc_util.my_util.Sphere;

@Environment(EnvType.CLIENT)
public class WireRenderingHelper {
   public static float getLineWidth() {
      return MinecraftClient.getInstance().getWindow().getMinimumLineWidth();
   }

   public static void renderSmallCubeFrame(VertexConsumer vertexConsumer, Vec3d cameraPos, Vec3d boxCenter, int color, double scale, MatrixStack matrixStack) {
      Random random = new Random(color);
      double boxSize = Math.pow(boxCenter.distanceTo(cameraPos), 0.3) * 0.09;
      matrixStack.push();
      matrixStack.translate(boxCenter.x - cameraPos.x, boxCenter.y - cameraPos.y, boxCenter.z - cameraPos.z);
      matrixStack.scale((float)scale, (float)scale, (float)scale);
      DQuaternion rotation = getRandomSmoothRotation(random);
      double periodLen = 100.0;
      matrixStack.multiply(rotation.toMcQuaternion());
      Matrix4f matrix = matrixStack.peek().getPositionMatrix();
      float alpha = (color >> 24 & 0xFF) / 255.0F;
      float red = (color >> 16 & 0xFF) / 255.0F;
      float green = (color >> 8 & 0xFF) / 255.0F;
      float blue = (color & 0xFF) / 255.0F;
      renderLineBox(
         matrixStack, vertexConsumer, -boxSize / 2.0, -boxSize / 2.0, -boxSize / 2.0, boxSize / 2.0, boxSize / 2.0, boxSize / 2.0, red, green, blue, alpha
      );
      matrixStack.pop();
   }

   public static void renderLineBox(
      MatrixStack poseStack,
      VertexConsumer consumer,
      double minX,
      double minY,
      double minZ,
      double maxX,
      double maxY,
      double maxZ,
      float red,
      float green,
      float blue,
      float alpha
   ) {
      Entry pose = poseStack.peek();
      float lineWidth = getLineWidth();
      float x0 = (float)minX;
      float y0 = (float)minY;
      float z0 = (float)minZ;
      float x1 = (float)maxX;
      float y1 = (float)maxY;
      float z1 = (float)maxZ;
      consumer.vertex(pose, x0, y0, z0).color(red, green, blue, alpha).normal(pose, 1.0F, 0.0F, 0.0F).lineWidth(lineWidth);
      consumer.vertex(pose, x1, y0, z0).color(red, green, blue, alpha).normal(pose, 1.0F, 0.0F, 0.0F).lineWidth(lineWidth);
      consumer.vertex(pose, x0, y0, z0).color(red, green, blue, alpha).normal(pose, 0.0F, 1.0F, 0.0F).lineWidth(lineWidth);
      consumer.vertex(pose, x0, y1, z0).color(red, green, blue, alpha).normal(pose, 0.0F, 1.0F, 0.0F).lineWidth(lineWidth);
      consumer.vertex(pose, x0, y0, z0).color(red, green, blue, alpha).normal(pose, 0.0F, 0.0F, 1.0F).lineWidth(lineWidth);
      consumer.vertex(pose, x0, y0, z1).color(red, green, blue, alpha).normal(pose, 0.0F, 0.0F, 1.0F).lineWidth(lineWidth);
      consumer.vertex(pose, x1, y0, z0).color(red, green, blue, alpha).normal(pose, 0.0F, 1.0F, 0.0F).lineWidth(lineWidth);
      consumer.vertex(pose, x1, y1, z0).color(red, green, blue, alpha).normal(pose, 0.0F, 1.0F, 0.0F).lineWidth(lineWidth);
      consumer.vertex(pose, x1, y1, z0).color(red, green, blue, alpha).normal(pose, -1.0F, 0.0F, 0.0F).lineWidth(lineWidth);
      consumer.vertex(pose, x0, y1, z0).color(red, green, blue, alpha).normal(pose, -1.0F, 0.0F, 0.0F).lineWidth(lineWidth);
      consumer.vertex(pose, x0, y1, z0).color(red, green, blue, alpha).normal(pose, 0.0F, 0.0F, 1.0F).lineWidth(lineWidth);
      consumer.vertex(pose, x0, y1, z1).color(red, green, blue, alpha).normal(pose, 0.0F, 0.0F, 1.0F).lineWidth(lineWidth);
      consumer.vertex(pose, x0, y1, z1).color(red, green, blue, alpha).normal(pose, 0.0F, -1.0F, 0.0F).lineWidth(lineWidth);
      consumer.vertex(pose, x0, y0, z1).color(red, green, blue, alpha).normal(pose, 0.0F, -1.0F, 0.0F).lineWidth(lineWidth);
      consumer.vertex(pose, x0, y0, z1).color(red, green, blue, alpha).normal(pose, 1.0F, 0.0F, 0.0F).lineWidth(lineWidth);
      consumer.vertex(pose, x1, y0, z1).color(red, green, blue, alpha).normal(pose, 1.0F, 0.0F, 0.0F).lineWidth(lineWidth);
      consumer.vertex(pose, x1, y0, z1).color(red, green, blue, alpha).normal(pose, 0.0F, 0.0F, -1.0F).lineWidth(lineWidth);
      consumer.vertex(pose, x1, y0, z0).color(red, green, blue, alpha).normal(pose, 0.0F, 0.0F, -1.0F).lineWidth(lineWidth);
      consumer.vertex(pose, x0, y1, z1).color(red, green, blue, alpha).normal(pose, 1.0F, 0.0F, 0.0F).lineWidth(lineWidth);
      consumer.vertex(pose, x1, y1, z1).color(red, green, blue, alpha).normal(pose, 1.0F, 0.0F, 0.0F).lineWidth(lineWidth);
      consumer.vertex(pose, x1, y0, z1).color(red, green, blue, alpha).normal(pose, 0.0F, 1.0F, 0.0F).lineWidth(lineWidth);
      consumer.vertex(pose, x1, y1, z1).color(red, green, blue, alpha).normal(pose, 0.0F, 1.0F, 0.0F).lineWidth(lineWidth);
      consumer.vertex(pose, x1, y1, z0).color(red, green, blue, alpha).normal(pose, 0.0F, 0.0F, 1.0F).lineWidth(lineWidth);
      consumer.vertex(pose, x1, y1, z1).color(red, green, blue, alpha).normal(pose, 0.0F, 0.0F, 1.0F).lineWidth(lineWidth);
   }

   public static DQuaternion getRandomSmoothRotation(Random random) {
      double time = (double)StableClientTimer.getStableTickTime() + StableClientTimer.getStablePartialTicks();
      DQuaternion rotation = DQuaternion.identity;

      for (int i = 0; i < 6; i++) {
         rotation = rotation.hamiltonProduct(DQuaternion.rotationByDegrees(randomVec(random), CHelper.getSmoothCycles(random.nextInt(30, 60)) * 360.0));
      }

      return rotation;
   }

   public static double getRandomSmoothCycle(Random random) {
      double totalFactor = 0.1;
      double total = 0.0;

      for (int i = 0; i < 5; i++) {
         double smoothCycle = CHelper.getSmoothCycles(random.nextInt(30, 300));
         double sin = Math.sin((Math.PI * 2) * smoothCycle);
         double factor = random.nextDouble(0.1, 1.0);
         totalFactor += factor;
         total += sin * factor;
      }

      return total / totalFactor;
   }

   @NotNull
   public static Vec3d randomVec(Random random) {
      return new Vec3d(random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5);
   }

   public static void renderPlane(
      VertexConsumer vertexConsumer, Vec3d cameraPos, Plane plane, double renderedPlaneScale, int color, MatrixStack matrixStack, boolean isLineStrip
   ) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null) {
         Vec3d planeCenter = plane.pos();
         Vec3d normal = plane.normal();
         Vec3d anyVecNonNormal = new Vec3d(13.0, 29.0, 71.0).normalize();
         if (Math.abs(normal.dotProduct(anyVecNonNormal)) > 0.99) {
            anyVecNonNormal = new Vec3d(1.0, 0.0, 0.0);
         }

         Vec3d planeX = normal.crossProduct(anyVecNonNormal).normalize();
         Vec3d planeY = normal.crossProduct(planeX).normalize();
         matrixStack.push();
         matrixStack.translate(planeCenter.x - cameraPos.x, planeCenter.y - cameraPos.y, planeCenter.z - cameraPos.z);
         matrixStack.multiply(DQuaternion.rotationByDegrees(normal, CHelper.getSmoothCycles(211L) * 360.0).toMcQuaternion());
         Matrix4f matrix = matrixStack.peek().getPositionMatrix();
         double cameraDistanceToCenter = player.getCameraPosVec(RenderStates.getPartialTick()).distanceTo(planeCenter);
         int lineNumPerSide = 10;
         double lineInterval = cameraDistanceToCenter * 0.2 * renderedPlaneScale;
         double lineLenPerSide = lineNumPerSide * lineInterval;

         for (int ix = -lineNumPerSide; ix <= lineNumPerSide; ix++) {
            Vec3d lineStart = planeX.multiply(ix * lineInterval).add(planeY.multiply(-lineLenPerSide));
            Vec3d lineEnd = planeX.multiply(ix * lineInterval).add(planeY.multiply(lineLenPerSide));
            if (isLineStrip) {
               putLineToLineStrip(vertexConsumer, color, planeY, matrix, lineStart, lineEnd);
            } else {
               putLine(vertexConsumer, color, planeY, matrix, matrixStack.peek().getNormalMatrix(), lineStart, lineEnd);
            }
         }

         for (int iy = -lineNumPerSide; iy <= lineNumPerSide; iy++) {
            Vec3d lineStart = planeY.multiply(iy * lineInterval).add(planeX.multiply(-lineLenPerSide));
            Vec3d lineEnd = planeY.multiply(iy * lineInterval).add(planeX.multiply(lineLenPerSide));
            if (isLineStrip) {
               putLineToLineStrip(vertexConsumer, color, planeX, matrix, lineStart, lineEnd);
            } else {
               putLine(vertexConsumer, color, planeX, matrix, matrixStack.peek().getNormalMatrix(), lineStart, lineEnd);
            }
         }

         matrixStack.pop();
      }
   }

   public static void renderCircle(VertexConsumer vertexConsumer, Vec3d cameraPos, Circle circle, int color, MatrixStack matrixStack) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null) {
         Vec3d planeCenter = circle.plane().pos();
         Vec3d normal = circle.plane().normal();
         Vec3d anyVecNonNormal = new Vec3d(0.0, 1.0, 0.0);
         if (Math.abs(normal.dotProduct(anyVecNonNormal)) > 0.9) {
            anyVecNonNormal = new Vec3d(1.0, 0.0, 0.0);
         }

         Vec3d planeX = normal.crossProduct(anyVecNonNormal).normalize();
         Vec3d planeY = normal.crossProduct(planeX).normalize();
         Vec3d circleCenter = circle.circleCenter();
         double circleRadius = circle.radius();
         matrixStack.push();
         matrixStack.translate(circleCenter.x - cameraPos.x, circleCenter.y - cameraPos.y, circleCenter.z - cameraPos.z);
         Matrix4f matrix = matrixStack.peek().getPositionMatrix();
         int vertexNum = MathHelper.clamp((int)Math.round(circleRadius * 40.0), 40, 400);

         for (int i = 0; i < vertexNum; i++) {
            double angle = i * 2 * Math.PI / vertexNum;
            double nextAngle = (i + 1) * 2 * Math.PI / vertexNum;
            boolean isBegin = i == 0;
            boolean isEnd = i == vertexNum - 1;
            Vec3d lineStart = planeX.multiply(Math.cos(angle) * circleRadius).add(planeY.multiply(Math.sin(angle) * circleRadius));
            Vec3d lineEnd = planeX.multiply(Math.cos(nextAngle) * circleRadius).add(planeY.multiply(Math.sin(nextAngle) * circleRadius));
            if (isBegin) {
               vertexConsumer.vertex(matrix, (float)lineStart.x, (float)lineStart.y, (float)lineStart.z)
                  .color(0)
                  .normal((float)normal.x, (float)normal.y, (float)normal.z)
                  .lineWidth(getLineWidth());
               vertexConsumer.vertex(matrix, (float)lineStart.x, (float)lineStart.y, (float)lineStart.z)
                  .color(color)
                  .normal((float)normal.x, (float)normal.y, (float)normal.z)
                  .lineWidth(getLineWidth());
            }

            vertexConsumer.vertex(matrix, (float)lineEnd.x, (float)lineEnd.y, (float)lineEnd.z)
               .color(color)
               .normal((float)normal.x, (float)normal.y, (float)normal.z)
               .lineWidth(getLineWidth());
            if (isEnd) {
               vertexConsumer.vertex(matrix, (float)lineEnd.x, (float)lineEnd.y, (float)lineEnd.z)
                  .color(0)
                  .normal((float)normal.x, (float)normal.y, (float)normal.z)
                  .lineWidth(getLineWidth());
            }
         }

         matrixStack.pop();
      }
   }

   public static void renderLockShape(VertexConsumer vertexConsumer, Vec3d cameraPos, Vec3d center, double scale, int color, MatrixStack matrixStack) {
      double w = 380.0;
      double h = 270.0;
      double ringWidth = 60.0;
      double ringAreaWidth = 152.0;
      double rightAreaHeight = 136.0;
      Vec3d[] lineVertices = new Vec3d[]{
         new Vec3d(w / 2.0, h / 2.0, 0.0),
         new Vec3d(-w / 2.0, h / 2.0, 0.0),
         new Vec3d(-w / 2.0, h / 2.0, 0.0),
         new Vec3d(-w / 2.0, -h / 2.0, 0.0),
         new Vec3d(-w / 2.0, -h / 2.0, 0.0),
         new Vec3d(w / 2.0, -h / 2.0, 0.0),
         new Vec3d(w / 2.0, -h / 2.0, 0.0),
         new Vec3d(w / 2.0, h / 2.0, 0.0),
         new Vec3d(ringAreaWidth / 2.0, h / 2.0, 0.0),
         new Vec3d(ringAreaWidth / 2.0, h / 2.0 + rightAreaHeight, 0.0),
         new Vec3d(ringAreaWidth / 2.0, h / 2.0 + rightAreaHeight, 0.0),
         new Vec3d(-ringAreaWidth / 2.0, h / 2.0 + rightAreaHeight, 0.0),
         new Vec3d(-ringAreaWidth / 2.0, h / 2.0 + rightAreaHeight, 0.0),
         new Vec3d(-ringAreaWidth / 2.0, h / 2.0, 0.0),
         new Vec3d(ringAreaWidth / 2.0 + ringWidth, h / 2.0, 0.0),
         new Vec3d(ringAreaWidth / 2.0 + ringWidth, h / 2.0 + rightAreaHeight + ringWidth, 0.0),
         new Vec3d(ringAreaWidth / 2.0 + ringWidth, h / 2.0 + rightAreaHeight + ringWidth, 0.0),
         new Vec3d(-ringAreaWidth / 2.0 - ringWidth, h / 2.0 + rightAreaHeight + ringWidth, 0.0),
         new Vec3d(-ringAreaWidth / 2.0 - ringWidth, h / 2.0 + rightAreaHeight + ringWidth, 0.0),
         new Vec3d(-ringAreaWidth / 2.0 - ringWidth, h / 2.0, 0.0)
      };
      float renderScale = (float)(scale * 0.001);
      DQuaternion rotation = DQuaternion.rotationByDegrees(new Vec3d(0.0, 1.0, 0.0), CHelper.getSmoothCycles(60L) * 360.0);
      renderLines(vertexConsumer, cameraPos, center, lineVertices, renderScale, rotation, color, matrixStack);
   }

   public static void renderLines(
      VertexConsumer vertexConsumer,
      Vec3d cameraPos,
      Vec3d center,
      Vec3d[] lineVertices,
      double scale,
      DQuaternion rotation,
      int color,
      MatrixStack matrixStack
   ) {
      matrixStack.push();
      matrixStack.translate(center.x - cameraPos.x, center.y - cameraPos.y, center.z - cameraPos.z);
      matrixStack.multiply(rotation.toMcQuaternion());
      matrixStack.scale((float)scale, (float)scale, (float)scale);
      Matrix4f matrix = matrixStack.peek().getPositionMatrix();
      Matrix3f normalMatrix = matrixStack.peek().getNormalMatrix();

      for (int i = 0; i < lineVertices.length / 2; i++) {
         putLine(vertexConsumer, color, matrix, normalMatrix, lineVertices[i * 2], lineVertices[i * 2 + 1]);
      }

      matrixStack.pop();
   }

   public static void putLine(VertexConsumer vertexConsumer, int color, Matrix4f matrix, Matrix3f normalMatrix, Vec3d lineStart, Vec3d lineEnd) {
      putLine(vertexConsumer, color, lineEnd.subtract(lineStart), matrix, normalMatrix, lineStart, lineEnd);
   }

   public static void putLine(VertexConsumer vertexConsumer, int color, Vec3d normal, Matrix4f matrix, Matrix3f normalMatrix, Vec3d lineStart, Vec3d lineEnd) {
      Vector3f normalTemp = new Vector3f();
      normalTemp.set(normal.getX(), normal.getY(), normal.getZ());
      normalMatrix.transform(normalTemp);
      vertexConsumer.vertex(matrix, (float)lineStart.x, (float)lineStart.y, (float)lineStart.z)
         .color(color)
         .normal(normalTemp.x(), normalTemp.y(), normalTemp.z())
         .lineWidth(getLineWidth());
      vertexConsumer.vertex(matrix, (float)lineEnd.x, (float)lineEnd.y, (float)lineEnd.z)
         .color(color)
         .normal(normalTemp.x(), normalTemp.y(), normalTemp.z())
         .lineWidth(getLineWidth());
   }

   private static void putLineToLineStrip(VertexConsumer vertexConsumer, int color, Vec3d normal, Matrix4f matrix, Vec3d lineStart, Vec3d lineEnd) {
      vertexConsumer.vertex(matrix, (float)lineStart.x, (float)lineStart.y, (float)lineStart.z)
         .color(0)
         .normal((float)normal.x, (float)normal.y, (float)normal.z)
         .lineWidth(getLineWidth());
      vertexConsumer.vertex(matrix, (float)lineStart.x, (float)lineStart.y, (float)lineStart.z)
         .color(color)
         .normal((float)normal.x, (float)normal.y, (float)normal.z)
         .lineWidth(getLineWidth());
      vertexConsumer.vertex(matrix, (float)lineEnd.x, (float)lineEnd.y, (float)lineEnd.z)
         .color(color)
         .normal((float)normal.x, (float)normal.y, (float)normal.z)
         .lineWidth(getLineWidth());
      vertexConsumer.vertex(matrix, (float)lineEnd.x, (float)lineEnd.y, (float)lineEnd.z)
         .color(0)
         .normal((float)normal.x, (float)normal.y, (float)normal.z)
         .lineWidth(getLineWidth());
   }

   public static void renderRectLine(
      VertexConsumer vertexConsumer,
      Vec3d cameraPos,
      UnilateralPortalState rect,
      int flowCount,
      int color,
      double shrinkFactor,
      int flowDirection,
      MatrixStack matrixStack
   ) {
      matrixStack.push();
      matrixStack.translate(rect.position().x - cameraPos.x, rect.position().y - cameraPos.y, rect.position().z - cameraPos.z);
      Vec3d[] vertices = getRectVertices(rect, shrinkFactor);
      Random random = new Random(color);
      renderFlowLines(vertexConsumer, vertices, flowCount, color, flowDirection, matrixStack, () -> random.nextInt(30, 300));
      matrixStack.pop();
   }

   public static void renderFlowLines(
      VertexConsumer vertexConsumer, Vec3d[] vertices, int flowCount, int color, int flowDirection, MatrixStack matrixStack, IntSupplier randCycleSupplier
   ) {
      Matrix4f matrix = matrixStack.peek().getPositionMatrix();
      Matrix3f normalMatrix = matrixStack.peek().getNormalMatrix();

      for (int i = 0; i < flowCount; i++) {
         double offset = flowDirection * CHelper.getSmoothCycles(randCycleSupplier.getAsInt());
         double totalStartRatio = i * 2.0 / (flowCount * 2) + offset;
         double totalEndRatio = (i * 2.0 + 1.0) / (flowCount * 2) + offset;
         renderSubLineInLineLoop(vertexConsumer, matrix, normalMatrix, vertices, color, totalStartRatio, totalEndRatio);
      }
   }

   private static Vec3d[] getRectVertices(UnilateralPortalState rect, double shrinkFactor) {
      Vec3d normal = rect.orientation().getNormal();
      Vec3d axisW = rect.orientation().getAxisW();
      Vec3d axisH = rect.orientation().getAxisH();
      Vec3d facingOffset = normal.multiply(0.01);
      return new Vec3d[]{
         axisW.multiply(shrinkFactor * rect.width() / 2.0).add(axisH.multiply(shrinkFactor * rect.height() / 2.0)).add(facingOffset),
         axisW.multiply(shrinkFactor * rect.width() / 2.0).add(axisH.multiply(-1.0 * shrinkFactor * rect.height() / 2.0)).add(facingOffset),
         axisW.multiply(-1.0 * shrinkFactor * rect.width() / 2.0).add(axisH.multiply(-1.0 * shrinkFactor * rect.height() / 2.0)).add(facingOffset),
         axisW.multiply(-1.0 * shrinkFactor * rect.width() / 2.0).add(axisH.multiply(shrinkFactor * rect.height() / 2.0)).add(facingOffset),
         axisW.multiply(shrinkFactor * rect.width() / 2.0).add(axisH.multiply(shrinkFactor * rect.height() / 2.0)).add(facingOffset)
      };
   }

   public static void renderSubLineInLineLoop(
      VertexConsumer vertexConsumer, Matrix4f matrix, Matrix3f normalMatrix, Vec3d[] lineVertices, int color, double totalStartRatio, double totalEndRatio
   ) {
      int lineNum = lineVertices.length - 1;
      double startRatioByLine = totalStartRatio * lineNum;
      double endRatioByLine = totalEndRatio * lineNum;
      int startRatioLineIndex = (int)Math.floor(startRatioByLine);
      int endRatioLineIndex = (int)Math.floor(endRatioByLine);

      for (int lineIndex = startRatioLineIndex; lineIndex <= endRatioLineIndex; lineIndex++) {
         double startLimit = lineIndex;
         double endLimit = lineIndex + 1;
         double startRatio = Math.max(startLimit, startRatioByLine);
         double endRatio = Math.min(endLimit, endRatioByLine);
         putLinePart(
            vertexConsumer,
            color,
            matrix,
            normalMatrix,
            lineVertices[Math.floorMod(lineIndex, lineNum)],
            lineVertices[Math.floorMod(lineIndex, lineNum) + 1],
            startRatio - lineIndex,
            endRatio - lineIndex
         );
      }
   }

   private static void putLinePart(
      VertexConsumer vertexConsumer, int color, Matrix4f matrix, Matrix3f normalMatrix, Vec3d lineStart, Vec3d lineEnd, double startRatio, double endRatio
   ) {
      Vec3d vec = lineEnd.subtract(lineStart);
      Vec3d partStartPos = lineStart.add(vec.multiply(startRatio));
      Vec3d partEndPos = lineStart.add(vec.multiply(endRatio));
      putLine(vertexConsumer, color, matrix, normalMatrix, partStartPos, partEndPos);
   }

   public static void renderSphere(
      VertexConsumer vertexConsumer,
      int color,
      MatrixStack matrixStack,
      Vec3d cameraPos,
      Sphere sphere,
      DQuaternion sphereOrientation,
      double animationProgress,
      double rotationProgress
   ) {
      matrixStack.push();
      matrixStack.translate(sphere.center().x - cameraPos.x, sphere.center().y - cameraPos.y, sphere.center().z - cameraPos.z);
      matrixStack.multiply(sphereOrientation.toMcQuaternion());
      matrixStack.scale((float)sphere.radius(), (float)sphere.radius(), (float)sphere.radius());
      Matrix4f matrix = matrixStack.peek().getPositionMatrix();
      int meridianCount = 30;
      int parallelCount = 15;
      int vertexNum = MathHelper.clamp((int)Math.round(sphere.radius() * 40.0), 20, 400);
      int transparentColor = color & 16777215;

      for (int i = 0; i < meridianCount; i++) {
         double longitude = ((double)i / meridianCount + rotationProgress) * Math.PI * 2.0;

         for (int j = 0; j < vertexNum; j++) {
            double latitudeRatio = (double)j / vertexNum;
            latitudeRatio = Math.min(latitudeRatio, animationProgress);
            double latitude = latitudeRatio * Math.PI - (Math.PI / 2);
            double x = Math.cos(latitude) * Math.cos(longitude);
            double y = Math.sin(latitude);
            double z = Math.cos(latitude) * Math.sin(longitude);
            boolean isFirst = j == 0;
            if (isFirst) {
               vertexConsumer.vertex(matrix, (float)x, (float)y, (float)z).color(transparentColor).normal(0.0F, 1.0F, 0.0F).lineWidth(getLineWidth());
            }

            vertexConsumer.vertex(matrix, (float)x, (float)y, (float)z).color(color).normal(0.0F, 1.0F, 0.0F).lineWidth(getLineWidth());
            boolean isLast = j == vertexNum - 1;
            if (isLast) {
               vertexConsumer.vertex(matrix, (float)x, (float)y, (float)z).color(transparentColor).normal(0.0F, 1.0F, 0.0F).lineWidth(getLineWidth());
            }
         }
      }

      for (int i = 0; i < parallelCount; i++) {
         double latitudeRatiox = (double)i / parallelCount + rotationProgress;
         latitudeRatiox -= Math.floor(latitudeRatiox);
         double latitudex = latitudeRatiox * Math.PI - (Math.PI / 2);

         for (int j = 0; j <= vertexNum; j++) {
            double longitudeRatio = (double)j / vertexNum;
            longitudeRatio = Math.min(longitudeRatio, animationProgress);
            double longitude = longitudeRatio * Math.PI * 2.0;
            double xx = Math.cos(latitudex) * Math.cos(longitude);
            double yx = Math.sin(latitudex);
            double zx = Math.cos(latitudex) * Math.sin(longitude);
            boolean isFirstx = j == 0;
            if (isFirstx) {
               vertexConsumer.vertex(matrix, (float)xx, (float)yx, (float)zx).color(transparentColor).normal(0.0F, 1.0F, 0.0F).lineWidth(getLineWidth());
            }

            vertexConsumer.vertex(matrix, (float)xx, (float)yx, (float)zx).color(color).normal(0.0F, 1.0F, 0.0F).lineWidth(getLineWidth());
            boolean isLast = j == vertexNum;
            if (isLast) {
               vertexConsumer.vertex(matrix, (float)xx, (float)yx, (float)zx).color(transparentColor).normal(0.0F, 1.0F, 0.0F).lineWidth(getLineWidth());
            }
         }
      }

      matrixStack.pop();
   }

   public static void renderRectFrameFlow(
      MatrixStack matrixStack, Vec3d cameraPos, VertexConsumer vertexConsumer, UnilateralPortalState rect, int innerColor, int outerColor
   ) {
      matrixStack.push();
      matrixStack.scale(0.5F, 0.5F, 0.5F);
      renderRectLine(vertexConsumer, cameraPos, rect, 10, innerColor, 0.99, 1, matrixStack);
      renderRectLine(vertexConsumer, cameraPos, rect, 10, outerColor, 1.01, -1, matrixStack);
      matrixStack.pop();
   }

   public static void renderPortalShapeMeshDebug(MatrixStack matrixStack, VertexConsumer vertexConsumer, Portal portal) {
      double cycle = (Math.sin(CHelper.getSmoothCycles(50L) * 2.0 * Math.PI) + 1.0) / 2.0;
      double shrink = 0.03 * cycle;
      if (portal.getPortalShape() instanceof SpecialFlatPortalShape shape) {
         shape.mesh.compact();
         int triangleNum = shape.mesh.getStoredTriangleNum();
         int vertexNum = triangleNum * 3;
         Vec3d[] vertexes = new Vec3d[vertexNum];
         double halfWidth = portal.getWidth() / 2.0;
         double halfHeight = portal.getHeight() / 2.0;
         Vec3d X = portal.getAxisW().multiply(halfWidth);
         Vec3d Y = portal.getAxisH().multiply(halfHeight);
         matrixStack.push();
         Matrix4f matrix = matrixStack.peek().getPositionMatrix();
         Matrix3f normalMatrix = matrixStack.peek().getNormalMatrix();

         for (int i = 0; i < triangleNum; i++) {
            int p0Index = shape.mesh.getTrianglePointIndex(i, 0);
            int p1Index = shape.mesh.getTrianglePointIndex(i, 1);
            int p2Index = shape.mesh.getTrianglePointIndex(i, 2);
            double p0x = shape.mesh.pointCoords.getDouble(p0Index * 2);
            double p0y = shape.mesh.pointCoords.getDouble(p0Index * 2 + 1);
            double p1x = shape.mesh.pointCoords.getDouble(p1Index * 2);
            double p1y = shape.mesh.pointCoords.getDouble(p1Index * 2 + 1);
            double p2x = shape.mesh.pointCoords.getDouble(p2Index * 2);
            double p2y = shape.mesh.pointCoords.getDouble(p2Index * 2 + 1);
            double centerX = (p0x + p1x + p2x) / 3.0;
            double centerY = (p0y + p1y + p2y) / 3.0;
            double x0 = p0x * (1.0 - shrink) + centerX * shrink;
            double y0 = p0y * (1.0 - shrink) + centerY * shrink;
            double x1 = p1x * (1.0 - shrink) + centerX * shrink;
            double y1 = p1y * (1.0 - shrink) + centerY * shrink;
            double x2 = p2x * (1.0 - shrink) + centerX * shrink;
            double y2 = p2y * (1.0 - shrink) + centerY * shrink;
            putLine(vertexConsumer, -2130771968, matrix, normalMatrix, X.multiply(x0).add(Y.multiply(y0)), X.multiply(x1).add(Y.multiply(y1)));
            putLine(vertexConsumer, -2130771968, matrix, normalMatrix, X.multiply(x1).add(Y.multiply(y1)), X.multiply(x2).add(Y.multiply(y2)));
            putLine(vertexConsumer, -2130771968, matrix, normalMatrix, X.multiply(x2).add(Y.multiply(y2)), X.multiply(x0).add(Y.multiply(y0)));
         }

         matrixStack.pop();
      }
   }
}
