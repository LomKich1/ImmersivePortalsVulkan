package qouteall.imm_ptl.core.portal;

import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.World;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.registry.RegistryKey;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.DQuaternion;
import qouteall.q_misc_util.my_util.Plane;

public interface PortalLike {
   boolean isConventionalPortal();

   Box getThinBoundingBox();

   Vec3d transformPoint(Vec3d var1);

   Vec3d transformLocalVec(Vec3d var1);

   Vec3d transformLocalVecNonScale(Vec3d var1);

   Vec3d inverseTransformLocalVec(Vec3d var1);

   Vec3d inverseTransformPoint(Vec3d var1);

   double getDistanceToNearestPointInPortal(Vec3d var1);

   double getDestAreaRadiusEstimation();

   Vec3d getOriginPos();

   Vec3d getDestPos();

   World getOriginWorld();

   World getDestWorld();

   RegistryKey<World> getDestDim();

   @Environment(EnvType.CLIENT)
   boolean isRoughlyVisibleTo(Vec3d var1);

   @Nullable
   Plane getInnerClipping();

   @Nullable
   DQuaternion getRotation();

   double getScale();

   boolean getIsGlobal();

   boolean isVisible();

   @Nullable
   Vec3d[] getOuterFrustumCullingVertices();

   @Nullable
   Matrix4f getAdditionalCameraTransformation();

   @Nullable
   UUID getDiscriminator();

   boolean cannotRenderInMe(Portal var1);

   boolean isFuseView();

   boolean getDoRenderPlayer();

   boolean getHasCrossPortalCollision();

   default boolean hasScaling() {
      return Math.abs(this.getScale() - 1.0) > 0.01;
   }

   default RegistryKey<World> getOriginDim() {
      return this.getOriginWorld().getRegistryKey();
   }

   default boolean isOnDestinationSide(Vec3d entityPos, double valve) {
      Plane innerClipping = this.getInnerClipping();
      if (innerClipping == null) {
         return true;
      } else {
         double v = entityPos.subtract(innerClipping.pos()).dotProduct(innerClipping.normal());
         return v > valve;
      }
   }

   default double getSizeEstimation() {
      Vec3d boxSize = Helper.getBoxSize(this.getThinBoundingBox());
      return Math.max(Math.max(boxSize.x, boxSize.y), boxSize.z);
   }
}
