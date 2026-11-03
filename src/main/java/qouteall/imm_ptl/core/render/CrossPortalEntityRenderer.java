package qouteall.imm_ptl.core.render;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider.class_4598;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.collision.PortalCollisionEntry;
import qouteall.imm_ptl.core.collision.PortalCollisionHandler;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface;
import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.ducks.IEWorldRenderer;
import qouteall.imm_ptl.core.miscellaneous.IPVanillaCopy;
import qouteall.imm_ptl.core.portal.Mirror;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalManipulation;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.imm_ptl.core.render.renderer.PortalRenderer;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.Plane;

@Environment(EnvType.CLIENT)
public class CrossPortalEntityRenderer {
   private static final MinecraftClient client = MinecraftClient.getInstance();
   private static final WeakHashMap<Entity, Object> collidedEntities = new WeakHashMap<>();
   public static boolean isRenderingEntityNormally = false;
   public static boolean isRenderingEntityProjection = false;
   private static final List<Entity> deferredEntities = new ArrayList<>();
   @Nullable
   private static Entity lastDeferredEntity = null;

   public static void init() {
      IPGlobal.POST_CLIENT_TICK_EVENT.register(CrossPortalEntityRenderer::onClientTick);
      IPCGlobal.CLIENT_CLEANUP_EVENT.register(CrossPortalEntityRenderer::cleanUp);
      ClientWorldLoader.CLIENT_DIMENSION_DYNAMIC_REMOVE_EVENT.register(dim -> cleanUp());
   }

   private static void cleanUp() {
      collidedEntities.clear();
      deferredEntities.clear();
      lastDeferredEntity = null;
   }

   private static void onClientTick() {
      collidedEntities.entrySet().removeIf(entry -> {
         Entity entity = entry.getKey();
         return entity.isRemoved() || !((IEEntity)entity).ip_isCollidingWithPortal();
      });
   }

   public static void onEntityTickClient(Entity entity) {
      if (!(entity instanceof Portal)) {
         if (((IEEntity)entity).ip_isCollidingWithPortal()) {
            collidedEntities.put(entity, null);
         }
      }
   }

   public static void onBeginRenderingEntitiesAndBlockEntities(Matrix4f modelView) {
      isRenderingEntityNormally = true;
      if (PortalRendering.isRendering()) {
         FrontClipping.setupInnerClipping(PortalRendering.getActiveClippingPlane(), modelView, 0.0);
      }
   }

   private static boolean isCrossPortalRenderingEnabled() {
      return IrisInterface.invoker.isIrisPresent() ? false : IPGlobal.correctCrossPortalEntityRendering;
   }

   public static void onEndRenderingEntitiesAndBlockEntities(MatrixStack matrixStack) {
      renderDeferredEntities(matrixStack);
      isRenderingEntityNormally = false;
      FrontClipping.disableClipping();
      if (isCrossPortalRenderingEnabled()) {
         renderEntityProjections(matrixStack);
      }
   }

   public static void onBeginExtractingEntities() {
      deferredEntities.clear();
      lastDeferredEntity = null;
   }

   public static boolean shouldDeferEntityRendering(Entity entity) {
      if (!isCrossPortalRenderingEnabled()) {
         return false;
      } else if (PortalRendering.isRendering()) {
         return false;
      } else if (!collidedEntities.containsKey(entity)) {
         return false;
      } else {
         PortalCollisionHandler collisionHandler = ((IEEntity)entity).ip_getPortalCollisionHandler();
         return collisionHandler != null && !collisionHandler.portalCollisions.isEmpty();
      }
   }

   public static void deferEntity(Entity entity) {
      deferredEntities.add(entity);
      lastDeferredEntity = entity;
   }

   public static boolean isLastDeferredEntity(Entity entity) {
      return lastDeferredEntity == entity;
   }

   private static void renderDeferredEntities(MatrixStack matrixStack) {
      if (!deferredEntities.isEmpty()) {
         try {
            for (Entity entity : deferredEntities) {
               beforeRenderingEntity(entity, matrixStack);
               renderEntityNormallyNow(entity, matrixStack);
               afterRenderingEntity(entity);
            }
         } finally {
            deferredEntities.clear();
            lastDeferredEntity = null;
         }
      }
   }

   public static void beforeRenderingEntity(Entity entity, MatrixStack matrixStack) {
      if (isCrossPortalRenderingEnabled()) {
         if (!PortalRendering.isRendering() && collidedEntities.containsKey(entity)) {
            PortalCollisionHandler collisionHandler = ((IEEntity)entity).ip_getPortalCollisionHandler();
            if (collisionHandler != null) {
               for (PortalCollisionEntry e : collisionHandler.portalCollisions) {
                  Portal collidingPortal = e.portal;
                  client.getBufferBuilders().getEntityVertexConsumers().draw();
                  FrontClipping.setupOuterClipping(matrixStack, collidingPortal);
               }
            }
         }
      }
   }

   public static void afterRenderingEntity(Entity entity) {
      if (isCrossPortalRenderingEnabled()) {
         if (!PortalRendering.isRendering() && collidedEntities.containsKey(entity)) {
            client.getBufferBuilders().getEntityVertexConsumers().draw();
            FrontClipping.disableClipping();
         }
      }
   }

   @IPVanillaCopy
   private static void renderEntityNormallyNow(Entity entity, MatrixStack matrixStack) {
      if (entity.age == 0) {
         entity.lastRenderX = entity.getX();
         entity.lastRenderY = entity.getY();
         entity.lastRenderZ = entity.getZ();
      }

      float partialTick = client.getRenderTickCounter().getTickProgress(!client.world.getTickManager().shouldSkipTick(entity));
      Vec3d cameraPos = client.gameRenderer.getCamera().getCameraPos();
      class_4598 consumers = client.getBufferBuilders().getEntityVertexConsumers();
      ((IEWorldRenderer)client.worldRenderer)
         .ip_renderSingleEntity(entity, cameraPos.x, cameraPos.y, cameraPos.z, partialTick, matrixStack, consumers);
   }

   private static void renderEntityProjections(MatrixStack matrixStack) {
      if (isCrossPortalRenderingEnabled()) {
         RegistryKey<World> clientDim = client.world.getRegistryKey();

         for (Entity entity : collidedEntities.keySet()) {
            PortalCollisionHandler collisionHandler = ((IEEntity)entity).ip_getPortalCollisionHandler();
            if (collisionHandler != null) {
               for (PortalCollisionEntry e : collisionHandler.portalCollisions) {
                  Portal collidingPortal = e.portal;
                  if (!(collidingPortal instanceof Mirror)) {
                     RegistryKey<World> projectionDimension = collidingPortal.getDestDim();
                     if (clientDim == projectionDimension) {
                        renderProjectedEntity(entity, collidingPortal, matrixStack);
                     }
                  }
               }
            }
         }
      }
   }

   public static boolean hasIntersection(Vec3d outerPlanePos, Vec3d outerPlaneNormal, Vec3d entityPos, Vec3d collidingPortalNormal) {
      return entityPos.subtract(outerPlanePos).dotProduct(outerPlaneNormal) > 0.01
         && outerPlanePos.subtract(entityPos).dotProduct(collidingPortalNormal) > 0.01;
   }

   private static void renderProjectedEntity(Entity entity, Portal collidingPortal, MatrixStack matrixStack) {
      if (PortalRendering.isRendering()) {
         Portal renderingPortal = PortalRendering.getRenderingPortal();
         if (renderingPortal instanceof Portal
            && !Portal.isFlippedPortal(renderingPortal, collidingPortal)
            && !Portal.isReversePortal(renderingPortal, collidingPortal)) {
            Vec3d cameraPos = client.gameRenderer.getCamera().getCameraPos();
            Plane innerClipping = collidingPortal.getInnerClipping();
            boolean isHidden = innerClipping != null && !innerClipping.isPointOnPositiveSide(cameraPos);
            if (renderingPortal == collidingPortal || !isHidden) {
               renderEntity(entity, collidingPortal, matrixStack);
            }
         }
      } else {
         FrontClipping.disableClipping();
         client.getBufferBuilders().getEntityVertexConsumers().draw();
         FrontClipping.setupInnerClipping(collidingPortal.getInnerClipping(), matrixStack.peek().getPositionMatrix(), 0.0);
         renderEntity(entity, collidingPortal, matrixStack);
         FrontClipping.disableClipping();
      }
   }

   private static void renderEntity(Entity entity, Portal transformingPortal, MatrixStack matrixStack) {
      Vec3d cameraPos = client.gameRenderer.getCamera().getCameraPos();
      ClientWorld newWorld = ClientWorldLoader.getWorld(transformingPortal.getDestDim());
      Vec3d entityPos = entity.getEntityPos();
      Vec3d entityEyePos = McHelper.getEyePos(entity);
      Vec3d entityLastTickPos = McHelper.lastTickPosOf(entity);
      Vec3d entityLastTickEyePos = McHelper.getLastTickEyePos(entity);
      World oldWorld = entity.getEntityWorld();
      Vec3d newEyePos = transformingPortal.transformPoint(entityEyePos);
      if (PortalRendering.isRendering()) {
         Portal renderingPortal = PortalRendering.getRenderingPortal();
         Vec3d transformedEntityPos = newEyePos.subtract(McHelper.getEyeOffset(entity));
         Box transformedBoundingBox = McHelper.getBoundingBoxWithMovedPosition(entity, transformedEntityPos);
         boolean intersects = PortalManipulation.isOtherSideBoxInside(transformedBoundingBox, renderingPortal);
         if (!intersects) {
            return;
         }
      }

      if (entity instanceof ClientPlayerEntity) {
         if (!IPGlobal.renderYourselfInPortal) {
            return;
         }

         if (!transformingPortal.getDoRenderPlayer()) {
            return;
         }

         if (client.options.getPerspective().isFirstPerson()) {
            double dis = newEyePos.distanceTo(cameraPos);
            double valve = 0.5 + entityLastTickPos.distanceTo(entityPos);
            if (transformingPortal.getScaling() > 1.0) {
               valve *= transformingPortal.getScaling();
            }

            if (dis < valve) {
               return;
            }

            Box transformedBoundingBox = Helper.transformBox(RenderStates.originalPlayerBoundingBox, transformingPortal::transformPoint);
            if (transformedBoundingBox.contains(CHelper.getCurrentCameraPos())) {
               return;
            }
         }
      }

      isRenderingEntityProjection = true;
      matrixStack.push();

      try {
         Vec3d entityInstantPos = entityLastTickPos.lerp(entityPos, RenderStates.getPartialTick());
         Vec3d newEntityInstantPos = transformingPortal.transformPoint(entityInstantPos);
         Vec3d newCameraPos = entityInstantPos.subtract(newEntityInstantPos).add(cameraPos);
         setupEntityProjectionRenderingTransformation(transformingPortal, matrixStack, entityPos, entityLastTickPos, newCameraPos);
         class_4598 consumers = client.getBufferBuilders().getEntityVertexConsumers();
         ((IEWorldRenderer)client.worldRenderer)
            .ip_renderSingleEntity(
               entity, newCameraPos.x, newCameraPos.y, newCameraPos.z, RenderStates.getPartialTick(), matrixStack, consumers
            );
         consumers.draw();
      } finally {
         matrixStack.pop();
         isRenderingEntityProjection = false;
      }
   }

   private static void setupEntityProjectionRenderingTransformation(
      Portal portal, MatrixStack matrixStack, Vec3d entityPos, Vec3d entityLastTickPos, Vec3d cameraPos
   ) {
      if (portal.getScaling() != 1.0 || portal.getRotation() != null) {
         Vec3d anchor = entityLastTickPos.lerp(entityPos, RenderStates.getPartialTick()).subtract(cameraPos);
         matrixStack.translate(anchor.x, anchor.y, anchor.z);
         float scaling = (float)portal.getScaling();
         matrixStack.scale(scaling, scaling, scaling);
         if (portal.getRotation() != null) {
            matrixStack.multiply(portal.getRotation().toMcQuaternion());
         }

         matrixStack.translate(-anchor.x, -anchor.y, -anchor.z);
      }
   }

   public static boolean shouldRenderPlayerDefault() {
      if (!IPGlobal.renderYourselfInPortal) {
         return false;
      } else if (!WorldRenderInfo.isRendering()) {
         return false;
      } else {
         ClientPlayerEntity player = client.player;

         assert player != null;

         if (PortalRendering.isRendering()) {
            Portal renderingPortal = PortalRendering.getRenderingPortal();
            if (renderingPortal instanceof Mirror) {
               float width = player.getWidth();
               if (renderingPortal.getDistanceToNearestPointInPortal(player.getEyePos()) < width * 0.8) {
                  return false;
               }
            }
         }

         return client.world == player.getEntityWorld();
      }
   }

   public static boolean shouldRenderEntityNow(Entity entity) {
      Validate.notNull(entity);
      if (IrisInterface.invoker.isRenderingShadowMap()) {
         return true;
      } else if (PortalRendering.isRendering()) {
         Portal renderingPortal = PortalRendering.getRenderingPortal();
         Portal collidingPortal = ((IEEntity)entity).ip_getCollidingPortal();
         if (entity instanceof PlayerEntity && !renderingPortal.getDoRenderPlayer()) {
            return false;
         } else {
            if (collidingPortal != null
               && !(entity instanceof ClientPlayerEntity)
               && renderingPortal instanceof Portal
               && !Portal.isReversePortal(collidingPortal, renderingPortal)) {
               Vec3d cameraPos = PortalRenderer.client.gameRenderer.getCamera().getCameraPos();
               boolean isHidden = cameraPos.subtract(collidingPortal.getOriginPos()).dotProduct(collidingPortal.getNormal()) < 0.0;
               if (isHidden) {
                  return false;
               }
            }

            return renderingPortal.isOnDestinationSide(getRenderingCameraPos(entity), -0.01);
         }
      } else {
         return true;
      }
   }

   public static boolean shouldRenderPlayerNormally(Entity entity) {
      if (!client.options.getPerspective().isFirstPerson()) {
         return true;
      } else if (RenderStates.originalPlayerBoundingBox.contains(CHelper.getCurrentCameraPos())) {
         return false;
      } else {
         double distanceToCamera = getRenderingCameraPos(entity).distanceTo(client.gameRenderer.getCamera().getCameraPos());
         return distanceToCamera > 1.0 || PortalRendering.isRenderingOddNumberOfMirrors();
      }
   }

   public static Vec3d getRenderingCameraPos(Entity entity) {
      return entity instanceof ClientPlayerEntity
         ? RenderStates.originalPlayerPos.add(McHelper.getEyeOffset(entity))
         : entity.getCameraPosVec(RenderStates.getPartialTick());
   }
}
