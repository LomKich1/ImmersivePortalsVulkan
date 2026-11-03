package qouteall.imm_ptl.core.mixin.common.interaction;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import java.util.ArrayList;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BucketItem;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.RaycastContext.class_242;
import net.minecraft.world.RaycastContext.class_3960;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import qouteall.imm_ptl.core.portal.PortalUtils;

@Mixin({BucketItem.class})
public class MixinBucketItem {
   @Redirect(
      method = {"use"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/BucketItem;raycast(Lnet/minecraft/World;Lnet/minecraft/PlayerEntity;Lnet/minecraft/RaycastContext.FluidHandling;)Lnet/minecraft/BlockHitResult;"
      )}
   )
   private BlockHitResult redirectRayTrace(World level, PlayerEntity player, class_242 fluid, @Local(argsOnly = true) LocalRef<World> worldRef) {
      double distance = 5.0;
      Vec3d eyePos = player.getEyePos();
      Vec3d viewVector = player.getRotationVec(1.0F);
      PortalUtils.PortalAwareRaytraceResult r = PortalUtils.portalAwareRayTraceFull(
         player.getEntityWorld(), eyePos, viewVector, distance, player, class_3960.field_17559, fluid, new ArrayList<>(), 1
      );
      if (r != null) {
         worldRef.set(r.world());
         return r.hitResult();
      } else {
         Vec3d dest = eyePos.add(viewVector.multiply(distance));
         return BlockHitResult.createMissed(
            dest, Direction.getFacing(viewVector.x, viewVector.y, viewVector.z), BlockPos.ofFloored(dest)
         );
      }
   }
}
