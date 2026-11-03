package qouteall.imm_ptl.core.portal.global_portals;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.IntBox;

public class WorldWrappingPortal extends GlobalTrackedPortal {
   public static final EntityType<WorldWrappingPortal> ENTITY_TYPE = createPortalEntityType(WorldWrappingPortal::new, portalEntityTypeKey("border_portal"));
   public boolean isInward = true;
   public int zoneId = -1;

   public WorldWrappingPortal(EntityType<?> entityType, World world) {
      super(entityType, world);
   }

   @Override
   protected void readAdditionalSaveData(NbtCompound compoundTag) {
      super.readAdditionalSaveData(compoundTag);
      if (compoundTag.contains("isInward")) {
         this.isInward = compoundTag.getBoolean("isInward", false);
      }

      if (compoundTag.contains("zoneId")) {
         this.zoneId = compoundTag.getInt("zoneId", 0);
      }
   }

   @Override
   protected void addAdditionalSaveData(NbtCompound compoundTag) {
      super.addAdditionalSaveData(compoundTag);
      compoundTag.putBoolean("isInward", this.isInward);
      compoundTag.putInt("zoneId", this.zoneId);
   }

   private static WorldWrappingPortal createWrappingPortal(ServerWorld serverWorld, Box area, Direction direction, int zoneId, boolean isInward) {
      WorldWrappingPortal portal = (WorldWrappingPortal)ENTITY_TYPE.create(serverWorld, SpawnReason.TRIGGERED);
      portal.isInward = isInward;
      portal.zoneId = zoneId;
      initWrappingPortal(serverWorld, area, direction, isInward, portal);
      return portal;
   }

   public static void initWrappingPortal(ServerWorld serverWorld, Box area, Direction direction, boolean isInward, Portal portal) {
      Vec3d areaSize = Helper.getBoxSize(area);
      Pair<Direction, Direction> axises = Helper.getPerpendicularDirections(isInward ? direction : direction.getOpposite());
      Box boxSurface = Helper.getBoxSurfaceInversed(area, direction);
      Vec3d center = boxSurface.getCenter();
      Box oppositeSurface = Helper.getBoxSurfaceInversed(area, direction.getOpposite());
      Vec3d destination = oppositeSurface.getCenter();
      portal.setPosition(center.x, center.y, center.z);
      portal.setDestination(destination);
      portal.setAxisW(Vec3d.of(((Direction)axises.getLeft()).getVector()));
      portal.setAxisH(Vec3d.of(((Direction)axises.getRight()).getVector()));
      portal.setWidth(Helper.getCoordinate(areaSize, ((Direction)axises.getLeft()).getAxis()));
      portal.setHeight(Helper.getCoordinate(areaSize, ((Direction)axises.getRight()).getAxis()));
      portal.setDestDim(serverWorld.getRegistryKey());
   }

   public static List<WorldWrappingPortal.WrappingZone> getWrappingZones(ServerWorld world) {
      GlobalPortalStorage gps = GlobalPortalStorage.get(world);
      List<WorldWrappingPortal.WrappingZone> result = new ArrayList<>();
      gps.data
         .stream()
         .filter(portal -> portal instanceof WorldWrappingPortal)
         .map(portal -> (WorldWrappingPortal)portal)
         .collect(Collectors.groupingBy(portal -> portal.zoneId))
         .forEach(
            (zoneId, portals) -> result.add(new WorldWrappingPortal.WrappingZone(world, portals.get(0).isInward, zoneId, (List<WorldWrappingPortal>)portals))
         );
      return result;
   }

   public static int getAvailableId(List<WorldWrappingPortal.WrappingZone> zones) {
      return zones.stream().max(Comparator.comparingInt(z -> z.id)).map(z -> z.id + 1).orElse(1);
   }

   public static void invokeAddWrappingZone(ServerWorld world, int x1, int z1, int x2, int z2, boolean isInward, Consumer<Text> feedbackSender) {
      List<WorldWrappingPortal.WrappingZone> wrappingZones = getWrappingZones(world);

      for (WorldWrappingPortal.WrappingZone zone : wrappingZones) {
         if (!zone.isValid()) {
            feedbackSender.accept(Text.translatable("imm_ptl.removed_invalid_wrapping_portals", new Object[]{Helper.myToString(zone.portals.stream())}));
            zone.removeFromWorld();
         }
      }

      int availableId = getAvailableId(wrappingZones);
      Box box = new IntBox(new BlockPos(x1, McHelper.getMinY(world), z1), new BlockPos(x2, McHelper.getMaxYExclusive(world) - 1, z2)).toRealNumberBox();
      WorldWrappingPortal p1 = createWrappingPortal(world, box, Direction.NORTH, availableId, isInward);
      WorldWrappingPortal p2 = createWrappingPortal(world, box, Direction.SOUTH, availableId, isInward);
      WorldWrappingPortal p3 = createWrappingPortal(world, box, Direction.WEST, availableId, isInward);
      WorldWrappingPortal p4 = createWrappingPortal(world, box, Direction.EAST, availableId, isInward);
      GlobalPortalStorage gps = GlobalPortalStorage.get(world);
      gps.addPortal(p1);
      gps.addPortal(p2);
      gps.addPortal(p3);
      gps.addPortal(p4);
   }

   public static void invokeViewWrappingZones(ServerWorld world, Consumer<Text> feedbackSender) {
      List<WorldWrappingPortal.WrappingZone> wrappingZones = getWrappingZones(world);
      wrappingZones.forEach(wrappingZone -> feedbackSender.accept(Text.literal(wrappingZone.toString())));
   }

   public static void invokeRemoveWrappingZone(ServerWorld world, Vec3d playerPos, Consumer<Text> feedbackSender) {
      List<WorldWrappingPortal.WrappingZone> wrappingZones = getWrappingZones(world);
      WorldWrappingPortal.WrappingZone zone = wrappingZones.stream().filter(z -> z.getArea().contains(playerPos)).findFirst().orElse(null);
      if (zone != null) {
         zone.removeFromWorld();
         feedbackSender.accept(Text.translatable("imm_ptl.removed_portal", new Object[]{zone.toString()}));
      } else {
         feedbackSender.accept(Text.translatable("imm_ptl.not_in_wrapping_zone"));
      }
   }

   public static void invokeRemoveWrappingZone(ServerWorld world, int zoneId, Consumer<Text> feedbackSender) {
      List<WorldWrappingPortal.WrappingZone> wrappingZones = getWrappingZones(world);
      WorldWrappingPortal.WrappingZone zone = wrappingZones.stream().filter(wrappingZone -> wrappingZone.id == zoneId).findFirst().orElse(null);
      if (zone != null) {
         zone.removeFromWorld();
         feedbackSender.accept(Text.translatable("imm_ptl.removed_portal", new Object[]{zone.toString()}));
      } else {
         feedbackSender.accept(Text.translatable("imm_ptl.cannot_find_zone"));
      }
   }

   public static class WrappingZone {
      public ServerWorld world;
      public boolean isInwardZone;
      public int id;
      public List<WorldWrappingPortal> portals;

      public WrappingZone(ServerWorld world, boolean isInwardZone, int id, List<WorldWrappingPortal> portals) {
         this.world = world;
         this.isInwardZone = isInwardZone;
         this.id = id;
         this.portals = portals;
      }

      public boolean isValid() {
         return this.portals.size() == 4
            && this.portals.get(0).isInward == this.isInwardZone
            && this.portals.get(1).isInward == this.isInwardZone
            && this.portals.get(2).isInward == this.isInwardZone
            && this.portals.get(3).isInward == this.isInwardZone;
      }

      public void removeFromWorld() {
         GlobalPortalStorage gps = GlobalPortalStorage.get(this.world);
         this.portals.forEach(worldWrappingPortal -> gps.removePortal(worldWrappingPortal));
      }

      public Box getArea() {
         return this.portals.stream().map(Portal::getThinBoundingBox).reduce(Box::union).orElse(null);
      }

      public IntBox getIntArea() {
         Box floatBox = this.getArea();
         return new IntBox(
            new BlockPos((int)Math.round(floatBox.minX), McHelper.getMinY(this.world), (int)Math.round(floatBox.minZ)),
            new BlockPos((int)Math.round(floatBox.maxX) - 1, McHelper.getMaxYExclusive(this.world), (int)Math.round(floatBox.maxZ) - 1)
         );
      }

      public IntBox getBorderBox() {
         if (!this.isInwardZone) {
            return this.getIntArea();
         } else {
            Box floatBox = this.getArea();
            return new IntBox(
               new BlockPos((int)Math.round(floatBox.minX) - 1, McHelper.getMinY(this.world), (int)Math.round(floatBox.minZ) - 1),
               new BlockPos((int)Math.round(floatBox.maxX), McHelper.getMaxYExclusive(this.world), (int)Math.round(floatBox.maxZ))
            );
         }
      }

      @Override
      public String toString() {
         Box area = this.getArea();
         return String.format("[%d] %s %s %s ~ %s %s\n", this.id, this.isInwardZone ? "inward" : "outward", area.minX, area.minZ, area.maxX, area.maxZ);
      }
   }
}
