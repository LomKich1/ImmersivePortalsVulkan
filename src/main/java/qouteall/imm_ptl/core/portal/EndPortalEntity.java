package qouteall.imm_ptl.core.portal;

import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.Entity.RemovalReason;
import net.minecraft.entity.boss.dragon.EnderDragonFight;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.EndPlatformFeature;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.mixin.common.miscellaneous.IEEndDragonFight;
import qouteall.imm_ptl.core.portal.animation.NormalAnimation;
import qouteall.imm_ptl.core.portal.animation.RotationAnimation;
import qouteall.imm_ptl.core.portal.shape.BoxPortalShape;
import qouteall.q_misc_util.Helper;

public class EndPortalEntity extends Portal {
   private static final Logger LOGGER = LogManager.getLogger(EndPortalEntity.class);
   public static final EntityType<EndPortalEntity> ENTITY_TYPE = createPortalEntityType(EndPortalEntity::new, portalEntityTypeKey("end_portal"));
   public static final String PORTAL_TAG_VIEW_BOX = "view_box";
   private static final double BOX_PORTAL_SIDE_LEN = 3.0;
   private static final double BOX_PORTAL_HEIGHT = 1.5;
   private static final Vec3d NORMAL_MODE_DESTINATION = new Vec3d(0.0, 120.0, 0.0);

   public EndPortalEntity(EntityType<?> entityType, World world) {
      super(entityType, world);
      this.setCrossPortalCollisionEnabled(false);
   }

   public static void onEndPortalComplete(ServerWorld world, Vec3d portalCenter) {
      ServerWorld endDim = world.getServer().getWorld(World.END);
      if (endDim != null) {
         IPGlobal.EndPortalMode endPortalMode = IPGlobal.endPortalMode;
         if (endPortalMode == IPGlobal.EndPortalMode.normal) {
            generateClassicalEndPortal(world, NORMAL_MODE_DESTINATION, portalCenter);
         } else if (endPortalMode == IPGlobal.EndPortalMode.toObsidianPlatform) {
            generateClassicalEndPortal(world, getObsidianPlatformDestination(), portalCenter);
         } else if (endPortalMode == IPGlobal.EndPortalMode.scaledView) {
            generateScaledViewEndPortal(world, portalCenter, false);
         } else if (endPortalMode == IPGlobal.EndPortalMode.scaledViewRotating) {
            generateScaledViewEndPortal(world, portalCenter, true);
         } else {
            LOGGER.error("End portal mode abnormal");
         }

         doCreateEndPlatform(endDim);
         EnderDragonFight dragonFight = endDim.getEnderDragonFight();
         if (dragonFight != null) {
            if (((IEEndDragonFight)dragonFight).ip_getNeedsStateScanning()) {
               ((IEEndDragonFight)dragonFight).ip_scanState();
            }
         }
      }
   }

   private static void doCreateEndPlatform(ServerWorld world) {
      EndPlatformFeature.generate(world, BlockPos.ofFloored(ServerWorld.END_SPAWN_POS.toBottomCenterPos()).down(), true);
   }

   private static void generateClassicalEndPortal(ServerWorld world, Vec3d destination, Vec3d portalCenter) {
      Portal portal = new EndPortalEntity(ENTITY_TYPE, world);
      portal.setPosition(portalCenter.x, portalCenter.y, portalCenter.z);
      portal.setDestination(destination);
      portal.setDestDim(World.END);
      portal.setAxisW(new Vec3d(0.0, 0.0, 1.0));
      portal.setAxisH(new Vec3d(1.0, 0.0, 0.0));
      portal.setWidth(3.0);
      portal.setHeight(3.0);
      world.spawnEntity(portal);
   }

   private static void generateScaledViewEndPortal(ServerWorld world, Vec3d frameCenter, boolean hasAnimation) {
      ServerWorld endWorld = world.getServer().getWorld(World.END);
      Validate.notNull(endWorld, "End dimension is not loaded", new Object[0]);
      double sideLen = 3.0;
      double otherSideSideLen = 288.0;
      double height = 1.5;
      Vec3d viewBoxSize = new Vec3d(sideLen, height, sideLen);
      double scale = otherSideSideLen / sideLen;
      Box thisSideBox = Helper.getBoxByBottomPosAndSize(frameCenter.add(0.0, 0.2, 0.0), viewBoxSize);
      Box otherSideBox = Helper.getBoxByBottomPosAndSize(new Vec3d(0.0, 0.0, 0.0), viewBoxSize.multiply(scale));
      Vec3d portalCenter = thisSideBox.getCenter();
      EndPortalEntity portal = (EndPortalEntity)ENTITY_TYPE.create(world, SpawnReason.TRIGGERED);

      assert portal != null;

      portal.setOriginPos(portalCenter);
      portal.setDestDim(endWorld.getRegistryKey());
      portal.setDestination(otherSideBox.getCenter());
      portal.setAxisW(new Vec3d(1.0, 0.0, 0.0));
      portal.setAxisH(new Vec3d(0.0, 1.0, 0.0));
      portal.setWidth(sideLen);
      portal.setHeight(height);
      portal.setThickness(sideLen);
      portal.setPortalShape(BoxPortalShape.FACING_OUTWARDS);
      portal.setScaling(scale);
      portal.setTeleportChangesScale(false);
      portal.portalTag = "view_box";
      portal.setInteractable(false);
      portal.setCrossPortalCollisionEnabled(false);
      portal.setFuseView(true);
      if (hasAnimation) {
         portal.addThisSideAnimationDriver(
            new RotationAnimation.Builder()
               .setDegreesPerTick(0.5)
               .setInitialOffset(Vec3d.ZERO)
               .setRotationAxis(new Vec3d(0.0, 1.0, 0.0))
               .setStartGameTime(world.getTime())
               .setEndGameTime(Long.MAX_VALUE)
               .build()
         );
         portal.addThisSideAnimationDriver(NormalAnimation.createOscillationAnimation(new Vec3d(0.0, 0.3, 0.0), 100, world.getTime()));
      }

      McHelper.spawnServerEntity(portal);
   }

   @Override
   public void tick() {
      super.tick();
      if (this.getEntityWorld().isClient()) {
         this.tickClient();
      } else if (!this.isRemoved() && this.age % 20 == 0) {
         this.followEndPortalMode((ServerWorld)this.getEntityWorld());
      }
   }

   private void followEndPortalMode(ServerWorld world) {
      IPGlobal.EndPortalMode currentMode = IPGlobal.endPortalMode;
      IPGlobal.EndPortalMode madeInMode = this.getModeItWasMadeIn();
      if (madeInMode != null && madeInMode != currentMode) {
         BlockPos interiorCorner = this.findFrameInterior(world);
         if (interiorCorner != null) {
            LOGGER.info("End portal at {} made in mode {} follows the End portal mode {}", interiorCorner, madeInMode, currentMode);
            this.remove(RemovalReason.KILLED);
            if (currentMode == IPGlobal.EndPortalMode.vanilla) {
               for (int dx = 0; dx < 3; dx++) {
                  for (int dz = 0; dz < 3; dz++) {
                     world.setBlockState(interiorCorner.add(dx, 0, dz), Blocks.END_PORTAL.getDefaultState(), 2);
                  }
               }
            } else {
               onEndPortalComplete(world, Vec3d.of(interiorCorner).add(1.5, 0.5, 1.5));
            }
         }
      }
   }

   @Nullable
   private IPGlobal.EndPortalMode getModeItWasMadeIn() {
      if (this.isViewBoxPortal()) {
         return this.animation.thisSideAnimations.isEmpty() ? IPGlobal.EndPortalMode.scaledView : IPGlobal.EndPortalMode.scaledViewRotating;
      } else if (this.getDestDim() != World.END) {
         return null;
      } else if (this.getDestPos().squaredDistanceTo(NORMAL_MODE_DESTINATION) < 1.0E-4) {
         return IPGlobal.EndPortalMode.normal;
      } else {
         return this.getDestPos().squaredDistanceTo(getObsidianPlatformDestination()) < 1.0E-4 ? IPGlobal.EndPortalMode.toObsidianPlatform : null;
      }
   }

   @Nullable
   private BlockPos findFrameInterior(ServerWorld world) {
      BlockPos near = BlockPos.ofFloored(this.getOriginPos());
      int minX = Integer.MAX_VALUE;
      int minY = Integer.MAX_VALUE;
      int minZ = Integer.MAX_VALUE;
      int maxX = Integer.MIN_VALUE;
      int maxY = Integer.MIN_VALUE;
      int maxZ = Integer.MIN_VALUE;
      int count = 0;

      for (BlockPos pos : BlockPos.iterate(near.add(-2, -3, -2), near.add(2, 1, 2))) {
         BlockState state = world.getBlockState(pos);
         if (state.getBlock() == PortalPlaceholderBlock.instance && state.get(PortalPlaceholderBlock.AXIS) == Axis.Y) {
            count++;
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
         }
      }

      return count == 9 && maxX - minX == 2 && maxZ - minZ == 2 && maxY == minY ? new BlockPos(minX, minY, minZ) : null;
   }

   private static Vec3d getObsidianPlatformDestination() {
      return Vec3d.ofCenter(ServerWorld.END_SPAWN_POS).add(0.0, 1.0, 0.0);
   }

   @Environment(EnvType.CLIENT)
   private void tickClient() {
      if (this.isViewBoxPortal()) {
         ClientPlayerEntity player = MinecraftClient.getInstance().player;
         if (player == null) {
            return;
         }

         if (this.getPortalShape() instanceof BoxPortalShape) {
            Vec3d cameraPosVec = player.getCameraPosVec(1.0F);
            double dist = this.getDistanceToNearestPointInPortal(cameraPosVec);
            if (dist < 1.0 && this.isInBoxPortalTeleportataionRange(cameraPosVec)) {
               double mul = 0.5;
               player.setVelocity(player.getVelocity().x * mul, player.getVelocity().y * mul, player.getVelocity().z * mul);
            }
         } else if (this.getNormal().y > 0.5 && ((IEEntity)player).ip_getCollidingPortal() == this) {
            Vec3d cameraPosVec = player.getCameraPosVec(1.0F);
            double dist = this.getDistanceToNearestPointInPortal(cameraPosVec);
            if (dist < 1.0) {
               double mul = dist / 2.0 + 0.1;
               player.setVelocity(player.getVelocity().x * mul, player.getVelocity().y * mul, player.getVelocity().z * mul);
            }
         }

         this.setFuseView(true);
      }
   }

   @Override
   public void onEntityTeleportedOnServer(Entity entity) {
      super.onEntityTeleportedOnServer(entity);
      if (this.shouldAddSlowFalling(entity)) {
         int duration = 200;
         if (this.isViewBoxPortal()) {
            duration = 400;
         }

         LivingEntity livingEntity = (LivingEntity)entity;
         livingEntity.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, duration, 1));
      }

      MinecraftServer server = this.getEntityWorld().getServer();

      assert server != null;

      ServerWorld endWorld = server.getWorld(World.END);
      if (endWorld != null) {
         doCreateEndPlatform(endWorld);
      }
   }

   private boolean isViewBoxPortal() {
      return Objects.equals(this.portalTag, "view_box");
   }

   @Override
   public Vec3d transformVelocityRelativeToPortal(Vec3d originalVelocityRelativeToPortal, Entity entity, Vec3d oldEntityPos) {
      if (this.isViewBoxPortal()) {
         Vec3d offset = oldEntityPos.subtract(this.getOriginPos());
         double threshold = 1.0;
         double horizontalDistanceSq = offset.x * offset.x + offset.z * offset.z;
         if (horizontalDistanceSq > threshold * threshold) {
            return Vec3d.ZERO;
         }
      }

      return super.transformVelocityRelativeToPortal(originalVelocityRelativeToPortal, entity, oldEntityPos);
   }

   @Override
   public boolean canTeleportEntity(Entity entity) {
      if (entity instanceof ArrowEntity) {
         return false;
      } else {
         return this.getPortalShape() instanceof BoxPortalShape && !this.isInBoxPortalTeleportataionRange(entity.getCameraPosVec(1.0F))
            ? false
            : super.canTeleportEntity(entity);
      }
   }

   private boolean shouldAddSlowFalling(Entity entity) {
      if (entity instanceof LivingEntity) {
         if (entity instanceof ServerPlayerEntity player) {
            if (player.interactionManager.getGameMode() == GameMode.CREATIVE) {
               return false;
            }

            if (player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean shouldLimitBoundingBox() {
      return false;
   }

   @Override
   public void onCollidingWithEntity(Entity entity) {
      if (!this.getEntityWorld().isClient() && entity instanceof ServerPlayerEntity && IPGlobal.endPortalMode == IPGlobal.EndPortalMode.toObsidianPlatform) {
         MinecraftServer server = this.getEntityWorld().getServer();

         assert server != null;

         ServerWorld endWorld = server.getWorld(World.END);
         if (endWorld != null) {
            doCreateEndPlatform(endWorld);
         }
      }
   }

   public boolean isInBoxPortalTeleportataionRange(Vec3d pos) {
      Vec3d offset = pos.subtract(this.getOriginPos());
      double threshold = 1.5;
      double horizontalDistanceSq = offset.x * offset.x + offset.z * offset.z;
      return horizontalDistanceSq <= threshold * threshold;
   }
}
