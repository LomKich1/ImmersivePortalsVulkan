package qouteall.imm_ptl.core.render;

import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.platform.ClientPlatform;

public class SecondaryFrameBuffer {
   public SimpleFramebuffer fb;

   public void prepare() {
      Framebuffer mainFrameBuffer = MinecraftClient.getInstance().getFramebuffer();
      int width = mainFrameBuffer.textureWidth;
      int height = mainFrameBuffer.textureHeight;
      this.prepare(width, height);
   }

   public void prepare(int width, int height) {
      if (this.fb == null) {
         this.fb = ClientPlatform.get().createSecondaryTextureTarget("ImmPtl secondary", width, height, true);
         Helper.log("Secondary Framebuffer init");
      }

      if (width != this.fb.textureWidth || height != this.fb.textureHeight) {
         this.fb.resize(width, height);
         Helper.log("Secondary Framebuffer resized");
      }
   }
}
