package qouteall.imm_ptl.peripheral.dim_stack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.NoticeScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.apache.commons.lang3.Validate;
import qouteall.imm_ptl.core.portal.global_portals.VerticalConnectingPortal;
import qouteall.imm_ptl.peripheral.alternate_dimension.AlternateDimensions;
import qouteall.q_misc_util.Helper;

public class DimStackGuiController {
   private static final int entryCountLimit = 64;
   private final Screen parentScreen;
   public final DimStackGuiModel model;
   public final DimStackScreen view;
   private boolean hasConflict = false;
   private final Supplier<List<RegistryKey<World>>> dimensionListSupplier;
   private final Consumer<DimStackInfo> finishCallback;

   public DimStackGuiController(Screen parentScreen, Supplier<List<RegistryKey<World>>> dimensionListSupplier, Consumer<DimStackInfo> finishCallback) {
      this.parentScreen = parentScreen;
      this.model = new DimStackGuiModel();
      this.view = new DimStackScreen(parentScreen, this);
      this.dimensionListSupplier = dimensionListSupplier;
      this.finishCallback = finishCallback;
   }

   public void setEnabled(boolean enabled) {
      this.model.isEnabled = enabled;
      this.view.setEnabled(enabled);
      this.updateViewState();
   }

   private void updateViewState() {
      this.hasConflict = false;
      if (this.model.isEnabled) {
         Map<DimStackInfo.PortalInfo, List<DimStackEntry>> portalInfoMap = this.model.dimStackInfo.getPortalInfoMap();

         for (int i = 0; i < this.view.dimListWidget.children().size(); i++) {
            DimEntryWidget widget = (DimEntryWidget)this.view.dimListWidget.children().get(i);
            widget.entryIndex = i;
            List<DimStackEntry> ceilEntries = portalInfoMap.get(new DimStackInfo.PortalInfo(widget.dimension, VerticalConnectingPortal.ConnectorType.ceil));
            int ceilConnectionCount = ceilEntries == null ? 0 : ceilEntries.size();
            List<DimStackEntry> floorEntries = portalInfoMap.get(new DimStackInfo.PortalInfo(widget.dimension, VerticalConnectingPortal.ConnectorType.floor));
            int floorConnectionCount = floorEntries == null ? 0 : floorEntries.size();

            assert widget.entry != null;

            int toPreviousConnectionCount = widget.entry.flipped ? floorConnectionCount : ceilConnectionCount;
            int toNextConnectionCount = widget.entry.flipped ? ceilConnectionCount : floorConnectionCount;
            boolean conflictsToPrevious = toPreviousConnectionCount > 1;
            boolean conflictsToNext = toNextConnectionCount > 1;
            widget.arrowToPrevious = this.model.dimStackInfo.isEffectivelyConnectingPrevious(i)
               ? (conflictsToPrevious ? DimEntryWidget.ArrowType.conflicting : DimEntryWidget.ArrowType.enabled)
               : DimEntryWidget.ArrowType.none;
            widget.arrowToNext = this.model.dimStackInfo.isEffectivelyConnectionNext(i)
               ? (conflictsToNext ? DimEntryWidget.ArrowType.conflicting : DimEntryWidget.ArrowType.enabled)
               : DimEntryWidget.ArrowType.none;
            if (widget.arrowToPrevious == DimEntryWidget.ArrowType.conflicting || widget.arrowToNext == DimEntryWidget.ArrowType.conflicting) {
               this.hasConflict = true;
            }
         }
      }
   }

   public boolean addEntry(int index, DimStackEntry entry) {
      if (this.model.dimStackInfo.entries.size() >= 64) {
         return false;
      } else {
         Validate.isTrue(index >= 0 && index <= this.model.dimStackInfo.entries.size());
         this.model.dimStackInfo.entries.add(index, entry);
         this.view.dimListWidget.children().add(index, this.view.createDimEntryWidget(entry));
         this.updateViewState();
         return true;
      }
   }

   public void batchAddEntries(int index, List<DimStackEntry> entries) {
      Validate.isTrue(index >= 0 && index <= this.model.dimStackInfo.entries.size());
      List<DimStackEntry> entriesToAdd = entries;
      if (this.model.dimStackInfo.entries.size() + entries.size() > 64) {
         entriesToAdd = entries.subList(0, 64 - this.model.dimStackInfo.entries.size());
      }

      int currentIndex = index;

      for (DimStackEntry entry : entriesToAdd) {
         this.model.dimStackInfo.entries.add(currentIndex, entry);
         this.view.dimListWidget.children().add(currentIndex, this.view.createDimEntryWidget(entry));
         currentIndex++;
      }

      this.updateViewState();
   }

   public void removeEntry(int index) {
      Validate.isTrue(index >= 0 && index < this.model.dimStackInfo.entries.size());
      this.model.dimStackInfo.entries.remove(index);
      this.view.dimListWidget.children().remove(index);
      this.updateViewState();
   }

   public void clear() {
      this.model.dimStackInfo.entries.clear();
      this.view.dimListWidget.children().clear();
      this.updateViewState();
   }

   public void editEntry(int index, DimStackEntry newEntry) {
      Validate.isTrue(index >= 0 && index < this.model.dimStackInfo.entries.size());
      this.model.dimStackInfo.entries.set(index, newEntry);

      for (DimStackEntry entry : this.model.dimStackInfo.entries) {
         if (entry.dimensionIdStr.equals(newEntry.dimensionIdStr)) {
            entry.bedrockReplacementStr = newEntry.bedrockReplacementStr;
         }
      }

      DimEntryWidget newWidget = this.view.createDimEntryWidget(newEntry);
      this.view.dimListWidget.children().set(index, newWidget);
      this.view.dimListWidget.setSelected(newWidget);
      this.updateViewState();
   }

   public void onDragged(int selected, int mouseOver) {
      Validate.isTrue(selected >= 0 && selected < this.model.dimStackInfo.entries.size());
      Validate.isTrue(mouseOver >= 0 && mouseOver < this.model.dimStackInfo.entries.size());
      Helper.swapListElement(this.model.dimStackInfo.entries, selected, mouseOver);
      Helper.swapListElement(this.view.dimListWidget.children(), selected, mouseOver);
      this.updateViewState();
      this.view.dimListWidget.setSelected((DimEntryWidget)this.view.dimListWidget.children().get(mouseOver));
   }

   public void initializeAsDefault() {
      DimStackInfo preset = DimStackManagement.getDimStackPreset();
      if (preset != null) {
         this.setEnabled(true);
         this.setLoopEnabled(preset.loop);
         this.setGravityTransformEnabled(preset.gravityTransform);
         this.batchAddEntries(0, preset.entries);
      } else {
         this.setEnabled(false);
         this.setLoopEnabled(false);
         this.setGravityTransformEnabled(false);
         List<DimStackEntry> entriesToAdd = new ArrayList<>();
         entriesToAdd.add(new DimStackEntry(AlternateDimensions.BRIGHT_VOID));
         entriesToAdd.add(new DimStackEntry(AlternateDimensions.BRIGHT_SKYLAND));
         entriesToAdd.add(new DimStackEntry(World.OVERWORLD));
         entriesToAdd.add(new DimStackEntry(World.NETHER));
         this.batchAddEntries(0, entriesToAdd);
      }
   }

   public List<RegistryKey<World>> getDimensionList() {
      return this.dimensionListSupplier.get();
   }

   public void onFinish() {
      if (this.hasConflict) {
         this.showConflictingAlert();
      } else {
         this.finishCallback.accept(this.model.getResult());
      }
   }

   private void showConflictingAlert() {
      MinecraftClient.getInstance()
         .setScreen(
            new NoticeScreen(
               () -> MinecraftClient.getInstance().setScreen(this.view),
               Text.translatable("imm_ptl.conflicting_dim_stack").formatted(Formatting.RED),
               Text.translatable("imm_ptl.conflicting_dim_stack_detail")
            )
         );
   }

   public void toggleEnabled() {
      this.setEnabled(!this.model.isEnabled);
   }

   public void toggleLoop() {
      boolean cond = !this.model.dimStackInfo.loop;
      this.setLoopEnabled(cond);
   }

   public void setLoopEnabled(boolean cond) {
      this.model.dimStackInfo.loop = cond;
      this.view.setLoopEnabled(this.model.dimStackInfo.loop);
      this.updateViewState();
   }

   public void toggleGravityMode() {
      boolean cond = !this.model.dimStackInfo.gravityTransform;
      this.setGravityTransformEnabled(cond);
   }

   public void setGravityTransformEnabled(boolean cond) {
      this.model.dimStackInfo.gravityTransform = cond;
      this.view.setGravityTransformEnabled(this.model.dimStackInfo.gravityTransform);
   }

   public void setAsDefault() {
      if (this.hasConflict) {
         this.showConflictingAlert();
      } else {
         DimStackManagement.setDimStackPreset(this.model.getResult());
         MinecraftClient.getInstance()
            .setScreen(
               new NoticeScreen(
                  () -> MinecraftClient.getInstance().setScreen(this.view), Text.literal(""), Text.translatable("imm_ptl.dim_stack_default_updated")
               )
            );
      }
   }
}
