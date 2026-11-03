package qouteall.imm_ptl.core.portal.custom_portal_gen.form;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.function.Predicate;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import qouteall.imm_ptl.core.portal.nether_portal.BlockPortalShape;

public class DiligentForm extends AbstractDiligentForm {
   public static final MapCodec<DiligentForm> CODEC = RecordCodecBuilder.mapCodec(
      instance -> instance.group(
            Registries.BLOCK.getCodec().fieldOf("from_frame_block").forGetter(o -> o.fromFrameBlock),
            Registries.BLOCK.getCodec().fieldOf("area_block").forGetter(o -> o.areaBlock),
            Registries.BLOCK.getCodec().fieldOf("to_frame_block").forGetter(o -> o.toFrameBlock),
            Codec.BOOL.fieldOf("generate_frame_if_not_found").forGetter(o -> o.generateFrameIfNotFound)
         )
         .apply(instance, instance.stable(DiligentForm::new))
   );
   public final Block fromFrameBlock;
   public final Block areaBlock;
   public final Block toFrameBlock;

   public DiligentForm(Block fromFrameBlock, Block areaBlock, Block toFrameBlock, boolean generateFrameIfNotFound) {
      super(generateFrameIfNotFound);
      this.fromFrameBlock = fromFrameBlock;
      this.areaBlock = areaBlock;
      this.toFrameBlock = toFrameBlock;
   }

   @Override
   public MapCodec<? extends PortalGenForm> getCodec() {
      return CODEC;
   }

   @Override
   public PortalGenForm getReverse() {
      return new DiligentForm(this.toFrameBlock, this.areaBlock, this.fromFrameBlock, this.generateFrameIfNotFound);
   }

   @Override
   public void generateNewFrame(ServerWorld fromWorld, BlockPortalShape fromShape, ServerWorld toWorld, BlockPortalShape toShape) {
      for (BlockPos blockPos : toShape.frameAreaWithCorner) {
         toWorld.setBlockState(blockPos, this.toFrameBlock.getDefaultState());
      }
   }

   @Override
   public Predicate<BlockState> getOtherSideFramePredicate() {
      return s -> s.getBlock() == this.toFrameBlock;
   }

   @Override
   public Predicate<BlockState> getThisSideFramePredicate() {
      return s -> s.getBlock() == this.fromFrameBlock;
   }

   @Override
   public Predicate<BlockState> getAreaPredicate() {
      return s -> s.getBlock() == this.areaBlock;
   }
}
