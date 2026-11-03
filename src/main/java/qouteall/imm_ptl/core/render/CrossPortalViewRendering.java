package qouteall.imm_ptl.core.render;

import com.mojang.datafixers.util.Pair;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.ScaleUtils;
import qouteall.imm_ptl.core.commands.PortalCommand;
import qouteall.imm_ptl.core.ducks.IECamera;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.imm_ptl.core.teleportation.ClientTeleportationManager;

public class CrossPortalViewRendering {
   public static final MinecraftClient client = MinecraftClient.getInstance();

   public static boolean renderCrossPortalView() {
      if (!IPGlobal.enableCrossPortalView) {
         return false;
      } else {
         Entity cameraEntity = client.getCameraEntity();
         Camera camera1 = new Camera();
         float cameraY = ((IECamera)RenderStates.originalCamera).ip_getCameraY();
         float lastCameraY = ((IECamera)RenderStates.originalCamera).ip_getLastCameraY();
         ((IECamera)camera1).ip_setCameraY(cameraY, lastCameraY);
         camera1.update(client.world, cameraEntity, isThirdPerson(), isFrontView(), RenderStates.getPartialTick());
         Vec3d realCameraPos = camera1.getCameraPos();
         Vec3d isometricAdjustedOriginalCameraPos = TransformationManager.getIsometricAdjustedCameraPos(camera1);
         Vec3d physicalPlayerHeadPos = ClientTeleportationManager.getPlayerEyePos(RenderStates.getPartialTick());
         Pair<Portal, Vec3d> portalHit = PortalCommand.raytracePortals(client.world, physicalPlayerHeadPos, isometricAdjustedOriginalCameraPos, true)
            .orElse(null);
         if (portalHit == null) {
            return false;
         } else {
            Portal portal = (Portal)portalHit.getFirst();
            Vec3d hitPos = (Vec3d)portalHit.getSecond();
            if (!portal.canTeleportEntity(cameraEntity)) {
               return false;
            } else {
               Vec3d renderingCameraPos;
               if (isThirdPerson()) {
                  double distance = getThirdPersonMaxDistance();
                  Vec3d thirdPersonPos = realCameraPos.subtract(physicalPlayerHeadPos).normalize().multiply(distance).add(physicalPlayerHeadPos);
                  renderingCameraPos = getThirdPersonCameraPos(thirdPersonPos, portal, hitPos);
               } else {
                  renderingCameraPos = portal.transformPoint(realCameraPos);
               }

               ((IECamera)RenderStates.originalCamera).portal_setPos(renderingCameraPos);
               WorldRenderInfo worldRenderInfo = new WorldRenderInfo.Builder()
                  .setWorld(ClientWorldLoader.getWorld(portal.getDestDim()))
                  .setCameraPos(renderingCameraPos)
                  .setCameraTransformation(portal.getAdditionalCameraTransformation())
                  .setOverwriteCameraTransformation(false)
                  .setDescription(null)
                  .setRenderDistance(client.options.getClampedViewDistance())
                  .setDoRenderHand(false)
                  .setEnableViewBobbing(false)
                  .build();
               IPCGlobal.renderer.invokeWorldRendering(worldRenderInfo);
               return true;
            }
         }
      }
   }

   private static boolean isFrontView() {
      return client.options.getPerspective().isFrontView();
   }

   private static boolean isThirdPerson() {
      return !client.options.getPerspective().isFirstPerson();
   }

   private static Vec3d getThirdPersonCameraPos(Vec3d endPos, Portal portal, Vec3d startPos) {
      Vec3d rtStart = portal.transformPoint(startPos);
      Vec3d rtEnd = portal.transformPoint(endPos);

      assert client.getCameraEntity() != null;

      BlockHitResult blockHitResult = portal.getDestinationWorld()
         .raycast(new RaycastContext(rtStart, rtEnd, ShapeType.VISUAL, FluidHandling.NONE, client.getCameraEntity()));
      return blockHitResult.getType() == Type.BLOCK
         ? rtStart.add(rtEnd.subtract(rtStart).normalize().multiply(getThirdPersonMaxDistance()))
         : blockHitResult.getPos();
   }

   private static double getThirdPersonMaxDistance() {
      return 4.0 * ScaleUtils.computeThirdPersonScale(client.player);
   }
}
