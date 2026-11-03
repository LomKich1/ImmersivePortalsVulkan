package qouteall.imm_ptl.core.portal;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.profiler.Profilers;
import net.minecraft.world.tick.ScheduledTickView;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.BlockRenderType;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.block.BlockState;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Property;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.WorldView;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.random.Random;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.math.Direction.class_2351;
import net.minecraft.util.hit.HitResult.class_240;
import net.minecraft.state.StateManager.class_2690;
import net.minecraft.block.AbstractBlock.class_2251;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.portal.nether_portal.BreakablePortalEntity;

public class PortalPlaceholderBlock extends Block {
   public static final EnumProperty<class_2351> AXIS = Properties.AXIS;
   public static final VoxelShape X_AABB = Block.createCuboidShape(6.0, 0.0, 0.0, 10.0, 16.0, 16.0);
   public static final VoxelShape Y_AABB = Block.createCuboidShape(0.0, 6.0, 0.0, 16.0, 10.0, 16.0);
   public static final VoxelShape Z_AABB = Block.createCuboidShape(0.0, 0.0, 6.0, 16.0, 16.0, 10.0);
   public static final PortalPlaceholderBlock instance = new PortalPlaceholderBlock(
      class_2251.create()
         .noCollision()
         .sounds(BlockSoundGroup.GLASS)
         .strength(1.0F, 0.0F)
         .nonOpaque()
         .dropsNothing()
         .luminance(s -> 15)
         .registryKey(RegistryKey.of(RegistryKeys.field_41254, McHelper.newResourceLocation("immersive_portals", "nether_portal_block")))
   );

   public PortalPlaceholderBlock(class_2251 properties) {
      super(properties);
      this.setDefaultState((BlockState)((BlockState)this.getStateManager().getDefaultState()).with(AXIS, class_2351.field_11048));
   }

   public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos blockPos, ShapeContext shapeContext) {
      switch ((class_2351)state.get(AXIS)) {
         case field_11051:
            return Z_AABB;
         case field_11052:
            return Y_AABB;
         case field_11048:
         default:
            return X_AABB;
      }
   }

   protected void appendProperties(class_2690<Block, BlockState> builder) {
      builder.add(new Property[]{AXIS});
   }

   protected BlockState getStateForNeighborUpdate(
      BlockState thisState,
      WorldView worldAccess,
      ScheduledTickView scheduledTickAccess,
      BlockPos blockPos,
      Direction direction,
      BlockPos neighborPos,
      BlockState neighborState,
      Random random
   ) {
      if (!worldAccess.isClient() && worldAccess instanceof World world) {
         Profilers.get().push("portal_placeholder");
         class_2351 axis = (class_2351)thisState.get(AXIS);
         if (direction.getAxis() != axis) {
            McHelper.findEntitiesRough(BreakablePortalEntity.class, world, Vec3d.of(blockPos), 2, e -> true)
               .forEach(portal -> portal.notifyPlaceholderUpdate());
         }

         Profilers.get().pop();
      }

      return super.getStateForNeighborUpdate(thisState, worldAccess, scheduledTickAccess, blockPos, direction, neighborPos, neighborState, random);
   }

   public static boolean isHitOnPlaceholder(HitResult hitResult, World world) {
      if (hitResult.getType() == class_240.field_1332 && hitResult instanceof BlockHitResult blockHitResult) {
         Block hittingBlock = world.getBlockState(blockHitResult.getBlockPos()).getBlock();
         return hittingBlock == instance;
      } else {
         return false;
      }
   }

   protected boolean isTransparent(BlockState blockState_1) {
      return true;
   }

   public BlockRenderType getRenderType(BlockState blockState_1) {
      return BlockRenderType.field_11455;
   }

   @Environment(EnvType.CLIENT)
   public float getAmbientOcclusionLightLevel(BlockState blockState_1, BlockView blockView_1, BlockPos blockPos_1) {
      return 1.0F;
   }
}
