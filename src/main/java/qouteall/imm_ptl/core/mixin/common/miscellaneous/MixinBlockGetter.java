package qouteall.imm_ptl.core.mixin.common.miscellaneous;

import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.world.BlockView;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import qouteall.imm_ptl.core.IPMcHelper;
import qouteall.q_misc_util.Helper;

@Mixin({BlockView.class})
public interface MixinBlockGetter {
   @ModifyVariable(
      method = {"raycast"},
      at = {@At("HEAD")},
      argsOnly = true,
      index = 1
   )
   private static <T, C> Vec3d onTraverseBlocks(
      Vec3d originalArgument, Vec3d from, Vec3d _to, C context, BiFunction<C, BlockPos, T> tester, Function<C, T> onFail
   ) {
      if (from.squaredDistanceTo(_to) > 262144.0) {
         IPMcHelper.limitedLogger.invoke(() -> Helper.LOGGER.error("Raycast too far", new Throwable()));
         return _to.subtract(from).normalize().multiply(30.0).add(from);
      } else {
         return _to;
      }
   }
}
