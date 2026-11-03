package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Builder;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.vertex.VertexFormat.class_5596;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class IPRenderPipelines {
   public static final String NAMESPACE = "immersive_portals";
   private static final Snippet MATRICES_SNIPPET = RenderPipeline.builder(new Snippet[0])
      .withUniform("DynamicTransforms", UniformType.field_60031)
      .withUniform("Projection", UniformType.field_60031)
      .buildSnippet();
   private static final BlendFunction PORTAL_AREA_BLEND = new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA);
   private static final Map<Integer, RenderPipeline> PORTAL_AREA_VARIANTS = new HashMap<>();
   public static final RenderPipeline SCREEN_TRIANGLE_DEPTH_CLEAR = screenTriangle("screen_triangle_depth_clear")
      .withDepthTestFunction(DepthTestFunction.GREATER_DEPTH_TEST)
      .withDepthWrite(true)
      .withColorWrite(false, false)
      .build();
   public static final RenderPipeline SCREEN_TRIANGLE_STENCIL_ONLY = screenTriangle("screen_triangle_stencil_only")
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withDepthWrite(false)
      .withColorWrite(false, false)
      .build();
   public static final RenderPipeline SCREEN_TRIANGLE_COLOR = screenTriangle("screen_triangle_color")
      .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
      .withDepthWrite(false)
      .withColorWrite(true, true)
      .build();
   public static final RenderPipeline SCREEN_TRIANGLE_ALPHA_ONLY = screenTriangle("screen_triangle_alpha_only")
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withDepthWrite(false)
      .withColorWrite(false, true)
      .build();
   public static final BlendFunction FRAMEBUFFER_DRAW_BLEND = new BlendFunction(
      SourceFactor.ONE, DestFactor.ONE_MINUS_SRC_ALPHA, SourceFactor.ZERO, DestFactor.ONE
   );
   public static final RenderPipeline BLIT_BLEND = blit("blit_blend", false).withBlend(FRAMEBUFFER_DRAW_BLEND).withColorWrite(true, true).build();
   public static final RenderPipeline BLIT_BLEND_KEEP_ALPHA = blit("blit_blend_keep_alpha", false)
      .withBlend(FRAMEBUFFER_DRAW_BLEND)
      .withColorWrite(true, false)
      .build();
   public static final RenderPipeline BLIT_NO_BLEND = blit("blit_no_blend", true).withoutBlend().withColorWrite(true, true).build();
   public static final RenderPipeline BLIT_NO_BLEND_KEEP_ALPHA = blit("blit_no_blend_keep_alpha", true).withoutBlend().withColorWrite(true, false).build();
   public static final RenderPipeline DRAW_FB_IN_AREA = RenderPipeline.builder(new Snippet[]{MATRICES_SNIPPET})
      .withLocation(id("pipeline/portal_draw_fb_in_area"))
      .withVertexShader(id("core/portal_draw_fb_in_area"))
      .withFragmentShader(id("core/portal_draw_fb_in_area"))
      .withSampler("DiffuseSampler")
      .withUniform("IPScreenSize", UniformType.field_60031)
      .withBlend(PORTAL_AREA_BLEND)
      .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
      .withDepthWrite(true)
      .withVertexFormat(VertexFormats.POSITION_COLOR, class_5596.field_27379)
      .build();
   public static final RenderPipeline DEBUG_LINE_STRIP = RenderPipeline.builder(new Snippet[]{MATRICES_SNIPPET})
      .withLocation(id("pipeline/debug_line_strip"))
      .withVertexShader("core/position_color")
      .withFragmentShader("core/position_color")
      .withCull(false)
      .withVertexFormat(VertexFormats.POSITION_COLOR, class_5596.field_29345)
      .build();

   public static Identifier id(String path) {
      return Identifier.of("immersive_portals", path);
   }

   public static RenderPipeline portalArea(boolean cull, boolean writeColor, boolean writeDepth, DepthTestFunction depthTest) {
      int key = (cull ? 1 : 0) | (writeColor ? 2 : 0) | (writeDepth ? 4 : 0) | depthTest.ordinal() << 3;
      return PORTAL_AREA_VARIANTS.computeIfAbsent(
         key,
         k -> RenderPipeline.builder(new Snippet[]{MATRICES_SNIPPET})
            .withLocation(id("pipeline/portal_area_" + k))
            .withVertexShader(id("core/portal_area"))
            .withFragmentShader(id("core/portal_area"))
            .withBlend(PORTAL_AREA_BLEND)
            .withCull(cull)
            .withColorWrite(writeColor, writeColor)
            .withDepthWrite(writeDepth)
            .withDepthTestFunction(depthTest)
            .withVertexFormat(VertexFormats.POSITION_COLOR, class_5596.field_27379)
            .build()
      );
   }

   private static Builder screenTriangle(String name) {
      return RenderPipeline.builder(new Snippet[]{MATRICES_SNIPPET})
         .withLocation(id("pipeline/" + name))
         .withVertexShader("core/position_color")
         .withFragmentShader("core/position_color")
         .withCull(false)
         .withoutBlend()
         .withVertexFormat(VertexFormats.POSITION_COLOR, class_5596.field_27379);
   }

   private static Builder blit(String name, boolean forceAlphaToOne) {
      return RenderPipeline.builder(new Snippet[0])
         .withLocation(id("pipeline/" + name))
         .withVertexShader("core/screenquad")
         .withFragmentShader(forceAlphaToOne ? id("core/blit_screen_noblend") : Identifier.ofVanilla("core/blit_screen"))
         .withSampler("InSampler")
         .withDepthWrite(false)
         .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
         .withCull(false)
         .withVertexFormat(VertexFormats.EMPTY, class_5596.field_27379);
   }

   public static RenderPipeline blitScreen(boolean doUseAlphaBlend, boolean doEnableModifyAlpha) {
      if (doUseAlphaBlend) {
         return doEnableModifyAlpha ? BLIT_BLEND : BLIT_BLEND_KEEP_ALPHA;
      } else {
         return doEnableModifyAlpha ? BLIT_NO_BLEND : BLIT_NO_BLEND_KEEP_ALPHA;
      }
   }
}
