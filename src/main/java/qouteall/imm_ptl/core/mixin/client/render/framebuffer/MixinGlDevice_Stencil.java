package qouteall.imm_ptl.core.mixin.client.render.framebuffer;

import com.mojang.blaze3d.opengl.GlConst;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.gl.GlBackend;
import net.minecraft.client.texture.GlTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.render.StencilDepthTextures;

@Mixin({GlBackend.class})
public class MixinGlDevice_Stencil {
   @ModifyArgs(
      method = {"createTexture(Ljava/lang/String;ILcom/mojang/blaze3d/textures/TextureFormat;IIII)Lcom/mojang/blaze3d/textures/GpuTexture;", "createTexture(Ljava/lang/String;ILcom/mojang/blaze3d/textures/TextureFormat;IIIIZ)Lcom/mojang/blaze3d/textures/GpuTexture;"},
      at = @At(
         value = "INVOKE",
         target = "Lcom/mojang/blaze3d/opengl/GlStateManager;_texImage2D(IIIIIIIILjava/nio/ByteBuffer;)V"
      ),
      require = 1
   )
   private void modifyTexImage2D(Args args) {
      if (StencilDepthTextures.creatingStencilDepth && (Integer)args.get(2) == GlConst.toGlInternalId(TextureFormat.DEPTH32)) {
         args.set(2, IPCGlobal.useSeparatedStencilFormat ? '貭' : '裰');
         args.set(6, 34041);
         args.set(7, IPCGlobal.useSeparatedStencilFormat ? '趭' : '蓺');
      }
   }

   @Inject(
      method = {"createTexture(Ljava/lang/String;ILcom/mojang/blaze3d/textures/TextureFormat;IIII)Lcom/mojang/blaze3d/textures/GpuTexture;"},
      at = @At("RETURN")
   )
   private void onCreatedTexture(
      String label, int usage, TextureFormat format, int width, int height, int depthOrLayers, int mipLevels, CallbackInfoReturnable<GpuTexture> cir
   ) {
      if (StencilDepthTextures.creatingStencilDepth && format == TextureFormat.DEPTH32) {
         StencilDepthTextures.register(((GlTexture)cir.getReturnValue()).getGlId());
      }
   }
}
