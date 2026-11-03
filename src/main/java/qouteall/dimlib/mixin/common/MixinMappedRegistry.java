package qouteall.dimlib.mixin.common;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import java.util.Map;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntryInfo;
import net.minecraft.registry.entry.RegistryEntry.class_6883;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import qouteall.dimlib.ducks.IMappedRegistry;

@Mixin({SimpleRegistry.class})
public abstract class MixinMappedRegistry<T> implements IMappedRegistry {
   @Shadow
   @Final
   private Map<Identifier, class_6883<T>> idToEntry;
   @Unique
   private static final Logger dimlib_LOGGER = LogUtils.getLogger();
   @Shadow
   @Final
   private ObjectList<class_6883<T>> rawIdToEntry;
   @Shadow
   @Final
   private Reference2IntMap<T> entryToRawId;
   @Shadow
   @Final
   private Map<RegistryKey<T>, class_6883<T>> keyToEntry;
   @Shadow
   @Final
   RegistryKey<? extends Registry<T>> key;
   @Shadow
   @Final
   private Map<T, class_6883<T>> valueToEntry;
   @Shadow
   private boolean frozen;
   @Shadow
   @Final
   private Map<RegistryKey<T>, RegistryEntryInfo> keyToEntryInfo;

   @Shadow
   @Nullable
   public abstract T get(int var1);

   @Override
   public boolean dimlib_getIsFrozen() {
      return this.frozen;
   }

   @Override
   public void dimlib_setIsFrozen(boolean cond) {
      this.frozen = cond;
   }

   @Override
   public boolean dimlib_forceRemove(Identifier id) {
      dimlib_LOGGER.debug("[DimLib] Trying to remove {} from {}", id, this.key);
      class_6883<T> holder = this.idToEntry.remove(id);
      if (holder == null) {
         dimlib_LOGGER.debug("[DimLib] {} not found in {} when trying to remove", id, this.key);
         return false;
      } else {
         RegistryKey<T> eleKey = holder.registryKey();
         T value = (T)holder.comp_349();
         int intId = this.entryToRawId.getInt(value);
         if (intId == -1) {
            dimlib_LOGGER.error("[DimLib] missing integer id for {} {}", value, id);
         } else {
            this.entryToRawId.removeInt(value);
            this.rawIdToEntry.set(intId, null);
         }

         this.keyToEntry.remove(eleKey);
         this.valueToEntry.remove(value);
         this.keyToEntryInfo.remove(eleKey);
         return true;
      }
   }
}
