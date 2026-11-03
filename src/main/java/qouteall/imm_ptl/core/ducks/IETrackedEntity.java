package qouteall.imm_ptl.core.ducks;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.ChunkSectionPos;

public interface IETrackedEntity {
   Entity ip_getEntity();

   void ip_updateEntityTrackingStatus();

   void ip_onDimensionRemove();

   void ip_resendSpawnPacketToTrackers();

   void ip_stopTrackingToAllPlayers();

   void ip_sendChanges();

   ChunkSectionPos ip_getLastSectionPos();

   void ip_setLastSectionPos(ChunkSectionPos var1);
}
