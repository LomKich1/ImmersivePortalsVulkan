package qouteall.dimlib;

import net.fabricmc.api.ClientModInitializer;

public class DimLibEntryClient implements ClientModInitializer {
   public void onInitializeClient() {
      DimLibNetworking.initClient();
   }
}
