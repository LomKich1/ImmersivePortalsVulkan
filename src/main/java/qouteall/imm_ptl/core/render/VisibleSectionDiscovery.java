package qouteall.imm_ptl.core.render;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayDeque;
import java.util.Stack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.render.chunk.ChunkBuilder.class_851;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.chunk_loading.PerformanceLevel;
import qouteall.imm_ptl.core.ducks.IERenderSection;
import qouteall.imm_ptl.core.miscellaneous.ClientPerformanceMonitor;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.nether_portal.BlockTraverse;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;

@Environment(EnvType.CLIENT)
public class VisibleSectionDiscovery {
   private static ImmPtlViewArea builtChunks;
   private static Frustum vanillaFrustum;
   private static ObjectArrayList<class_851> resultHolder;
   private static final ArrayDeque<class_851> tempQueue = new ArrayDeque<>();
   private static ChunkSectionPos cameraSectionPos;
   private static long timeMark;
   private static int viewDistance;
   private static final Stack<ObjectArrayList<class_851>> listCaches = new Stack<>();

   public static void discoverVisibleSections(
      ClientWorld world, ImmPtlViewArea builtChunks_, Camera camera, Frustum vanillaFrustum_, ObjectArrayList<class_851> resultHolder_
   ) {
      builtChunks = builtChunks_;
      vanillaFrustum = vanillaFrustum_;
      resultHolder = resultHolder_;
      resultHolder.clear();
      tempQueue.clear();
      updateViewDistance();
      timeMark = System.nanoTime();
      Vec3d cameraPos = camera.getCameraPos();
      vanillaFrustum.setPosition(cameraPos.x, cameraPos.y, cameraPos.z);
      cameraSectionPos = ChunkSectionPos.from(BlockPos.ofFloored(cameraPos));
      ChunkSectionPos modifiedVisibleSectionIterationOrigin = null;
      if (PortalRendering.isRendering()) {
         Portal renderingPortal = PortalRendering.getRenderingPortal();
         modifiedVisibleSectionIterationOrigin = renderingPortal.getPortalShape().getModifiedVisibleSectionIterationOrigin(renderingPortal, cameraPos);
      }

      if (modifiedVisibleSectionIterationOrigin != null) {
         checkSection(
            modifiedVisibleSectionIterationOrigin.getX(),
            modifiedVisibleSectionIterationOrigin.getY(),
            modifiedVisibleSectionIterationOrigin.getZ(),
            true
         );
      } else if (cameraPos.y < world.getBottomY()) {
         discoverBottomOrTopLayerVisibleChunks(builtChunks.minSectionY);
      } else if (cameraPos.y > world.getTopYInclusive() + 1) {
         discoverBottomOrTopLayerVisibleChunks(builtChunks.endSectionY - 1);
      } else {
         checkSection(cameraSectionPos.getSectionX(), cameraSectionPos.getSectionY(), cameraSectionPos.getSectionZ(), true);
      }

      while (!tempQueue.isEmpty()) {
         class_851 curr = tempQueue.poll();
         int cx = ChunkSectionPos.getSectionCoord(curr.getOrigin().getX());
         int cy = ChunkSectionPos.getSectionCoord(curr.getOrigin().getY());
         int cz = ChunkSectionPos.getSectionCoord(curr.getOrigin().getZ());
         checkSection(cx + 1, cy, cz, false);
         checkSection(cx - 1, cy, cz, false);
         checkSection(cx, cy + 1, cz, false);
         checkSection(cx, cy - 1, cz, false);
         checkSection(cx, cy, cz + 1, false);
         checkSection(cx, cy, cz - 1, false);
      }

      resultHolder = null;
      builtChunks = null;
      vanillaFrustum = null;
   }

   private static void updateViewDistance() {
      int distance = WorldRenderInfo.getRenderDistance();
      viewDistance = PerformanceLevel.getPortalRenderingDistance(ClientPerformanceMonitor.level, distance);
   }

   private static boolean isVisible(class_851 builtChunk) {
      Box box = builtChunk.getBoundingBox();
      return vanillaFrustum.isVisible(box);
   }

   private static void discoverBottomOrTopLayerVisibleChunks(int cy) {
      BlockTraverse.searchOnPlane(cameraSectionPos.getSectionX(), cameraSectionPos.getSectionZ(), viewDistance - 1, (cx, cz) -> {
         checkSection(cx, cy, cz, false);
         return null;
      });
   }

   private static void checkSection(int cx, int cy, int cz, boolean skipFrustumTest) {
      if (Math.abs(cx - cameraSectionPos.getSectionX()) <= viewDistance) {
         if (Math.abs(cy - cameraSectionPos.getSectionY()) <= viewDistance) {
            if (Math.abs(cz - cameraSectionPos.getSectionZ()) <= viewDistance) {
               class_851 builtChunk = builtChunks.rawFetch(cx, cy, cz, timeMark);
               if (builtChunk != null) {
                  IERenderSection ieRenderSection = (IERenderSection)builtChunk;
                  if (ieRenderSection.portal_getMark() != timeMark) {
                     ieRenderSection.portal_setMark(timeMark);
                     if (skipFrustumTest || isVisible(builtChunk)) {
                        tempQueue.add(builtChunk);
                        resultHolder.add(builtChunk);
                     }
                  }
               }
            }
         }
      }
   }

   public static ObjectArrayList<class_851> takeList() {
      return listCaches.isEmpty() ? new ObjectArrayList() : listCaches.pop();
   }

   public static void returnList(ObjectArrayList<class_851> list) {
      list.clear();
      listCaches.push(list);
   }

   public static void init() {
      IPCGlobal.CLIENT_CLEANUP_EVENT.register(VisibleSectionDiscovery::cleanUp);
      ClientWorldLoader.CLIENT_DIMENSION_DYNAMIC_REMOVE_EVENT.register(dim -> cleanUp());
   }

   private static void cleanUp() {
      listCaches.clear();
      resultHolder = null;
      builtChunks = null;
      vanillaFrustum = null;
   }
}
