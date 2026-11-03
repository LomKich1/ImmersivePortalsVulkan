package qouteall.imm_ptl.core.render;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Set;
import me.shedaniel.cloth.clothconfig.shadowed.org.yaml.snakeyaml.Yaml;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;

public class ShaderCodeTransformation {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static List<ShaderCodeTransformation.Config> configs;

   private static boolean matches(ShaderCodeTransformation.ShaderType me, com.mojang.blaze3d.shaders.ShaderType type) {
      if (type == com.mojang.blaze3d.shaders.ShaderType.FRAGMENT) {
         return me == ShaderCodeTransformation.ShaderType.fs;
      } else {
         return type == com.mojang.blaze3d.shaders.ShaderType.VERTEX ? me == ShaderCodeTransformation.ShaderType.vs : false;
      }
   }

   public static void init() {
      if (IPGlobal.enableClippingMechanism) {
         Yaml yaml = new Yaml();
         String yamlStr = McHelper.readTextResource(McHelper.newResourceLocation("immersive_portals:shaders/shader_transformation.yaml"));
         ShaderCodeTransformation.ConfigsObj configsObj = (ShaderCodeTransformation.ConfigsObj)yaml.loadAs(yamlStr, ShaderCodeTransformation.ConfigsObj.class);
         configs = configsObj.configs;
         LOGGER.info("Loaded Shader Code Transformation");
      } else {
         LOGGER.info("Shader Transformation Disabled");
      }
   }

   public static String shaderIdToName(Identifier id) {
      String namespace = id.getNamespace();
      if (!namespace.equals("minecraft") && !namespace.equals("immersive_portals")) {
         return id.toString();
      } else {
         String path = id.getPath();
         return path.startsWith("core/") ? path.substring("core/".length()) : path;
      }
   }

   public static String transform(com.mojang.blaze3d.shaders.ShaderType type, String shaderId, String inputCode) {
      if (configs == null) {
         LOGGER.info("Shader Transform Skipping {}", shaderId);
         return inputCode;
      } else {
         ShaderCodeTransformation.Config selected = getConfig(type, shaderId);
         if (selected == null) {
            return inputCode;
         } else {
            String result = inputCode;

            for (ShaderCodeTransformation.TransformationEntry entry : selected.transformations) {
               String replacement = String.join("\n", entry.replacement);
               result = result.replaceAll(entry.pattern, replacement);
            }

            if (selected.debugOutput) {
               LOGGER.info("Shader Transformed {}\n{}", shaderId, result);
            }

            return result;
         }
      }
   }

   @Nullable
   private static ShaderCodeTransformation.Config getConfig(com.mojang.blaze3d.shaders.ShaderType type, String shaderId) {
      return configs.stream().filter(config -> matches(config.type, type) && config.affectedShaders.contains(shaderId)).findFirst().orElse(null);
   }

   public static class Config {
      public String comment;
      public ShaderCodeTransformation.ShaderType type;
      public Set<String> affectedShaders;
      public List<ShaderCodeTransformation.TransformationEntry> transformations;
      public boolean debugOutput;
   }

   public static class ConfigsObj {
      public List<ShaderCodeTransformation.Config> configs;
   }

   public static enum ShaderType {
      vs,
      fs;
   }

   public static class TransformationEntry {
      public String comment;
      public String pattern;
      public String replacement;
   }
}
