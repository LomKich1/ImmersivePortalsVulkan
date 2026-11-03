package qouteall.imm_ptl.core.mixin.client.accessor;

import java.util.Map;
import net.minecraft.item.map.MapState;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({ClientWorld.class})
public interface IEClientLevel_Accessor {
   @Accessor("mapStates")
   Map<String, MapState> ip_getMapData();

   @Mutable
   @Accessor("mapStates")
   void ip_setMapData(Map<String, MapState> var1);
}
