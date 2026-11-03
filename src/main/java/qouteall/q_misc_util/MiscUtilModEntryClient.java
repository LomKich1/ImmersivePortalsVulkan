package qouteall.q_misc_util;

import net.fabricmc.api.ClientModInitializer;

public class MiscUtilModEntryClient implements ClientModInitializer {
   public void onInitializeClient() {
      ImplRemoteProcedureCall.initClient();
      MiscNetworking.initClient();
   }
}
