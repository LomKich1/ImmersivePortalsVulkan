package qouteall.imm_ptl.core.ducks;

import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.WorldRenderer;

public interface IEMinecraftClient {
   void ip_setFrameBuffer(Framebuffer var1);

   Screen ip_getCurrentScreen();

   void ip_setWorldRenderer(WorldRenderer var1);

   void ip_setRenderBuffers(BufferBuilderStorage var1);

   Thread ip_getRunningThread();
}
