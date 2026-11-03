package qouteall.imm_ptl.core.portal.custom_portal_gen.form;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.function.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos.class_2339;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.portal.custom_portal_gen.PortalGenInfo;
import qouteall.imm_ptl.core.portal.nether_portal.BlockPortalShape;
import qouteall.imm_ptl.core.portal.nether_portal.FastBlockPortalShape;
import qouteall.imm_ptl.core.portal.nether_portal.FrameSearching;
import qouteall.imm_ptl.core.portal.nether_portal.NetherPortalGeneration;
import qouteall.q_misc_util.my_util.IntBox;

public class ScalingSquareForm extends NetherPortalLikeForm {
   public static final MapCodec<ScalingSquareForm> CODEC = RecordCodecBuilder.mapCodec(
      instance -> instance.group(
            Registries.BLOCK.getCodec().fieldOf("from_frame_block").forGetter(o -> o.fromFrameBlock),
            Registries.BLOCK.getCodec().fieldOf("area_block").forGetter(o -> o.areaBlock),
            Registries.BLOCK.getCodec().fieldOf("to_frame_block").forGetter(o -> o.toFrameBlock),
            Codec.INT.fieldOf("from_length").forGetter(o -> o.fromLength),
            Codec.INT.fieldOf("to_length").forGetter(o -> o.toLength),
            Codec.BOOL.fieldOf("generate_frame_if_not_found").forGetter(o -> o.generateFrameIfNotFound)
         )
         .apply(instance, instance.stable(ScalingSquareForm::new))
   );
   public final Block fromFrameBlock;
   public final Block areaBlock;
   public final Block toFrameBlock;
   public final int fromLength;
   public final int toLength;

   public ScalingSquareForm(
      Block fromFrameBlock, Block areaBlock, Block toFrameBlock, int fromLength, int toLength, boolean generateFrameIfNotFound
   ) {
      super(generateFrameIfNotFound);
      this.fromFrameBlock = fromFrameBlock;
      this.areaBlock = areaBlock;
      this.toFrameBlock = toFrameBlock;
      this.fromLength = fromLength;
      this.toLength = toLength;
   }

   @Override
   public MapCodec<? extends PortalGenForm> getCodec() {
      return CODEC;
   }

   @Override
   public PortalGenForm getReverse() {
      return new ScalingSquareForm(this.toFrameBlock, this.areaBlock, this.fromFrameBlock, this.toLength, this.fromLength, this.generateFrameIfNotFound);
   }

   @Override
   public boolean testThisSideShape(ServerWorld fromWorld, BlockPortalShape fromShape) {
      return BlockPortalShape.isSquareShape(fromShape, this.fromLength);
   }

   @Override
   public FrameSearching.FrameSearchingFunc<PortalGenInfo> getFrameMatchingFunc(ServerWorld fromWorld, ServerWorld toWorld, BlockPortalShape fromShape) {
      BlockPortalShape template = this.getTemplateToShape(fromShape);
      FastBlockPortalShape fastTemplate = FastBlockPortalShape.fromBlockPortalShape(template);
      Predicate<BlockState> areaPredicate = this.getAreaPredicate();
      Predicate<BlockState> otherSideFramePredicate = this.getOtherSideFramePredicate();
      new class_2339();
      return (blockAccess, x, y, z) -> {
         boolean matches = fastTemplate.matchShape(
            x,
            y,
            z,
            (px, py, pz) -> otherSideFramePredicate.test(blockAccess.getBlockState(px, py, pz)),
            (px, py, pz) -> areaPredicate.test(blockAccess.getBlockState(px, py, pz))
         );
         if (matches) {
            boolean matchToSelf = fromWorld == toWorld && x == fastTemplate.basePosX() && y == fastTemplate.basePosY() && z == fastTemplate.basePosZ();
            if (!matchToSelf) {
               return new PortalGenInfo(
                  fromWorld.getRegistryKey(), toWorld.getRegistryKey(), fromShape, fastTemplate.withNewBase(x, y, z).toBlockPortalShape(), null, this.getScale()
               );
            }
         }

         return null;
      };
   }

   private double getScale() {
      return (double)this.toLength / this.fromLength;
   }

   private BlockPortalShape getTemplateToShape(BlockPortalShape fromShape) {
      return BlockPortalShape.getSquareShapeTemplate(fromShape.axis, this.toLength);
   }

   @Override
   public void generateNewFrame(ServerWorld fromWorld, BlockPortalShape fromShape, ServerWorld toWorld, BlockPortalShape toShape) {
      for (BlockPos blockPos : toShape.frameAreaWithCorner) {
         toWorld.setBlockState(blockPos, this.toFrameBlock.getDefaultState());
      }
   }

   @Nullable
   @Override
   public PortalGenInfo getNewPortalPlacement(
      ServerWorld toWorld, BlockPos toPos, ServerWorld fromWorld, BlockPortalShape fromShape, @Nullable Entity triggeringEntity
   ) {
      BlockPortalShape templateShape = this.getTemplateToShape(fromShape);
      IntBox airCubePlacement = NetherPortalGeneration.findAirCubePlacement(toWorld, toPos, templateShape.axis, templateShape.totalAreaBox.getSize(), true);
      if (airCubePlacement == null) {
         return null;
      } else {
         BlockPortalShape placedShape = templateShape.getShapeWithMovedTotalAreaBox(airCubePlacement);
         return new PortalGenInfo(fromWorld.getRegistryKey(), toWorld.getRegistryKey(), fromShape, placedShape, null, this.getScale());
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
