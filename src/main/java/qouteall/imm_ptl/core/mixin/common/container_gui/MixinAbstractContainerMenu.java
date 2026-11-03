package qouteall.imm_ptl.core.mixin.common.container_gui;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import qouteall.imm_ptl.core.block_manipulation.BlockManipulationServer;

@Mixin({ScreenHandler.class})
public class MixinAbstractContainerMenu {
   @WrapOperation(
      method = {"lambda$stillValid$0", "method_17696"},
      require = 1,
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/entity/player/PlayerEntity;canInteractWithBlockAt(Lnet/minecraft/util/math/BlockPos;D)Z"
      )}
   )
   private static boolean wrapDistanceToSqr(
      PlayerEntity player, BlockPos blockPos, double distance, Operation<Boolean> operation, @Local(argsOnly = true) World world
   ) {
      boolean canInteract = (Boolean)operation.call(new Object[]{player, blockPos, distance});
      return canInteract ? true : BlockManipulationServer.validateReach(player, world, blockPos);
   }
}
