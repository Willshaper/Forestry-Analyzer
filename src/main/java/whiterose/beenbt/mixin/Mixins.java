package whiterose.beenbt.mixin;

import com.gtnewhorizon.gtnhmixins.builders.IBaseTransformer.Phase;
import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizon.gtnhmixins.builders.MixinBuilder;

import cpw.mods.fml.common.Loader;

public enum Mixins implements IMixins {

    CT_HIDE_TOOLTIP(new MixinBuilder("Hide ChromaticTooltips tooltip for genome items")
            .addClientMixins("MixinCTTooltipHandler")
            .setApplyIf(() -> Loader.isModLoaded("chromatictooltips"))
            .setPhase(Phase.LATE));

    private final MixinBuilder builder;

    Mixins(MixinBuilder builder) {
        this.builder = builder;
    }

    @Override
    public MixinBuilder getBuilder() {
        return this.builder;
    }
}
