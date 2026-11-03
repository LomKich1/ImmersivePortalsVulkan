package qouteall.imm_ptl.core.mixin.client;

import net.minecraft.entity.PositionInterpolator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.portal.Portal;

@Mixin({PositionInterpolator.class})
public abstract class MixinInterpolationHandler {
   @Shadow
   @Final
   private Entity entity;

   @Inject(
      method = {"refreshPositionAndAngles"},
      at = {@At("RETURN")}
   )
   private void onUpdateTrackedPositionAndAngles(Vec3d targetPos, float yaw, float pitch, CallbackInfo ci) {
      if (this.entity instanceof LivingEntity this_) {
         if (!IPGlobal.allowClientEntityPosInterpolation) {
            this_.setPosition(targetPos.x, targetPos.y, targetPos.z);
            return;
         }

         Portal collidingPortal = ((IEEntity)this_).ip_getCollidingPortal();
         if (collidingPortal != null) {
            double dx = this_.getX() - targetPos.x;
            double dy = this_.getY() - targetPos.y;
            double dz = this_.getZ() - targetPos.z;
            if (dx * dx + dy * dy + dz * dz > 4.0) {
               Vec3d currPos = new Vec3d(targetPos.x, targetPos.y, targetPos.z);
               McHelper.setPosAndLastTickPos(this_, currPos, currPos.subtract(McHelper.getWorldVelocity(this_)));
               McHelper.updateBoundingBox(this_);
            }
         }
      } else if (this.entity instanceof AbstractMinecartEntity this_ && !IPGlobal.allowClientEntityPosInterpolation) {
         this_.setPosition(targetPos.x, targetPos.y, targetPos.z);
      }
   }
}
