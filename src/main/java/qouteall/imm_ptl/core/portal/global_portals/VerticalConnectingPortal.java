package qouteall.imm_ptl.core.portal.global_portals;

import java.util.function.Predicate;
import net.minecraft.entity.EntityType;
import net.minecraft.world.World;
import net.minecraft.util.math.Vec3d;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.RegistryKey;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalExtension;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.MiscHelper;
import qouteall.q_misc_util.my_util.DQuaternion;

public class VerticalConnectingPortal extends GlobalTrackedPortal {
   public static final EntityType<VerticalConnectingPortal> ENTITY_TYPE = createPortalEntityType(
      VerticalConnectingPortal::new, portalEntityTypeKey("end_floor_portal")
   );

   private static Predicate<Portal> getPredicate(VerticalConnectingPortal.ConnectorType connectorType) {
      switch (connectorType) {
         case ceil:
         default:
            return portal -> portal instanceof VerticalConnectingPortal && portal.getNormal().y < 0.0;
         case floor:
            return portal -> portal instanceof VerticalConnectingPortal && portal.getNormal().y > 0.0;
      }
   }

   public VerticalConnectingPortal(EntityType<?> entityType, World world) {
      super(entityType, world);
   }

   public static void connect(RegistryKey<World> from, VerticalConnectingPortal.ConnectorType connectorType, RegistryKey<World> to) {
      connect(from, connectorType, to, false);
   }

   public static void connect(
      RegistryKey<World> from, VerticalConnectingPortal.ConnectorType connectorType, RegistryKey<World> to, boolean respectSpaceRatio
   ) {
      removeConnectingPortal(connectorType, from);
      ServerWorld fromWorld = MiscHelper.getServer().getWorld(from);
      ServerWorld toWorld = MiscHelper.getServer().getWorld(to);
      VerticalConnectingPortal connectingPortal = createConnectingPortal(
         fromWorld,
         connectorType,
         toWorld,
         respectSpaceRatio ? fromWorld.getDimension().comp_646() / toWorld.getDimension().comp_646() : 1.0,
         false,
         0.0,
         McHelper.getMinY(fromWorld),
         McHelper.getMaxContentYExclusive(fromWorld),
         McHelper.getMinY(toWorld),
         McHelper.getMaxContentYExclusive(toWorld)
      );
      GlobalPortalStorage storage = GlobalPortalStorage.get(fromWorld);
      storage.addPortal(connectingPortal);
   }

   public static void connectMutually(RegistryKey<World> up, RegistryKey<World> down, boolean respectSpaceRatio) {
      connect(up, VerticalConnectingPortal.ConnectorType.floor, down, respectSpaceRatio);
      connect(down, VerticalConnectingPortal.ConnectorType.ceil, up, respectSpaceRatio);
   }

   public static VerticalConnectingPortal createConnectingPortal(
      ServerWorld fromWorld,
      VerticalConnectingPortal.ConnectorType connectorType,
      ServerWorld toWorld,
      double scaling,
      boolean inverted,
      double rotationAlongYDegrees,
      int fromWorldMinY,
      int fromWorldMaxY,
      int toWorldMinY,
      int toWorldMaxY
   ) {
      VerticalConnectingPortal verticalConnectingPortal = new VerticalConnectingPortal(ENTITY_TYPE, fromWorld);
      verticalConnectingPortal.setDestDim(toWorld.getRegistryKey());
      verticalConnectingPortal.setWidth(2.3333333333E10);
      verticalConnectingPortal.setHeight(2.3333333333E10);
      switch (connectorType) {
         case ceil:
            verticalConnectingPortal.setPosition(0.0, fromWorldMaxY, 0.0);
            verticalConnectingPortal.setAxisW(new Vec3d(1.0, 0.0, 0.0));
            verticalConnectingPortal.setAxisH(new Vec3d(0.0, 0.0, 1.0));
            break;
         case floor:
            verticalConnectingPortal.setPosition(0.0, fromWorldMinY, 0.0);
            verticalConnectingPortal.setAxisW(new Vec3d(0.0, 0.0, 1.0));
            verticalConnectingPortal.setAxisH(new Vec3d(1.0, 0.0, 0.0));
      }

      if (!inverted) {
         switch (connectorType) {
            case ceil:
               verticalConnectingPortal.setDestination(new Vec3d(0.0, toWorldMinY, 0.0));
               break;
            case floor:
               verticalConnectingPortal.setDestination(new Vec3d(0.0, toWorldMaxY, 0.0));
         }
      } else {
         switch (connectorType) {
            case ceil:
               verticalConnectingPortal.setDestination(new Vec3d(0.0, toWorldMaxY, 0.0));
               break;
            case floor:
               verticalConnectingPortal.setDestination(new Vec3d(0.0, toWorldMinY, 0.0));
         }
      }

      DQuaternion inversionRotation = inverted ? DQuaternion.rotationByDegrees(new Vec3d(1.0, 0.0, 0.0), 180.0) : null;
      DQuaternion additionalRotation = rotationAlongYDegrees != 0.0 ? DQuaternion.rotationByDegrees(new Vec3d(0.0, 1.0, 0.0), rotationAlongYDegrees) : null;
      DQuaternion rotation = Helper.combineNullable(inversionRotation, additionalRotation, DQuaternion::hamiltonProduct);
      verticalConnectingPortal.setRotation(rotation);
      if (scaling != 1.0) {
         verticalConnectingPortal.setScaling(scaling);
         verticalConnectingPortal.setTeleportChangesScale(false);
         PortalExtension.get(verticalConnectingPortal).adjustPositionAfterTeleport = false;
      }

      return verticalConnectingPortal;
   }

   public static void removeConnectingPortal(VerticalConnectingPortal.ConnectorType connectorType, RegistryKey<World> dimension) {
      removeConnectingPortal(getPredicate(connectorType), dimension);
   }

   private static void removeConnectingPortal(Predicate<Portal> predicate, RegistryKey<World> dimension) {
      ServerWorld endWorld = MiscHelper.getServer().getWorld(dimension);
      GlobalPortalStorage storage = GlobalPortalStorage.get(endWorld);
      storage.removePortals(portal -> portal instanceof VerticalConnectingPortal && predicate.test(portal));
   }

   public static VerticalConnectingPortal getConnectingPortal(World world, VerticalConnectingPortal.ConnectorType type) {
      return (VerticalConnectingPortal)GlobalPortalStorage.getGlobalPortals(world).stream().filter(getPredicate(type)).findFirst().orElse(null);
   }

   public static enum ConnectorType {
      ceil,
      floor;
   }
}
