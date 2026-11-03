package qouteall.imm_ptl.core.portal;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.AbstractBlock.Settings;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager.Builder;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.profiler.Profilers;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.tick.ScheduledTickView;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.portal.nether_portal.BreakablePortalEntity;

public class PortalPlaceholderBlock extends Block {
   public static final EnumProperty<Axis> AXIS = Properties.AXIS;
   public static final VoxelShape X_AABB = Block.createCuboidShape(6.0, 0.0, 0.0, 10.0, 16.0, 16.0);
   public static final VoxelShape Y_AABB = Block.createCuboidShape(0.0, 6.0, 0.0, 16.0, 10.0, 16.0);
   public static final VoxelShape Z_AABB = Block.createCuboidShape(0.0, 0.0, 6.0, 16.0, 16.0, 10.0);
   public static final PortalPlaceholderBlock instance = new PortalPlaceholderBlock(
      Settings.create()
         .noCollision()
         .sounds(BlockSoundGroup.GLASS)
         .strength(1.0F, 0.0F)
         .nonOpaque()
         .dropsNothing()
         .luminance(s -> 15)
         .registryKey(RegistryKey.of(RegistryKeys.BLOCK, McHelper.newResourceLocation("immersive_portals", "nether_portal_block")))
   );

   public PortalPlaceholderBlock(Settings properties) {
      super(properties);
      this.setDefaultState((BlockState)((BlockState)this.getStateManager().getDefaultState()).with(AXIS, Axis.X));
   }

   public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
      switch ((Axis)state.get(AXIS)) {
         case Z:
            return Z_AABB;
         case Y:
            return Y_AABB;
         case X:
         default:
            return X_AABB;
      }
   }

   protected void appendProperties(Builder<Block, BlockState> builder) {
      builder.add(new Property[]{AXIS});
   }

   protected BlockState getStateForNeighborUpdate(
      BlockState state,
      WorldView world,
      ScheduledTickView tickView,
      BlockPos pos,
      Direction direction,
      BlockPos neighborPos,
      BlockState neighborState,
      Random random
   ) {
      if (!world.isClient() && world instanceof World worldx) {
         Profilers.get().push("portal_placeholder");
         Axis axis = (Axis)state.get(AXIS);
         if (direction.getAxis() != axis) {
            McHelper.findEntitiesRough(BreakablePortalEntity.class, worldx, Vec3d.of(pos), 2, e -> true).forEach(portal -> portal.notifyPlaceholderUpdate());
         }

         Profilers.get().pop();
      }

      return super.getStateForNeighborUpdate(state, world, tickView, pos, direction, neighborPos, neighborState, random);
   }

   public static boolean isHitOnPlaceholder(HitResult hitResult, World world) {
      if (hitResult.getType() == Type.BLOCK && hitResult instanceof BlockHitResult blockHitResult) {
         Block hittingBlock = world.getBlockState(blockHitResult.getBlockPos()).getBlock();
         return hittingBlock == instance;
      } else {
         return false;
      }
   }

   protected boolean isTransparent(BlockState state) {
      return true;
   }

   public BlockRenderType getRenderType(BlockState state) {
      return BlockRenderType.INVISIBLE;
   }

   @Environment(EnvType.CLIENT)
   public float getAmbientOcclusionLightLevel(BlockState state, BlockView world, BlockPos pos) {
      return 1.0F;
   }
}
