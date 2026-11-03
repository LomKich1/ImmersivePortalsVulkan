package qouteall.imm_ptl.core.portal;

import java.util.function.Predicate;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.StainedGlassBlock;
import net.minecraft.block.StainedGlassPaneBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.Entity.RemovalReason;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.portal.nether_portal.BlockPortalShape;
import qouteall.imm_ptl.core.portal.shape.SpecialFlatPortalShape;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.IntBox;
import qouteall.q_misc_util.my_util.Mesh2D;

public class BreakableMirror extends Mirror {
   public static final EntityType<BreakableMirror> ENTITY_TYPE = createPortalEntityType(BreakableMirror::new, portalEntityTypeKey("breakable_mirror"));
   @Nullable
   public IntBox wallArea;
   @Nullable
   public BlockPortalShape blockPortalShape;
   public boolean unbreakable = false;

   public BreakableMirror(EntityType<?> entityType, World world) {
      super(entityType, world);
   }

   @Override
   protected void readAdditionalSaveData(NbtCompound tag) {
      super.readAdditionalSaveData(tag);
      if (tag.contains("boxXL")) {
         this.wallArea = new IntBox(
            new BlockPos(tag.getInt("boxXL", 0), tag.getInt("boxYL", 0), tag.getInt("boxZL", 0)),
            new BlockPos(tag.getInt("boxXH", 0), tag.getInt("boxYH", 0), tag.getInt("boxZH", 0))
         );
      } else {
         this.wallArea = null;
      }

      if (tag.contains("blockPortalShape")) {
         this.blockPortalShape = BlockPortalShape.fromTag(tag.getCompoundOrEmpty("blockPortalShape"));
      } else {
         this.blockPortalShape = null;
      }

      if (tag.contains("unbreakable")) {
         this.unbreakable = tag.getBoolean("unbreakable", false);
      }
   }

   @Override
   protected void addAdditionalSaveData(NbtCompound tag) {
      super.addAdditionalSaveData(tag);
      if (this.wallArea != null) {
         tag.putInt("boxXL", this.wallArea.l.getX());
         tag.putInt("boxYL", this.wallArea.l.getY());
         tag.putInt("boxZL", this.wallArea.l.getZ());
         tag.putInt("boxXH", this.wallArea.h.getX());
         tag.putInt("boxYH", this.wallArea.h.getY());
         tag.putInt("boxZH", this.wallArea.h.getZ());
      }

      if (this.blockPortalShape != null) {
         tag.put("blockPortalShape", this.blockPortalShape.toTag());
      }

      tag.putBoolean("unbreakable", this.unbreakable);
   }

   @Override
   public void tick() {
      super.tick();
      if (!this.getEntityWorld().isClient() && !this.unbreakable && this.getEntityWorld().getTime() % 10L == this.getId() % 10) {
         this.checkWallIntegrity();
      }
   }

   @Override
   public boolean isPortalValid() {
      return super.isPortalValid() && (this.wallArea != null || this.blockPortalShape != null);
   }

   private void checkWallIntegrity() {
      boolean wallValid;
      if (this.wallArea != null) {
         wallValid = this.wallArea.fastStream().allMatch(blockPos -> isGlass(this.getEntityWorld(), blockPos));
      } else if (this.blockPortalShape != null) {
         wallValid = this.blockPortalShape.area.stream().allMatch(blockPos -> isGlass(this.getEntityWorld(), blockPos));
      } else {
         wallValid = false;
      }

      if (!wallValid) {
         this.remove(RemovalReason.KILLED);
      }
   }

   public static boolean isGlass(World world, BlockPos blockPos) {
      Block block = world.getBlockState(blockPos).getBlock();
      return block == Blocks.GLASS || block == Blocks.GLASS_PANE || block instanceof StainedGlassBlock || block instanceof StainedGlassPaneBlock;
   }

   private static boolean isGlassPane(World world, BlockPos blockPos) {
      Block block = world.getBlockState(blockPos).getBlock();
      return block == Blocks.GLASS_PANE || block instanceof StainedGlassPaneBlock;
   }

   public static BreakableMirror createMirror(ServerWorld world, BlockPos glassPos, Direction facing) {
      if (!isGlass(world, glassPos)) {
         return null;
      } else {
         boolean isPane = isGlassPane(world, glassPos);
         if (facing.getAxis() == Axis.Y && isPane) {
            return null;
         } else {
            Predicate<BlockPos> glassWallPredicate = blockPos -> isGlass(world, blockPos)
               && isPane == isGlassPane(world, blockPos)
               && world.getBlockState(blockPos.offset(facing)).isAir();
            BlockPortalShape shape = BlockPortalShape.findArea(glassPos, facing.getAxis(), glassWallPredicate, blockPos -> !glassWallPredicate.test(blockPos));
            if (shape == null) {
               return null;
            } else {
               BreakableMirror breakableMirror = (BreakableMirror)ENTITY_TYPE.create(world, SpawnReason.TRIGGERED);

               assert breakableMirror != null;

               double distanceToCenter = isPane ? 0.0625 : 0.5;
               breakableMirror.blockPortalShape = shape;
               Box wallBox = McHelper.getWallBox(world, shape.area.stream());
               if (wallBox == null) {
                  return null;
               } else {
                  Vec3d pos = Helper.getBoxSurfaceInversed(wallBox, facing.getOpposite()).getCenter();
                  pos = Helper.putCoordinate(
                     pos,
                     facing.getAxis(),
                     Helper.getCoordinate(shape.innerAreaBox.getCenterVec().add(Vec3d.of(facing.getVector()).multiply(distanceToCenter)), facing.getAxis())
                  );
                  breakableMirror.setPosition(pos.x, pos.y, pos.z);
                  breakableMirror.setDestination(pos);
                  breakableMirror.setDestDim(world.getRegistryKey());
                  Pair<Direction, Direction> perpendicularDirections = Helper.getPerpendicularDirections(facing);
                  Direction wDirection = (Direction)perpendicularDirections.getLeft();
                  Direction hDirection = (Direction)perpendicularDirections.getRight();
                  breakableMirror.setWidth(Helper.getCoordinate(Helper.getBoxSize(wallBox), wDirection.getAxis()));
                  breakableMirror.setHeight(Helper.getCoordinate(Helper.getBoxSize(wallBox), hDirection.getAxis()));
                  breakableMirror.setAxisW(Vec3d.of(wDirection.getVector()));
                  breakableMirror.setAxisH(Vec3d.of(hDirection.getVector()));
                  initializeMirrorGeometryShape(breakableMirror, facing, shape);
                  breakIntersectedMirror(breakableMirror);
                  world.spawnEntity(breakableMirror);
                  return breakableMirror;
               }
            }
         }
      }
   }

   private static void initializeMirrorGeometryShape(BreakableMirror breakableMirror, Direction facing, BlockPortalShape shape) {
      if (shape.isRectangle()) {
         breakableMirror.setPortalShapeToDefault();
      } else {
         Vec3d center = breakableMirror.getOriginPos();
         World world = breakableMirror.getEntityWorld();
         Vec3d axisW = breakableMirror.getAxisW();
         Vec3d axisH = breakableMirror.getAxisH();
         Mesh2D mesh2D = new Mesh2D();

         for (BlockPos blockPos : shape.area) {
            VoxelShape collisionShape = world.getBlockState(blockPos).getCollisionShape(world, blockPos);
            if (!collisionShape.isEmpty()) {
               Box bounds = collisionShape.getBoundingBox().offset(Vec3d.of(blockPos));
               Vec3d p1 = new Vec3d(bounds.minX, bounds.minY, bounds.minZ);
               Vec3d p2 = new Vec3d(bounds.maxX, bounds.maxY, bounds.maxZ);
               double p1LocalX = p1.subtract(center).dotProduct(axisW);
               double p1LocalY = p1.subtract(center).dotProduct(axisH);
               double p2LocalX = p2.subtract(center).dotProduct(axisW);
               double p2LocalY = p2.subtract(center).dotProduct(axisH);
               mesh2D.addQuad(p1LocalX, p1LocalY, p2LocalX, p2LocalY);
            }
         }

         breakableMirror.setPortalShape(new SpecialFlatPortalShape(mesh2D));
      }
   }

   public IntBox getAreaBox() {
      if (this.wallArea != null) {
         return this.wallArea;
      } else if (this.blockPortalShape != null) {
         return this.blockPortalShape.innerAreaBox;
      } else {
         throw new RuntimeException();
      }
   }

   private static void breakIntersectedMirror(BreakableMirror newMirror) {
      McHelper.getEntitiesNearby(newMirror, BreakableMirror.class, IPGlobal.maxNormalPortalRadius)
         .stream()
         .filter(mirror1 -> mirror1.getNormal().dotProduct(newMirror.getNormal()) > 0.5)
         .filter(mirror1 -> IntBox.getIntersect(mirror1.getAreaBox(), newMirror.getAreaBox()) != null)
         .filter(mirror -> mirror != newMirror)
         .forEach(e -> e.remove(RemovalReason.KILLED));
   }
}
