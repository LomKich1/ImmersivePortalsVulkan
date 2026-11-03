package qouteall.imm_ptl.core.portal.nether_portal;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.Entity.RemovalReason;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.mc_utils.ServerTaskList;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalPlaceholderBlock;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.DQuaternion;
import qouteall.q_misc_util.my_util.LimitedLogger;
import qouteall.q_misc_util.my_util.MyTaskList;

public abstract class BreakablePortalEntity extends Portal {
   private static final Logger LOGGER = LogUtils.getLogger();
   public BlockPortalShape blockPortalShape;
   public UUID reversePortalId;
   public boolean unbreakable = false;
   private boolean isNotified = true;
   private boolean shouldBreakPortal = false;
   @Nullable
   protected BreakablePortalEntity.OverlayInfo overlayInfo;
   private static final LimitedLogger limitedLogger = new LimitedLogger(20);

   public BreakablePortalEntity(EntityType<?> entityType_1, World world_1) {
      super(entityType_1, world_1);
   }

   @Override
   public boolean isPortalValid() {
      return this.getEntityWorld().isClient() ? super.isPortalValid() : super.isPortalValid() && this.blockPortalShape != null && this.reversePortalId != null;
   }

   @Override
   protected void readAdditionalSaveData(NbtCompound compoundTag) {
      super.readAdditionalSaveData(compoundTag);
      if (compoundTag.contains("netherPortalShape")) {
         this.blockPortalShape = new BlockPortalShape(compoundTag.getCompoundOrEmpty("netherPortalShape"));
      }

      this.reversePortalId = Helper.getUuid(compoundTag, "reversePortalId");
      if (this.reversePortalId == null) {
         Helper.err("missing reverse portal id " + compoundTag);
         this.reversePortalId = Util.NIL_UUID;
      }

      this.unbreakable = compoundTag.getBoolean("unbreakable", false);
      if (compoundTag.contains("overlayBlockState")) {
         BlockState overlayBlockState = NbtHelper.toBlockState(
            this.getEntityWorld().createCommandRegistryWrapper(RegistryKeys.BLOCK), compoundTag.getCompoundOrEmpty("overlayBlockState")
         );
         if (overlayBlockState.isAir()) {
            this.overlayInfo = null;
         } else {
            double overlayOpacity = compoundTag.getDouble("overlayOpacity", 0.0);
            if (overlayOpacity == 0.0) {
               overlayOpacity = 0.5;
            }

            double overlayOffset = compoundTag.getDouble("overlayOffset", 0.0);
            DQuaternion rotation = Helper.getQuaternion(compoundTag, "overlayRotation");
            this.overlayInfo = new BreakablePortalEntity.OverlayInfo(overlayBlockState, overlayOpacity, overlayOffset, rotation);
         }
      } else {
         this.overlayInfo = null;
      }
   }

   @Override
   protected void addAdditionalSaveData(NbtCompound compoundTag) {
      super.addAdditionalSaveData(compoundTag);
      if (this.blockPortalShape != null) {
         compoundTag.put("netherPortalShape", this.blockPortalShape.toTag());
      }

      Helper.putUuid(compoundTag, "reversePortalId", this.reversePortalId);
      compoundTag.putBoolean("unbreakable", this.unbreakable);
      if (this.overlayInfo != null) {
         compoundTag.put("overlayBlockState", NbtHelper.fromBlockState(this.overlayInfo.blockState));
         compoundTag.putDouble("overlayOpacity", this.overlayInfo.opacity);
         compoundTag.putDouble("overlayOffset", this.overlayInfo.offset);
         Helper.putQuaternion(compoundTag, "overlayRotation", this.overlayInfo.rotation);
      }
   }

   private void breakPortalOnThisSide() {
      this.blockPortalShape.area.forEach(blockPos -> {
         if (this.getEntityWorld().getBlockState(blockPos).getBlock() == PortalPlaceholderBlock.instance) {
            this.getEntityWorld().setBlockState(blockPos, Blocks.AIR.getDefaultState());
         }
      });
      this.remove(RemovalReason.KILLED);
      Helper.log("Broke " + this);
   }

   public void notifyPlaceholderUpdate() {
      this.isNotified = true;
   }

   private BreakablePortalEntity getReversePortal() {
      ServerWorld world = this.getEntityWorld().getServer().getWorld(this.getDestDim());
      Entity entity = world.getEntity(this.reversePortalId);
      return entity instanceof BreakablePortalEntity ? (BreakablePortalEntity)entity : null;
   }

   @Override
   public void tick() {
      super.tick();
      if (this.getEntityWorld().isClient()) {
         this.addSoundAndParticle();
      } else if (!this.unbreakable) {
         if (this.isNotified || this.getEntityWorld().getTime() % 233L == this.getId() % 233) {
            this.isNotified = false;
            this.checkPortalIntegrity();
         }

         if (this.shouldBreakPortal) {
            this.breakPortalOnThisSide();
         }
      }
   }

   private void checkPortalIntegrity() {
      Validate.isTrue(!this.getEntityWorld().isClient());
      if (!this.isPortalValid()) {
         this.remove(RemovalReason.KILLED);
      } else {
         if (!this.isPortalIntactOnThisSide()) {
            this.markShouldBreak();
         } else if (!this.isPortalPaired()) {
            Helper.err("Break portal because of abnormal pairing");
            this.markShouldBreak();
         }
      }
   }

   protected abstract boolean isPortalIntactOnThisSide();

   @Environment(EnvType.CLIENT)
   protected abstract void addSoundAndParticle();

   public boolean isPortalPaired() {
      Validate.isTrue(!this.getEntityWorld().isClient());
      if (this.isOneWay()) {
         return true;
      } else if (!this.isOtherSideChunkLoaded()) {
         return true;
      } else {
         List<BreakablePortalEntity> revs = findReversePortals(this);
         if (revs.size() == 1) {
            BreakablePortalEntity reversePortal = revs.get(0);
            return !(reversePortal.getDestPos().squaredDistanceTo(this.getOriginPos()) > 1.0);
         } else {
            return revs.size() <= 1;
         }
      }
   }

   public void markShouldBreak() {
      this.shouldBreakPortal = true;
      if (!this.isOneWay()) {
         BreakablePortalEntity reversePortal = this.getReversePortal();
         if (reversePortal != null) {
            reversePortal.shouldBreakPortal = true;
         } else {
            ServerTaskList.of(this.getEntityWorld().getServer()).addTask(MyTaskList.withRetryNumberLimit(30, () -> {
               if (this.isRemoved()) {
                  return true;
               } else if (!this.isOtherSideChunkLoaded()) {
                  LOGGER.info("The other side chunk is not loaded. Delay finding reverse portal of {}.", this);
                  return false;
               } else {
                  BreakablePortalEntity reversePortal1 = this.getReversePortal();
                  if (reversePortal1 != null) {
                     reversePortal1.shouldBreakPortal = true;
                  }

                  return true;
               }
            }, () -> {}));
         }
      }
   }

   public static <T extends Portal> List<T> findReversePortals(T portal) {
      return McHelper.findEntitiesByBox(
         (Class<T>)portal.getClass(),
         portal.getDestinationWorld(),
         new Box(BlockPos.ofFloored(portal.getDestPos())),
         10.0,
         e -> e.getOriginPos().squaredDistanceTo(portal.getDestPos()) < 0.1 && e.getContentDirection().dotProduct(portal.getNormal()) > 0.6
      );
   }

   public boolean isOneWay() {
      return this.reversePortalId.equals(Util.NIL_UUID);
   }

   public void markOneWay() {
      this.reversePortalId = Util.NIL_UUID;
   }

   public BreakablePortalEntity.OverlayInfo getActualOverlay() {
      return this.overlayInfo;
   }

   public record OverlayInfo(BlockState blockState, double opacity, double offset, @Nullable DQuaternion rotation) {
   }
}
