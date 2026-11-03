package qouteall.imm_ptl.core.mixin.client.render;

import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({DrawContext.class})
public interface IEGuiGraphics {
   @Accessor("state")
   GuiRenderState ip_getGuiRenderState();
}
