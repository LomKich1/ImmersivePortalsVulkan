package qouteall.imm_ptl.core.api;

import net.minecraft.entity.Entity;

public interface ImmPtlEntityExtension {
   default boolean imm_ptl_canTeleportThroughPortal(Entity portal) {
      return true;
   }
}
