package qouteall.imm_ptl.core.portal.animation;

import java.util.List;
import net.minecraft.text.Text;
import qouteall.imm_ptl.core.portal.IntraClusterRelation;
import qouteall.imm_ptl.core.portal.Portal;

public record AnimationView(Portal viewedPortal, Portal animationHolder, IntraClusterRelation relationToHolder) {
   public List<PortalAnimationDriver> getThisSideAnimations() {
      return this.relationToHolder.isReverse ? this.animationHolder.animation.otherSideAnimations : this.animationHolder.animation.thisSideAnimations;
   }

   public List<PortalAnimationDriver> getOtherSideAnimations() {
      return this.relationToHolder.isReverse ? this.animationHolder.animation.thisSideAnimations : this.animationHolder.animation.otherSideAnimations;
   }

   public PortalAnimationDriver convertToHolderAnimation(PortalAnimationDriver driver) {
      return this.relationToHolder.isFlipped ? driver.getFlippedVersion() : driver;
   }

   public void addThisSideAnimation(PortalAnimationDriver driver) {
      this.getThisSideAnimations().add(this.convertToHolderAnimation(driver));
   }

   public void addOtherSideAnimation(PortalAnimationDriver driver) {
      this.getOtherSideAnimations().add(this.convertToHolderAnimation(driver));
   }

   public Text getInfo() {
      return this.animationHolder.animation.getInfo(this.animationHolder, this.relationToHolder.isReverse);
   }
}
