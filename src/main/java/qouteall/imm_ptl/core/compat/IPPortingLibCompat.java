package qouteall.imm_ptl.core.compat;

import java.lang.reflect.Field;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.Framebuffer;
import qouteall.imm_ptl.core.ducks.IEFrameBuffer;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.platform.ClientPlatform;
import qouteall.q_misc_util.platform.Platform;

public class IPPortingLibCompat {
   public static boolean isPortingLibPresent = false;
   private static Field f_port_lib$stencilEnabled;

   public static void init() {
      if (Platform.get().isModLoaded("porting_lib")) {
         Helper.log("Porting Lib is present");
         isPortingLibPresent = true;
         if (!Platform.get().isDedicatedServer()) {
            f_port_lib$stencilEnabled = Helper.noError(() -> Framebuffer.class.getDeclaredField("port_lib$stencilEnabled"));
            f_port_lib$stencilEnabled.setAccessible(true);
         }
      }
   }

   @Environment(EnvType.CLIENT)
   public static boolean getIsStencilEnabled(Framebuffer renderTarget) {
      if (isPortingLibPresent) {
         return Helper.noError(() -> (Boolean)f_port_lib$stencilEnabled.get(renderTarget));
      } else {
         return ClientPlatform.get().hasNativeStencilSupport()
            ? ClientPlatform.get().isStencilEnabled(renderTarget)
            : ((IEFrameBuffer)(Object)renderTarget).ip_getIsStencilBufferEnabled();
      }
   }

   @Environment(EnvType.CLIENT)
   public static void setIsStencilEnabled(Framebuffer renderTarget, boolean cond) {
      if (isPortingLibPresent) {
         boolean oldValue = getIsStencilEnabled(renderTarget);
         if (oldValue != cond) {
            Helper.noError(() -> {
               f_port_lib$stencilEnabled.set(renderTarget, cond);
               return null;
            });
            renderTarget.resize(renderTarget.textureWidth, renderTarget.textureHeight);
         }
      } else if (ClientPlatform.get().hasNativeStencilSupport()) {
         ClientPlatform.get().setStencilEnabled(renderTarget, cond);
      } else {
         ((IEFrameBuffer)(Object)renderTarget).ip_setIsStencilBufferEnabledAndReload(cond);
      }
   }
}
