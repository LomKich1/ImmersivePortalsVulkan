package qouteall.imm_ptl.core.commands;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.Collection;
import java.util.List;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalExtension;
import qouteall.imm_ptl.core.portal.PortalState;
import qouteall.imm_ptl.core.portal.animation.AnimationView;
import qouteall.imm_ptl.core.portal.animation.DeltaUnilateralPortalState;
import qouteall.imm_ptl.core.portal.animation.NormalAnimation;
import qouteall.imm_ptl.core.portal.animation.PortalAnimation;
import qouteall.imm_ptl.core.portal.animation.PortalAnimationDriver;
import qouteall.imm_ptl.core.portal.animation.RotationAnimation;
import qouteall.imm_ptl.core.portal.animation.TimingFunction;
import qouteall.imm_ptl.core.portal.animation.UnilateralPortalState;
import qouteall.q_misc_util.my_util.Access;
import qouteall.q_misc_util.my_util.Vec2d;

public class PortalAnimationCommand {
   static void registerPortalAnimationCommands(LiteralArgumentBuilder<ServerCommandSource> builder) {
      builder.then(CommandManager.literal("clear").executes(context -> PortalCommand.processPortalTargetedCommand(context, portal -> {
         AnimationView animationView = portal.getAnimationView();
         animationView.getThisSideAnimations().clear();
         PortalCommand.reloadPortal(portal);
         ((ServerCommandSource)context.getSource()).sendFeedback(() -> getAnimationInfo(portal), false);
      })));
      builder.then(CommandManager.literal("clear_both_sides").executes(context -> PortalCommand.processPortalTargetedCommand(context, portal -> {
         AnimationView animationView = portal.getAnimationView();
         animationView.getThisSideAnimations().clear();
         animationView.getOtherSideAnimations().clear();
         PortalCommand.reloadPortal(portal);
         ((ServerCommandSource)context.getSource()).sendFeedback(() -> getAnimationInfo(portal), false);
      })));
      builder.then(CommandManager.literal("pause").executes(context -> PortalCommand.processPortalTargetedCommand(context, portal -> {
         PortalExtension.forClusterPortals(portal, Portal::pauseAnimation);
         PortalCommand.reloadPortal(portal);
         ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal("Paused"), false);
      })));
      builder.then(CommandManager.literal("resume").executes(context -> PortalCommand.processPortalTargetedCommand(context, portal -> {
         PortalExtension.forClusterPortals(portal, Portal::resumeAnimation);
         PortalCommand.reloadPortal(portal);
         ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal("Resumed"), false);
      })));
      builder.then(CommandManager.literal("all_pause").executes(context -> {
         ServerWorld level = ((ServerCommandSource)context.getSource()).getWorld();

         for (Entity entity : level.iterateEntities()) {
            if (entity instanceof Portal portal) {
               PortalExtension.forClusterPortals(portal, Portal::pauseAnimation);
               ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal("Paused " + portal.toString()), false);
            }
         }

         return 0;
      }));
      builder.then(
         CommandManager.literal("view")
            .executes(
               context -> PortalCommand.processPortalTargetedCommand(
                  context, portal -> ((ServerCommandSource)context.getSource()).sendFeedback(() -> getAnimationInfo(portal), false)
               )
            )
      );
      builder.then(
         CommandManager.literal("remove_at")
            .then(
               CommandManager.argument("index", IntegerArgumentType.integer())
                  .executes(context -> PortalCommand.processPortalTargetedCommand(context, portal -> {
                     int index = IntegerArgumentType.getInteger(context, "index");
                     AnimationView animationView = portal.getAnimationView();
                     List<PortalAnimationDriver> thisSideAnimations = animationView.getThisSideAnimations();
                     if (index >= 0 && index < thisSideAnimations.size()) {
                        thisSideAnimations.remove(index);
                        PortalCommand.reloadPortal(portal);
                        ((ServerCommandSource)context.getSource()).sendFeedback(() -> getAnimationInfo(portal), false);
                     } else {
                        ((ServerCommandSource)context.getSource()).sendError(Text.literal("Invalid index " + index));
                     }
                  }))
            )
      );
      builder.then(CommandManager.literal("remove_last").executes(context -> PortalCommand.processPortalTargetedCommand(context, portal -> {
         AnimationView animationView = portal.getAnimationView();
         List<PortalAnimationDriver> thisSideAnimations = animationView.getThisSideAnimations();
         if (!thisSideAnimations.isEmpty()) {
            thisSideAnimations.remove(thisSideAnimations.size() - 1);
            PortalCommand.reloadPortal(portal);
            ((ServerCommandSource)context.getSource()).sendFeedback(() -> getAnimationInfo(portal), false);
         } else {
            ((ServerCommandSource)context.getSource()).sendError(Text.literal("No animation"));
         }
      })));
      builder.then(
         CommandManager.literal("rotate_infinitely")
            .then(
               CommandManager.argument("rotationCenterEntity", EntityArgumentType.entity())
                  .then(
                     CommandManager.argument("rotationAxis", Vec3ArgumentType.vec3(false))
                        .then(
                           CommandManager.argument("degreesPerTick", DoubleArgumentType.doubleArg())
                              .executes(context -> PortalCommand.processPortalTargetedCommand(context, portal -> {
                                 Entity rotationCenterEntity = EntityArgumentType.getEntity(context, "rotationCenterEntity");
                                 Vec3d rotationCenter = rotationCenterEntity.getEntityPos();
                                 Vec3d axis = Vec3ArgumentType.getVec3(context, "rotationAxis").normalize();
                                 double angularVelocity = DoubleArgumentType.getDouble(context, "degreesPerTick");
                                 giveRotationAnimation(portal, rotationCenter, axis, angularVelocity);
                                 PortalCommand.reloadPortal(portal);
                                 ((ServerCommandSource)context.getSource()).sendFeedback(() -> getAnimationInfo(portal), false);
                              }))
                        )
                  )
            )
      );
      builder.then(CommandManager.literal("rotate_infinitely_random").executes(context -> PortalCommand.processPortalTargetedCommand(context, portal -> {
         Vec3d rotationCenter = ((ServerCommandSource)context.getSource()).getPosition();
         Vec3d axis = new Vec3d(Math.random() - 0.5, Math.random() - 0.5, Math.random() - 0.5).normalize();
         double angularVelocity = Math.random() * 3.0;
         giveRotationAnimation(portal, rotationCenter, axis, angularVelocity);
         PortalCommand.reloadPortal(portal);
         ((ServerCommandSource)context.getSource()).sendFeedback(() -> getAnimationInfo(portal), false);
      })));
      builder.then(
         CommandManager.literal("rotate_portals_infinitely")
            .then(
               CommandManager.argument("portals", EntityArgumentType.entities())
                  .then(
                     CommandManager.argument("rotationCenter", Vec3ArgumentType.vec3())
                        .then(
                           CommandManager.argument("axis", Vec3ArgumentType.vec3(false))
                              .then(CommandManager.argument("degreesPerTick", DoubleArgumentType.doubleArg()).executes(context -> {
                                 Collection<? extends Entity> portals = EntityArgumentType.getEntities(context, "portals");
                                 Vec3d rotationCenter = Vec3ArgumentType.getVec3(context, "rotationCenter");
                                 Vec3d axis = Vec3ArgumentType.getVec3(context, "axis").normalize();
                                 double angularVelocity = DoubleArgumentType.getDouble(context, "degreesPerTick");

                                 for (Entity entity : portals) {
                                    if (entity instanceof Portal portal) {
                                       giveRotationAnimation(portal, rotationCenter, axis, angularVelocity);
                                       PortalCommand.reloadPortal(portal);
                                    } else {
                                       ((ServerCommandSource)context.getSource()).sendError(Text.literal("the entity is not a portal"));
                                    }
                                 }

                                 for (Entity entityx : portals) {
                                    if (entityx instanceof Portal portal) {
                                       PortalCommand.reloadPortal(portal);
                                    }
                                 }

                                 return 0;
                              }))
                        )
                  )
            )
      );
      builder.then(
         CommandManager.literal("rotate_portals")
            .then(
               CommandManager.argument("portals", EntityArgumentType.entities())
                  .then(
                     CommandManager.argument("rotationCenter", Vec3ArgumentType.vec3())
                        .then(
                           CommandManager.argument("axis", Vec3ArgumentType.vec3(false))
                              .then(
                                 CommandManager.argument("degrees", DoubleArgumentType.doubleArg())
                                    .then(
                                       CommandManager.argument("duration", IntegerArgumentType.integer(1, 10000))
                                          .executes(
                                             context -> {
                                                Collection<? extends Entity> portals = EntityArgumentType.getEntities(context, "portals");
                                                Vec3d rotationCenter = Vec3ArgumentType.getVec3(context, "rotationCenter");
                                                Vec3d axis = Vec3ArgumentType.getVec3(context, "axis").normalize();
                                                double degrees = DoubleArgumentType.getDouble(context, "degrees");
                                                int duration = IntegerArgumentType.getInteger(context, "duration");

                                                for (Entity entity : portals) {
                                                   if (entity instanceof Portal portal) {
                                                      PortalState endingState = portal.getAnimationEndingState();
                                                      long currTime = portal.getEntityWorld().getTime();
                                                      portal.addThisSideAnimationDriver(
                                                         new RotationAnimation.Builder()
                                                            .setInitialOffset(endingState.fromPos.subtract(rotationCenter))
                                                            .setRotationAxis(axis)
                                                            .setDegreesPerTick(degrees / duration)
                                                            .setStartGameTime(currTime)
                                                            .setEndGameTime(currTime + duration)
                                                            .setTimingFunction(TimingFunction.sine)
                                                            .build()
                                                      );
                                                      PortalCommand.reloadPortal(portal);
                                                   } else {
                                                      ((ServerCommandSource)context.getSource()).sendError(Text.literal("the entity is not a portal"));
                                                   }
                                                }

                                                for (Entity entityx : portals) {
                                                   if (entityx instanceof Portal portal) {
                                                      PortalCommand.reloadPortal(portal);
                                                   }
                                                }

                                                return 0;
                                             }
                                          )
                                    )
                              )
                        )
                  )
            )
      );
      builder.then(
         CommandManager.literal("rotate_along_normal")
            .then(
               CommandManager.argument("degreesPerTick", DoubleArgumentType.doubleArg())
                  .executes(context -> PortalCommand.processPortalTargetedCommand(context, portal -> {
                     double angularVelocity = DoubleArgumentType.getDouble(context, "degreesPerTick");
                     giveRotationAnimation(portal, portal.getOriginPos(), portal.getNormal(), angularVelocity);
                     PortalCommand.reloadPortal(portal);
                     ((ServerCommandSource)context.getSource()).sendFeedback(() -> getAnimationInfo(portal), false);
                  }))
            )
      );
      builder.then(
         CommandManager.literal("expand_from_center")
            .then(
               CommandManager.argument("durationTicks", IntegerArgumentType.integer(0, 1000))
                  .executes(
                     context -> PortalCommand.processPortalTargetedCommand(
                        context,
                        portal -> {
                           int durationTicks = IntegerArgumentType.getInteger(context, "durationTicks");
                           double animationScale = 10.0;
                           portal.addThisSideAnimationDriver(
                              NormalAnimation.createSizeAnimation(
                                 portal,
                                 new Vec2d(1.0 / animationScale, 1.0 / animationScale),
                                 new Vec2d(1.0, 1.0),
                                 portal.getAnimationEffectiveTime(),
                                 durationTicks,
                                 TimingFunction.sine
                              )
                           );
                           PortalCommand.reloadPortal(portal);
                           ((ServerCommandSource)context.getSource()).sendFeedback(() -> getAnimationInfo(portal), false);
                        }
                     )
                  )
            )
      );
      LiteralArgumentBuilder<ServerCommandSource> buildBuilder = CommandManager.literal("build");
      buildBuilder.then(
         CommandManager.literal("begin")
            .executes(
               context -> PortalCommand.processPortalTargetedCommand(
                  context,
                  portal -> {
                     Portal animationHolder = portal.getAnimationHolder();
                     if (animationHolder != null && animationHolder != portal) {
                        sendAnimationHolderFailure(context, animationHolder);
                     } else {
                        PortalAnimation animation = portal.animation;
                        animation.setPaused(portal, true);
                        PortalState portalState = portal.getPortalState();

                        assert portalState != null;

                        if (animation.thisSideReferenceState != null && animation.otherSideReferenceState != null) {
                           PortalState newState = UnilateralPortalState.combine(animation.thisSideReferenceState, animation.otherSideReferenceState);
                           portal.setPortalState(newState);
                        } else {
                           animation.thisSideReferenceState = UnilateralPortalState.extractThisSide(portalState);
                           animation.otherSideReferenceState = UnilateralPortalState.extractOtherSide(portalState);
                        }

                        NormalAnimation newNormalAnimation = new NormalAnimation.Builder()
                           .phases(List.of())
                           .loopCount(1)
                           .startingGameTime(portal.getAnimationEffectiveTime())
                           .isBuilding(true)
                           .build();
                        animation.thisSideAnimations.add(newNormalAnimation);
                        animation.otherSideAnimations.add(newNormalAnimation);
                        PortalCommand.reloadPortal(portal);
                        ((ServerCommandSource)context.getSource()).sendFeedback(() -> getAnimationInfo(portal), false);
                     }
                  }
               )
            )
      );
      buildBuilder.then(
         CommandManager.literal("append_phase")
            .then(
               ((RequiredArgumentBuilder)CommandManager.argument("durationTicks", IntegerArgumentType.integer(0, 1000))
                     .executes(context -> PortalCommand.processPortalTargetedCommand(context, portal -> {
                        int durationTicks = IntegerArgumentType.getInteger(context, "durationTicks");
                        executeAppendPhase(context, portal, durationTicks, TimingFunction.sine);
                     })))
                  .then(
                     CommandManager.argument("timingFunction", TimingFunctionArgumentType.instance)
                        .executes(context -> PortalCommand.processPortalTargetedCommand(context, portal -> {
                           int durationTicks = IntegerArgumentType.getInteger(context, "durationTicks");
                           TimingFunction timingFunction = TimingFunctionArgumentType.get(context, "timingFunction");
                           executeAppendPhase(context, portal, durationTicks, timingFunction);
                        }))
                  )
            )
      );
      buildBuilder.then(
         ((LiteralArgumentBuilder)CommandManager.literal("finish")
               .executes(context -> PortalCommand.processPortalTargetedCommand(context, portal -> finishBuildingNormalAnimation(context, portal, 100000))))
            .then(
               CommandManager.argument("loopCount", IntegerArgumentType.integer(0, 100000))
                  .executes(context -> PortalCommand.processPortalTargetedCommand(context, portal -> {
                     int loopCount = IntegerArgumentType.getInteger(context, "loopCount");
                     finishBuildingNormalAnimation(context, portal, loopCount);
                  }))
            )
      );
      buildBuilder.then(
         CommandManager.literal("jump_to_phase")
            .then(
               CommandManager.argument("index", IntegerArgumentType.integer(0, 100000))
                  .executes(
                     context -> PortalCommand.processPortalTargetedCommand(
                        context,
                        portal -> {
                           int index = IntegerArgumentType.getInteger(context, "index");
                           Portal animationHolder = portal.getAnimationHolder();
                           if (animationHolder != null && animationHolder != portal) {
                              sendAnimationHolderFailure(context, animationHolder);
                           } else {
                              PortalAnimation animation = portal.animation;
                              PortalAnimationCommand.AnimationBuilderContext animationBuilderContext = getAnimationBuilderContext(portal);
                              if (animationBuilderContext == null) {
                                 ((ServerCommandSource)context.getSource()).sendError(Text.literal("No animation to build"));
                              } else {
                                 NormalAnimation thisSideAnimation = animationBuilderContext.thisSideAnimation().get();
                                 List<NormalAnimation.Phase> phases = thisSideAnimation.phases;
                                 if (index >= phases.size()) {
                                    ((ServerCommandSource)context.getSource()).sendError(Text.literal("Index out of range"));
                                 } else {
                                    long totalTicksBefore = phases.stream().limit(index + 1).mapToLong(p -> p.durationTicks()).sum();
                                    long newStartingGameTime = portal.getAnimationEffectiveTime() - totalTicksBefore;
                                    animationBuilderContext.thisSideAnimation()
                                       .set(
                                          new NormalAnimation.Builder()
                                             .from(animationBuilderContext.thisSideAnimation().get())
                                             .startingGameTime(newStartingGameTime)
                                             .isBuilding(false)
                                             .build()
                                       );
                                    animationBuilderContext.otherSideAnimation()
                                       .set(
                                          new NormalAnimation.Builder()
                                             .from(animationBuilderContext.otherSideAnimation().get())
                                             .startingGameTime(newStartingGameTime)
                                             .isBuilding(false)
                                             .build()
                                       );
                                    PortalCommand.reloadPortal(portal);
                                    ((ServerCommandSource)context.getSource()).sendFeedback(() -> getAnimationInfo(portal), false);
                                 }
                              }
                           }
                        }
                     )
                  )
            )
      );
      builder.then(buildBuilder);
   }

   private static void executeAppendPhase(CommandContext<ServerCommandSource> context, Portal portal, int durationTicks, TimingFunction timingFunction) {
      Portal animationHolder = portal.getAnimationHolder();
      if (animationHolder != null && animationHolder != portal) {
         sendAnimationHolderFailure(context, animationHolder);
      } else {
         PortalAnimation animation = portal.animation;
         PortalAnimationCommand.AnimationBuilderContext animationBuilderContext = getAnimationBuilderContext(portal);
         if (animationBuilderContext == null) {
            ((ServerCommandSource)context.getSource()).sendError(Text.literal("No animation to build"));
         } else {
            PortalState currentPortalState = portal.getPortalState();

            assert currentPortalState != null;

            UnilateralPortalState thisSideReferenceState = animation.thisSideReferenceState;
            UnilateralPortalState otherSideReferenceState = animation.otherSideReferenceState;
            if (thisSideReferenceState != null && otherSideReferenceState != null) {
               DeltaUnilateralPortalState thisSideDelta = UnilateralPortalState.extractThisSide(currentPortalState).subtract(thisSideReferenceState);
               DeltaUnilateralPortalState otherSideDelta = UnilateralPortalState.extractOtherSide(currentPortalState).subtract(otherSideReferenceState);
               NormalAnimation thisSideAnimation = animationBuilderContext.thisSideAnimation().get();
               animationBuilderContext.thisSideAnimation()
                  .set(
                     new NormalAnimation.Builder()
                        .phases(
                           ImmutableList.<NormalAnimation.Phase>builder()
                              .addAll(thisSideAnimation.phases)
                              .add(new NormalAnimation.Phase.Builder().durationTicks(durationTicks).timingFunction(timingFunction).delta(thisSideDelta).build())
                              .build()
                        )
                        .loopCount(thisSideAnimation.loopCount)
                        .startingGameTime(thisSideAnimation.startingGameTime)
                        .isBuilding(true)
                        .build()
                  );
               NormalAnimation otherSideAnimation = animationBuilderContext.otherSideAnimation().get();
               animationBuilderContext.otherSideAnimation()
                  .set(
                     new NormalAnimation.Builder()
                        .phases(
                           ImmutableList.<NormalAnimation.Phase>builder()
                              .addAll(otherSideAnimation.phases)
                              .add(
                                 new NormalAnimation.Phase.Builder().durationTicks(durationTicks).timingFunction(timingFunction).delta(otherSideDelta).build()
                              )
                              .build()
                        )
                        .loopCount(otherSideAnimation.loopCount)
                        .startingGameTime(otherSideAnimation.startingGameTime)
                        .isBuilding(true)
                        .build()
                  );
               PortalCommand.reloadPortal(portal);
               ((ServerCommandSource)context.getSource()).sendFeedback(() -> getAnimationInfo(portal), false);
            } else {
               ((ServerCommandSource)context.getSource()).sendError(Text.literal("No reference state"));
            }
         }
      }
   }

   private static PortalAnimationCommand.AnimationBuilderContext getAnimationBuilderContext(Portal portal) {
      final PortalAnimation animation = portal.animation;
      if (!animation.thisSideAnimations.isEmpty() && !animation.otherSideAnimations.isEmpty()) {
         final int thisSideIndex = animation.thisSideAnimations.size() - 1;
         PortalAnimationDriver lastThisSideAnimation = animation.thisSideAnimations.get(thisSideIndex);
         if (lastThisSideAnimation instanceof NormalAnimation thisSideNormalAnimation) {
            final int otherSideIndex = animation.otherSideAnimations.size() - 1;
            PortalAnimationDriver lastOtherSideAnimation = animation.otherSideAnimations.get(otherSideIndex);
            return lastOtherSideAnimation instanceof NormalAnimation otherSideNormalAnimation
               ? new PortalAnimationCommand.AnimationBuilderContext(portal, new Access<NormalAnimation>() {
                  public NormalAnimation get() {
                     return thisSideNormalAnimation;
                  }

                  public void set(NormalAnimation normalAnimation) {
                     animation.thisSideAnimations.set(thisSideIndex, normalAnimation);
                  }
               }, new Access<NormalAnimation>() {
                  public NormalAnimation get() {
                     return otherSideNormalAnimation;
                  }

                  public void set(NormalAnimation normalAnimation) {
                     animation.otherSideAnimations.set(otherSideIndex, normalAnimation);
                  }
               })
               : null;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private static void sendAnimationHolderFailure(CommandContext<ServerCommandSource> context, Portal animationHolder) {
      ((ServerCommandSource)context.getSource()).sendError(Text.literal("This portal entity is not animation holder. Use this command to " + animationHolder));
   }

   private static void finishBuildingNormalAnimation(CommandContext<ServerCommandSource> context, Portal portal, int loopCount) {
      Portal animationHolder = portal.getAnimationHolder();
      if (animationHolder != null && animationHolder != portal) {
         sendAnimationHolderFailure(context, animationHolder);
      } else {
         PortalAnimation animation = portal.animation;
         PortalAnimationCommand.AnimationBuilderContext animationBuilderContext = getAnimationBuilderContext(portal);
         if (animationBuilderContext == null) {
            ((ServerCommandSource)context.getSource()).sendError(Text.literal("No animation to build"));
         } else {
            List<NormalAnimation.Phase> thisSidePhases = animationBuilderContext.thisSideAnimation().get().phases;
            List<NormalAnimation.Phase> otherSidePhases = animationBuilderContext.otherSideAnimation().get().phases;
            if (!thisSidePhases.isEmpty() && !otherSidePhases.isEmpty()) {
               NormalAnimation.Phase lastThisSidePhase = thisSidePhases.get(thisSidePhases.size() - 1);
               DeltaUnilateralPortalState lastThisSidePhaseDelta = lastThisSidePhase.delta().purgeFPError();
               NormalAnimation.Phase lastOtherSidePhase = otherSidePhases.get(otherSidePhases.size() - 1);
               DeltaUnilateralPortalState lastOtherSidePhaseDelta = lastOtherSidePhase.delta().purgeFPError();
               if (loopCount > 1 && !lastThisSidePhaseDelta.isIdentity() || !lastOtherSidePhaseDelta.isIdentity()) {
                  thisSidePhases = ImmutableList.<NormalAnimation.Phase>builder()
                     .addAll(thisSidePhases)
                     .add(
                        new NormalAnimation.Phase.Builder()
                           .durationTicks(5L)
                           .timingFunction(TimingFunction.sine)
                           .delta(DeltaUnilateralPortalState.identity)
                           .build()
                     )
                     .build();
                  otherSidePhases = ImmutableList.<NormalAnimation.Phase>builder()
                     .addAll(otherSidePhases)
                     .add(
                        new NormalAnimation.Phase.Builder()
                           .durationTicks(5L)
                           .timingFunction(TimingFunction.sine)
                           .delta(DeltaUnilateralPortalState.identity)
                           .build()
                     )
                     .build();
               }

               animationBuilderContext.thisSideAnimation()
                  .set(
                     new NormalAnimation.Builder()
                        .phases(thisSidePhases)
                        .loopCount(loopCount)
                        .startingGameTime(animationBuilderContext.thisSideAnimation().get().startingGameTime)
                        .build()
                  );
               animationBuilderContext.otherSideAnimation()
                  .set(
                     new NormalAnimation.Builder()
                        .phases(otherSidePhases)
                        .loopCount(loopCount)
                        .startingGameTime(animationBuilderContext.otherSideAnimation().get().startingGameTime)
                        .build()
                  );
               animation.setBackToPausingState(portal);
               animation.setPaused(portal, false);
               PortalExtension.forClusterPortals(portal, Portal::reloadAndSyncToClientNextTick);
               ((ServerCommandSource)context.getSource()).sendFeedback(() -> getAnimationInfo(portal), false);
            } else {
               ((ServerCommandSource)context.getSource()).sendError(Text.literal("No phase"));
            }
         }
      }
   }

   private static void giveRotationAnimation(Portal portal, Vec3d rotationCenter, Vec3d axis, double angularVelocity) {
      portal.addThisSideAnimationDriver(
         new RotationAnimation.Builder()
            .setInitialOffset(portal.getOriginPos().subtract(rotationCenter))
            .setRotationAxis(axis)
            .setDegreesPerTick(angularVelocity)
            .setStartGameTime(portal.getAnimationEffectiveTime())
            .setEndGameTime(Long.MAX_VALUE)
            .build()
      );
   }

   private static Text getAnimationInfo(Portal portal) {
      return portal.getAnimationView().getInfo();
   }

   public record AnimationBuilderContext(Portal portal, Access<NormalAnimation> thisSideAnimation, Access<NormalAnimation> otherSideAnimation) {
   }
}
