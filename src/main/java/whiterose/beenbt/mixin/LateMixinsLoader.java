package whiterose.beenbt.mixin;

import java.util.List;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;
import com.gtnewhorizon.gtnhmixins.builders.IMixins;

/** Discovered by UniMixins (via {@link LateMixin}); registers our late mixin config. */
@LateMixin
public class LateMixinsLoader implements ILateMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.beenbtviewer.late.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        return IMixins.getLateMixins(Mixins.class, loadedMods);
    }
}
