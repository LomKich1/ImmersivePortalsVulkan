package qouteall.imm_ptl.core.portal.animation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalExtension;
import qouteall.imm_ptl.core.portal.PortalState;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.Signal;
import qouteall.q_misc_util.platform.event.Event;

@Environment(EnvType.CLIENT)
public class ClientPortalAnimationManagement {
   public static final Event<Consumer<Portal>> CLIENT_PORTAL_DEFAULT_ANIMATION_FINISH = Helper.createConsumerEvent();
   private static final Map<Portal, ClientPortalAnimationManagement.RunningDefaultAnimation> defaultAnimatedPortals = new HashMap<>();
   private static final HashSet<Portal> customAnimatedPortals = new HashSet<>();
   public static final Signal clientAnimationUpdateSignal = new Signal();

   public static void init() {
      IPCGlobal.CLIENT_CLEANUP_EVENT.register(ClientPortalAnimationManagement::cleanup);
      ClientWorldLoader.CLIENT_DIMENSION_DYNAMIC_REMOVE_EVENT.register(dim -> cleanup());
   }

   public static void addDefaultAnimation(Portal portal, PortalState fromState, PortalState toState, DefaultPortalAnimation animation) {
      if (animation.durationTicks > 0) {
         long currTime = System.nanoTime();
         ClientPortalAnimationManagement.RunningDefaultAnimation runningDefaultAnimation = new ClientPortalAnimationManagement.RunningDefaultAnimation(
            fromState, toState, currTime, currTime + Helper.secondToNano(animation.durationTicks / 20.0), animation.timingFunction, animation.inverseScale
         );
         defaultAnimatedPortals.put(portal, runningDefaultAnimation);
      }
   }

   public static void markRequiresCustomAnimationUpdate(Portal portal) {
      customAnimatedPortals.add(portal);
      defaultAnimatedPortals.remove(portal);
   }

   public static void tick() {
      updateCustomAnimations(true);
      updateCustomAnimations(false);
   }

   public static void debugCheck() {
      for (Portal portal : customAnimatedPortals) {
         PortalExtension.forClusterPortals(portal, p -> {
            PortalState thisTickAnimatedState = p.animation.thisTickAnimatedState;
            PortalState lastTickAnimatedState = p.animation.lastTickAnimatedState;
            if (thisTickAnimatedState == null || lastTickAnimatedState == null) {
               Helper.log("ouch");
            }
         });
      }
   }

   public static void update() {
      long currTime = System.nanoTime();
      defaultAnimatedPortals.entrySet().removeIf(entry -> {
         Portal portal = entry.getKey();
         ClientPortalAnimationManagement.RunningDefaultAnimation animation = entry.getValue();
         if (portal.isRemoved()) {
            return true;
         } else if (currTime > animation.toTimeNano) {
            portal.setPortalState(animation.toState);
            CLIENT_PORTAL_DEFAULT_ANIMATION_FINISH.invoker().accept(portal);
            return true;
         } else {
            PortalState currentState = animation.getCurrentState(currTime);
            if (currentState.fromWorld != portal.getOriginDim()) {
               return true;
            } else if (currentState.toWorld != portal.getDestDim()) {
               return true;
            } else {
               portal.setPortalState(currentState);
               return false;
            }
         }
      });
      updateCustomAnimations(false);
      clientAnimationUpdateSignal.emit();
   }

   private static void updateCustomAnimations(boolean isTicking) {
      long stableTickTime = StableClientTimer.getStableTickTime();
      float stablePartialTicks = StableClientTimer.getStablePartialTicks();
      long usedTickTime = isTicking ? stableTickTime + 1L : stableTickTime;
      customAnimatedPortals.removeIf(
         portal -> {
            if (portal.isRemoved()) {
               return true;
            } else if (!portal.animation.hasAnimationDriver()) {
               return true;
            } else {
               portal.animation
                  .updateAnimationDriver(portal, portal.animation, usedTickTime, portal.animation.isPaused() ? 0.0F : stablePartialTicks, isTicking, !isTicking);
               return !portal.animation.hasAnimationDriver();
            }
         }
      );
   }

   private static void cleanup() {
      defaultAnimatedPortals.clear();
      customAnimatedPortals.clear();
   }

   public static void foreachCustomAnimatedPortals(Consumer<Portal> consumer) {
      customAnimatedPortals.forEach(consumer);
   }

   public static class RunningDefaultAnimation {
      public PortalState fromState;
      public PortalState toState;
      public long startTimeNano;
      public long toTimeNano;
      public TimingFunction timingFunction;
      public boolean inverseScale;

      public RunningDefaultAnimation(
         PortalState fromState, PortalState toState, long startTimeNano, long toTimeNano, TimingFunction timingFunction, boolean inverseScale
      ) {
         this.fromState = fromState;
         this.toState = toState;
         this.startTimeNano = startTimeNano;
         this.toTimeNano = toTimeNano;
         this.timingFunction = timingFunction;
         this.inverseScale = inverseScale;
      }

      public PortalState getCurrentState(long currTime) {
         double progress = (double)(currTime - this.startTimeNano) / (this.toTimeNano - this.startTimeNano);
         if (progress < 0.0) {
            Helper.err("invalid portal animation");
            progress = 0.0;
         }

         progress = this.timingFunction.mapProgress(progress);
         return PortalState.interpolate(this.fromState, this.toState, progress, this.inverseScale);
      }
   }
}
