package qouteall.imm_ptl.core;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import qouteall.q_misc_util.platform.EarlyLoaderDetection;

public class IPMixinPlugin implements IMixinConfigPlugin {
   public void onLoad(String mixinPackage) {
   }

   public String getRefMapperConfig() {
      return null;
   }

   public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
      if (!EarlyLoaderDetection.isModLoaded("porting_lib") || !mixinClassName.contains("MixinRenderTarget") && !mixinClassName.contains("MixinMainTarget")) {
         return !EarlyLoaderDetection.isForgeLike() || !mixinClassName.contains("MixinGlDevice_Stencil") && !mixinClassName.contains("MixinMainTarget")
            ? !EarlyLoaderDetection.isNeoForge() || !mixinClassName.contains("MixinDirectStateAccess_") && !mixinClassName.contains("MixinGlTexture")
            : false;
      } else {
         return false;
      }
   }

   public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
   }

   public List<String> getMixins() {
      return null;
   }

   public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
   }

   public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
   }
}
