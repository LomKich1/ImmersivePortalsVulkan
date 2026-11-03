package qouteall.imm_ptl.core.ducks;

import java.util.List;
import net.minecraft.server.world.ChunkTicket;

public interface IEDistanceManager {
   List<ChunkTicket> portal_getTicketSet(long var1);
}
