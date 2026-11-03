package qouteall.imm_ptl.peripheral.mixin.common.nether_portal;

import net.minecraft.util.ActionResult;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FlintAndSteelItem;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.World;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.EntityPose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.portal.BreakableMirror;
import qouteall.imm_ptl.peripheral.PeripheralModMain;
import qouteall.imm_ptl.peripheral.portal_generation.IntrinsicPortalGeneration;

@Mixin({FlintAndSteelItem.class})
public class MixinFlintAndSteelItem_CVB {
   @Inject(
      method = {"Lnet/minecraft/FlintAndSteelItem;useOnBlock(Lnet/minecraft/ItemUsageContext;)Lnet/minecraft/ActionResult;"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onUseFlintAndSteel(ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
      WorldAccess world = context.getWorld();
      if (!world.isClient()) {
         BlockPos targetPos = context.getBlockPos();
         Direction side = context.getSide();
         BlockPos firePos = targetPos.method_10093(side);
         BlockState targetBlockState = world.getBlockState(targetPos);
         Block targetBlock = targetBlockState.getBlock();
         if (BreakableMirror.isGlass((World)world, targetPos) && IPGlobal.enableMirrorCreation) {
            BreakableMirror mirror = BreakableMirror.createMirror((ServerWorld)world, targetPos, side);
            cir.setReturnValue(ActionResult.SUCCESS);
            return;
         }

         if (targetBlock == PeripheralModMain.portalHelperBlock) {
            boolean result = IntrinsicPortalGeneration.activatePortalHelper((ServerWorld)world, firePos);
            cir.setReturnValue(ActionResult.SUCCESS);
            return;
         }

         if (targetBlock == Blocks.field_10540) {
            PlayerEntity player = context.getPlayer();
            if (player != null) {
               if (player.getPose() == EntityPose.field_18081) {
                  boolean succeeded = IntrinsicPortalGeneration.onCrouchingPlayerIgnite((ServerWorld)world, (ServerPlayerEntity)player, firePos);
                  if (succeeded) {
                     cir.setReturnValue(ActionResult.SUCCESS);
                     return;
                  }
               }

               boolean succ = IntrinsicPortalGeneration.onFireLitOnObsidian((ServerWorld)world, firePos, player);
               if (succ) {
                  cir.setReturnValue(ActionResult.SUCCESS);
                  return;
               }
            }
         }
      }
   }
}
