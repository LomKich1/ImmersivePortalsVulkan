package qouteall.imm_ptl.core.compat;

import com.mojang.logging.LogUtils;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.IPMcHelper;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface;
import qouteall.imm_ptl.core.mc_utils.ServerTaskList;
import qouteall.imm_ptl.core.platform_specific.IPConfig;
import qouteall.imm_ptl.core.platform_specific.O_O;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.MyTaskList;
import qouteall.q_misc_util.platform.ModInfo;
import qouteall.q_misc_util.platform.Platform;

public class IPModInfoChecking {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final boolean IS_TEST = false;
   @Nullable
   private static List<String> incompatibleShaderpacks;
   @Nullable
   private static String lastShaderpackName;

   @Nullable
   public static IPModInfoChecking.ImmPtlInfo fetchImmPtlInfoFromInternet() {
      String url = O_O.getImmPtlModInfoUrl();
      if (url == null) {
         LOGGER.info("Not fetching iPortal mod info");
         return null;
      } else {
         try {
            HttpResponse<String> response = HttpClient.newHttpClient()
               .send(HttpRequest.newBuilder().uri(URI.create(url)).header("User-Agent", "Immersive Portals mod").build(), BodyHandlers.ofString());
            if (response.statusCode() != 200) {
               LOGGER.error("Failed to fetch iPortal mod info {}", response.statusCode());
               return null;
            } else {
               String jsonStr = response.body();
               IPModInfoChecking.ImmPtlInfo immPtlInfo = (IPModInfoChecking.ImmPtlInfo)Helper.gson.fromJson(jsonStr, IPModInfoChecking.ImmPtlInfo.class);
               LOGGER.info("iPortal mod info fetched");
               return immPtlInfo;
            }
         } catch (Throwable var4) {
            LOGGER.error("Failed to fetch iPortal mod info", var4);
            return null;
         }
      }
   }

   @Environment(EnvType.CLIENT)
   public static void initClient() {
      Util.getMainWorkerExecutor()
         .execute(
            () -> {
               if (IPGlobal.checkModInfoFromInternet) {
                  IPModInfoChecking.ImmPtlInfo immPtlInfo = fetchImmPtlInfoFromInternet();
                  if (immPtlInfo != null) {
                     incompatibleShaderpacks = immPtlInfo.incompatibleShaderpacks;
                     List<MutableText> severeIncompatWarningText = getSevereIncompatWarningText(immPtlInfo);
                     if (!severeIncompatWarningText.isEmpty()) {
                        MutableText var2 = severeIncompatWarningText.stream().reduce(Text.empty(), (a, b) -> a.append("\n").append(b));
                     }

                     IPGlobal.CLIENT_TASK_LIST
                        .addTask(
                           MyTaskList.withDelayCondition(
                              () -> MinecraftClient.getInstance().world == null,
                              MyTaskList.oneShotTask(
                                 () -> {
                                    List<MutableText> texts = new ArrayList<>();
                                    if (IPGlobal.enableUpdateNotification && O_O.shouldUpdateImmPtl(immPtlInfo.latestReleaseVersion)) {
                                       MutableText text1 = Text.translatable("imm_ptl.new_version_available", new Object[]{immPtlInfo.latestReleaseVersion});
                                       text1.append(McHelper.getLinkText(O_O.getModDownloadLink()));
                                       text1.append(Text.literal("  "));
                                       text1.append(IPMcHelper.getDisableUpdateCheckText());
                                       texts.add(text1);
                                    }

                                    if (IPConfig.getConfig().shouldDisplayWarning("preview") && immPtlInfo.previewVersions.contains(O_O.getImmPtlVersionStr())) {
                                       MutableText text1 = Text.translatable("imm_ptl.preview_warning")
                                          .append(McHelper.getLinkText(O_O.getIssueLink()))
                                          .append(" ")
                                          .append(IPMcHelper.getDisableWarningText("preview"));
                                       texts.add(text1);
                                    }

                                    texts.addAll(severeIncompatWarningText);

                                    for (IPModInfoChecking.ModIncompatInfo info : immPtlInfo.incompatible) {
                                       if (info != null && info.isModLoadedWithinVersion() && IPConfig.getConfig().shouldDisplayWarning(info.modId)) {
                                          MutableText text1 = Text.translatable("imm_ptl.incompatible", new Object[]{info.modName, info.modId})
                                             .formatted(Formatting.RED)
                                             .append(IPMcHelper.getDisableWarningText(info.modId));
                                          if (info.desc != null) {
                                             text1.append(Text.literal(" " + info.desc + " "));
                                          }

                                          if (info.link != null) {
                                             text1.append(McHelper.getLinkText(" " + info.link));
                                          }

                                          texts.add(text1);
                                       }
                                    }

                                    for (Text text : texts) {
                                       CHelper.printChat(text);
                                    }
                                 }
                              )
                           )
                        );
                  }
               }
            }
         );
      IPGlobal.CLIENT_TASK_LIST
         .addTask(
            MyTaskList.withDelayCondition(
               () -> MinecraftClient.getInstance().world == null,
               MyTaskList.oneShotTask(
                  () -> {
                     if (IPConfig.getConfig().shouldDisplayWarning("many_mods") && !Platform.get().isDevelopmentEnvironment()) {
                        List<ModInfo> topLevelMods = Platform.get().getAllMods().stream().filter(modContainer -> !modContainer.isNested()).toList();
                        if (topLevelMods.size() > 20) {
                           CHelper.printChat(
                              Text.literal(
                                    "[Immersive Portals] WARNING: You are using many mods. It's likely that one of them has compatibility issues with Immersive Portals. If you are sure that there is no compatibility issue, disable this warning."
                                 )
                                 .formatted(Formatting.RED)
                                 .append(IPMcHelper.getDisableWarningText("many_mods"))
                           );
                        }
                     }
                  }
               )
            )
         );
   }

   private static List<MutableText> getSevereIncompatWarningText(IPModInfoChecking.ImmPtlInfo immPtlInfo) {
      List<MutableText> texts = new ArrayList<>();

      for (IPModInfoChecking.ModIncompatInfo info : immPtlInfo.severelyIncompatible) {
         if (info != null && info.isModLoadedWithinVersion()) {
            MutableText text1;
            if (info.startVersion == null && info.endVersion == null) {
               text1 = Text.translatable("imm_ptl.severely_incompatible", new Object[]{info.modName, info.modId}).formatted(Formatting.RED);
            } else {
               text1 = Text.translatable("imm_ptl.severely_incompatible_within_version", new Object[]{info.modName, info.modId, info.getVersionRangeStr()})
                  .formatted(Formatting.RED);
            }

            if (info.desc != null) {
               text1.append(Text.literal(" " + info.desc + " "));
            }

            if (info.link != null) {
               text1.append(Text.literal(" "));
               text1.append(McHelper.getLinkText(info.link));
            }

            texts.add(text1);
         }
      }

      return texts;
   }

   public static void initDedicatedServer() {
      Platform.get()
         .onServerStarted(
            server -> {
               if (IPGlobal.checkModInfoFromInternet) {
                  IPModInfoChecking.ImmPtlInfo immPtlInfo = fetchImmPtlInfoFromInternet();
                  if (immPtlInfo != null) {
                     ServerTaskList.of(server)
                        .addOneShotTask(
                           () -> {
                              if (IPGlobal.enableUpdateNotification && O_O.shouldUpdateImmPtl(immPtlInfo.latestReleaseVersion)) {
                                 LOGGER.info(
                                    "[Immersive Portals] A new version is available. It is recommended to update to " + immPtlInfo.latestReleaseVersion
                                 );
                              }

                              for (IPModInfoChecking.ModIncompatInfo mod : immPtlInfo.severelyIncompatible) {
                                 if (mod != null && mod.isModLoadedWithinVersion()) {
                                    MutableText text1;
                                    if (mod.startVersion == null && mod.endVersion == null) {
                                       text1 = Text.translatable("imm_ptl.severely_incompatible", new Object[]{mod.modName, mod.modId})
                                          .formatted(Formatting.RED);
                                       LOGGER.error(
                                          "[Immersive Portals] Detected an incompatible mod: {} {} description: {} link: {}",
                                          new Object[]{mod.modName, mod.modId, mod.desc, mod.link}
                                       );
                                    } else {
                                       text1 = Text.translatable(
                                             "imm_ptl.severely_incompatible_within_version", new Object[]{mod.modName, mod.modId, mod.getVersionRangeStr()}
                                          )
                                          .formatted(Formatting.RED);
                                       LOGGER.error(
                                          "[Immersive Portals] Detected an incompatible mod: {} {} version range: {} description: {} link: {}",
                                          new Object[]{mod.modName, mod.modId, mod.getVersionRangeStr(), mod.desc, mod.link}
                                       );
                                    }

                                    if (mod.desc != null) {
                                       text1.append(Text.literal(" " + mod.desc + " "));
                                    }

                                    if (mod.link != null) {
                                       text1.append(Text.literal(" "));
                                       text1.append(McHelper.getLinkText(mod.link));
                                    }

                                    McHelper.sendMessageToFirstLoggedPlayer(server, Text.translatable("imm_ptl.message_from_server").append(text1));
                                 }
                              }

                              for (IPModInfoChecking.ModIncompatInfo modx : immPtlInfo.incompatible) {
                                 if (modx != null && modx.isModLoadedWithinVersion() && IPConfig.getConfig().shouldDisplayWarning(modx.modId)) {
                                    MutableText text1x = Text.translatable("imm_ptl.incompatible", new Object[]{modx.modName, modx.modId})
                                       .formatted(Formatting.RED);
                                    if (modx.desc != null) {
                                       text1x.append(Text.literal(" " + modx.desc + " "));
                                    }

                                    if (modx.link != null) {
                                       text1x.append(McHelper.getLinkText(" " + modx.link));
                                    }

                                    LOGGER.error(
                                       "[Immersive Portals] Detected a mildly incompatible mod: {} {} description: {} link: {}",
                                       new Object[]{modx.modName, modx.modId, modx.desc, modx.link}
                                    );
                                    McHelper.sendMessageToFirstLoggedPlayer(server, Text.translatable("imm_ptl.message_from_server").append(text1x));
                                 }
                              }
                           }
                        );
                  }
               }
            }
         );
   }

   public static void checkShaderpack() {
      if (IPConfig.getConfig().shaderpackWarning) {
         String shaderpackName = IrisInterface.invoker.getShaderpackName();
         if (!Objects.equals(lastShaderpackName, shaderpackName)) {
            lastShaderpackName = shaderpackName;
            if (shaderpackName != null && incompatibleShaderpacks != null) {
               boolean incompatible = incompatibleShaderpacks.stream().anyMatch(n -> shaderpackName.toLowerCase().contains(n.toLowerCase()));
               if (incompatible) {
                  CHelper.printChat(Text.translatable("imm_ptl.incompatible_shaderpack").formatted(Formatting.RED));
               }
            }
         }
      }
   }

   public static final class ImmPtlInfo {
      public String latestReleaseVersion;
      public List<String> previewVersions;
      public List<IPModInfoChecking.ModIncompatInfo> severelyIncompatible;
      public List<IPModInfoChecking.ModIncompatInfo> incompatible;
      public List<String> incompatibleShaderpacks;

      public ImmPtlInfo(
         String latestReleaseVersion, List<IPModInfoChecking.ModIncompatInfo> severelyIncompatible, List<IPModInfoChecking.ModIncompatInfo> incompatible
      ) {
         this.latestReleaseVersion = latestReleaseVersion;
         this.severelyIncompatible = severelyIncompatible;
         this.incompatible = incompatible;
      }

      @Override
      public String toString() {
         return "ImmPtlInfo{latestReleaseVersion='"
            + this.latestReleaseVersion
            + "', severelyIncompatible="
            + this.severelyIncompatible
            + ", incompatible="
            + this.incompatible
            + "}";
      }
   }

   public record ModIncompatInfo(
      String modId, String modName, @Nullable String startVersion, @Nullable String endVersion, @Nullable String desc, @Nullable String link
   ) {
      boolean isModLoadedWithinVersion() {
         return O_O.isModLoadedWithinVersion(this.modId, this.startVersion, this.endVersion);
      }

      String getVersionRangeStr() {
         if (this.startVersion != null) {
            return this.endVersion != null ? this.startVersion + "-" + this.endVersion : this.startVersion + "+";
         } else {
            Validate.notNull(this.endVersion, "invalid version range", new Object[0]);
            return "-" + this.endVersion;
         }
      }
   }
}
