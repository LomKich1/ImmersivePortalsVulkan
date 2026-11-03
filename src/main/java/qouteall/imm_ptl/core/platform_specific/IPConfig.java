package qouteall.imm_ptl.core.platform_specific;

import com.google.gson.JsonObject;
import java.util.HashSet;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry.BoundedDiscrete;
import me.shedaniel.autoconfig.annotation.ConfigEntry.Category;
import me.shedaniel.autoconfig.annotation.ConfigEntry.Gui.EnumHandler;
import me.shedaniel.autoconfig.annotation.ConfigEntry.Gui.Excluded;
import me.shedaniel.autoconfig.annotation.ConfigEntry.Gui.Tooltip;
import me.shedaniel.autoconfig.annotation.ConfigEntry.Gui.EnumHandler.EnumDisplayOption;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.portal.nether_portal.BlockPortalShape;
import qouteall.imm_ptl.peripheral.platform_specific.IPFeatureControl;
import qouteall.q_misc_util.Helper;

@Config(
   name = "immersive_portals"
)
public class IPConfig implements ConfigData {
   @Excluded
   public String check_the_wiki_for_more_information = "https://qouteall.fun/immptl/wiki/Config-Options";
   @Category("client")
   @BoundedDiscrete(
      min = 0L,
      max = 10L
   )
   @Tooltip
   public int maxPortalLayer = 5;
   @Category("client")
   @Tooltip
   public boolean lagAttackProof = true;
   @Category("client")
   public boolean enableCrossPortalSound = true;
   @Category("client")
   @Tooltip
   public boolean pureMirror = false;
   @Category("client")
   public boolean renderYourselfInPortal = true;
   @Category("client")
   @Tooltip
   public boolean correctCrossPortalEntityRendering = true;
   @Category("client")
   public boolean reducedPortalRendering = false;
   @Category("client")
   public boolean netherPortalOverlay = false;
   @Category("client")
   public boolean enableNetherPortalEffect = true;
   @Category("client")
   @Tooltip
   public boolean enableClientPerformanceAdjustment = true;
   @Category("client")
   public boolean clientTolerantVersionMismatchWithServer = false;
   @Category("client")
   @Tooltip
   public boolean compatibilityRenderMode = false;
   @Excluded
   public boolean checkModInfoFromInternet = true;
   @Excluded
   public boolean enableUpdateNotification = true;
   @Excluded
   public boolean sharedBlockMeshBufferOptimization = true;
   @Excluded
   public boolean enableClippingMechanism = true;
   @Excluded
   public boolean visibilityPrediction = true;
   @Excluded
   public boolean useDepthClampForPortalRendering = true;
   @Excluded
   public boolean enableCrossPortalView = true;
   @Excluded
   public int portalRenderLimit = 200;
   @Excluded
   public boolean doCheckGlError = false;
   @Excluded
   public boolean shaderpackWarning = true;
   @Excluded
   public int portalWandCursorAlignment = 2;
   @Excluded
   public boolean saveMemoryInBufferPack = false;
   @Excluded
   public boolean initialScreenShown = false;
   @EnumHandler(
      option = EnumDisplayOption.BUTTON
   )
   public IPGlobal.NetherPortalMode netherPortalMode = IPFeatureControl.enableVanillaBehaviorChangingByDefault()
      ? IPGlobal.NetherPortalMode.adaptive
      : IPGlobal.NetherPortalMode.vanilla;
   @EnumHandler(
      option = EnumDisplayOption.BUTTON
   )
   public IPGlobal.EndPortalMode endPortalMode = IPFeatureControl.enableVanillaBehaviorChangingByDefault()
      ? IPGlobal.EndPortalMode.normal
      : IPGlobal.EndPortalMode.vanilla;
   public boolean enableMirrorCreation = IPFeatureControl.enableVanillaBehaviorChangingByDefault();
   public boolean enableWarning = true;
   public boolean lightVanillaNetherPortalWhenCrouching = true;
   @Tooltip
   public boolean enableServerPerformanceAdjustment = true;
   public boolean enableDatapackPortalGen = true;
   @BoundedDiscrete(
      min = 1L,
      max = 32L
   )
   @Tooltip
   public int indirectLoadingRadiusCap = 8;
   @Tooltip
   public boolean keepChunksLoadedForever = false;
   @BoundedDiscrete(
      min = 3L,
      max = 64L
   )
   public int regularPortalLengthLimit = 64;
   @BoundedDiscrete(
      min = 4L,
      max = 128L
   )
   public int scaleLimit = 30;
   @Tooltip
   public boolean easeCreativePermission = true;
   @Tooltip
   public boolean easeCommandStickPermission = false;
   public boolean portalsChangeGravityByDefault = false;
   public boolean portalWandUsableOnSurvivalMode = false;
   @Excluded
   public int portalSearchingRange = 128;
   @Excluded
   public boolean serverSideNormalChunkLoading = true;
   @Excluded
   public boolean teleportationDebug = false;
   @Excluded
   public boolean looseMovementCheck = false;
   @Excluded
   public boolean chunkPacketDebug = false;
   @Excluded
   public boolean enableImmPtlChunkLoading = true;
   @Excluded
   public boolean serverTolerantVersionMismatchWithClient = false;
   @Excluded
   public boolean serverRejectClientWithoutImmPtl = true;
   @Excluded
   public boolean serverTeleportLogging = false;
   @Excluded
   public boolean enableCrossPortalInteraction = true;
   @Excluded
   public HashSet<String> disabledWarnings = new HashSet<>();
   @Excluded
   @Nullable
   public JsonObject dimStackPreset = null;

   public static IPConfig getConfig() {
      return (IPConfig)IPGlobal.configHolder.getConfig();
   }

   public void saveConfigFile() {
      IPGlobal.configHolder.setConfig(this);
      IPGlobal.configHolder.save();
   }

   public void onConfigChanged() {
      this.indirectLoadingRadiusCap = MathHelper.clamp(this.indirectLoadingRadiusCap, 1, 32);
      this.regularPortalLengthLimit = MathHelper.clamp(this.regularPortalLengthLimit, 3, 64);
      this.scaleLimit = MathHelper.clamp(this.scaleLimit, 8, 128);
      if (this.netherPortalMode == null) {
         this.netherPortalMode = IPGlobal.NetherPortalMode.adaptive;
      }

      if (this.endPortalMode == null) {
         this.endPortalMode = IPGlobal.EndPortalMode.normal;
      }

      IPGlobal.renderMode = this.compatibilityRenderMode ? IPGlobal.RenderMode.compatibility : IPGlobal.RenderMode.normal;
      IPGlobal.enableWarning = this.enableWarning;
      IPGlobal.enableMirrorCreation = this.enableMirrorCreation;
      IPGlobal.doCheckGlError = this.doCheckGlError;
      IPGlobal.maxPortalLayer = this.maxPortalLayer;
      IPGlobal.lagAttackProof = this.lagAttackProof;
      IPGlobal.portalRenderLimit = this.portalRenderLimit;
      IPGlobal.netherPortalFindingRadius = this.portalSearchingRange;
      IPGlobal.renderYourselfInPortal = this.renderYourselfInPortal;
      IPGlobal.activeLoading = this.serverSideNormalChunkLoading;
      IPGlobal.teleportationDebugEnabled = this.teleportationDebug;
      IPGlobal.correctCrossPortalEntityRendering = this.correctCrossPortalEntityRendering;
      IPGlobal.looseMovementCheck = this.looseMovementCheck;
      IPGlobal.pureMirror = this.pureMirror;
      IPGlobal.indirectLoadingRadiusCap = this.indirectLoadingRadiusCap;
      IPGlobal.keepChunksLoadedForever = this.keepChunksLoadedForever;
      IPGlobal.netherPortalMode = this.netherPortalMode;
      IPGlobal.endPortalMode = this.endPortalMode;
      IPGlobal.reducedPortalRendering = this.reducedPortalRendering;
      IPGlobal.offsetOcclusionQuery = this.visibilityPrediction;
      IPGlobal.netherPortalOverlay = this.netherPortalOverlay;
      IPGlobal.scaleLimit = this.scaleLimit;
      IPGlobal.easeCreativePermission = this.easeCreativePermission;
      IPGlobal.easeCommandStickPermission = this.easeCommandStickPermission;
      IPGlobal.enableSharedBlockMeshBuffers = this.sharedBlockMeshBufferOptimization;
      IPGlobal.enableDatapackPortalGen = this.enableDatapackPortalGen;
      IPGlobal.enableCrossPortalView = this.enableCrossPortalView;
      IPGlobal.enableClippingMechanism = this.enableClippingMechanism;
      IPGlobal.lightVanillaNetherPortalWhenCrouching = this.lightVanillaNetherPortalWhenCrouching;
      IPGlobal.enableNetherPortalEffect = this.enableNetherPortalEffect;
      IPGlobal.enableClientPerformanceAdjustment = this.enableClientPerformanceAdjustment;
      IPGlobal.enableServerPerformanceAdjustment = this.enableServerPerformanceAdjustment;
      IPGlobal.enableCrossPortalSound = this.enableCrossPortalSound;
      IPGlobal.checkModInfoFromInternet = this.checkModInfoFromInternet;
      IPGlobal.enableUpdateNotification = this.enableUpdateNotification;
      IPGlobal.enableDepthClampForPortalRendering = this.useDepthClampForPortalRendering;
      BlockPortalShape.defaultLengthLimit = this.regularPortalLengthLimit;
      IPGlobal.maxNormalPortalRadius = Math.max(this.regularPortalLengthLimit / 2, 16);
      IPGlobal.chunkPacketDebug = this.chunkPacketDebug;
      IPGlobal.saveMemoryInBufferPack = this.saveMemoryInBufferPack;
      Helper.LOGGER.info("iPortal Config Applied");
   }

   public boolean shouldDisplayWarning(String warningKey) {
      return this.enableWarning && !this.disabledWarnings.contains(warningKey);
   }
}
