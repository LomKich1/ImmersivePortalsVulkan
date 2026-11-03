package qouteall.q_misc_util.mixin;

import net.minecraft.world.level.storage.LevelStorage.class_5143;
import net.minecraft.world.level.storage.LevelStorage.class_7411;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({class_5143.class})
public interface IELevelStorageAccess_Misc {
   @Accessor("directory")
   class_7411 ip_getLevelPath();
}
