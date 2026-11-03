package qouteall.imm_ptl.core;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import qouteall.imm_ptl.core.render.renderer.PortalRenderer;
import qouteall.imm_ptl.core.render.renderer.RendererDebug;
import qouteall.imm_ptl.core.render.renderer.RendererDummy;
import qouteall.imm_ptl.core.render.renderer.RendererUsingFrameBuffer;
import qouteall.imm_ptl.core.render.renderer.RendererUsingStencil;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.platform.event.Event;

@Environment(EnvType.CLIENT)
public class IPCGlobal {
   public static PortalRenderer renderer;
   public static RendererUsingStencil rendererUsingStencil;
   public static RendererUsingFrameBuffer rendererUsingFrameBuffer;
   public static RendererDummy rendererDummy = new RendererDummy();
   public static RendererDebug rendererDebug = new RendererDebug();
   public static int maxIdleChunkRendererNum = 500;
   public static boolean doUseAdvancedFrustumCulling = true;
   public static boolean useHackedChunkRenderDispatcher = true;
   public static boolean isClientRemoteTickingEnabled = true;
   public static boolean useFrontClipping = true;
   public static boolean doDisableAlphaTestWhenRenderingFrameBuffer = true;
   public static boolean lateClientLightUpdate = true;
   public static boolean earlyRemoteUpload = true;
   public static boolean useSuperAdvancedFrustumCulling = true;
   public static boolean earlyFrustumCullingPortal = true;
   public static boolean useSeparatedStencilFormat = false;
   public static boolean experimentalIrisPortalRenderer = false;
   public static boolean debugEnableStencilWithIris = false;
   public static final Event<Runnable> CLIENT_CLEANUP_EVENT = Helper.createRunnableEvent();
   public static final Event<Runnable> CLIENT_EXIT_EVENT = Helper.createRunnableEvent();
}
