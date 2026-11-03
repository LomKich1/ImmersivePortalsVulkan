package qouteall.imm_ptl.core.portal.custom_portal_gen.form;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.function.Predicate;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import qouteall.imm_ptl.core.portal.custom_portal_gen.SimpleBlockPredicate;
import qouteall.imm_ptl.core.portal.nether_portal.BlockPortalShape;

public class HeterogeneousForm extends NetherPortalLikeForm {
   public final SimpleBlockPredicate areaBlock;
   public final SimpleBlockPredicate frameBlock;
   public static final MapCodec<HeterogeneousForm> CODEC = RecordCodecBuilder.mapCodec(
      instance -> instance.group(
            Codec.BOOL.fieldOf("generate_frame_if_not_found").forGetter(o -> o.generateFrameIfNotFound),
            SimpleBlockPredicate.CODEC.fieldOf("area_block").forGetter(o -> o.areaBlock),
            SimpleBlockPredicate.CODEC.fieldOf("frame_block").forGetter(o -> o.frameBlock)
         )
         .apply(instance, HeterogeneousForm::new)
   );

   public HeterogeneousForm(boolean generateFrameIfNotFound, SimpleBlockPredicate areaBlock, SimpleBlockPredicate frameBlock) {
      super(generateFrameIfNotFound);
      this.areaBlock = areaBlock;
      this.frameBlock = frameBlock;
   }

   @Override
   public void generateNewFrame(ServerWorld fromWorld, BlockPortalShape fromShape, ServerWorld toWorld, BlockPortalShape toShape) {
      BlockPos offset = toShape.innerAreaBox.l.subtract(fromShape.innerAreaBox.l);
      fromShape.frameAreaWithoutCorner.forEach(blockPos -> toWorld.setBlockState(blockPos.add(offset), fromWorld.getBlockState(blockPos)));
   }

   @Override
   public Predicate<BlockState> getOtherSideFramePredicate() {
      return this.frameBlock;
   }

   @Override
   public Predicate<BlockState> getThisSideFramePredicate() {
      return this.frameBlock;
   }

   @Override
   public Predicate<BlockState> getAreaPredicate() {
      return this.areaBlock;
   }

   @Override
   public MapCodec<? extends PortalGenForm> getCodec() {
      return CODEC;
   }

   @Override
   public PortalGenForm getReverse() {
      return new HeterogeneousForm(this.generateFrameIfNotFound, this.areaBlock, this.frameBlock);
   }
}
