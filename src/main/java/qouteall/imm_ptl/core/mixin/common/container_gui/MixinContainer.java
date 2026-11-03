package qouteall.imm_ptl.core.mixin.common.container_gui;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.inventory.Inventory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import qouteall.imm_ptl.core.block_manipulation.BlockManipulationServer;

@Mixin({Inventory.class})
public interface MixinContainer {
   @WrapOperation(
      method = {"canPlayerUse(Lnet/minecraft/BlockEntity;Lnet/minecraft/PlayerEntity;F)Z"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/PlayerEntity;canInteractWithBlockAt(Lnet/minecraft/BlockPos;D)Z"
      )}
   )
   private static boolean wrapCanInteractWithBlock(
      PlayerEntity player, BlockPos blockPos, double distance, Operation<Boolean> operation, @Local BlockEntity blockEntity
   ) {
      Boolean originalResult = (Boolean)operation.call(new Object[]{player, blockPos, distance});
      if (!originalResult) {
         BlockPos targetPos = blockEntity.getPos();
         World targetWorld = blockEntity.getWorld();
         return BlockManipulationServer.validateReach(player, targetWorld, targetPos);
      } else {
         return true;
      }
   }
}
