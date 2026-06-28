package whiterose.beenbt.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.slprime.chromatictooltips.TooltipHandler;
import com.slprime.chromatictooltips.api.TooltipRequest;
import com.slprime.chromatictooltips.api.TooltipTarget;

import whiterose.beenbt.HudRenderHandler;

/**
 * Fully hides ChromaticTooltips' tooltip for the items our genome panel covers.
 *
 * CT renders every tooltip through {@code TooltipHandler.drawHoveringText(TooltipRequest)}; it
 * also unconditionally adds a keyboard-modifier chip header, so merely emptying the body (via the
 * CT target-sanitizer) leaves a residual box/[alt]. Cancelling this method at HEAD means CT never
 * builds, caches, or draws a tooltip for that item.
 */
@Mixin(value = TooltipHandler.class, remap = false)
public class MixinCTTooltipHandler {

    @Inject(method = "drawHoveringText(Lcom/slprime/chromatictooltips/api/TooltipRequest;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void beenbt$hideForGenomePanel(TooltipRequest request, CallbackInfo ci) {
        try {
            TooltipTarget target = request == null ? null : request.target;
            if (target != null && target.isItem()
                    && HudRenderHandler.ClientEvents.shouldSuppressTooltip(target.getItem())) {
                ci.cancel();
            }
        } catch (Throwable t) {
            // never break tooltips
        }
    }
}
