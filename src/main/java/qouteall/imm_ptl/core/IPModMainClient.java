package qouteall.imm_ptl.core;

import net.minecraft.util.Formatting;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import qouteall.imm_ptl.core.collision.CollisionHelper;
import qouteall.imm_ptl.core.commands.ClientDebugCommand;
import qouteall.imm_ptl.core.compat.IPFlywheelCompat;

import qouteall.imm_ptl.core.compat.sodium_compatibility.SodiumInterface;
import qouteall.imm_ptl.core.miscellaneous.DubiousThings;
import qouteall.imm_ptl.core.miscellaneous.GcMonitor;
import qouteall.imm_ptl.core.network.ImmPtlNetworkConfig;
import qouteall.imm_ptl.core.network.ImmPtlNetworking;
import qouteall.imm_ptl.core.platform_specific.IPConfig;
import qouteall.imm_ptl.core.platform_specific.O_O;
import qouteall.imm_ptl.core.portal.PortalRenderInfo;
import qouteall.imm_ptl.core.portal.animation.ClientPortalAnimationManagement;
import qouteall.imm_ptl.core.portal.animation.StableClientTimer;
import qouteall.imm_ptl.core.render.CrossPortalEntityRenderer;
import qouteall.imm_ptl.core.render.ForceMainThreadRebuild;
import qouteall.imm_ptl.core.render.GuiPortalRendering;
import qouteall.imm_ptl.core.render.ImmPtlViewArea;
import qouteall.imm_ptl.core.render.MyRenderHelper;
import qouteall.imm_ptl.core.render.ShaderCodeTransformation;
import qouteall.imm_ptl.core.render.VisibleSectionDiscovery;
import qouteall.imm_ptl.core.render.context_management.CloudContext;
import qouteall.imm_ptl.core.render.optimization.GLResourceCache;
import qouteall.imm_ptl.core.render.optimization.SharedBlockMeshBuffers;
import qouteall.imm_ptl.core.render.renderer.RendererUsingFrameBuffer;
import qouteall.imm_ptl.core.render.renderer.RendererUsingStencil;
import qouteall.imm_ptl.core.teleportation.ClientTeleportationManager;
import qouteall.q_misc_util.dimension.DimensionIntId;
import qouteall.q_misc_util.my_util.MyTaskList;
import qouteall.q_misc_util.platform.ClientPlatform;

public class IPModMainClient {
   private static void showNvidiaVideoCardWarning() {
      IPGlobal.CLIENT_TASK_LIST
         .addTask(
            MyTaskList.withDelayCondition(
               () -> MinecraftClient.getInstance().world == null,
               MyTaskList.oneShotTask(
                  () -> {
                     if (IPMcHelper.isNvidiaVideocard() && !SodiumInterface.invoker.isSodiumPresent()) {
                        CHelper.printChat(
                           Text.translatable("imm_ptl.nvidia_warning")
                              .formatted(Formatting.field_1061)
                              .append(McHelper.getLinkText("https://github.com/CaffeineMC/sodium-fabric/issues/1486"))
                        );
                     }
                  }
               )
            )
         );
   }

   private static void showQuiltWarning() {
      IPGlobal.CLIENT_TASK_LIST.addTask(MyTaskList.withDelayCondition(() -> MinecraftClient.getInstance().world == null, MyTaskList.oneShotTask(() -> {
         if (O_O.isQuilt() && IPConfig.getConfig().shouldDisplayWarning("quilt")) {
            CHelper.printChat(Text.translatable("imm_ptl.quilt_warning").append(IPMcHelper.getDisableWarningText("quilt")));
         }
      })));
   }

   public static void init() {
      ClientWorldLoader.init();
      ClientDimensionPrewarm.init();
      ClientTeleportationManager.init();
      MinecraftClient.getInstance().execute(() -> {
         ShaderCodeTransformation.init();
         MyRenderHelper.init();
         IPCGlobal.rendererUsingStencil = new RendererUsingStencil();
         IPCGlobal.rendererUsingFrameBuffer = new RendererUsingFrameBuffer();
         IPCGlobal.renderer = IPCGlobal.rendererUsingStencil;
      });
      DubiousThings.init();
      CrossPortalEntityRenderer.init();
      GLResourceCache.init();
      CollisionHelper.initClient();
      PortalRenderInfo.init();
      CloudContext.init();
      SharedBlockMeshBuffers.init();
      GcMonitor.initClient();
      ClientPlatform.get().onRegisterClientCommands(ClientDebugCommand::register);
      showNvidiaVideoCardWarning();
      showQuiltWarning();
      StableClientTimer.init();
      ClientPortalAnimationManagement.init();
      VisibleSectionDiscovery.init();
      ImmPtlViewArea.init();
      IPFlywheelCompat.init();
      GuiPortalRendering._init();
      ImmPtlNetworking.initClient();
      ImmPtlNetworkConfig.initClient();
      ForceMainThreadRebuild.init();
      IPCGlobal.CLIENT_CLEANUP_EVENT.register(() -> IPGlobal.CLIENT_TASK_LIST.forceClearTasks());
      DimensionIntId.initClient();
   }
}
