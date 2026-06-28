package whiterose.beenbt;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.relauncher.Side;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import forestry.api.apiculture.BeeManager;
import forestry.api.apiculture.EnumBeeChromosome;
import forestry.api.apiculture.IBee;
import forestry.api.arboriculture.EnumTreeChromosome;
import forestry.api.arboriculture.IAlleleFruit;
import forestry.api.arboriculture.IAlleleGrowth;
import forestry.api.arboriculture.TreeManager;
import forestry.api.genetics.AlleleManager;
import forestry.api.genetics.IAllele;
import forestry.api.genetics.IAlleleBoolean;
import forestry.api.genetics.IAlleleInteger;
import forestry.api.genetics.IAlleleSpecies;
import forestry.api.genetics.IChromosomeType;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.IIndividual;
import forestry.api.genetics.IMutation;
import forestry.api.genetics.ISpeciesRoot;

/**
 * Forestry Genome Tooltip ("Forestry Beealyzer as a Tooltip").
 *
 * Shows a Forestry individual's genome as a compact table when hovering the item
 * in a container GUI (hold ALT, or toggle it permanent with the hotkey). Works for
 * bees and trees; each gets a curated row order and labels matching Forestry's own
 * Beealyzer / Treealyzer. Optionally shows a pure-bred badge and breeding mutations.
 *
 * This is effectively a client-only mod (it renders GUIs and registers
 * keybindings); it is intended to be installed client-side.
 */
@Mod(modid = HudRenderHandler.MODID, name = "Forestry Beealyzer as a Tooltip", version = "9.3",
        dependencies = "required-after:Forestry;after:NotEnoughItems;after:chromatictooltips", useMetadata = true)
public class HudRenderHandler {
    public static final String MODID = "beenbtviewer";

    public static Logger logger = FMLLog.getLogger();

    public static KeyBinding keyCopyNBT;
    public static KeyBinding keyToggleRawNBT;
    public static KeyBinding keyToggleMutations;
    public static KeyBinding keyShowPanel; // hold-to-show (default Alt), rebindable in Controls

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        loadConfig(event.getSuggestedConfigurationFile());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // Client-only mod. This FML build's @Mod has no clientSideOnly attribute, so guard
        // manually: on a dedicated server, do nothing (never touch KeyBinding/ClientRegistry).
        if (FMLCommonHandler.instance().getSide() != Side.CLIENT) {
            return;
        }
        logger.info("Forestry Genome Tooltip initializing (bees & trees)...");

        ClientEvents handler = new ClientEvents();
        ClientEvents.INSTANCE = handler;
        // Forge-bus events: GuiScreenEvent (rendering / in-GUI key polling) and ItemTooltipEvent.
        MinecraftForge.EVENT_BUS.register(handler);
        // FML-bus events: InputEvent.KeyInputEvent fires here, so the in-world
        // hotkeys only work if we also register on this bus.
        FMLCommonHandler.instance().bus().register(handler);

        keyCopyNBT = new KeyBinding("key.beenbtviewer.copy", Keyboard.KEY_N, "key.categories.beenbtviewer");
        ClientRegistry.registerKeyBinding(keyCopyNBT);

        keyToggleRawNBT = new KeyBinding("key.beenbtviewer.toggle_raw", Keyboard.KEY_O, "key.categories.beenbtviewer");
        ClientRegistry.registerKeyBinding(keyToggleRawNBT);

        keyToggleMutations = new KeyBinding("key.beenbtviewer.toggle_mutations", Keyboard.KEY_M, "key.categories.beenbtviewer");
        ClientRegistry.registerKeyBinding(keyToggleMutations);

        // Hold-to-show key (default Alt). Rebindable in Controls to avoid conflicts.
        keyShowPanel = new KeyBinding("key.beenbtviewer.show_panel", Keyboard.KEY_LMENU, "key.categories.beenbtviewer");
        ClientRegistry.registerKeyBinding(keyShowPanel);

        // Hiding the normal tooltip while our panel shows is done two ways:
        //  - ChromaticTooltips (the pack's tooltip renderer): the MixinCTTooltipHandler mixin,
        //    applied by UniMixins when CT is present (see mixins.beenbtviewer.late.json).
        //  - Anything else: the onItemTooltip fallback below clears the tooltip lines.
    }

    /** Loads the Forge config and applies it to the render settings. */
    private static void loadConfig(File file) {
        Configuration config = new Configuration(file);
        try {
            config.load();

            final String COLORS = "colors";
            config.addCustomCategoryComment(COLORS,
                    "Tooltip colors as ARGB hex, e.g. 0xE63A4A5A (AA = alpha/opacity, 00 = transparent, FF = solid).");
            ClientEvents.BORDER_COLOR =
                    parseColor(config.getString("border", COLORS, "0xE63A4A5A", "Outer border color"), 0xE63A4A5A);
            ClientEvents.HR_COLOR =
                    parseColor(config.getString("divider", COLORS, "0xF26A7F99", "Horizontal divider color (fades out at the edges)"), 0xF26A7F99);
            ClientEvents.COLOR_BACKGROUND =
                    parseColor(config.getString("background", COLORS, "0xCC202020", "Panel background color"), 0xCC202020);
            ClientEvents.COLOR_MATCH_GREEN =
                    parseColor(config.getString("match", COLORS, "0xFF7FD97F", "Color for matching (pure/homozygous) alleles, and the pure-bred badge"), 0xFF7FD97F);
            ClientEvents.COLOR_MISMATCH_RED =
                    parseColor(config.getString("mismatch", COLORS, "0xFFFF5A5A", "Color for differing (hybrid/heterozygous) alleles"), 0xFFFF5A5A);
            ClientEvents.COLOR_TEXT_WHITE =
                    parseColor(config.getString("text", COLORS, "0xFFFFFFFF", "Trait name (row label) text color; the Trait/Active/Inactive headers stay gray"), 0xFFFFFFFF);

            final String DISPLAY = "display";
            config.addCustomCategoryComment(DISPLAY, "What to show and where to put the panel.");
            ClientEvents.alwaysShow = config.getBoolean("alwaysShow", DISPLAY, false,
                    "If true, the trait table always shows on hover (no ALT or toggle needed).");
            ClientEvents.hideNormalTooltip = config.getBoolean("hideNormalTooltip", DISPLAY, true,
                    "Hide the normal item tooltip while the genome panel is showing (needs ChromaticTooltips for full effect).");
            ClientEvents.showPureBadge = config.getBoolean("showPureBadge", DISPLAY, true,
                    "Show a 'PURE-BRED' badge when every chromosome is homozygous.");
            ClientEvents.showMutations = config.getBoolean("showMutations", DISPLAY, true,
                    "Show breeding mutations. Also toggleable in-game with the Toggle Mutation Hints key.");
            ClientEvents.maxMutations = config.getInt("maxMutations", DISPLAY, 6, 1, 50,
                    "Max mutation lines to list per direction (breeds-into / made-from).");
            ClientEvents.anchor = validateAnchor(config.getString("anchor", DISPLAY, "SLOT",
                    "Panel position: SLOT (under the hovered slot), TOPLEFT, TOPRIGHT, BOTTOMLEFT, BOTTOMRIGHT."));
            ClientEvents.offsetX = config.getInt("offsetX", DISPLAY, 0, -10000, 10000, "Horizontal pixel offset applied to the anchor.");
            ClientEvents.offsetY = config.getInt("offsetY", DISPLAY, 0, -10000, 10000, "Vertical pixel offset applied to the anchor.");
            ClientEvents.scale = config.getFloat("scale", DISPLAY, 1.0f, 0.25f, 4.0f, "Panel scale multiplier (0.25 - 4.0).");

            String[] hidden = config.get(DISPLAY, "hiddenRows", new String[0],
                    "Row labels to hide, e.g. Territory, Effect, Area. Case-insensitive.").getStringList();
            Set<String> hiddenSet = new HashSet<String>();
            for (String h : hidden) {
                if (h != null && !h.trim().isEmpty()) {
                    hiddenSet.add(h.trim().toLowerCase());
                }
            }
            ClientEvents.hiddenRows = hiddenSet;
        } catch (Exception e) {
            logger.error("[Forestry Genome Tooltip] Failed to load config, using defaults.", e);
        } finally {
            if (config.hasChanged()) {
                config.save();
            }
        }
    }

    private static String validateAnchor(String s) {
        String a = s == null ? "SLOT" : s.trim().toUpperCase();
        if (a.equals("SLOT") || a.equals("TOPLEFT") || a.equals("TOPRIGHT")
                || a.equals("BOTTOMLEFT") || a.equals("BOTTOMRIGHT")) {
            return a;
        }
        return "SLOT";
    }

    /** Parses an ARGB hex string like "0xE63A4A5A" or "#E63A4A5A" into an int, falling back to def. */
    private static int parseColor(String s, int def) {
        try {
            s = s.trim();
            if (s.startsWith("#")) {
                s = s.substring(1);
            } else if (s.length() >= 2 && (s.charAt(0) == '0') && (s.charAt(1) == 'x' || s.charAt(1) == 'X')) {
                s = s.substring(2);
            }
            if (s.isEmpty()) {
                return def;
            }
            return (int) Long.parseLong(s, 16);
        } catch (Exception e) {
            return def;
        }
    }

    public static class ClientEvents {
        // The single registered handler instance (used by the CT sanitizer, a separate class).
        static ClientEvents INSTANCE;

        private static boolean isDisplayingRawNBT = false;
        private static boolean wasCopyPressed = false;
        private static boolean wasTogglePressed = false;
        private static boolean wasMutTogglePressed = false;
        // Render errors are logged once, then suppressed (the event fires every frame).
        private static boolean loggedRenderError = false;

        // 1-entry cache: avoid re-parsing the genome / re-scanning mutations every render frame
        // while hovering the same item. Keyed on the ItemStack reference. Cleared when the
        // mutation toggle flips (so the panel rebuilds with/without mutations).
        private static ItemStack cachedStack;
        private static PanelData cachedPanel;

        // Cached reflected GuiContainer fields (looked up once instead of every frame).
        private static Field xSizeField;
        private static Field ySizeField;

        // --- Bee-specific curated layout ---
        // Display order, expressed as indices into NBT_INDEX_TO_CHROMOSOME below.
        private static final int[] DISPLAY_ORDER_INDICES = new int[]{0, 2, 1, 10, 9, 3, 11, 12, 4, 6, 5, 7, 8};

        private static final EnumBeeChromosome[] NBT_INDEX_TO_CHROMOSOME = new EnumBeeChromosome[]{
                EnumBeeChromosome.SPECIES,
                EnumBeeChromosome.SPEED,
                EnumBeeChromosome.LIFESPAN,
                EnumBeeChromosome.FERTILITY,
                EnumBeeChromosome.TEMPERATURE_TOLERANCE,
                EnumBeeChromosome.NOCTURNAL,
                EnumBeeChromosome.HUMIDITY_TOLERANCE,
                EnumBeeChromosome.TOLERANT_FLYER,
                EnumBeeChromosome.CAVE_DWELLING,
                EnumBeeChromosome.FLOWER_PROVIDER,
                EnumBeeChromosome.FLOWERING,
                EnumBeeChromosome.TERRITORY,
                EnumBeeChromosome.EFFECT
        };

        private static final String[] TRAIT_CATEGORIES_DISPLAY = new String[]{
                "Species", "Lifespan", "Speed", "Pollination", "Flowers", "Fertility",
                "Area", "Effect", "Temperature", "Humidity", "Nocturnal", "Tolerant Flyer", "Cave Dwelling"
        };

        private static final String CATEGORY_TITLE = EnumChatFormatting.GRAY + "Trait";
        private static final String ACTIVE_TITLE = EnumChatFormatting.GRAY + "Active";
        private static final String INACTIVE_TITLE = EnumChatFormatting.GRAY + "Inactive";

        // ARGB colors. Defaults match the in-game tooltip resource pack; overridable via config.
        static int COLOR_MATCH_GREEN = 0xFF7FD97F;
        static int COLOR_MISMATCH_RED = 0xFFFF5A5A;
        static int COLOR_BACKGROUND = 0xCC202020;
        static int COLOR_TEXT_WHITE = 0xFFFFFFFF;
        static int BORDER_COLOR = 0xE63A4A5A;   // all four edges
        static int HR_COLOR = 0xF26A7F99;       // internal divider lines (faded at the edges)
        private static final int LABEL_GRAY = 0xFFB0B0B0;       // mutation conditions / "+N more"
        private static final int BREEDS_INTO_COLOR = 0xFF7FE6F0; // aqua: "Breeds into" section
        private static final int MADE_FROM_COLOR = 0xFFD6B84A;   // gold: "Made from" section

        // Display config (loaded from file at preInit).
        static boolean alwaysShow = false;
        static boolean hideNormalTooltip = true;
        static boolean showPureBadge = true;
        static boolean showMutations = true;
        static int maxMutations = 6;
        static Set<String> hiddenRows = Collections.emptySet();
        static String anchor = "SLOT";
        static int offsetX = 0;
        static int offsetY = 0;
        static float scale = 1.0f;

        private static final int COL_PADDING = 12;
        private static final int LINE_HEIGHT = 10;
        private static final int SIDE_PADDING = 4;
        private static final int TOP_PADDING = 4;
        private static final int BOTTOM_MARGIN = 3;
        private static final int STATUS_GAP = 2;   // gap before the pure-bred badge
        private static final int MUT_GAP = 4;      // gap + divider before the mutation block

        private static final PanelData EMPTY_PANEL = new PanelData(
                Collections.<TraitRow>emptyList(), Collections.<ExtraLine>emptyList(), Collections.<ExtraLine>emptyList());

        /** Whether the configurable hold-to-show key (default Alt) is currently held. */
        static boolean showKeyDown() {
            return keyShowPanel != null && Keyboard.isKeyDown(keyShowPanel.getKeyCode());
        }

        private boolean isMouseOverSlot(Slot slot, int mouseX, int mouseY, int guiLeft, int guiTop) {
            if (slot == null) {
                return false;
            }
            int x = guiLeft + slot.xDisplayPosition;
            int y = guiTop + slot.yDisplayPosition;
            return mouseX >= x - 1 && mouseX < x + 16 + 1 && mouseY >= y - 1 && mouseY < y + 16 + 1;
        }

        private int getGuiXSize(GuiContainer gui) {
            try {
                if (xSizeField == null) {
                    xSizeField = ReflectionHelper.findField(GuiContainer.class, "xSize", "field_146999_f");
                }
                return xSizeField.getInt(gui);
            } catch (Exception e) {
                return 176;
            }
        }

        private int getGuiYSize(GuiContainer gui) {
            try {
                if (ySizeField == null) {
                    ySizeField = ReflectionHelper.findField(GuiContainer.class, "ySize", "field_147000_g");
                }
                return ySizeField.getInt(gui);
            } catch (Exception e) {
                return 166;
            }
        }

        @SubscribeEvent
        public void onGuiPostRender(GuiScreenEvent.DrawScreenEvent.Post event) {
            Minecraft mc = Minecraft.getMinecraft();
            GuiScreen gui = event.gui;
            if (!(gui instanceof GuiContainer)) {
                return;
            }
            GuiContainer container = (GuiContainer) gui;
            if (container.inventorySlots == null || container.inventorySlots.inventorySlots == null) {
                return;
            }

            int mouseX = event.mouseX;
            int mouseY = event.mouseY;
            int guiLeft = (container.width - this.getGuiXSize(container)) / 2;
            int guiTop = (container.height - this.getGuiYSize(container)) / 2;

            ItemStack hoveredItem = null;
            Slot hoveredSlot = null;
            for (Object obj : container.inventorySlots.inventorySlots) {
                if (!(obj instanceof Slot)) {
                    continue;
                }
                Slot slot = (Slot) obj;
                if (!this.isMouseOverSlot(slot, mouseX, mouseY, guiLeft, guiTop) || !slot.getHasStack()) {
                    continue;
                }
                hoveredItem = slot.getStack();
                hoveredSlot = slot;
                break;
            }

            // If not over a real slot, ask NEI for the item under the cursor (its item panel
            // and NEI GUIs aren't real container slots). No-op if NEI isn't installed.
            if (hoveredItem == null) {
                hoveredItem = NEICompat.stackUnderMouse(container);
            }

            // Toggle/copy hotkeys while a container GUI is open. (KeyInputEvent doesn't fire here.)
            this.handleKeyInputInGUI(mc, hoveredItem);

            boolean shouldRenderTable = alwaysShow || isDisplayingRawNBT || showKeyDown();
            if (!shouldRenderTable || hoveredItem == null) {
                return;
            }

            // Anchor box: a real slot's bounds, or a small box around the cursor (NEI items).
            int anchorCenterX;
            int anchorTopY;
            int anchorBottomY;
            if (hoveredSlot != null) {
                anchorCenterX = guiLeft + hoveredSlot.xDisplayPosition + 8;
                anchorTopY = guiTop + hoveredSlot.yDisplayPosition;
                anchorBottomY = anchorTopY + 16;
            } else {
                anchorCenterX = mouseX;
                anchorTopY = mouseY - 8;
                anchorBottomY = mouseY + 8;
            }

            // Guard the whole read+draw: a malformed individual (bad addon data) must not
            // crash the GUI or spam the log every frame.
            try {
                this.renderPanel(mc, hoveredItem, anchorCenterX, anchorTopY, anchorBottomY);
            } catch (Throwable t) {
                if (!loggedRenderError) {
                    loggedRenderError = true;
                    logger.error("[Forestry Genome Tooltip] Error rendering trait table "
                            + "(further errors suppressed this session).", t);
                }
            }
        }

        private void renderPanel(Minecraft mc, ItemStack hoveredItem, int anchorCenterX, int anchorTopY, int anchorBottomY) {
            PanelData data = this.getPanelCached(hoveredItem);
            if (data.rows.isEmpty()) {
                return;
            }

            FontRenderer font = mc.fontRenderer;

            // Column widths (genome table).
            int col1W = font.getStringWidth(CATEGORY_TITLE);
            int col2W = font.getStringWidth(ACTIVE_TITLE);
            int col3W = font.getStringWidth(INACTIVE_TITLE);
            for (TraitRow trait : data.rows) {
                col1W = Math.max(col1W, font.getStringWidth(trait.category));
                col2W = Math.max(col2W, font.getStringWidth(trait.active));
                col3W = Math.max(col3W, font.getStringWidth(trait.inactive));
            }
            int tableWidth = col1W + COL_PADDING + col2W + COL_PADDING + col3W + 8;

            // Extra (badge / mutation) line widths.
            int extrasWidth = 0;
            for (ExtraLine e : data.statusLines) {
                extrasWidth = Math.max(extrasWidth, font.getStringWidth(e.text));
            }
            for (ExtraLine e : data.mutationLines) {
                extrasWidth = Math.max(extrasWidth, font.getStringWidth(e.text));
            }

            int panelWidth = Math.max(tableWidth, extrasWidth + 2 * SIDE_PADDING);
            int panelHeight = this.computeHeight(data.rows.size(), data.statusLines.size(), data.mutationLines.size());

            ScaledResolution scaledRes = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
            int screenW = scaledRes.getScaledWidth();
            int screenH = scaledRes.getScaledHeight();

            int effW = Math.round(panelWidth * scale);
            int effH = Math.round(panelHeight * scale);

            int hudX;
            int hudY;
            if ("TOPLEFT".equals(anchor)) {
                hudX = SIDE_PADDING;
                hudY = SIDE_PADDING;
            } else if ("TOPRIGHT".equals(anchor)) {
                hudX = screenW - effW - SIDE_PADDING;
                hudY = SIDE_PADDING;
            } else if ("BOTTOMLEFT".equals(anchor)) {
                hudX = SIDE_PADDING;
                hudY = screenH - effH - SIDE_PADDING;
            } else if ("BOTTOMRIGHT".equals(anchor)) {
                hudX = screenW - effW - SIDE_PADDING;
                hudY = screenH - effH - SIDE_PADDING;
            } else { // SLOT (or the cursor, for NEI items)
                hudX = anchorCenterX - effW / 2;
                hudY = anchorBottomY + 2;
                if (hudY + effH > screenH) {
                    hudY = anchorTopY - effH - 2; // flip above the anchor
                }
            }
            hudX += offsetX;
            hudY += offsetY;
            // Keep it on-screen.
            if (hudX + effW > screenW) {
                hudX = screenW - effW - SIDE_PADDING;
            }
            if (hudX < SIDE_PADDING) {
                hudX = SIDE_PADDING;
            }
            if (hudY + effH > screenH) {
                hudY = screenH - effH - SIDE_PADDING;
            }
            if (hudY < SIDE_PADDING) {
                hudY = SIDE_PADDING;
            }

            int col2X = SIDE_PADDING + col1W + COL_PADDING;
            int col3X = col2X + col2W + COL_PADDING;

            GL11.glPushMatrix();
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            try {
                GL11.glTranslatef(hudX, hudY, 0.0f);
                if (scale != 1.0f) {
                    GL11.glScalef(scale, scale, 1.0f);
                }
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glDisable(GL11.GL_CULL_FACE);
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

                // Background + border (local coords, origin at the panel's top-left).
                Gui.drawRect(0, 0, panelWidth, panelHeight, COLOR_BACKGROUND);
                Gui.drawRect(0, 0, panelWidth, 1, BORDER_COLOR);                          // top
                Gui.drawRect(0, panelHeight - 1, panelWidth, panelHeight, BORDER_COLOR);  // bottom
                Gui.drawRect(0, 0, 1, panelHeight, BORDER_COLOR);                         // left
                Gui.drawRect(panelWidth - 1, 0, panelWidth, panelHeight, BORDER_COLOR);   // right

                GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
                int y = TOP_PADDING;

                // Header row + faded divider.
                font.drawString(CATEGORY_TITLE, SIDE_PADDING, y, COLOR_TEXT_WHITE, false);
                font.drawString(ACTIVE_TITLE, col2X, y, COLOR_TEXT_WHITE, false);
                font.drawString(INACTIVE_TITLE, col3X, y, COLOR_TEXT_WHITE, false);
                y += LINE_HEIGHT;
                drawFadeDivider(2, panelWidth - 2, y - 1, HR_COLOR);

                // Genome rows.
                for (TraitRow trait : data.rows) {
                    int traitColor = trait.isHeterozygous ? COLOR_MISMATCH_RED : COLOR_MATCH_GREEN;
                    font.drawString(trait.category, SIDE_PADDING, y, COLOR_TEXT_WHITE, false);
                    font.drawString(trait.active, col2X, y, traitColor, false);
                    font.drawString(trait.inactive, col3X, y, traitColor, false);
                    y += LINE_HEIGHT;
                }

                // Status lines (pure-bred badge), grouped with the genome, above the mutation divider.
                if (!data.statusLines.isEmpty()) {
                    y += STATUS_GAP;
                    for (ExtraLine e : data.statusLines) {
                        int ex = e.center ? (panelWidth - font.getStringWidth(e.text)) / 2 : SIDE_PADDING;
                        font.drawString(e.text, ex, y, e.color, false);
                        y += LINE_HEIGHT;
                    }
                }

                // Mutation block: own faded divider then the lines.
                if (!data.mutationLines.isEmpty()) {
                    y += 2;
                    drawFadeDivider(2, panelWidth - 2, y, HR_COLOR);
                    y += 2;
                    for (ExtraLine e : data.mutationLines) {
                        int ex = e.center ? (panelWidth - font.getStringWidth(e.text)) / 2 : SIDE_PADDING;
                        font.drawString(e.text, ex, y, e.color, false);
                        y += LINE_HEIGHT;
                    }
                }
            } finally {
                GL11.glPopAttrib();
                GL11.glPopMatrix();
            }
        }

        /** A 1px horizontal line at y, spanning x1..x2, solid (HR alpha) in the middle and fading to
         *  fully transparent at both ends. */
        private void drawFadeDivider(int x1, int x2, int y, int color) {
            float a = ((color >> 24) & 0xFF) / 255.0f;
            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g = ((color >> 8) & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;
            float mid = (x1 + x2) / 2.0f;

            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glShadeModel(GL11.GL_SMOOTH);
            GL11.glBegin(GL11.GL_QUADS);
            // left half: transparent at x1 -> solid at mid
            GL11.glColor4f(r, g, b, 0.0f);
            GL11.glVertex2f(x1, y);
            GL11.glVertex2f(x1, y + 1);
            GL11.glColor4f(r, g, b, a);
            GL11.glVertex2f(mid, y + 1);
            GL11.glVertex2f(mid, y);
            // right half: solid at mid -> transparent at x2
            GL11.glColor4f(r, g, b, a);
            GL11.glVertex2f(mid, y);
            GL11.glVertex2f(mid, y + 1);
            GL11.glColor4f(r, g, b, 0.0f);
            GL11.glVertex2f(x2, y + 1);
            GL11.glVertex2f(x2, y);
            GL11.glEnd();
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        }

        private int computeHeight(int rowCount, int statusCount, int mutCount) {
            int h = TOP_PADDING + LINE_HEIGHT /* header */ + rowCount * LINE_HEIGHT;
            if (statusCount > 0) {
                h += STATUS_GAP + statusCount * LINE_HEIGHT;
            }
            if (mutCount > 0) {
                h += MUT_GAP + mutCount * LINE_HEIGHT;
            }
            return h + BOTTOM_MARGIN;
        }

        @SubscribeEvent
        public void onKeyInput(InputEvent.KeyInputEvent event) {
            // Fires in-world (no screen open). Handles the hotkeys.
            if (keyToggleRawNBT == null || keyCopyNBT == null || keyToggleMutations == null) {
                return;
            }
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer == null || mc.currentScreen != null) {
                return;
            }
            if (keyToggleRawNBT.isPressed()) {
                this.toggleDisplay(mc);
            }
            if (keyToggleMutations.isPressed()) {
                this.toggleMutations(mc);
            }
            if (keyCopyNBT.isPressed()) {
                this.copyNBT(mc, mc.thePlayer.getHeldItem());
            }
        }

        // Polled from the render event because KeyInputEvent doesn't fire while a GUI is open.
        private void handleKeyInputInGUI(Minecraft mc, ItemStack hoveredItem) {
            if (keyToggleRawNBT == null || keyCopyNBT == null || keyToggleMutations == null) {
                return;
            }

            if (Keyboard.isKeyDown(keyToggleRawNBT.getKeyCode())) {
                if (!wasTogglePressed) {
                    this.toggleDisplay(mc);
                    wasTogglePressed = true;
                }
            } else {
                wasTogglePressed = false;
            }

            if (Keyboard.isKeyDown(keyToggleMutations.getKeyCode())) {
                if (!wasMutTogglePressed) {
                    this.toggleMutations(mc);
                    wasMutTogglePressed = true;
                }
            } else {
                wasMutTogglePressed = false;
            }

            if (Keyboard.isKeyDown(keyCopyNBT.getKeyCode())) {
                if (!wasCopyPressed) {
                    // Prefer the item under the cursor; fall back to the held item.
                    ItemStack target = hoveredItem;
                    if (target == null && mc.thePlayer != null) {
                        target = mc.thePlayer.getHeldItem();
                    }
                    this.copyNBT(mc, target);
                    wasCopyPressed = true;
                }
            } else {
                wasCopyPressed = false;
            }
        }

        private void toggleDisplay(Minecraft mc) {
            isDisplayingRawNBT = !isDisplayingRawNBT;
            String state;
            if (isDisplayingRawNBT) {
                state = EnumChatFormatting.GREEN + "ON (Permanent)";
            } else if (alwaysShow) {
                state = EnumChatFormatting.GREEN + "ON (always-show config)";
            } else {
                String keyName = keyShowPanel != null ? Keyboard.getKeyName(keyShowPanel.getKeyCode()) : "Alt";
                state = EnumChatFormatting.RED + "OFF (Hold " + keyName + ")";
            }
            if (mc.thePlayer != null) {
                mc.thePlayer.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "Trait Display: " + state));
            }
        }

        private void toggleMutations(Minecraft mc) {
            showMutations = !showMutations;
            cachedStack = null; // force the panel to rebuild with/without mutations
            cachedPanel = null;
            if (mc.thePlayer != null) {
                String state = showMutations ? EnumChatFormatting.GREEN + "ON" : EnumChatFormatting.RED + "OFF";
                mc.thePlayer.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "Mutation Hints: " + state));
            }
        }

        private void copyNBT(Minecraft mc, ItemStack stack) {
            if (mc.thePlayer == null) {
                return;
            }
            if (stack != null && stack.hasTagCompound()) {
                GuiScreen.setClipboardString(stack.getTagCompound().toString());
                mc.thePlayer.addChatMessage(new ChatComponentText(
                        EnumChatFormatting.GREEN + "Copied FULL RAW NBT for " + stack.getDisplayName()));
            } else {
                mc.thePlayer.addChatMessage(new ChatComponentText(
                        EnumChatFormatting.YELLOW + "No NBT to copy."));
            }
        }

        // --- panel building (cached per hovered item) ---

        private PanelData getPanelCached(ItemStack stack) {
            if (cachedPanel != null && sameAsCached(stack)) {
                cachedStack = stack; // refresh reference for the fast path
                return cachedPanel;
            }
            PanelData data = this.buildPanel(stack);
            cachedStack = stack;
            cachedPanel = data;
            return data;
        }

        // NEI may hand us a fresh ItemStack copy each frame, so compare by item + meta + NBT,
        // not just reference identity.
        private boolean sameAsCached(ItemStack stack) {
            if (stack == cachedStack) {
                return true;
            }
            if (stack == null || cachedStack == null) {
                return false;
            }
            return stack.getItem() == cachedStack.getItem()
                    && stack.getItemDamage() == cachedStack.getItemDamage()
                    && ItemStack.areItemStackTagsEqual(stack, cachedStack);
        }

        // --- normal-tooltip suppression ---

        /** True if our panel is currently set to render (ALT held / toggled / always-show). */
        static boolean panelActive() {
            return alwaysShow || isDisplayingRawNBT || showKeyDown();
        }

        /** True if our genome panel would render for this item (bee/tree with a readable genome). */
        boolean wouldShowPanelFor(ItemStack stack) {
            return stack != null && !this.getPanelCached(stack).rows.isEmpty();
        }

        /** Whether the normal tooltip should be hidden for this item right now. Called by the mixin. */
        public static boolean shouldSuppressTooltip(ItemStack stack) {
            if (!hideNormalTooltip || !panelActive()) {
                return false;
            }
            ClientEvents inst = INSTANCE;
            return inst != null && inst.wouldShowPanelFor(stack);
        }

        /**
         * Fallback suppression for tooltip systems other than ChromaticTooltips (which is handled
         * fully by the mixin): clearing the lines makes the normal/NEI tooltip render nothing.
         */
        @SubscribeEvent
        public void onItemTooltip(ItemTooltipEvent event) {
            try {
                if (event.itemStack != null && shouldSuppressTooltip(event.itemStack)) {
                    event.toolTip.clear();
                }
            } catch (Throwable t) {
                // never break tooltips
            }
        }

        private PanelData buildPanel(ItemStack stack) {
            ISpeciesRoot root = AlleleManager.alleleRegistry.getSpeciesRoot(stack);
            if (root == null) {
                return EMPTY_PANEL;
            }
            IIndividual individual = root.getMember(stack);
            if (individual == null) {
                return EMPTY_PANEL;
            }

            List<TraitRow> allRows = this.getTraitData(root, individual);
            if (allRows.isEmpty()) {
                return EMPTY_PANEL;
            }

            // Pure-bred is decided from the full genome (genetic truth), regardless of hidden rows.
            boolean pure = true;
            for (TraitRow r : allRows) {
                if (r.isHeterozygous) {
                    pure = false;
                    break;
                }
            }

            List<TraitRow> visible;
            if (hiddenRows.isEmpty()) {
                visible = allRows;
            } else {
                visible = new ArrayList<TraitRow>();
                for (TraitRow r : allRows) {
                    if (!hiddenRows.contains(r.category.toLowerCase())) {
                        visible.add(r);
                    }
                }
            }

            List<ExtraLine> status = new ArrayList<ExtraLine>();
            if (showPureBadge && pure) {
                status.add(new ExtraLine("PURE-BRED", COLOR_MATCH_GREEN, true));
            }

            List<ExtraLine> mutations = new ArrayList<ExtraLine>();
            if (showMutations) {
                this.addMutationLines(mutations, root, individual.getGenome().getPrimary());
            }

            return new PanelData(visible, status, mutations);
        }

        private void addMutationLines(List<ExtraLine> mutationLines, ISpeciesRoot root, IAlleleSpecies species) {
            if (species == null) {
                return;
            }
            Collection<? extends IMutation> mutations = root.getMutations(false);
            if (mutations == null || mutations.isEmpty()) {
                return;
            }
            String speciesUID = species.getUID();

            List<IMutation> breedsInto = new ArrayList<IMutation>();
            List<IMutation> madeFrom = new ArrayList<IMutation>();
            for (IMutation m : mutations) {
                IAllele[] template = m.getTemplate();
                IAllele result = (template != null && template.length > 0) ? template[0] : null;
                if (m.isPartner(species) && result instanceof IAlleleSpecies) {
                    breedsInto.add(m);
                }
                if (result != null && speciesUID.equals(result.getUID())) {
                    madeFrom.add(m);
                }
            }

            this.appendMutationSection(mutationLines, "Breeds into:", BREEDS_INTO_COLOR, breedsInto, species, true);
            this.appendMutationSection(mutationLines, "Made from:", MADE_FROM_COLOR, madeFrom, species, false);
        }

        private void appendMutationSection(List<ExtraLine> out, String label, int color,
                List<IMutation> muts, IAlleleSpecies species, boolean breedsInto) {
            if (muts.isEmpty()) {
                return;
            }
            out.add(new ExtraLine(label, color, false));
            int shown = Math.min(muts.size(), maxMutations);
            for (int i = 0; i < shown; i++) {
                IMutation m = muts.get(i);
                out.add(new ExtraLine("  " + mutationText(m, species, breedsInto), color, false));
                // Special breeding requirements (e.g. "Requires <block> as a foundation", biome, etc.)
                Collection<String> conditions = m.getSpecialConditions();
                if (conditions != null) {
                    for (String c : conditions) {
                        if (c == null) {
                            continue;
                        }
                        String clean = EnumChatFormatting.getTextWithoutFormattingCodes(c).trim();
                        if (!clean.isEmpty()) {
                            out.add(new ExtraLine("    - " + clean, LABEL_GRAY, false));
                        }
                    }
                }
            }
            if (muts.size() > shown) {
                out.add(new ExtraLine("  +" + (muts.size() - shown) + " more", color, false));
            }
        }

        private String mutationText(IMutation m, IAlleleSpecies species, boolean breedsInto) {
            String chance = fmtChance(m.getBaseChance());
            if (breedsInto) {
                IAllele[] template = m.getTemplate();
                IAllele result = (template != null && template.length > 0) ? template[0] : null;
                IAlleleSpecies partner = m.getAllele0();
                if (partner != null && partner.getUID().equals(species.getUID())) {
                    partner = m.getAllele1();
                }
                String partnerName = partner != null ? partner.getName() : "?";
                String resultName = (result instanceof IAlleleSpecies) ? ((IAlleleSpecies) result).getName() : "?";
                return "+ " + partnerName + " -> " + resultName + "  " + chance;
            }
            String a = m.getAllele0() != null ? m.getAllele0().getName() : "?";
            String b = m.getAllele1() != null ? m.getAllele1().getName() : "?";
            return a + " x " + b + "  " + chance;
        }

        private String fmtChance(float c) {
            if (c == Math.floor(c)) {
                return ((int) c) + "%";
            }
            // Locale.US so the decimal separator is always '.' (some locales would use ',').
            return String.format(Locale.US, "%.1f%%", c);
        }

        // --- genome rows ---

        /** Dispatch: only bees and trees are supported (butterflies/others are ignored). */
        private List<TraitRow> getTraitData(ISpeciesRoot root, IIndividual individual) {
            if (BeeManager.beeRoot != null && root == BeeManager.beeRoot && individual instanceof IBee) {
                return getBeeTraitData((IBee) individual);
            }
            if (TreeManager.treeRoot != null && root == TreeManager.treeRoot) {
                return getTreeTraitData(individual);
            }
            return new ArrayList<TraitRow>();
        }

        /** Curated tree layout matching Forestry's Treealyzer (labels, order, value formatting). */
        private List<TraitRow> getTreeTraitData(IIndividual tree) {
            IGenome g = tree.getGenome();
            List<TraitRow> rows = new ArrayList<TraitRow>();
            addNamedRow(rows, g, EnumTreeChromosome.SPECIES, "Species");
            addNamedRow(rows, g, EnumTreeChromosome.FERTILITY, "Saplings");
            addNamedRow(rows, g, EnumTreeChromosome.MATURATION, "Matures");
            addNamedRow(rows, g, EnumTreeChromosome.HEIGHT, "Height");
            addRow(rows, g, EnumTreeChromosome.GIRTH, "Girth", FORMAT_GIRTH);
            addNamedRow(rows, g, EnumTreeChromosome.YIELD, "Yield");
            addNamedRow(rows, g, EnumTreeChromosome.SAPPINESS, "Sappiness");
            addRow(rows, g, EnumTreeChromosome.FIREPROOF, "Fireproof", FORMAT_YESNO);
            addNamedRow(rows, g, EnumTreeChromosome.EFFECT, "Effect");
            addRow(rows, g, EnumTreeChromosome.GROWTH, "Growth", FORMAT_GROWTH);
            addRow(rows, g, EnumTreeChromosome.FRUITS, "Fruits", FORMAT_FRUIT);
            return rows;
        }

        private List<TraitRow> getBeeTraitData(IBee bee) {
            IGenome g = bee.getGenome();
            List<TraitRow> traits = new ArrayList<TraitRow>();
            int displayIndex = 0;
            for (int nbtIndex : DISPLAY_ORDER_INDICES) {
                if (nbtIndex >= NBT_INDEX_TO_CHROMOSOME.length) {
                    ++displayIndex;
                    continue;
                }
                EnumBeeChromosome chromosome = NBT_INDEX_TO_CHROMOSOME[nbtIndex];
                IAllele active = g.getActiveAllele(chromosome);
                IAllele inactive = g.getInactiveAllele(chromosome);
                if (active != null && inactive != null) {
                    String activeTrait;
                    String inactiveTrait;
                    if (chromosome == EnumBeeChromosome.FERTILITY
                            && active instanceof IAlleleInteger && inactive instanceof IAlleleInteger) {
                        activeTrait = "x" + ((IAlleleInteger) active).getValue();
                        inactiveTrait = "x" + ((IAlleleInteger) inactive).getValue();
                    } else {
                        activeTrait = getAlleleDisplay(active);
                        inactiveTrait = getAlleleDisplay(inactive);
                    }
                    String label = displayIndex < TRAIT_CATEGORIES_DISPLAY.length
                            ? TRAIT_CATEGORIES_DISPLAY[displayIndex]
                            : chromosome.toString();
                    traits.add(new TraitRow(label, activeTrait, inactiveTrait, heterozygous(active, inactive)));
                }
                ++displayIndex;
            }
            return traits;
        }

        // --- value formatters ---
        private interface AlleleFormatter {
            String format(IAllele allele);
        }

        private final AlleleFormatter FORMAT_NAME = new AlleleFormatter() {
            public String format(IAllele a) {
                return getAlleleDisplay(a);
            }
        };
        private final AlleleFormatter FORMAT_GIRTH = new AlleleFormatter() {
            public String format(IAllele a) {
                if (a instanceof IAlleleInteger) {
                    int v = ((IAlleleInteger) a).getValue();
                    return v + "x" + v;
                }
                return getAlleleDisplay(a);
            }
        };
        private final AlleleFormatter FORMAT_YESNO = new AlleleFormatter() {
            public String format(IAllele a) {
                if (a instanceof IAlleleBoolean) {
                    return ((IAlleleBoolean) a).getValue() ? "Yes" : "No";
                }
                return getAlleleDisplay(a);
            }
        };
        private final AlleleFormatter FORMAT_GROWTH = new AlleleFormatter() {
            public String format(IAllele a) {
                if (a instanceof IAlleleGrowth) {
                    return ((IAlleleGrowth) a).getProvider().getDescription();
                }
                return getAlleleDisplay(a);
            }
        };
        // Show just the fruit's name; modded fruits (e.g. ExtraTrees) expose an unlocalized key
        // like "extratrees.item.food.beechnut" -> fall back to the last segment, capitalized.
        private final AlleleFormatter FORMAT_FRUIT = new AlleleFormatter() {
            public String format(IAllele a) {
                if (a instanceof IAlleleFruit) {
                    String name = StatCollector.translateToLocal(((IAlleleFruit) a).getProvider().getDescription());
                    int dot = name.lastIndexOf('.');
                    if (dot >= 0 && dot < name.length() - 1) {
                        name = name.substring(dot + 1);
                        name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
                    }
                    return name;
                }
                return getAlleleDisplay(a);
            }
        };

        private void addNamedRow(List<TraitRow> rows, IGenome g, IChromosomeType type, String label) {
            addRow(rows, g, type, label, FORMAT_NAME);
        }

        private void addRow(List<TraitRow> rows, IGenome g, IChromosomeType type, String label, AlleleFormatter fmt) {
            IAllele active = g.getActiveAllele(type);
            IAllele inactive = g.getInactiveAllele(type);
            if (active == null || inactive == null) {
                return;
            }
            rows.add(new TraitRow(label, fmt.format(active), fmt.format(inactive), heterozygous(active, inactive)));
        }

        private String getAlleleDisplay(IAllele allele) {
            if (allele == null) {
                return "";
            }
            if (allele instanceof IAlleleBoolean) {
                return ((IAlleleBoolean) allele).getValue() ? "True" : "False";
            }
            return allele.getName();
        }

        // Pure (homozygous) vs hybrid (heterozygous) is decided by allele identity, not display text.
        private boolean heterozygous(IAllele active, IAllele inactive) {
            if (active == null || inactive == null) {
                return active != inactive;
            }
            return !active.getUID().equals(inactive.getUID());
        }
    }

    /**
     * Optional NotEnoughItems integration. Loaded lazily via reflection so the mod has no
     * hard dependency on NEI and still works without it. {@code getStackMouseOver} returns the
     * item under the cursor across NEI's item panel, NEI GUIs, and container slots.
     */
    public static class NEICompat {
        private static boolean resolved = false;
        private static java.lang.reflect.Method getStackMouseOver;

        static ItemStack stackUnderMouse(GuiContainer gui) {
            if (!resolved) {
                resolved = true;
                try {
                    Class<?> cls = Class.forName("codechicken.nei.guihook.GuiContainerManager");
                    getStackMouseOver = cls.getMethod("getStackMouseOver", GuiContainer.class);
                    logger.info("[Forestry Genome Tooltip] NEI detected; item-panel support enabled.");
                } catch (Throwable t) {
                    getStackMouseOver = null; // NEI absent or incompatible; silently disabled.
                }
            }
            if (getStackMouseOver == null) {
                return null;
            }
            try {
                return (ItemStack) getStackMouseOver.invoke(null, gui);
            } catch (Throwable t) {
                return null;
            }
        }
    }

    /** A single genome row (3 columns). */
    public static class TraitRow {
        public final String category;
        public final String active;
        public final String inactive;
        public final boolean isHeterozygous;

        public TraitRow(String category, String active, String inactive, boolean heterozygous) {
            this.category = category;
            this.active = active;
            this.inactive = inactive;
            this.isHeterozygous = heterozygous;
        }
    }

    /** A full-width extra line below the genome table (pure badge / mutation hint). */
    public static class ExtraLine {
        public final String text;
        public final int color;
        public final boolean center;

        public ExtraLine(String text, int color, boolean center) {
            this.text = text;
            this.color = color;
            this.center = center;
        }
    }

    /** Cached, fully-built panel content for one item. */
    public static class PanelData {
        public final List<TraitRow> rows;
        public final List<ExtraLine> statusLines;   // pure-bred badge etc. (grouped with the genome)
        public final List<ExtraLine> mutationLines;  // breeding mutations (below their own divider)

        public PanelData(List<TraitRow> rows, List<ExtraLine> statusLines, List<ExtraLine> mutationLines) {
            this.rows = rows;
            this.statusLines = statusLines;
            this.mutationLines = mutationLines;
        }
    }
}
