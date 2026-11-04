package qouteall.imm_ptl.core.mixin.client.render;

import net.minecraft.client.render.Frustum;
import org.joml.FrustumIntersection;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import qouteall.imm_ptl.core.miscellaneous.IPVanillaCopy;
import qouteall.imm_ptl.core.render.TransformationManager;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.LimitedLogger;

@Mixin({Frustum.class})
public abstract class MixinFrustum_FixDeadLoop {
   @Shadow
   private double x;
   @Shadow
   private double y;
   @Shadow
   private double z;
   @Shadow
   private Vector4f recession;
   @Shadow
   @Final
   private FrustumIntersection frustumIntersection;
   private static LimitedLogger limitedLogger = new LimitedLogger(10);

   @Overwrite
   @IPVanillaCopy
   public Frustum coverBoxAroundSetPosition(int gridSize) {
      if (TransformationManager.isIsometricView) {
         return (Frustum)(Object)this;
      } else {
         double minX = Math.floor(this.x / gridSize) * gridSize;
         double minY = Math.floor(this.y / gridSize) * gridSize;
         double minZ = Math.floor(this.z / gridSize) * gridSize;
         double maxX = Math.ceil(this.x / gridSize) * gridSize;
         double maxY = Math.ceil(this.y / gridSize) * gridSize;
         double maxZ = Math.ceil(this.z / gridSize) * gridSize;
         int countLimit = 10;

         while (
            this.frustumIntersection
                  .intersectAab(
                     (float)(minX - this.x),
                     (float)(minY - this.y),
                     (float)(minZ - this.z),
                     (float)(maxX - this.x),
                     (float)(maxY - this.y),
                     (float)(maxZ - this.z)
                  )
               != -2
         ) {
            this.x = this.x - this.recession.x() * 4.0F;
            this.y = this.y - this.recession.y() * 4.0F;
            this.z = this.z - this.recession.z() * 4.0F;
            if (--countLimit <= 0) {
               limitedLogger.invoke(() -> {
                  Helper.err("the projection matrix and the frustum are abnormal");
                  new Throwable().printStackTrace();
               });
               break;
            }
         }

         return (Frustum)(Object)this;
      }
   }
}
