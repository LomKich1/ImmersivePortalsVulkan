package qouteall.imm_ptl.core.mixin.client.sync;

import net.minecraft.client.network.PendingUpdateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({PendingUpdateManager.class})
public interface IEBlockStatePredictionHandler {
   @Accessor("sequence")
   void ip_setCurrentSequenceNumber(int var1);
}
