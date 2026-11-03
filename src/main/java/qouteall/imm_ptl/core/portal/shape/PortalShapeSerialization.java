package qouteall.imm_ptl.core.portal.shape;

import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.nbt.NbtCompound;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class PortalShapeSerialization {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Map<String, PortalShapeSerialization.Serializer<? extends PortalShape>> FROM_TYPE_NAME = new HashMap<>();
   private static final Map<Class<?>, PortalShapeSerialization.Serializer<? extends PortalShape>> FROM_CLASS = new HashMap<>();

   public static void addSerializer(PortalShapeSerialization.Serializer<? extends PortalShape> serializer) {
      FROM_TYPE_NAME.put(serializer.typeName(), serializer);
      FROM_CLASS.put(serializer.clazz(), serializer);
   }

   @Nullable
   public static PortalShape deserialize(NbtCompound tag) {
      String typeName = tag.getString("type", "");
      PortalShapeSerialization.Serializer<? extends PortalShape> serializer = FROM_TYPE_NAME.get(typeName);
      if (serializer == null) {
         LOGGER.warn("unknown portal shape type {} {}", typeName, tag);
         return null;
      } else {
         return serializer.deserializer().apply(tag);
      }
   }

   public static NbtCompound serialize(PortalShape portalShape) {
      PortalShapeSerialization.Serializer<? extends PortalShape> serializer = FROM_CLASS.get(portalShape.getClass());
      if (serializer == null) {
         LOGGER.warn("unknown portal shape class {}", portalShape.getClass());
         return new NbtCompound();
      } else {
         NbtCompound tag = serializer.serializer().apply(portalShape);
         tag.putString("type", serializer.typeName());
         return tag;
      }
   }

   public record Serializer<T extends PortalShape>(String typeName, Class<T> clazz, Function<T, NbtCompound> serializer, Function<NbtCompound, T> deserializer) {
   }
}
