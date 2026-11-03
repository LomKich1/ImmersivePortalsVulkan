package qouteall.imm_ptl.core.mixin.client.accessor;

import net.minecraft.client.world.ClientWorld.class_5271;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({class_5271.class})
public interface IEClientLevelData {
   @Accessor("flatWorld")
   boolean ip_getIsFlat();
}
