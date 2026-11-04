package qouteall.imm_ptl.core.mixin.client.render.optimization;

import net.minecraft.client.render.Frustum;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface;
import qouteall.imm_ptl.core.ducks.IEFrustum;
import qouteall.imm_ptl.core.render.FrustumCuller;

@Mixin({Frustum.class})
public class MixinFrustum implements IEFrustum {
   @Shadow
   private double x;
   @Shadow
   private double y;
   @Shadow
   private double z;
   @Shadow
   private Vector4f recession;
   private double portal_camX;
   private double portal_camY;
   private double portal_camZ;
   private FrustumCuller portal_frustumCuller;

   @Inject(
      method = {"<init>(Lnet/minecraft/client/render/Frustum;)V"},
      at = @At("RETURN")
   )
   private void onFrustumCopy(Frustum other, CallbackInfo ci) {
      if (other instanceof IEFrustum) {
         MixinFrustum otherFrustum = (MixinFrustum)(Object)other;
         this.portal_camX = otherFrustum.portal_camX;
         this.portal_camY = otherFrustum.portal_camY;
         this.portal_camZ = otherFrustum.portal_camZ;
         this.portal_frustumCuller = otherFrustum.portal_frustumCuller;
      }
   }

   @Inject(
      method = {"Lnet/minecraft/client/render/Frustum;setPosition(DDD)V"},
      at = @At("TAIL")
   )
   private void onSetOrigin(double double_1, double double_2, double double_3, CallbackInfo ci) {
      if (!IrisInterface.invoker.isRenderingShadowMap()) {
         if (this.portal_frustumCuller == null) {
            this.portal_frustumCuller = new FrustumCuller();
         }

         this.portal_frustumCuller.update(this.x, this.y, this.z);
         this.portal_camX = this.x;
         this.portal_camY = this.y;
         this.portal_camZ = this.z;
      }
   }

   @Inject(
      method = {"intersectAab(DDDDDD)I"},
      at = @At("HEAD"),
      cancellable = true
   )
   private void onCubeInFrustum(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, CallbackInfoReturnable<Integer> cir) {
      if (this.ip_canDetermineInvisibleWithCamCoord(
         (float)(minX - this.portal_camX),
         (float)(minY - this.portal_camY),
         (float)(minZ - this.portal_camZ),
         (float)(maxX - this.portal_camX),
         (float)(maxY - this.portal_camY),
         (float)(maxZ - this.portal_camZ)
      )) {
         cir.setReturnValue(-3);
      }
   }

   @Inject(
      method = {"init"},
      at = @At("RETURN")
   )
   private void onCalculateFrustumReturn(Matrix4f matrix4f, Matrix4f matrix4f2, CallbackInfo ci) {
      this.recession.normalize();
   }

   @Override
   public boolean ip_canDetermineInvisibleWithCamCoord(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
      return this.portal_frustumCuller.canDetermineInvisibleWithCameraCoord(minX, minY, minZ, maxX, maxY, maxZ);
   }

   @Override
   public Vec3d ip_getViewVec3() {
      return new Vec3d(this.recession.x(), this.recession.y(), this.recession.z());
   }
}
