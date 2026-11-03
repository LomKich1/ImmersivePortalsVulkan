package qouteall.imm_ptl.core.ducks;

import net.minecraft.entity.Entity;
import net.minecraft.world.MutableWorldProperties;
import net.minecraft.world.entity.EntityLookup;

public interface IEWorld {
   MutableWorldProperties ip_getLevelData();

   void portal_setWeather(float var1, float var2, float var3, float var4);

   EntityLookup<Entity> portal_getEntityLookup();

   Thread portal_getThread();
}
