package qouteall.imm_ptl.core.portal.custom_portal_gen.form;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import net.minecraft.entity.Entity;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.SpawnReason;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Direction.class_2351;
import net.minecraft.util.math.Direction.class_2352;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.portal.PortalExtension;
import qouteall.imm_ptl.core.portal.PortalManipulation;
import qouteall.imm_ptl.core.portal.PortalPlaceholderBlock;
import qouteall.imm_ptl.core.portal.custom_portal_gen.CustomPortalGeneration;
import qouteall.imm_ptl.core.portal.custom_portal_gen.SimpleBlockPredicate;
import qouteall.imm_ptl.core.portal.nether_portal.BlockPortalShape;
import qouteall.imm_ptl.core.portal.nether_portal.GeneralBreakablePortal;
import qouteall.imm_ptl.core.portal.nether_portal.NetherPortalGeneration;
import qouteall.q_misc_util.my_util.DQuaternion;
import qouteall.q_misc_util.my_util.IntBox;

public class FlippingFloorSquareForm extends PortalGenForm {
   public static final Codec<List<Block>> blockListCodec = Registries.BLOCK.getCodec().listOf();
   public static final MapCodec<FlippingFloorSquareForm> CODEC = RecordCodecBuilder.mapCodec(
      instance -> instance.group(
            Codec.INT.fieldOf("length").forGetter(o -> o.length),
            SimpleBlockPredicate.CODEC.fieldOf("frame_block").forGetter(o -> o.frameBlock),
            SimpleBlockPredicate.CODEC.fieldOf("area_block").forGetter(o -> o.areaBlock),
            SimpleBlockPredicate.CODEC.optionalFieldOf("up_frame_block", SimpleBlockPredicate.pass).forGetter(o -> o.upFrameBlock),
            SimpleBlockPredicate.CODEC.optionalFieldOf("bottom_block", SimpleBlockPredicate.pass).forGetter(o -> o.bottomBlock)
         )
         .apply(instance, instance.stable(FlippingFloorSquareForm::new))
   );
   public final int length;
   public final SimpleBlockPredicate frameBlock;
   public final SimpleBlockPredicate areaBlock;
   public final SimpleBlockPredicate upFrameBlock;
   public final SimpleBlockPredicate bottomBlock;

   public FlippingFloorSquareForm(
      int length, SimpleBlockPredicate frameBlock, SimpleBlockPredicate areaBlock, SimpleBlockPredicate upFrameBlock, SimpleBlockPredicate bottomBlock
   ) {
      this.length = length;
      this.frameBlock = frameBlock;
      this.areaBlock = areaBlock;
      this.upFrameBlock = upFrameBlock;
      this.bottomBlock = bottomBlock;
   }

   @Override
   public MapCodec<? extends PortalGenForm> getCodec() {
      return CODEC;
   }

   @Override
   public PortalGenForm getReverse() {
      return this;
   }

   @Override
   public boolean perform(CustomPortalGeneration cpg, ServerWorld fromWorld, BlockPos startingPos, ServerWorld toWorld, @Nullable Entity triggeringEntity) {
      Predicate<BlockState> areaPredicate = this.areaBlock;
      Predicate<BlockState> framePredicate = this.frameBlock;
      Predicate<BlockState> bottomPredicate = this.bottomBlock;
      if (!areaPredicate.test(fromWorld.getBlockState(startingPos))) {
         return false;
      } else if (!bottomPredicate.test(fromWorld.getBlockState(startingPos.method_10074()))) {
         return false;
      } else {
         BlockPortalShape fromShape = BlockPortalShape.findArea(
            startingPos,
            class_2351.field_11052,
            blockPos -> areaPredicate.test(fromWorld.getBlockState(blockPos)),
            blockPos -> framePredicate.test(fromWorld.getBlockState(blockPos))
         );
         if (fromShape == null) {
            return false;
         } else if (!this.checkFromShape(fromWorld, fromShape)) {
            return false;
         } else {
            BlockPos areaSize = fromShape.innerAreaBox.getSize();
            BlockPos toPos = cpg.mapPosition(fromShape.innerAreaBox.l, fromWorld, toWorld);
            IntBox placingBox = findPortalPlacement(toWorld, areaSize, toPos);
            BlockPos offset = placingBox.l.method_10059(fromShape.innerAreaBox.l);
            BlockPortalShape toShape = fromShape.getShapeWithMovedAnchor(fromShape.anchor.method_10081(offset));
            fromShape.frameAreaWithoutCorner.forEach(fromWorldPos -> {
               BlockPos toWorldPos = fromWorldPos.method_10081(offset);
               toWorld.setBlockState(toWorldPos, fromWorld.getBlockState(fromWorldPos));
               toWorld.setBlockState(toWorldPos.up(), fromWorld.getBlockState(fromWorldPos.up()));
            });
            NetherPortalGeneration.fillInPlaceHolderBlocks(fromWorld, fromShape);
            NetherPortalGeneration.fillInPlaceHolderBlocks(toWorld, toShape);
            GeneralBreakablePortal[] portals = createPortals(fromWorld, toWorld, fromShape, toShape);
            cpg.onPortalsGenerated(portals);
            return true;
         }
      }
   }

   public boolean checkFromShape(ServerWorld fromWorld, BlockPortalShape fromShape) {
      boolean areaSizeTest = BlockPortalShape.isSquareShape(fromShape, this.length);
      return !areaSizeTest
         ? false
         : fromShape.frameAreaWithoutCorner.stream().allMatch(blockPos -> this.upFrameBlock.test(fromWorld.getBlockState(blockPos.up())))
            && fromShape.area.stream().allMatch(blockPos -> this.bottomBlock.test(fromWorld.getBlockState(blockPos.method_10074())));
   }

   public static IntBox findPortalPlacement(ServerWorld toWorld, BlockPos areaSize, BlockPos toPos) {
      return IntStream.range(toPos.getX() - 8, toPos.getX() + 8)
         .boxed()
         .flatMap(
            x -> IntStream.range(toPos.getZ() - 8, toPos.getZ() + 8)
               .boxed()
               .flatMap(
                  z -> IntStream.range(McHelper.getMinY(toWorld) + 5, McHelper.getMaxContentYExclusive(toWorld) - 5)
                     .map(y -> McHelper.getMaxContentYExclusive(toWorld) - y)
                     .mapToObj(y -> new BlockPos(x, y, z))
               )
         )
         .map(blockPos -> IntBox.fromBasePointAndSize(blockPos, areaSize))
         .filter(intBox -> intBox.stream().allMatch(pos -> {
            BlockState blockState = toWorld.getBlockState(pos);
            return !blockState.isOpaqueFullCube() && blockState.getBlock() != PortalPlaceholderBlock.instance && blockState.getFluidState().isEmpty();
         }))
         .filter(intBox -> intBox.getSurfaceLayer(Direction.field_11033).getMoved(Direction.field_11033.getVector()).stream().allMatch(blockPos -> {
            BlockState blockState = toWorld.getBlockState(blockPos);
            return !blockState.isAir() && blockState.getBlock() != PortalPlaceholderBlock.instance;
         }))
         .findFirst()
         .orElseGet(() -> IntBox.fromBasePointAndSize(toPos, areaSize))
         .getMoved(Direction.field_11033.getVector());
   }

   public static GeneralBreakablePortal[] createPortals(ServerWorld fromWorld, ServerWorld toWorld, BlockPortalShape fromShape, BlockPortalShape toShape) {
      GeneralBreakablePortal pa = (GeneralBreakablePortal)GeneralBreakablePortal.ENTITY_TYPE.create(fromWorld, SpawnReason.field_16461);
      fromShape.initPortalPosAxisShape(pa, class_2352.field_11056);
      pa.setDestination(toShape.innerAreaBox.getCenterVec());
      pa.setDestDim(toWorld.getRegistryKey());
      pa.setRotation(DQuaternion.rotationByDegrees(new Vec3d(1.0, 0.0, 0.0), 180.0));
      GeneralBreakablePortal pb = PortalManipulation.createReversePortal(pa, GeneralBreakablePortal.ENTITY_TYPE);
      pa.blockPortalShape = fromShape;
      pb.blockPortalShape = toShape;
      pa.reversePortalId = pb.getUuid();
      pb.reversePortalId = pa.getUuid();
      PortalExtension.get(pa).motionAffinity = 0.1;
      PortalExtension.get(pb).motionAffinity = 0.1;
      McHelper.spawnServerEntity(pa);
      McHelper.spawnServerEntity(pb);
      return new GeneralBreakablePortal[]{pa, pb};
   }
}
