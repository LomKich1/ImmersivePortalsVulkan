package qouteall.imm_ptl.core.portal.custom_portal_gen.form;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Direction.class_2351;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.portal.custom_portal_gen.PortalGenInfo;
import qouteall.imm_ptl.core.portal.custom_portal_gen.SimpleBlockPredicate;
import qouteall.imm_ptl.core.portal.nether_portal.BlockPortalShape;
import qouteall.imm_ptl.core.portal.nether_portal.BreakablePortalEntity;
import qouteall.imm_ptl.core.portal.nether_portal.NetherPortalGeneration;
import qouteall.q_misc_util.my_util.DQuaternion;
import qouteall.q_misc_util.my_util.IntBox;

public class FlippingFloorSquareNewForm extends HeterogeneousForm {
   public static final MapCodec<FlippingFloorSquareNewForm> CODEC = RecordCodecBuilder.mapCodec(
      instance -> instance.group(
            Codec.BOOL.fieldOf("generate_frame_if_not_found").forGetter(o -> o.generateFrameIfNotFound),
            SimpleBlockPredicate.CODEC.fieldOf("area_block").forGetter(o -> o.areaBlock),
            SimpleBlockPredicate.CODEC.fieldOf("frame_block").forGetter(o -> o.frameBlock)
         )
         .apply(instance, FlippingFloorSquareNewForm::new)
   );

   public FlippingFloorSquareNewForm(boolean generateFrameIfNotFound, SimpleBlockPredicate areaBlock, SimpleBlockPredicate frameBlock) {
      super(generateFrameIfNotFound, areaBlock, frameBlock);
   }

   public BreakablePortalEntity[] generatePortalEntitiesAndPlaceholder(PortalGenInfo info) {
      ServerWorld fromWorld = McHelper.getServerWorld(info.from);
      ServerWorld toWorld = McHelper.getServerWorld(info.to);
      NetherPortalGeneration.fillInPlaceHolderBlocks(fromWorld, info.fromShape);
      NetherPortalGeneration.fillInPlaceHolderBlocks(toWorld, info.toShape);
      return FlippingFloorSquareForm.createPortals(fromWorld, toWorld, info.fromShape, info.toShape);
   }

   @Override
   public boolean testThisSideShape(ServerWorld fromWorld, BlockPortalShape fromShape) {
      if (fromShape.axis != class_2351.field_11052) {
         return false;
      } else {
         IntBox box = fromShape.innerAreaBox;
         BlockPos boxSize = box.getSize();
         return boxSize.getX() == boxSize.getZ() && boxSize.getX() * boxSize.getZ() == fromShape.area.size();
      }
   }

   @Override
   public PortalGenInfo getNewPortalPlacement(
      ServerWorld toWorld, BlockPos toPos, ServerWorld fromWorld, BlockPortalShape fromShape, @Nullable Entity triggeringEntity
   ) {
      IntBox portalPlacement = FlippingFloorSquareForm.findPortalPlacement(toWorld, fromShape.totalAreaBox.getSize(), toPos);
      BlockPortalShape placedShape = fromShape.getShapeWithMovedTotalAreaBox(portalPlacement);
      return new PortalGenInfo(
         fromWorld.getRegistryKey(), toWorld.getRegistryKey(), fromShape, placedShape, DQuaternion.rotationByDegrees(new Vec3d(1.0, 0.0, 0.0), 180.0), 1.0
      );
   }

   @Override
   public MapCodec<? extends PortalGenForm> getCodec() {
      return CODEC;
   }

   @Override
   public PortalGenForm getReverse() {
      return new FlippingFloorSquareNewForm(this.generateFrameIfNotFound, this.areaBlock, this.frameBlock);
   }
}
