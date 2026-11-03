package qouteall.imm_ptl.core;

import com.mojang.blaze3d.systems.RenderSystem;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Pair;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.text.MutableText;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.hit.HitResult.class_240;
import net.minecraft.text.ClickEvent.class_10609;
import qouteall.imm_ptl.core.ducks.IERayTraceContext;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.global_portals.GlobalPortalStorage;
import qouteall.imm_ptl.core.render.CrossPortalEntityRenderer;
import qouteall.q_misc_util.my_util.LimitedLogger;

public class IPMcHelper {
   public static final LimitedLogger limitedLogger = new LimitedLogger(20);

   public static void foreachNearbyPortals(World world, Vec3d pos, int range, Consumer<Portal> func) {
      for (Portal globalPortal : GlobalPortalStorage.getGlobalPortals(world)) {
         if (globalPortal.getDistanceToNearestPointInPortal(pos) < range * 2) {
            func.accept(globalPortal);
         }
      }

      McHelper.foreachEntitiesByPointAndRoughRadius(Portal.class, world, pos, range, func);
   }

   public static List<Portal> getNearbyPortalList(Entity center, double range, Predicate<Portal> predicate) {
      return getNearbyPortalList(center.getEntityWorld(), center.getEntityPos(), range, predicate);
   }

   public static List<Portal> getNearbyPortalList(World world, Vec3d pos, double range, Predicate<Portal> predicate) {
      List<Portal> result = new ArrayList<>();
      foreachNearbyPortals(world, pos, (int)range, portal -> {
         if (predicate.test(portal)) {
            result.add(portal);
         }
      });
      return result;
   }

   public static Stream<Portal> getNearbyPortals(Entity center, double range) {
      return getNearbyPortals(center.getEntityWorld(), center.getEntityPos(), range);
   }

   public static Stream<Portal> getNearbyPortals(World world, Vec3d pos, double range) {
      return getNearbyPortalList(world, pos, range, e -> true).stream();
   }

   public static void traverseNearbyPortals(World world, Vec3d pos, int range, Consumer<Portal> func) {
      for (Portal globalPortal : GlobalPortalStorage.getGlobalPortals(world)) {
         if (globalPortal.getDistanceToNearestPointInPortal(pos) < range * 2) {
            func.accept(globalPortal);
         }
      }

      McHelper.traverseEntitiesByPointAndRoughRadius(Portal.class, world, pos, range, portal -> {
         func.accept(portal);
         return null;
      });
   }

   public static void onClientEntityTick(Entity entity) {
      CrossPortalEntityRenderer.onEntityTickClient(entity);
   }

   public static List<Pair<Portal, Vec3d>> rayTracePortals(
      World world, Vec3d start, Vec3d end, boolean includeGlobalPortals, Predicate<Portal> filter
   ) {
      Vec3d middle = start.multiply(0.5).add(end.multiply(0.5));
      int chunkRadius = (int)Math.ceil(Math.abs(start.distanceTo(end) / 2.0) / 16.0);
      List<Portal> nearby = McHelper.getEntitiesNearby(world, middle, Portal.class, chunkRadius * 16);
      if (includeGlobalPortals) {
         nearby.addAll(GlobalPortalStorage.getGlobalPortals(world));
      }

      List<Pair<Portal, Vec3d>> hits = new ArrayList<>();
      nearby.forEach(portal -> {
         if (filter == null || filter.test(portal)) {
            Vec3d intersection = portal.rayTrace(start, end);
            if (intersection != null) {
               hits.add(new Pair(portal, intersection));
            }
         }
      });
      hits.sort((pair1, pair2) -> {
         Vec3d intersection1 = (Vec3d)pair1.getRight();
         Vec3d intersection2 = (Vec3d)pair2.getRight();
         return (int)Math.signum(intersection1.squaredDistanceTo(start) - intersection2.squaredDistanceTo(start));
      });
      return hits;
   }

   public static <T> T withSwitchedContext(World world, Supplier<T> func) {
      return world.isClient() ? ClientWorldLoader.withSwitchedWorld((ClientWorld)world, func) : func.get();
   }

   private static Pair<BlockHitResult, List<Portal>> rayTrace(World world, RaycastContext context, boolean includeGlobalPortals, List<Portal> portals) {
      Vec3d start = context.getStart();
      Vec3d end = context.getEnd();
      if (portals.size() > IPGlobal.maxPortalLayer) {
         Vec3d diff = end.subtract(start);
         return new Pair(
            BlockHitResult.createMissed(end, Direction.getFacing(diff.x, diff.y, diff.z), BlockPos.ofFloored(end)), portals
         );
      } else {
         BlockHitResult hitResult = world.raycast(context);
         List<Pair<Portal, Vec3d>> rayTracedPortals = rayTracePortals(world, start, end, includeGlobalPortals, Portal::isInteractable);
         if (rayTracedPortals.isEmpty()) {
            return new Pair(hitResult, portals);
         } else {
            Pair<Portal, Vec3d> portalHit = rayTracedPortals.get(0);
            Portal portal = (Portal)portalHit.getLeft();
            Vec3d intersection = (Vec3d)portalHit.getRight();
            if (hitResult.getPos().squaredDistanceTo(start) < intersection.squaredDistanceTo(start)) {
               return new Pair(hitResult, portals);
            } else {
               IERayTraceContext betterContext = (IERayTraceContext)context;
               betterContext.ip_setStart(portal.transformPoint(intersection)).ip_setEnd(portal.transformPoint(end));
               portals.add(portal);
               World destWorld = portal.getDestinationWorld();
               Pair<BlockHitResult, List<Portal>> recursion = withSwitchedContext(
                  destWorld, () -> rayTrace(destWorld, context, includeGlobalPortals, portals)
               );
               betterContext.ip_setStart(start).ip_setEnd(end);
               return recursion;
            }
         }
      }
   }

   public static Pair<BlockHitResult, List<Portal>> rayTrace(World world, RaycastContext context, boolean includeGlobalPortals) {
      return rayTrace(world, context, includeGlobalPortals, new ArrayList<>());
   }

   public static boolean hitResultIsMissedOrNull(HitResult hitResult) {
      return hitResult == null || hitResult.getType() == class_240.field_1333;
   }

   public static MutableText getTextWithCommand(MutableText component, String command) {
      return component.styled(style -> style.withClickEvent(new class_10609(command)).withUnderline(true));
   }

   public static Text getDisableWarningText(String warningKey) {
      return Text.literal(" ")
         .append(
            getTextWithCommand(Text.translatable("imm_ptl.disable_warning"), "/imm_ptl_client_debug disable_warning_for \"" + warningKey + "\"")
         )
         .append(" ");
   }

   public static Text getDisableUpdateCheckText() {
      return Text.literal(" ")
         .append(getTextWithCommand(Text.translatable("imm_ptl.disable_update_check"), "/imm_ptl_client_debug disable_update_check"));
   }

   @Environment(EnvType.CLIENT)
   public static boolean isNvidiaVideocard() {
      return RenderSystem.getDevice().getVendor().toLowerCase().contains("nvidia");
   }

   public static PacketByteBuf bytesToBuf(byte[] packetBytes) {
      return new PacketByteBuf(Unpooled.wrappedBuffer(packetBytes));
   }

   public static byte[] bufToBytes(PacketByteBuf buf) {
      byte[] packetBytes = new byte[buf.readableBytes()];
      buf.method_52979(packetBytes);
      return packetBytes;
   }
}
