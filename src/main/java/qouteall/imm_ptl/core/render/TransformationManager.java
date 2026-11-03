package qouteall.imm_ptl.core.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Pair;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import qouteall.imm_ptl.core.compat.GravityChangerInterface;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.DQuaternion;

@Environment(EnvType.CLIENT)
public class TransformationManager {
   private static DQuaternion animationDeltaStart;
   private static long interpolationStartTime = 0L;
   private static long interpolationEndTime = 1L;
   public static final MinecraftClient client = MinecraftClient.getInstance();
   public static boolean isIsometricView = false;
   public static float isometricViewLength = 50.0F;
   public static boolean isCalculatingViewBobbingOffset = false;

   public static DQuaternion getPlayerCameraRotation() {
      ClientPlayerEntity player = client.player;
      if (player == null) {
         return DQuaternion.identity;
      } else {
         Direction gravity = GravityChangerInterface.invoker.getGravityDirection(player);
         return getCameraRotationWithGravity(gravity, player.getPitch(), player.getYaw());
      }
   }

   private static DQuaternion getCameraRotationWithGravity(Direction gravityDirection, float pitch, float yaw) {
      DQuaternion gravity = GravityChangerInterface.invoker.getExtraCameraRotation(gravityDirection);
      DQuaternion rawCameraRotation = DQuaternion.getCameraRotation(pitch, yaw);
      return gravity == null ? rawCameraRotation : rawCameraRotation.hamiltonProduct(gravity);
   }

   @Nullable
   private static DQuaternion getCurrentAnimationDelta() {
      Double animationProgress = getAnimationProgress();
      if (animationProgress != null) {
         double progress = animationProgress;
         return DQuaternion.interpolate(animationDeltaStart, DQuaternion.identity, mapProgress(progress));
      } else {
         return null;
      }
   }

   private static void processTransformation(Camera camera, MatrixStack matrixStack) {
      DQuaternion currentAnimationDelta = getCurrentAnimationDelta();
      if (currentAnimationDelta != null) {
         matrixStack.multiply(currentAnimationDelta.toMcQuaternion());
      }

      WorldRenderInfo.applyAdditionalTransformations(matrixStack);
   }

   public static Matrix4f processTransformation(Camera camera, Matrix4f matrix4f) {
      MatrixStack poseStack = new MatrixStack();
      poseStack.peek().getPositionMatrix().set(matrix4f);
      processTransformation(camera, poseStack);
      return poseStack.peek().getPositionMatrix();
   }

   public static boolean isAnimationRunning() {
      return getAnimationProgress() != null;
   }

   @Nullable
   public static Double getAnimationProgress() {
      if (interpolationStartTime == 0L) {
         return null;
      } else if (animationDeltaStart == null) {
         return null;
      } else {
         double progress = (RenderStates.renderStartNanoTime - interpolationStartTime) / ((double)interpolationEndTime - interpolationStartTime);
         return progress >= 0.0 && progress <= 1.0 ? progress : null;
      }
   }

   public static double mapProgress(double progress) {
      return Math.sin(progress * (Math.PI / 2));
   }

   public static void managePlayerRotationAndChangeGravity(Portal portal) {
      if (portal.getRotation() != null) {
         ClientPlayerEntity player = client.player;
         Direction oldGravityDir = GravityChangerInterface.invoker.getGravityDirection(player);
         DQuaternion oldCameraRotation = getCameraRotationWithGravity(
            oldGravityDir, player.getPitch(RenderStates.getPartialTick()), player.getYaw(RenderStates.getPartialTick())
         );
         DQuaternion currentAnimationDelta = getCurrentAnimationDelta();
         if (currentAnimationDelta != null) {
            oldCameraRotation = oldCameraRotation.hamiltonProduct(currentAnimationDelta);
         }

         DQuaternion immediateFinalRot = oldCameraRotation.hamiltonProduct(portal.getRotation().getConjugated());
         Direction oldBaseGravityDir = GravityChangerInterface.invoker.getBaseGravityDirection(player);
         Direction newBaseGravityDir = portal.getTeleportedGravityDirection(oldBaseGravityDir);
         if (newBaseGravityDir != oldBaseGravityDir) {
            GravityChangerInterface.invoker.setClientPlayerGravityDirection(player, newBaseGravityDir);
         }

         Direction immediateNewGravityDir = GravityChangerInterface.invoker.getGravityDirection(player);
         DQuaternion newGravityRot = DQuaternion.fromNullable(GravityChangerInterface.invoker.getExtraCameraRotation(immediateNewGravityDir));
         DQuaternion newRawCameraRotation = immediateFinalRot.hamiltonProduct(newGravityRot.getConjugated());
         Pair<Double, Double> pitchYaw = DQuaternion.getPitchYawFromRotation(newRawCameraRotation);
         float finalYaw = (float)((Double)pitchYaw.getRight()).doubleValue();
         float finalPitch = (float)((Double)pitchYaw.getLeft()).doubleValue();
         if (finalPitch > 90.0F) {
            finalPitch = 90.0F - (finalPitch - 90.0F);
         } else if (finalPitch < -90.0F) {
            finalPitch = -90.0F + (-90.0F - finalPitch);
         }

         player.setYaw(finalYaw);
         player.setPitch(finalPitch);
         player.lastYaw = finalYaw;
         player.lastPitch = finalPitch;
         player.renderYaw = finalYaw;
         player.renderPitch = finalPitch;
         player.lastRenderYaw = finalYaw;
         player.lastRenderPitch = finalPitch;
         DQuaternion newCameraRotationWithGravity = getCameraRotationWithGravity(immediateNewGravityDir, finalPitch, finalYaw);
         DQuaternion newAnimationDelta = newCameraRotationWithGravity.getConjugated().hamiltonProduct(immediateFinalRot);
         if (newAnimationDelta.getRotatingAngleDegrees() > 0.1) {
            animationDeltaStart = newAnimationDelta;
            interpolationStartTime = RenderStates.renderStartNanoTime;
            interpolationEndTime = interpolationStartTime + Helper.secondToNano(getAnimationDurationSeconds());
         }

         updateCamera(client);
      }
   }

   private static double getAnimationDurationSeconds() {
      return 1.0;
   }

   private static void updateCamera(MinecraftClient client) {
      Camera camera = client.gameRenderer.getCamera();
      camera.update(
         client.world,
         client.player,
         !client.options.getPerspective().isFirstPerson(),
         client.options.getPerspective().isFrontView(),
         RenderStates.getPartialTick()
      );
   }

   public static Matrix4f getMirrorTransformation(Vec3d normal) {
      float x = (float)normal.x;
      float y = (float)normal.y;
      float z = (float)normal.z;
      Matrix4f matrix = new Matrix4f();
      matrix.reflection(x, y, z, 0.0F);
      return matrix;
   }

   public static Matrix4f getIsometricProjection() {
      int w = client.getWindow().getFramebufferWidth();
      int h = client.getWindow().getFramebufferHeight();
      float wView = isometricViewLength / h * w;
      float near = -2000.0F;
      float far = 2000.0F;
      float left = -wView / 2.0F;
      float right = wView / 2.0F;
      float top = isometricViewLength / 2.0F;
      float bottom = -isometricViewLength / 2.0F;
      float[] arr = new float[]{
         2.0F / (right - left),
         0.0F,
         0.0F,
         -(right + left) / (right - left),
         0.0F,
         2.0F / (top - bottom),
         0.0F,
         -(top + bottom) / (top - bottom),
         0.0F,
         0.0F,
         -2.0F / (far - near),
         -(far + near) / (far - near),
         0.0F,
         0.0F,
         0.0F,
         1.0F
      };
      Matrix4f m1 = new Matrix4f();
      m1.set(arr);
      return m1;
   }

   public static Vec3d getIsometricAdjustedCameraPos() {
      Camera camera = client.gameRenderer.getCamera();
      return getIsometricAdjustedCameraPos(camera);
   }

   public static Vec3d getIsometricAdjustedCameraPos(Camera camera) {
      Vec3d cameraPos = camera.getCameraPos();
      if (!isIsometricView) {
         return cameraPos;
      } else {
         Quaternionf rotation = camera.getRotation();
         Vector3f vec = new Vector3f(0.0F, 0.0F, client.options.getClampedViewDistance() * -10);
         rotation.transform(vec);
         return cameraPos.add(vec.x(), vec.y(), vec.z());
      }
   }

   @Environment(EnvType.CLIENT)
   public static class RemoteCallables {
      public static void enableIsometricView(float viewLength) {
         TransformationManager.isometricViewLength = viewLength;
         TransformationManager.isIsometricView = true;
         TransformationManager.client.chunkCullingEnabled = false;
      }

      public static void disableIsometricView() {
         TransformationManager.isIsometricView = false;
         TransformationManager.client.chunkCullingEnabled = true;
      }
   }
}
