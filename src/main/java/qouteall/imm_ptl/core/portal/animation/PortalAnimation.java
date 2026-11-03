package qouteall.imm_ptl.core.portal.animation;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Formatting;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalExtension;
import qouteall.imm_ptl.core.portal.PortalState;
import qouteall.q_misc_util.Helper;

public class PortalAnimation {
   @NotNull
   public DefaultPortalAnimation defaultAnimation = DefaultPortalAnimation.createDefault();
   @NotNull
   public List<PortalAnimationDriver> thisSideAnimations = new ArrayList<>();
   @NotNull
   public List<PortalAnimationDriver> otherSideAnimations = new ArrayList<>();
   public long pauseTime = 0L;
   public long timeOffset = 0L;
   @Nullable
   public UnilateralPortalState thisSideReferenceState;
   @Nullable
   public UnilateralPortalState otherSideReferenceState;
   @Nullable
   private UnilateralPortalState pausedThisSideState;
   @Nullable
   private UnilateralPortalState pausedOtherSideState;
   @Nullable
   public PortalState lastTickAnimatedState;
   @Nullable
   public PortalState thisTickAnimatedState;
   public long updateCounter;
   @Nullable
   public PortalState clientLastFramePortalState;
   public long clientLastFramePortalStateCounter = -1L;
   @Nullable
   public PortalState clientCurrentFramePortalState;
   public long clientCurrentFramePortalStateCounter = -1L;

   public void readFromTag(NbtCompound tag) {
      if (tag.contains("animation")) {
         this.defaultAnimation = DefaultPortalAnimation.fromNbt(tag.getCompoundOrEmpty("animation"));
      } else if (tag.contains("defaultAnimation")) {
         this.defaultAnimation = DefaultPortalAnimation.fromNbt(tag.getCompoundOrEmpty("defaultAnimation"));
      } else {
         this.defaultAnimation = DefaultPortalAnimation.createDefault();
      }

      if (tag.contains("thisSideAnimations")) {
         NbtList listTag = tag.getListOrEmpty("thisSideAnimations");
         this.thisSideAnimations = Helper.listTagToList(listTag, PortalAnimationDriver::fromTag);
      } else {
         this.thisSideAnimations.clear();
      }

      if (tag.contains("otherSideAnimations")) {
         NbtList listTag = tag.getListOrEmpty("otherSideAnimations");
         this.otherSideAnimations = Helper.listTagToList(listTag, PortalAnimationDriver::fromTag);
      } else {
         this.otherSideAnimations.clear();
      }

      if (tag.contains("pauseTime")) {
         this.pauseTime = tag.getLong("pauseTime", 0L);
      } else {
         this.pauseTime = 0L;
      }

      if (tag.contains("timeOffset")) {
         this.timeOffset = tag.getLong("timeOffset", 0L);
      } else {
         this.timeOffset = 0L;
      }

      if (tag.contains("thisSideReferenceState")) {
         this.thisSideReferenceState = UnilateralPortalState.fromTag(tag.getCompoundOrEmpty("thisSideReferenceState"));
      } else {
         this.thisSideReferenceState = null;
      }

      if (tag.contains("otherSideReferenceState")) {
         this.otherSideReferenceState = UnilateralPortalState.fromTag(tag.getCompoundOrEmpty("otherSideReferenceState"));
      } else {
         this.otherSideReferenceState = null;
      }

      if (tag.contains("pausedThisSideState")) {
         this.pausedThisSideState = UnilateralPortalState.fromTag(tag.getCompoundOrEmpty("pausedThisSideState"));
      } else {
         this.pausedThisSideState = null;
      }

      if (tag.contains("pausedOtherSideState")) {
         this.pausedOtherSideState = UnilateralPortalState.fromTag(tag.getCompoundOrEmpty("pausedOtherSideState"));
      } else {
         this.pausedOtherSideState = null;
      }
   }

   public void writeToTag(NbtCompound tag) {
      tag.put("defaultAnimation", this.defaultAnimation.toNbt());
      if (!this.thisSideAnimations.isEmpty()) {
         tag.put("thisSideAnimations", Helper.listToListTag(this.thisSideAnimations, PortalAnimationDriver::toTag));
      }

      if (!this.otherSideAnimations.isEmpty()) {
         tag.put("otherSideAnimations", Helper.listToListTag(this.otherSideAnimations, PortalAnimationDriver::toTag));
      }

      if (this.pauseTime != 0L) {
         tag.putLong("pauseTime", this.pauseTime);
      }

      if (this.timeOffset != 0L) {
         tag.putLong("timeOffset", this.timeOffset);
      }

      if (this.thisSideReferenceState != null) {
         tag.put("thisSideReferenceState", this.thisSideReferenceState.toTag());
      }

      if (this.otherSideReferenceState != null) {
         tag.put("otherSideReferenceState", this.otherSideReferenceState.toTag());
      }

      if (this.pausedThisSideState != null) {
         tag.put("pausedThisSideState", this.pausedThisSideState.toTag());
      }

      if (this.pausedOtherSideState != null) {
         tag.put("pausedOtherSideState", this.pausedOtherSideState.toTag());
      }
   }

   public boolean isRoughlyRunningAnimation() {
      return this.lastTickAnimatedState != null || this.thisTickAnimatedState != null || this.hasRunningAnimationDriver();
   }

   public boolean hasRunningAnimationDriver() {
      return !this.isPaused() && this.hasAnimationDriver();
   }

   public boolean hasAnimationDriver() {
      return !this.thisSideAnimations.isEmpty() || !this.otherSideAnimations.isEmpty();
   }

   public boolean isPaused() {
      return this.pauseTime != 0L;
   }

   public void setPaused(Portal portal, boolean paused) {
      if (paused != this.isPaused()) {
         if (paused) {
            this.pauseTime = portal.getEntityWorld().getTime();
            PortalState portalState = portal.getPortalState();

            assert portalState != null;

            this.pausedThisSideState = portalState.getThisSideState();
            this.pausedOtherSideState = portalState.getOtherSideState();
         } else {
            this.timeOffset = this.timeOffset - (portal.getEntityWorld().getTime() - this.pauseTime);
            this.pauseTime = 0L;
            if (this.pausedThisSideState != null && this.pausedOtherSideState != null) {
               PortalState currentState = portal.getPortalState();

               assert currentState != null;

               UnilateralPortalState currentThisSideState = currentState.getThisSideState();
               UnilateralPortalState currentOtherSideState = currentState.getOtherSideState();
               DeltaUnilateralPortalState thisSideDelta = currentThisSideState.subtract(this.pausedThisSideState);
               DeltaUnilateralPortalState otherSideDelta = currentOtherSideState.subtract(this.pausedOtherSideState);
               if (this.thisSideReferenceState != null) {
                  this.thisSideReferenceState = this.thisSideReferenceState.apply(thisSideDelta);
               }

               if (this.otherSideReferenceState != null) {
                  this.otherSideReferenceState = this.otherSideReferenceState.apply(otherSideDelta);
               }

               this.pausedThisSideState = null;
               this.pausedOtherSideState = null;
            }
         }

         PortalExtension.forClusterPortals(portal, Portal::reloadAndSyncToClientNextTick);
      }
   }

   public void setBackToPausingState(Portal portal) {
      if (this.pausedThisSideState != null && this.pausedOtherSideState != null) {
         portal.setPortalState(UnilateralPortalState.combine(this.pausedThisSideState, this.pausedOtherSideState));
      }
   }

   public long getEffectiveTime(long gameTime) {
      return (this.isPaused() ? this.pauseTime : gameTime) + this.timeOffset;
   }

   public void tick(Portal portal) {
      this.swapTickRelativeStateIfNeeded(portal);
      if (!portal.getEntityWorld().isClient()) {
         this.updateAnimationDriver(portal, portal.animation, portal.getEntityWorld().getTime(), 1.0F, true, true);
         if (this.thisSideAnimations.isEmpty()) {
            this.thisSideReferenceState = null;
         }

         if (this.otherSideAnimations.isEmpty()) {
            this.otherSideReferenceState = null;
         }

         if (!this.hasAnimationDriver()) {
            this.setPaused(portal, false);
         }
      } else if (this.hasAnimationDriver() && !this.isPaused()) {
         markRequiresClientAnimationUpdate(portal);
      }
   }

   public void swapTickRelativeStateIfNeeded(Portal portal) {
      int counter = portal.age;
      if (counter != this.updateCounter) {
         this.lastTickAnimatedState = this.thisTickAnimatedState;
         this.thisTickAnimatedState = null;
         this.updateCounter = counter;
      }
   }

   private void provideThisTickState(Portal portal, PortalState portalState) {
      this.swapTickRelativeStateIfNeeded(portal);
      if (this.thisTickAnimatedState != null && !portal.getEntityWorld().isClient()) {
         Helper.log("Conflicting animation in " + portal);
         portal.clearAnimationDrivers(true, true);
         this.thisTickAnimatedState = null;
      } else {
         this.thisTickAnimatedState = portalState;
      }
   }

   @Environment(EnvType.CLIENT)
   private static void markRequiresClientAnimationUpdate(Portal portal) {
      ClientPortalAnimationManagement.markRequiresCustomAnimationUpdate(portal);
   }

   public void updateAnimationDriver(Portal portal, PortalAnimation animation, long gameTime, float partialTicks, boolean isTicking, boolean canRemoveAnimation) {
      if (this.hasAnimationDriver()) {
         if (!this.isPaused()) {
            PortalState portalState = portal.getPortalState();
            if (portalState != null) {
               this.initializeReferenceStates(portalState);

               assert this.thisSideReferenceState != null;

               assert this.otherSideReferenceState != null;

               long effectiveGameTime = animation.getEffectiveTime(gameTime);
               float effectivePartialTicks = animation.isPaused() ? 0.0F : partialTicks;
               UnilateralPortalState.Builder thisSideState = new UnilateralPortalState.Builder().from(this.thisSideReferenceState);
               UnilateralPortalState.Builder otherSideState = new UnilateralPortalState.Builder().from(this.otherSideReferenceState);
               int originalThisSideAnimationCount = this.thisSideAnimations.size();
               int originalOtherSideAnimationCount = this.otherSideAnimations.size();
               AnimationContext context = new AnimationContext(portal.getEntityWorld().isClient(), isTicking);
               this.thisSideAnimations.removeIf(animationDriver -> {
                  AnimationResult animationResult = animationDriver.getAnimationResult(effectiveGameTime, effectivePartialTicks, context);
                  if (animationResult.delta() != null) {
                     thisSideState.apply(animationResult.delta());
                  }

                  boolean animationRemoved = canRemoveAnimation && animationResult.isFinished();
                  if (animationRemoved && animationResult.delta() != null) {
                     assert this.thisSideReferenceState != null;

                     this.thisSideReferenceState = new UnilateralPortalState.Builder().from(this.thisSideReferenceState).apply(animationResult.delta()).build();
                  }

                  return animationRemoved;
               });
               this.otherSideAnimations
                  .removeIf(
                     animationDriver -> {
                        AnimationResult animationResult = animationDriver.getAnimationResult(effectiveGameTime, effectivePartialTicks, context);
                        if (animationResult.delta() != null) {
                           otherSideState.apply(animationResult.delta());
                        }

                        boolean animationRemoved = canRemoveAnimation && animationResult.isFinished();
                        if (animationRemoved && animationResult.delta() != null) {
                           assert this.otherSideReferenceState != null;

                           this.otherSideReferenceState = new UnilateralPortalState.Builder()
                              .from(this.otherSideReferenceState)
                              .apply(animationResult.delta())
                              .build();
                        }

                        return animationRemoved;
                     }
                  );
               if (thisSideState.dimension == portalState.fromWorld && otherSideState.dimension == portalState.toWorld) {
                  UnilateralPortalState newThisSideState = thisSideState.build();
                  UnilateralPortalState newOtherSideState = otherSideState.build();
                  PortalState newPortalState = UnilateralPortalState.combine(newThisSideState, newOtherSideState);
                  portal.setPortalState(newPortalState);
                  if (isTicking) {
                     this.provideThisTickState(portal, newPortalState);
                  }

                  portal.rectifyClusterPortals(false);
                  if (isTicking) {
                     PortalExtension.forConnectedPortals(portal, p -> p.animation.provideThisTickState(p, p.getPortalState()));
                  }

                  if (!portal.getEntityWorld().isClient()
                     && (this.thisSideAnimations.size() != originalThisSideAnimationCount || this.otherSideAnimations.size() != originalOtherSideAnimationCount)
                     )
                   {
                     PortalExtension.forClusterPortals(portal, p -> p.reloadAndSyncToClientWithTickDelay(1));
                  }
               } else {
                  Helper.err("Portal animation driver cannot change dimension");
                  if (!portal.getEntityWorld().isClient()) {
                     portal.clearAnimationDrivers(true, true);
                  }
               }
            }
         }
      }
   }

   public void initializeReferenceStates(PortalState portalState) {
      if (this.thisSideReferenceState == null) {
         this.thisSideReferenceState = UnilateralPortalState.extractThisSide(portalState);
      }

      if (this.otherSideReferenceState == null) {
         this.otherSideReferenceState = UnilateralPortalState.extractOtherSide(portalState);
      }
   }

   public void resetReferenceState(Portal portal, boolean thisSide, boolean otherSide) {
      PortalState portalState = portal.getPortalState();

      assert portalState != null;

      if (thisSide && this.thisSideReferenceState != null) {
         this.thisSideReferenceState = UnilateralPortalState.extractThisSide(portalState);
      }

      if (otherSide && this.otherSideReferenceState != null) {
         this.otherSideReferenceState = UnilateralPortalState.extractOtherSide(portalState);
      }
   }

   public void clearAnimationDrivers(Portal portal, boolean clearThisSide, boolean clearOtherSide) {
      Validate.isTrue(!portal.getEntityWorld().isClient());
      this.setPaused(portal, false);
      if (!this.thisSideAnimations.isEmpty() || !this.otherSideAnimations.isEmpty()) {
         AnimationContext context = new AnimationContext(portal.getEntityWorld().isClient(), true);
         PortalState portalState = portal.getPortalState();

         assert portalState != null;

         UnilateralPortalState.Builder from = new UnilateralPortalState.Builder().from(UnilateralPortalState.extractThisSide(portalState));
         UnilateralPortalState.Builder to = new UnilateralPortalState.Builder().from(UnilateralPortalState.extractOtherSide(portalState));
         this.applyEndingState(portal, clearThisSide, clearOtherSide, context, from, to);
         if (clearThisSide) {
            this.thisSideReferenceState = null;
            this.thisSideAnimations.clear();
         }

         if (clearOtherSide) {
            this.otherSideReferenceState = null;
            this.otherSideAnimations.clear();
         }

         PortalState newState = UnilateralPortalState.combine(from.build(), to.build());
         portal.setPortalState(newState);
         PortalExtension.get(portal).rectifyClusterPortals(portal, true);
      }
   }

   private void applyEndingState(
      Portal portal,
      boolean includeThisSide,
      boolean includeOtherSide,
      AnimationContext context,
      UnilateralPortalState.Builder from,
      UnilateralPortalState.Builder to
   ) {
      long effectiveGameTime = portal.getEntityWorld().getTime() + this.timeOffset;
      if (includeThisSide) {
         for (PortalAnimationDriver animationDriver : this.thisSideAnimations) {
            DeltaUnilateralPortalState endingResult = animationDriver.getEndingResult(effectiveGameTime, context);
            if (endingResult != null) {
               from.apply(endingResult);
            }
         }
      }

      if (includeOtherSide) {
         for (PortalAnimationDriver animationDriverx : this.otherSideAnimations) {
            DeltaUnilateralPortalState endingResult = animationDriverx.getEndingResult(effectiveGameTime, context);
            if (endingResult != null) {
               to.apply(endingResult);
            }
         }
      }
   }

   public PortalState getAnimationEndingState(Portal portal) {
      PortalState portalState = portal.getPortalState();

      assert portalState != null;

      this.initializeReferenceStates(portalState);

      assert this.thisSideReferenceState != null;

      assert this.otherSideReferenceState != null;

      UnilateralPortalState.Builder from = new UnilateralPortalState.Builder().from(this.thisSideReferenceState);
      UnilateralPortalState.Builder to = new UnilateralPortalState.Builder().from(this.otherSideReferenceState);
      this.applyEndingState(portal, true, true, new AnimationContext(portal.getEntityWorld().isClient(), true), from, to);
      return UnilateralPortalState.combine(from.build(), to.build());
   }

   @Environment(EnvType.CLIENT)
   public void updateClientState(Portal portal, long currentTeleportationCounter) {
      if (currentTeleportationCounter != this.clientCurrentFramePortalStateCounter) {
         if (this.isRoughlyRunningAnimation()) {
            this.clientLastFramePortalState = this.clientCurrentFramePortalState;
            this.clientLastFramePortalStateCounter = this.clientCurrentFramePortalStateCounter;
            this.clientCurrentFramePortalState = portal.getPortalState();
            this.clientCurrentFramePortalStateCounter = currentTeleportationCounter;
         }
      }
   }

   public Text getInfo(Portal portal, boolean reverse) {
      MutableText component = Text.literal("");
      List<PortalAnimationDriver> l1 = reverse ? this.otherSideAnimations : this.thisSideAnimations;
      List<PortalAnimationDriver> l2 = reverse ? this.thisSideAnimations : this.otherSideAnimations;
      component.append(Text.literal("This Side:\n"));

      for (int i = 0; i < l1.size(); i++) {
         PortalAnimationDriver animation = l1.get(i);
         component.append(
            Text.literal("[%d]: ".formatted(i)).formatted(Formatting.field_1065).append(animation.getInfo()).append("\n")
         );
      }

      component.append(Text.literal("Other Side:\n"));

      for (int i = 0; i < l2.size(); i++) {
         PortalAnimationDriver animation = l2.get(i);
         component.append(
            Text.literal("[%d]: ".formatted(i)).formatted(Formatting.field_1065).append(animation.getInfo()).append("\n")
         );
      }

      return component;
   }
}
