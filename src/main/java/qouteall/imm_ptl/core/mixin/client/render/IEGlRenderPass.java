package qouteall.imm_ptl.core.mixin.client.render;

import net.minecraft.client.gl.RenderPassImpl;
import net.minecraft.client.gl.CompiledShaderPipeline;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({RenderPassImpl.class})
public interface IEGlRenderPass {
   @Accessor("pipeline")
   @Nullable
   CompiledShaderPipeline ip_getPipeline();
}
