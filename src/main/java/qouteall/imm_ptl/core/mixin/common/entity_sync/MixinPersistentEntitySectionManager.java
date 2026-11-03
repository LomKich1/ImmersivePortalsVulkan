package qouteall.imm_ptl.core.mixin.common.entity_sync;

import net.minecraft.server.world.ServerEntityManager;
import org.spongepowered.asm.mixin.Mixin;
import qouteall.imm_ptl.core.ducks.IEServerEntityManager;

@Mixin({ServerEntityManager.class})
public class MixinPersistentEntitySectionManager implements IEServerEntityManager {
}
