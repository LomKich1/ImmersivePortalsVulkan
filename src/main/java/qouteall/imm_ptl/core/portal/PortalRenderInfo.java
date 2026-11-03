package qouteall.imm_ptl.core.portal;

import com.mojang.logging.LogUtils;
import java.lang.ref.Cleaner;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.profiler.Profilers;
import net.minecraft.util.profiler.Profiler;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.render.GlQueryObject;
import qouteall.imm_ptl.core.render.QueryManager;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.q_misc_util.Helper;

@Environment(EnvType.CLIENT)
public class PortalRenderInfo implements AutoCloseable {
   private static final Logger LOGGER = LogUtils.getLogger();
   private final Map<List<UUID>, PortalRenderInfo.Visibility> infoMap = new HashMap<>();
   public int thisFrameQueryFrameIndex = -1;
   private long mispredictTime1 = 0L;
   private long mispredictTime2 = 0L;
   private int totalMispredictCount = 0;
   private static final Cleaner CLEANER = Cleaner.create();

   public static void init() {
      Portal.CLIENT_PORTAL_TICK_SIGNAL.register(portal -> {
         PortalRenderInfo presentation = getOptional(portal);
         if (presentation != null) {
            presentation.tick(portal);
         }
      });
      Portal.PORTAL_DISPOSE_SIGNAL.register(portal -> {
         if (portal.getEntityWorld().isClient()) {
            PortalRenderInfo renderInfo = getOptional(portal);
            if (renderInfo != null) {
               renderInfo.dispose();
            }
         }
      });
   }

   @Nullable
   public static PortalRenderInfo getOptional(Portal portal) {
      Validate.isTrue(portal.getEntityWorld().isClient());
      return portal.portalRenderInfo;
   }

   public static PortalRenderInfo get(Portal portal) {
      Validate.isTrue(portal.getEntityWorld().isClient());
      if (portal.portalRenderInfo == null) {
         portal.portalRenderInfo = new PortalRenderInfo();
      }

      return portal.portalRenderInfo;
   }

   public PortalRenderInfo() {
      CLEANER.register(this, this.getGcDirectedCleaningFunc());
   }

   private void tick(Portal portal) {
      Validate.isTrue(portal.getEntityWorld().isClient());
   }

   public void dispose() {
      disposeInfoMap(this.infoMap);
   }

   private Runnable getGcDirectedCleaningFunc() {
      Map<List<UUID>, PortalRenderInfo.Visibility> infoMap1 = this.infoMap;
      return () -> {
         LOGGER.debug("Running GC-directed PortalRenderInfo clean");
         IPGlobal.PRE_TOTAL_RENDER_TASK_LIST.addOneShotTask(() -> disposeInfoMap(infoMap1));
      };
   }

   @Override
   public void close() throws Exception {
      this.dispose();
   }

   private static void disposeInfoMap(Map<List<UUID>, PortalRenderInfo.Visibility> infoMap) {
      infoMap.values().forEach(PortalRenderInfo.Visibility::dispose);
      infoMap.clear();
   }

   private void updateQuerySet() {
      if (RenderStates.frameIndex != this.thisFrameQueryFrameIndex) {
         if (RenderStates.frameIndex == this.thisFrameQueryFrameIndex + 1) {
            this.infoMap.entrySet().removeIf(entry -> {
               PortalRenderInfo.Visibility visibility = entry.getValue();
               return visibility.lastFrameQuery == null && visibility.thisFrameQuery == null;
            });
            this.infoMap.values().forEach(PortalRenderInfo.Visibility::update);
         } else {
            disposeInfoMap(this.infoMap);
         }

         this.thisFrameQueryFrameIndex = RenderStates.frameIndex;
      }
   }

   @NotNull
   private PortalRenderInfo.Visibility getVisibility(List<UUID> desc) {
      this.updateQuerySet();
      return this.infoMap.computeIfAbsent(desc, k -> new PortalRenderInfo.Visibility());
   }

   private void onMispredict() {
      this.mispredictTime1 = this.mispredictTime2;
      this.mispredictTime2 = System.nanoTime();
      this.totalMispredictCount++;
   }

   private boolean isFrequentlyMispredicted() {
      if (this.totalMispredictCount > 5) {
         return true;
      } else {
         long currTime = System.nanoTime();
         return currTime - this.mispredictTime1 < Helper.secondToNano(30.0);
      }
   }

   private void updatePredictionStatus(PortalRenderInfo.Visibility visibility, boolean thisFrameDecision) {
      visibility.thisFrameRendered = thisFrameDecision;
      if (thisFrameDecision && visibility.lastFrameRendered != null && !visibility.lastFrameRendered && !this.isFrequentlyMispredicted()) {
         this.onMispredict();
      }
   }

   public static boolean renderAndDecideVisibility(Portal portal, Runnable queryRendering) {
      Profiler profiler = Profilers.get();
      boolean decision;
      if (IPGlobal.offsetOcclusionQuery) {
         PortalRenderInfo renderInfo = get(portal);
         List<UUID> renderingDescription = WorldRenderInfo.getRenderingDescription();
         PortalRenderInfo.Visibility visibility = renderInfo.getVisibility(renderingDescription);
         GlQueryObject lastFrameQuery = visibility.lastFrameQuery;
         GlQueryObject thisFrameQuery = visibility.acquireThisFrameQuery();
         thisFrameQuery.performQueryAnySamplePassed(queryRendering);
         boolean noPredict = renderInfo.isFrequentlyMispredicted() || QueryManager.queryStallCounter <= 3;
         if (lastFrameQuery != null) {
            boolean lastFrameVisible = lastFrameQuery.fetchQueryResult();
            if (!lastFrameVisible && noPredict) {
               profiler.push("fetch_this_frame");
               decision = thisFrameQuery.fetchQueryResult();
               profiler.pop();
               QueryManager.queryStallCounter++;
            } else {
               decision = lastFrameVisible;
               renderInfo.updatePredictionStatus(visibility, lastFrameVisible);
            }
         } else {
            profiler.push("fetch_this_frame");
            decision = thisFrameQuery.fetchQueryResult();
            profiler.pop();
            QueryManager.queryStallCounter++;
         }
      } else {
         decision = QueryManager.renderAndGetDoesAnySamplePass(queryRendering);
      }

      return decision;
   }

   public static class Visibility {
      public GlQueryObject lastFrameQuery = null;
      public GlQueryObject thisFrameQuery = null;
      public Boolean lastFrameRendered = null;
      public Boolean thisFrameRendered;

      void update() {
         if (this.lastFrameQuery != null) {
            GlQueryObject.returnQueryObject(this.lastFrameQuery);
         }

         this.lastFrameQuery = this.thisFrameQuery;
         this.thisFrameQuery = null;
         this.lastFrameRendered = this.thisFrameRendered;
         this.thisFrameRendered = null;
      }

      void dispose() {
         if (this.lastFrameQuery != null) {
            GlQueryObject.returnQueryObject(this.lastFrameQuery);
         }

         if (this.thisFrameQuery != null) {
            GlQueryObject.returnQueryObject(this.thisFrameQuery);
         }
      }

      GlQueryObject acquireThisFrameQuery() {
         if (this.thisFrameQuery == null) {
            this.thisFrameQuery = GlQueryObject.acquireQueryObject();
         }

         return this.thisFrameQuery;
      }
   }
}
