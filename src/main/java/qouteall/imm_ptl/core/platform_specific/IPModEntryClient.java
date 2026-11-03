package qouteall.imm_ptl.core.platform_specific;

import java.util.Arrays;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.EntityType;
import net.minecraft.text.Text;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.IPMcHelper;
import qouteall.imm_ptl.core.IPModMainClient;
import qouteall.imm_ptl.core.compat.IPModInfoChecking;

import qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface;
import qouteall.imm_ptl.core.compat.sodium_compatibility.SodiumInterface;
import qouteall.imm_ptl.core.compat.voxy_compatibility.VoxyInterface;
import qouteall.imm_ptl.core.portal.BreakableMirror;
import qouteall.imm_ptl.core.portal.EndPortalEntity;
import qouteall.imm_ptl.core.portal.LoadingIndicatorEntity;
import qouteall.imm_ptl.core.portal.Mirror;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.global_portals.GlobalTrackedPortal;
import qouteall.imm_ptl.core.portal.global_portals.VerticalConnectingPortal;
import qouteall.imm_ptl.core.portal.global_portals.WorldWrappingPortal;
import qouteall.imm_ptl.core.portal.nether_portal.GeneralBreakablePortal;
import qouteall.imm_ptl.core.portal.nether_portal.NetherPortalEntity;
import qouteall.imm_ptl.core.render.LoadingIndicatorRenderer;
import qouteall.imm_ptl.core.render.PortalEntityRenderer;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.MyTaskList;

public class IPModEntryClient implements ClientModInitializer {
   public static void initPortalRenderers() {
      Arrays.stream(
            new EntityType[]{
               Portal.ENTITY_TYPE,
               NetherPortalEntity.ENTITY_TYPE,
               EndPortalEntity.ENTITY_TYPE,
               Mirror.ENTITY_TYPE,
               BreakableMirror.ENTITY_TYPE,
               GlobalTrackedPortal.ENTITY_TYPE,
               WorldWrappingPortal.ENTITY_TYPE,
               VerticalConnectingPortal.ENTITY_TYPE,
               GeneralBreakablePortal.ENTITY_TYPE
            }
         )
         .forEach(entityType -> EntityRendererRegistry.register(entityType, PortalEntityRenderer::new));
      EntityRendererRegistry.register(LoadingIndicatorEntity.entityType, LoadingIndicatorRenderer::new);
   }

   public void onInitializeClient() {
      IPModMainClient.init();
      initPortalRenderers();
      boolean isSodiumPresent = FabricLoader.getInstance().isModLoaded("sodium");
      if (isSodiumPresent) {
         Helper.log("Sodium is present");
         SodiumInterface.invoker = new SodiumInterface.OnSodiumPresent();
      } else {
         Helper.log("Sodium is not present");
      }

      if (FabricLoader.getInstance().isModLoaded("voxy")) {
         Helper.log("Voxy is present");
         VoxyInterface.invoker = new VoxyInterface.OnVoxyPresent();
      } else {
         Helper.log("Voxy is not present");
      }

      if (FabricLoader.getInstance().isModLoaded("iris")) {
         Helper.log("Iris is present");
         IrisInterface.invoker = new IrisInterface.OnIrisPresent();
         IPGlobal.CLIENT_TASK_LIST.addTask(MyTaskList.oneShotTask(() -> {
            if (IPConfig.getConfig().shouldDisplayWarning("iris")) {
               CHelper.printChat(Text.translatable("imm_ptl.iris_warning").append(IPMcHelper.getDisableWarningText("iris")));
            }
         }));
      } else {
         Helper.log("Iris is not present");
      }

      IPModInfoChecking.initClient();
   }
}
