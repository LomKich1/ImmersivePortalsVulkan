package qouteall.imm_ptl.core.render.context_management;

import java.util.List;
import java.util.Stack;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.ducks.IECamera;

public class WorldRenderInfo {
   public final ClientWorld world;
   public final Vec3d cameraPos;
   public final boolean overwriteCameraTransformation;
   @Nullable
   public final Matrix4f cameraTransformation;
   @Nullable
   public final UUID description;
   public final int renderDistance;
   public final boolean doRenderHand;
   public final boolean enableViewBobbing;
   public final boolean doRenderSky;
   public final boolean hasFog;
   @Nullable
   public final WorldRenderInfo.IsometricParameters isometricParameters;
   private static final Stack<WorldRenderInfo> renderInfoStack = new Stack<>();
   @Nullable
   private static List<UUID> renderingDescCache = null;

   private WorldRenderInfo(
      ClientWorld world,
      Vec3d cameraPos,
      @Nullable Matrix4f cameraTransformation,
      boolean overwriteCameraTransformation,
      @Nullable UUID description,
      int renderDistance,
      boolean doRenderHand,
      boolean enableViewBobbing,
      boolean doRenderSky,
      boolean hasFog,
      @Nullable WorldRenderInfo.IsometricParameters isometricParameters
   ) {
      this.world = world;
      this.cameraPos = cameraPos;
      this.cameraTransformation = cameraTransformation;
      this.description = description;
      this.renderDistance = renderDistance;
      this.overwriteCameraTransformation = overwriteCameraTransformation;
      this.doRenderHand = doRenderHand;
      this.enableViewBobbing = enableViewBobbing;
      this.doRenderSky = doRenderSky;
      this.hasFog = hasFog;
      this.isometricParameters = isometricParameters;
   }

   public static void pushRenderInfo(WorldRenderInfo worldRenderInfo) {
      renderInfoStack.push(worldRenderInfo);
      renderingDescCache = null;
   }

   public static void popRenderInfo() {
      renderInfoStack.pop();
      renderingDescCache = null;
   }

   public static void adjustCameraPos(Camera camera) {
      if (!renderInfoStack.isEmpty()) {
         WorldRenderInfo currWorldRenderInfo = getTopRenderInfo();
         ((IECamera)camera).portal_setPos(currWorldRenderInfo.cameraPos);
         ((IECamera)camera).portal_tickAttributeProbe();
      }
   }

   public static void applyAdditionalTransformations(MatrixStack matrixStack) {
      for (WorldRenderInfo worldRenderInfo : renderInfoStack) {
         if (worldRenderInfo.overwriteCameraTransformation) {
            matrixStack.peek().getPositionMatrix().identity();
            matrixStack.peek().getNormalMatrix().identity();
         }

         Matrix4f matrix = worldRenderInfo.cameraTransformation;
         if (matrix != null) {
            matrixStack.peek().getPositionMatrix().mul(matrix);
            Matrix3f normalMatrixMult = new Matrix3f(matrix);
            normalMatrixMult.scale((float)Math.pow(1.0 / Math.abs(normalMatrixMult.determinant()), 0.3333333333333333));
            matrixStack.peek().getNormalMatrix().mul(normalMatrixMult);
         }
      }
   }

   public static boolean isRendering() {
      return !renderInfoStack.empty();
   }

   public static int getRenderingLayer() {
      return renderInfoStack.size();
   }

   public static List<UUID> getRenderingDescription() {
      if (renderingDescCache == null) {
         renderingDescCache = renderInfoStack.stream().map(renderInfo -> renderInfo.description).collect(Collectors.toList());
      }

      return renderingDescCache;
   }

   public static int getRenderDistance() {
      return renderInfoStack.isEmpty() ? MinecraftClient.getInstance().options.getClampedViewDistance() : getTopRenderInfo().renderDistance;
   }

   public static WorldRenderInfo getTopRenderInfo() {
      return renderInfoStack.peek();
   }

   public static Vec3d getCameraPos() {
      Validate.isTrue(!renderInfoStack.isEmpty());
      return getTopRenderInfo().cameraPos;
   }

   public static boolean isViewBobbingEnabled() {
      return renderInfoStack.stream().allMatch(info -> info.enableViewBobbing);
   }

   public static boolean isFogEnabled() {
      if (IPGlobal.debugDisableFog) {
         return false;
      } else {
         return isRendering() ? getTopRenderInfo().hasFog : true;
      }
   }

   public static class Builder {
      private ClientWorld world;
      private Vec3d cameraPos;
      private Matrix4f cameraTransformation = null;
      private boolean overwriteCameraTransformation = true;
      private UUID description = null;
      private int renderDistance = MinecraftClient.getInstance().options.getClampedViewDistance();
      private boolean doRenderHand = false;
      private boolean enableViewBobbing = true;
      private boolean doRenderSky = true;
      private boolean hasFog = true;
      @Nullable
      private WorldRenderInfo.IsometricParameters isometricParameters = null;

      public WorldRenderInfo.Builder setWorld(ClientWorld world) {
         this.world = world;
         return this;
      }

      public WorldRenderInfo.Builder setCameraPos(Vec3d cameraPos) {
         this.cameraPos = cameraPos;
         return this;
      }

      public WorldRenderInfo.Builder setCameraTransformation(Matrix4f cameraTransformation) {
         this.cameraTransformation = cameraTransformation;
         return this;
      }

      public WorldRenderInfo.Builder setOverwriteCameraTransformation(boolean overwriteCameraTransformation) {
         this.overwriteCameraTransformation = overwriteCameraTransformation;
         return this;
      }

      public WorldRenderInfo.Builder setDescription(UUID description) {
         this.description = description;
         return this;
      }

      public WorldRenderInfo.Builder setRenderDistance(int renderDistance) {
         this.renderDistance = renderDistance;
         return this;
      }

      public WorldRenderInfo.Builder setDoRenderHand(boolean doRenderHand) {
         this.doRenderHand = doRenderHand;
         return this;
      }

      public WorldRenderInfo.Builder setEnableViewBobbing(boolean enableViewBobbing) {
         this.enableViewBobbing = enableViewBobbing;
         return this;
      }

      public WorldRenderInfo.Builder setDoRenderSky(boolean doRenderSky) {
         this.doRenderSky = doRenderSky;
         return this;
      }

      public WorldRenderInfo.Builder setHasFog(boolean hasFog) {
         this.hasFog = hasFog;
         return this;
      }

      public WorldRenderInfo.Builder setIsometricParameters(@Nullable WorldRenderInfo.IsometricParameters isometricParameters) {
         this.isometricParameters = isometricParameters;
         return this;
      }

      public WorldRenderInfo build() {
         Validate.notNull(this.world);
         Validate.notNull(this.cameraPos);
         return new WorldRenderInfo(
            this.world,
            this.cameraPos,
            this.cameraTransformation,
            this.overwriteCameraTransformation,
            this.description,
            this.renderDistance,
            this.doRenderHand,
            this.enableViewBobbing,
            this.doRenderSky,
            this.hasFog,
            this.isometricParameters
         );
      }
   }

   public record IsometricParameters() {
   }
}
