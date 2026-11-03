package qouteall.q_misc_util.platform;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.MixinEnvironment.Side;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public class EnvironmentStripMixinPlugin implements IMixinConfigPlugin {
   private static final Logger LOGGER = LoggerFactory.getLogger("ImmersivePortals/EnvironmentStrip");
   private static final String ENVIRONMENT = "Lnet/fabricmc/api/Environment;";
   private static final String ENVIRONMENT_INTERFACE = "Lnet/fabricmc/api/EnvironmentInterface;";
   private static final String ENVIRONMENT_INTERFACES = "Lnet/fabricmc/api/EnvironmentInterfaces;";
   private String envType;

   public void onLoad(String mixinPackage) {
      Side side = MixinEnvironment.getCurrentEnvironment().getSide();
      if (side == Side.CLIENT) {
         this.envType = "CLIENT";
      } else if (side == Side.SERVER) {
         this.envType = "SERVER";
      } else {
         this.envType = detectSideFromLoader();
      }

      if (this.envType == null) {
         LOGGER.warn("Cannot tell the physical side; client-only members are not stripped");
      } else {
         LOGGER.info("Stripping the members of the other side on a {}", this.envType);
      }
   }

   private static String detectSideFromLoader() {
      for (String className : new String[]{"net.neoforged.fml.loading.FMLEnvironment", "net.minecraftforge.fml.loading.FMLEnvironment"}) {
         try {
            Class<?> environment = Class.forName(className);

            Object dist;
            try {
               Method getDist = environment.getMethod("getDist");
               dist = getDist.invoke(null);
            } catch (NoSuchMethodException var8) {
               Field distField = environment.getField("dist");
               dist = distField.get(null);
            }

            Method isDedicatedServer = dist.getClass().getMethod("isDedicatedServer");
            return isDedicatedServer.invoke(dist) ? "SERVER" : "CLIENT";
         } catch (Throwable var9) {
         }
      }

      return null;
   }

   public String getRefMapperConfig() {
      return null;
   }

   public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
      return this.envType != null;
   }

   public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
   }

   public List<String> getMixins() {
      return null;
   }

   public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
      if (this.envType != null) {
         strip(targetClass, this.envType);
      }
   }

   public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
   }

   static void strip(ClassNode node, String envType) {
      if (!isForOtherSide(node.visibleAnnotations, envType) && !isForOtherSide(node.invisibleAnnotations, envType)) {
         Iterator<FieldNode> it = node.fields.iterator();

         while (it.hasNext()) {
            FieldNode field = it.next();
            if (isForOtherSide(field.visibleAnnotations, envType) || isForOtherSide(field.invisibleAnnotations, envType)) {
               it.remove();
            }
         }

         it = node.methods.iterator();

         while (it.hasNext()) {
            MethodNode method = (MethodNode)it.next();
            if (isForOtherSide(method.visibleAnnotations, envType) || isForOtherSide(method.invisibleAnnotations, envType)) {
               it.remove();
            }
         }

         List<String> strippedInterfaces = new ArrayList<>();
         collectStrippedInterfaces(node.visibleAnnotations, envType, strippedInterfaces);
         collectStrippedInterfaces(node.invisibleAnnotations, envType, strippedInterfaces);
         if (!strippedInterfaces.isEmpty()) {
            node.interfaces.removeAll(strippedInterfaces);
         }
      } else {
         node.fields.clear();
         node.methods.clear();
         node.interfaces.clear();
      }
   }

   private static boolean isForOtherSide(List<AnnotationNode> annotations, String envType) {
      if (annotations == null) {
         return false;
      } else {
         for (AnnotationNode annotation : annotations) {
            if ("Lnet/fabricmc/api/Environment;".equals(annotation.desc)) {
               String value = enumValue(annotation, "value");
               if (value != null && !value.equals(envType)) {
                  return true;
               }
            }
         }

         return false;
      }
   }

   private static void collectStrippedInterfaces(List<AnnotationNode> annotations, String envType, List<String> out) {
      if (annotations != null) {
         for (AnnotationNode annotation : annotations) {
            if ("Lnet/fabricmc/api/EnvironmentInterface;".equals(annotation.desc)) {
               collectStrippedInterface(annotation, envType, out);
            } else if ("Lnet/fabricmc/api/EnvironmentInterfaces;".equals(annotation.desc)) {
               Object value = annotationValue(annotation, "value");
               if (value instanceof List) {
                  for (Object element : (List)value) {
                     if (element instanceof AnnotationNode nested) {
                        collectStrippedInterface(nested, envType, out);
                     }
                  }
               }
            }
         }
      }
   }

   private static void collectStrippedInterface(AnnotationNode annotation, String envType, List<String> out) {
      String value = enumValue(annotation, "value");
      if (value != null && !value.equals(envType) && annotationValue(annotation, "itf") instanceof Type type) {
         out.add(type.getInternalName());
      }
   }

   private static Object annotationValue(AnnotationNode annotation, String name) {
      if (annotation.values == null) {
         return null;
      } else {
         for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
            if (name.equals(annotation.values.get(i))) {
               return annotation.values.get(i + 1);
            }
         }

         return null;
      }
   }

   private static String enumValue(AnnotationNode annotation, String name) {
      return annotationValue(annotation, name) instanceof String[] pair && pair.length == 2 ? pair[1] : null;
   }
}
