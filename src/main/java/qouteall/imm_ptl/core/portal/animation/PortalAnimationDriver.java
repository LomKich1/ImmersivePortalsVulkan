package qouteall.imm_ptl.core.portal.animation;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.McHelper;
import qouteall.q_misc_util.Helper;

public interface PortalAnimationDriver {
   Map<Identifier, Function<NbtCompound, PortalAnimationDriver>> deserializerRegistry = new HashMap<>();

   static void registerDeserializer(Identifier key, Function<NbtCompound, PortalAnimationDriver> deserializer) {
      deserializerRegistry.put(key, deserializer);
   }

   @Nullable
   static PortalAnimationDriver fromTag(NbtCompound tag) {
      String type = tag.getString("type", "");
      Function<NbtCompound, PortalAnimationDriver> deserializer = deserializerRegistry.get(McHelper.newResourceLocation(type));
      if (deserializer == null) {
         Helper.err("Unknown animation type " + type);
         return null;
      } else {
         try {
            return deserializer.apply(tag);
         } catch (Exception var4) {
            var4.printStackTrace();
            return null;
         }
      }
   }

   NbtCompound toTag();

   @NotNull
   AnimationResult getAnimationResult(long var1, float var3, AnimationContext var4);

   @Nullable
   DeltaUnilateralPortalState getEndingResult(long var1, AnimationContext var3);

   default PortalAnimationDriver getFlippedVersion() {
      return this;
   }

   default Text getInfo() {
      return Text.literal("Unknown Animation");
   }
}
