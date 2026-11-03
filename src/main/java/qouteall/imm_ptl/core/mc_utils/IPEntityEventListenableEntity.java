package qouteall.imm_ptl.core.mc_utils;

import net.minecraft.entity.Entity.RemovalReason;

public interface IPEntityEventListenableEntity {
   void ip_onEntityPositionUpdated();

   void ip_onRemoved(RemovalReason var1);
}
