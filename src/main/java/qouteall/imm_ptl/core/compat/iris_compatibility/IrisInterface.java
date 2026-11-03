package qouteall.imm_ptl.core.compat.iris_compatibility;

import java.lang.reflect.Field;







import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import org.jetbrains.annotations.Nullable;
import qouteall.q_misc_util.Helper;

public class IrisInterface {
   public static IrisInterface.Invoker invoker = new IrisInterface.Invoker();

   public static class Invoker {
      public boolean isIrisPresent() {
         return false;
      }

      public boolean isShaders() {
         return false;
      }

      public boolean isRenderingShadowMap() {
         return false;
      }

      public Object getPipeline(WorldRenderer worldRenderer) {
         return null;
      }

      public void setPipeline(WorldRenderer worldRenderer, Object pipeline) {
      }

      public void reloadPipelines() {
      }

      public Object getCurrentPipeline() {
         return null;
      }

      @Nullable
      public String getShaderpackName() {
         return null;
      }

      public void preparePipelineForCurrentLevel() {
      }

      public boolean initializeCurrentPipelineForRendering() {
         return false;
      }
   }

}
