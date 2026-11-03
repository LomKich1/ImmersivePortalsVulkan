package qouteall.imm_ptl.peripheral.mixin.common.nether_portal;

import java.util.Optional;
import net.minecraft.world.WorldAccess;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.dimension.NetherPortal;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.util.math.Direction.class_2351;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.platform_specific.O_O;
import qouteall.imm_ptl.peripheral.portal_generation.IntrinsicPortalGeneration;

@Mixin({AbstractFireBlock.class})
public class MixinAbstractFireBlock_CVB {
   @Redirect(
      method = {"Lnet/minecraft/AbstractFireBlock;onBlockAdded(Lnet/minecraft/BlockState;Lnet/minecraft/World;Lnet/minecraft/BlockPos;Lnet/minecraft/BlockState;Z)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/NetherPortal;getNewPortal(Lnet/minecraft/WorldAccess;Lnet/minecraft/BlockPos;Lnet/minecraft/Direction.Axis;)Ljava/util/Optional;"
      )}
   )
   Optional<NetherPortal> redirectCreateAreaHelper(WorldAccess worldAccess, BlockPos blockPos, class_2351 axis) {
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
         if (O_O.isObsidian(access.getBlockState(blockPos.method_10093(value)))) {
            return true;
         }
      }

      return false;
   }

   @Redirect(
      method = {"Lnet/minecraft/AbstractFireBlock;shouldLightPortalAt(Lnet/minecraft/World;Lnet/minecraft/BlockPos;Lnet/minecraft/Direction;)Z"},
      at = {@At(
         value = "INVOKE",
         target = "Ljava/util/Optional;isPresent()Z"
      )}
   )
   private static boolean redirectIsPresent(Optional optional) {
      return IPGlobal.netherPortalMode != IPGlobal.NetherPortalMode.vanilla ? true : optional.isPresent();
   }
}
