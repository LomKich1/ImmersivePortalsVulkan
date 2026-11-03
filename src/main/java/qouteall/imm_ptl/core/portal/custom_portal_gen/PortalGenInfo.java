package qouteall.imm_ptl.core.portal.custom_portal_gen;

import net.minecraft.entity.EntityType;
import net.minecraft.world.World;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.SpawnReason;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.Direction.class_2352;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalExtension;
import qouteall.imm_ptl.core.portal.PortalManipulation;
import qouteall.imm_ptl.core.portal.nether_portal.BlockPortalShape;
import qouteall.imm_ptl.core.portal.nether_portal.BreakablePortalEntity;
import qouteall.imm_ptl.core.portal.nether_portal.NetherPortalGeneration;
import qouteall.q_misc_util.MiscHelper;
import qouteall.q_misc_util.my_util.DQuaternion;
import qouteall.q_misc_util.my_util.SignalArged;

public class PortalGenInfo {
   public static final SignalArged<PortalGenInfo> generatedSignal = new SignalArged<>();
   public RegistryKey<World> from;
   public RegistryKey<World> to;
   public BlockPortalShape fromShape;
   public BlockPortalShape toShape;
   @Nullable
   public DQuaternion rotation = null;
   public double scale = 1.0;

   public PortalGenInfo(RegistryKey<World> from, RegistryKey<World> to, BlockPortalShape fromShape, BlockPortalShape toShape) {
      this.from = from;
      this.to = to;
      this.fromShape = fromShape;
      this.toShape = toShape;
   }

   public PortalGenInfo(
      RegistryKey<World> from, RegistryKey<World> to, BlockPortalShape fromShape, BlockPortalShape toShape, DQuaternion rotation, double scale
   ) {
      this.from = from;
      this.to = to;
      this.fromShape = fromShape;
      this.toShape = toShape;
      this.rotation = rotation;
      this.scale = scale;
      if (rotation != null && rotation.getRotatingAngleDegrees() < 0.001) {
         this.rotation = null;
      }

      if (Math.abs(this.scale - 1.0) < 1.0E-5) {
         this.scale = 1.0;
      }
   }

   public <T extends Portal> T createTemplatePortal(EntityType<T> entityType) {
      ServerWorld fromWorld = McHelper.getServerWorld(this.from);
      T portal = (T)entityType.create(fromWorld, SpawnReason.field_16461);

      assert portal != null;

      this.fromShape.initPortalPosAxisShape(portal, class_2352.field_11056);
      portal.setDestDim(this.to);
      portal.setDestination(this.toShape.innerAreaBox.getCenterVec());
      portal.setScaling(this.scale);
      portal.setRotation(this.rotation);
      return portal;
   }

   public <T extends BreakablePortalEntity> BreakablePortalEntity[] generateBiWayBiFacedPortal(EntityType<T> entityType) {
      T f1 = (T)this.createTemplatePortal(entityType);
      T f2 = (T)PortalManipulation.createFlippedPortal(f1, entityType);
      T t1 = (T)PortalManipulation.createReversePortal(f1, entityType);
      T t2 = (T)PortalManipulation.createFlippedPortal(t1, entityType);
      f1.blockPortalShape = this.fromShape;
      f2.blockPortalShape = this.fromShape;
      t1.blockPortalShape = this.toShape;
      t2.blockPortalShape = this.toShape;
      f1.reversePortalId = t1.getUuid();
      t1.reversePortalId = f1.getUuid();
      f2.reversePortalId = t2.getUuid();
      t2.reversePortalId = f2.getUuid();
      PortalExtension.initializeClusterBind(f1, f2, t1, t2);
      McHelper.spawnServerEntity(f1);
      McHelper.spawnServerEntity(f2);
      McHelper.spawnServerEntity(t1);
      McHelper.spawnServerEntity(t2);
      return new BreakablePortalEntity[]{f1, f2, t1, t2};
   }

   public void generatePlaceholderBlocks() {
      MinecraftServer server = MiscHelper.getServer();
      NetherPortalGeneration.fillInPlaceHolderBlocks(server.getWorld(this.from), this.fromShape);
      NetherPortalGeneration.fillInPlaceHolderBlocks(server.getWorld(this.to), this.toShape);
      generatedSignal.emit(this);
   }
}
