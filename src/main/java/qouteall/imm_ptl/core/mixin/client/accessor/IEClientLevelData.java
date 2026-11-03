package qouteall.imm_ptl.core.mixin.client.accessor;

import net.minecraft.client.world.ClientWorld.Properties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({Properties.class})
public interface IEClientLevelData {
   @Accessor("flatWorld")
   boolean ip_getIsFlat();
}
