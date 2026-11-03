package qouteall.dimlib.ducks;

import net.minecraft.util.Identifier;

public interface IMappedRegistry {
   boolean dimlib_forceRemove(Identifier var1);

   boolean dimlib_getIsFrozen();

   void dimlib_setIsFrozen(boolean var1);
}
