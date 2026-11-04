package qouteall.imm_ptl.peripheral.mixin.common.nether_portal;

import java.util.Optional;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.dimension.NetherPortal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.platform_specific.O_O;
import qouteall.imm_ptl.peripheral.portal_generation.IntrinsicPortalGeneration;

@Mixin({AbstractFireBlock.class})
public class MixinAbstractFireBlock_CVB {
   @Redirect(
      method = {"Lnet/minecraft/block/AbstractFireBlock;onBlockAdded(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Z)V"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/dimension/NetherPortal;getNewPortal(Lnet/minecraft/world/WorldAccess;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Direction$Axis;)Ljava/util/Optional;"
      )
   )
   Optional<NetherPortal> redirectCreateAreaHelper(WorldAccess worldAccess, BlockPos blockPos, Axis axis) {
      if (IPGlobal.netherPortalMode == IPGlobal.NetherPortalMode.disabled) {
         return Optional.empty();
      } else if (IPGlobal.netherPortalMode == IPGlobal.NetherPortalMode.vanilla) {
         return NetherPortal.getNewPortal(worldAccess, blockPos, axis);
      } else {
         if (isNearObsidian(worldAccess, blockPos)) {
            IntrinsicPortalGeneration.onFireLitOnObsidian((ServerWorld)worldAccess, blockPos, null);
         }

         return Optional.empty();
      }
   }

   private static boolean isNearObsidian(WorldAccess access, BlockPos blockPos) {
      for (Direction value : Direction.values()) {
         if (O_O.isObsidian(access.getBlockState(blockPos.offset(value)))) {
            return true;
         }
      }

      return false;
   }

   @Redirect(
      method = {"Lnet/minecraft/block/AbstractFireBlock;shouldLightPortalAt(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Direction;)Z"},
      at = @At(
         value = "INVOKE",
         target = "Ljava/util/Optional;isPresent()Z"
      )
   )
   private static boolean redirectIsPresent(Optional optional) {
      return IPGlobal.netherPortalMode != IPGlobal.NetherPortalMode.vanilla ? true : optional.isPresent();
   }
}
