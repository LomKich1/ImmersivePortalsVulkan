package qouteall.imm_ptl.core.mixin.common.miscellaneous;

import net.minecraft.entity.boss.dragon.EnderDragonFight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({EnderDragonFight.class})
public interface IEEndDragonFight {
   @Accessor("doLegacyCheck")
   boolean ip_getNeedsStateScanning();

   @Invoker("convertFromLegacy")
   void ip_scanState();
}
