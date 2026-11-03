package qouteall.imm_ptl.core.mixin.common;

import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.class_3960;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.ducks.IERayTraceContext;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalPlaceholderBlock;

@Mixin({RaycastContext.class})
public abstract class MixinClipContext implements IERayTraceContext {
   @Shadow
   @Final
   @Mutable
   private Vec3d start;
   @Shadow
   @Final
   @Mutable
   private Vec3d end;
   @Shadow
   @Final
   private class_3960 shapeType;
   @Shadow
   @Final
   private ShapeContext shapeContext;

   @Override
   public IERayTraceContext ip_setStart(Vec3d newStart) {
      this.start = newStart;
      return this;
   }

   @Override
   public IERayTraceContext ip_setEnd(Vec3d newEnd) {
      this.end = newEnd;
      return this;
   }

   @Inject(
      at = {@At("HEAD")},
      method = {"Lnet/minecraft/RaycastContext;getBlockShape(Lnet/minecraft/BlockState;Lnet/minecraft/BlockView;Lnet/minecraft/BlockPos;)Lnet/minecraft/VoxelShape;"},
      cancellable = true
   )
   private void onGetBlockShape(BlockState blockState, BlockView blockView, BlockPos blockPos, CallbackInfoReturnable<VoxelShape> cir) {
      if (blockState.getBlock() == PortalPlaceholderBlock.instance && this.shapeType == class_3960.field_17559 && blockView instanceof World world) {
         boolean intersectingWithPortal = McHelper.traverseEntitiesByBox(
               Portal.class, world, new Box(blockPos), IPGlobal.maxNormalPortalRadius, p -> McHelper.placeholder
            )
            != null;
         if (intersectingWithPortal) {
            cir.setReturnValue(VoxelShapes.empty());
         }
      }
   }
}
