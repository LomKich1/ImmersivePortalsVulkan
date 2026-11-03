package qouteall.imm_ptl.core.ducks;

import java.util.List;
import java.util.Map;
import net.minecraft.item.map.MapState;
import net.minecraft.world.EntityList;
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.world.tick.TickManager;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.portal.Portal;

public interface IEClientWorld {
   @Nullable
   List<Portal> ip_getGlobalPortals();

   void ip_setGlobalPortals(List<Portal> var1);

   void ip_resetWorldRendererRef();

   EntityList ip_getEntityList();

   Map<String, MapState> ip_getAllMapData();

   void ip_addMapData(Map<String, MapState> var1);

   PendingUpdateManager ip_getBlockStatePredictionHandler();

   void ip_setTickRateManager(TickManager var1);
}
