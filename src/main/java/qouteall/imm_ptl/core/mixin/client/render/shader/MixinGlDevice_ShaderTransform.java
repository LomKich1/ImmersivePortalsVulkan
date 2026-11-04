package qouteall.imm_ptl.core.mixin.client.render.shader;

import com.mojang.blaze3d.shaders.ShaderType;
import net.minecraft.client.gl.GlBackend;
import net.minecraft.client.gl.ShaderSourceGetter;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import qouteall.imm_ptl.core.render.ShaderCodeTransformation;

@Mixin({GlBackend.class})
public class MixinGlDevice_ShaderTransform {
   @Redirect(
      method = {"compileShader(Lnet/minecraft/client/gl/GlBackend$ShaderKey;Lnet/minecraft/client/gl/ShaderSourceGetter;)Lnet/minecraft/client/gl/CompiledShader;"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gl/ShaderSourceGetter;get(Lnet/minecraft/util/Identifier;Lcom/mojang/blaze3d/shaders/ShaderType;)Ljava/lang/String;"
      )
   )
   private String redirectGetShaderSource(ShaderSourceGetter shaderSource, Identifier id, ShaderType type) {
      String shaderCode = shaderSource.get(id, type);
      return shaderCode == null ? null : ShaderCodeTransformation.transform(type, ShaderCodeTransformation.shaderIdToName(id), shaderCode);
   }
}
