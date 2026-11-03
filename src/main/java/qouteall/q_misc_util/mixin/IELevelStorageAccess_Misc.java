package qouteall.q_misc_util.mixin;

import net.minecraft.world.level.storage.LevelStorage.LevelSave;
import net.minecraft.world.level.storage.LevelStorage.Session;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({Session.class})
public interface IELevelStorageAccess_Misc {
   @Accessor("directory")
   LevelSave ip_getLevelPath();
}
