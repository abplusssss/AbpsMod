package dev.abps.client;

import dev.abps.net.Net;
import dev.abps.util.Text;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The inside of one player shop. Visitors buy what's for sale and sell what's wanted, choosing how many by typing
 * or with the step buttons. The owner adds listings from their inventory, restocks, changes prices and collects.
 * Nothing here moves items itself: every button asks the server, which checks and answers with the new shop.
 */
public final class ShopScreen extends Screen {

    private static final int GREEN = 0x69F0AE, GOLD = 0xFFD54F, RED = 0xFF5252, BLUE = 0x00E5FF;
    /** Common things to price in. Any item works: right-click something in your inventory to use it instead. */
    private static final String[] CURRENCIES = {"minecraft:diamond", "minecraft:emerald", "minecraft:gold_ingot", "minecraft:iron_ingot",
            "minecraft:netherite_ingot", "minecraft:netherite_scrap", "minecraft:lapis_lazuli", "minecraft:amethyst_shard"};

    private record Btn(int x, int y, int w, int h, Runnable action) {
    }

    private final List<Btn> buttons = new ArrayList<>();
    private int px, py, pw, ph;
    private double scroll;
    private int contentHeight, listTop, listHeight;
    private final long openedAt = System.currentTimeMillis();

    /** "" for none, or trade, manage, add, rename, close. */
    private String popup = "";
    private int listingId = -1;
    private final TextInput qty = new TextInput(7, true);
    private final TextInput second = new TextInput(7, true);
    private final TextInput priceInput = new TextInput(4, true);
    private final TextInput nameInput = new TextInput(24, false);
    // Add-listing choices
    private int pickSlot = -1;
    private boolean addSelling = true;
    private int bundle = 1;
    private ItemStack currency = new ItemStack(item("minecraft:diamond"));

    public ShopScreen() {
        super(Component.literal("Shop"));
    }

    @Override
    protected void init() {
        pw = Math.min(width - 16, 440);
        ph = Math.min(height - 16, 280);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static Item item(String id) {
        var i = net.minecraft.resources.Identifier.tryParse(id);
        Item it = i == null ? null : BuiltInRegistries.ITEM.getValue(i);
        return it == null ? net.minecraft.world.item.Items.DIAMOND : it;
    }

    private static String id(ItemStack s) {
        return BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
    }

    private void button(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, int h, String label, int color, boolean enabled, Runnable action) {
        boolean hover = enabled && Draw.inside(mx, my, x, y, w, h);
        int border = enabled ? Draw.opaque(hover ? Text.lerp(color, 0xFFFFFF, 0.35f) : color) : 0xFF3A3A44;
        int fill = enabled ? Draw.argb(color, hover ? 0x70 : 0x38) : 0xC0202028;
        Draw.framed(g, x, y, w, h, fill, border);
        Draw.centered(g, enabled ? label : "<dark_gray>" + Text.strip(label), x + w / 2, y + (h - 8) / 2);
        if (enabled) buttons.add(new Btn(x, y, w, h, action));
    }

    // ---- Inventory counting, the same way the server does it (main inventory only) ----

    private static Inventory inv() {
        return Minecraft.getInstance().player.getInventory();
    }

    private static int countSame(ItemStack template) {
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv().getItem(i);
            if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, template)) n += s.getCount();
        }
        return n;
    }

    private static int countCurrency(ItemStack currency) {
        return countSame(new ItemStack(currency.getItem()));
    }

    private static int room(ItemStack template) {
        int max = template.getMaxStackSize(), room = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv().getItem(i);
            if (s.isEmpty()) room += max;
            else if (ItemStack.isSameItemSameComponents(s, template)) room += Math.max(0, max - s.getCount());
        }
        return room;
    }

    private Net.ShopListing listing() {
        Net.ShopPayload shop = ClientState.shop;
        if (shop == null) return null;
        for (Net.ShopListing l : shop.listings()) if (l.id() == listingId) return l;
        return null;
    }

    /** Most items a visitor can trade in one go for this listing. Always a whole number of bundles. */
    private static int maxTrade(Net.ShopListing l) {
        int bundles;
        if (l.selling()) {
            int afford = countCurrency(l.priceItem()) / l.price();
            bundles = Math.min(Math.min(l.stock() / l.bundle(), afford), room(l.item()) / l.bundle());
        } else {
            int have = countSame(l.item()) / l.bundle();
            bundles = Math.min(have, l.funds() / l.price());
        }
        return Math.max(0, bundles) * l.bundle();
    }

    private static int snap(int items, int bundle, int max) {
        int b = Math.max(1, bundle);
        int v = Math.max(0, Math.min(items, max));
        return v / b * b;
    }

    // ================= Drawing =================

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        super.extractRenderState(g, mx, my, pt);
        buttons.clear();
        Net.ShopPayload shop = ClientState.shop;
        float open = Math.min(1f, (System.currentTimeMillis() - openedAt) / 180f);
        g.pose().pushMatrix();
        g.pose().translate(0, (1 - open) * 10);

        int c1 = 0x2E7D5B, c2 = GREEN;
        Draw.panel(g, px - 1, py - 1, pw + 2, ph + 2, Draw.argb(c2, 0x60));
        Draw.panel(g, px, py, pw, ph, 0xF00C0C12);
        Draw.hGradient(g, px + 1, py, pw - 2, 2, Draw.opaque(c1), Draw.opaque(BLUE));
        Draw.hGradient(g, px, py + 2, pw, 30, Draw.argb(c2, 0x28), Draw.argb(BLUE, 0x06));
        boolean popupOpen = !popup.isEmpty();
        int mmx = popupOpen ? -1 : mx, mmy = popupOpen ? -1 : my;

        button(g, mmx, mmy, px + 8, py + 8, 52, 16, "<white>← Shops", 0x7C4DFF, true,
                () -> Minecraft.getInstance().gui.setScreen(new MenuScreen("shops")));
        if (shop == null) {
            Draw.centered(g, "<gray>Loading...", px + pw / 2, py + ph / 2);
            g.pose().popMatrix();
            return;
        }
        g.item(shop.icon(), px + 68, py + 8);
        Draw.textFit(g, "<white><bold>" + shop.name(), px + 88, py + 7, pw - 96 - 8);
        Draw.scaled(g, "<gray>by " + shop.ownerName() + " · " + shop.sales() + " sales · " + shop.listings().size() + "/" + shop.maxListings() + " listings",
                px + 88, py + 18, 0.75f, false);

        int y = py + 36;
        if (shop.mine()) {
            // Owner toolbar
            int waiting = shop.collected();
            for (int c : shop.earningCounts()) waiting += c;
            int bx = px + 8;
            button(g, mmx, mmy, bx, y, 92, 18, "<white><bold>+ Add listing", GREEN, shop.listings().size() < shop.maxListings(), () -> openAdd());
            bx += 96;
            button(g, mmx, mmy, bx, y, 92, 18, (waiting > 0 ? "<gold><bold>" : "<white>") + "Collect (" + waiting + ")", GOLD, waiting > 0,
                    () -> AbpsClient.send("shop", "collect"));
            bx += 96;
            button(g, mmx, mmy, bx, y, 60, 18, "<white>Rename", BLUE, true, () -> {
                nameInput.set(shop.name());
                nameInput.focus(true);
                popup = "rename";
            });
            bx += 64;
            button(g, mmx, mmy, bx, y, 60, 18, "<white>Set icon", BLUE, true, () -> AbpsClient.send("shop", "icon|" + inv().getSelectedSlot()));
            button(g, mmx, mmy, px + pw - 76, y, 68, 18, "<#FF5252>Close shop", RED, true, () -> popup = "close");
            y += 22;
            if (waiting > 0) {
                StringBuilder sb = new StringBuilder("<gray>Waiting to collect: ");
                for (int k = 0; k < shop.earnings().size(); k++) {
                    sb.append("<white>").append(shop.earningCounts().get(k)).append(" ").append(shop.earnings().get(k).getHoverName().getString()).append("<gray>, ");
                }
                if (shop.collected() > 0) sb.append("<white>").append(shop.collected()).append(" bought items");
                String line = sb.toString();
                if (line.endsWith(", ")) line = line.substring(0, line.length() - 2);
                Draw.scaled(g, line, px + 8, y, 0.75f, false);
                y += 10;
            }
            Draw.scaled(g, "<dark_gray>Set icon uses the item in your hand.", px + pw - 8 - Draw.font().width("Set icon uses the item in your hand.") * 0.75f, py + 26, 0.75f, false);
        }

        // Listings
        listTop = y + 2;
        listHeight = py + ph - 8 - listTop;
        g.enableScissor(px + 6, listTop, px + pw - 6, listTop + listHeight);
        int cols = 2, gap = 6, cardW = (pw - 16 - gap) / cols, cardH = 46;
        int ly = listTop - (int) scroll;
        if (shop.listings().isEmpty()) {
            Draw.centered(g, shop.mine() ? "<gray>Your shop is empty. Click <white>+ Add listing</white> to put something up."
                    : "<gray>This shop has nothing listed yet.", px + pw / 2, listTop + 20);
        }
        for (int k = 0; k < shop.listings().size(); k++) {
            Net.ShopListing l = shop.listings().get(k);
            int x = px + 8 + (k % cols) * (cardW + gap), yy = ly + (k / cols) * (cardH + 4);
            card(g, mmx, mmy, l, x, yy, cardW, cardH, shop.mine());
        }
        contentHeight = ((shop.listings().size() + cols - 1) / cols) * (cardH + 4);
        g.disableScissor();
        buttons.removeIf(b -> b.y >= listTop - 1 && (b.y + b.h <= listTop || b.y >= listTop + listHeight));
        if (contentHeight > listHeight) {
            int barH = Math.max(12, listHeight * listHeight / contentHeight);
            int barY = listTop + (int) ((listHeight - barH) * (scroll / Math.max(1, contentHeight - listHeight)));
            g.fill(px + pw - 5, listTop, px + pw - 3, listTop + listHeight, 0x30FFFFFF);
            g.fill(px + pw - 5, barY, px + pw - 3, barY + barH, Draw.opaque(GREEN));
        }

        if (popupOpen) {
            buttons.clear();
            g.fill(px, py, px + pw, py + ph, 0xA0000000);
            switch (popup) {
                case "trade" -> tradePopup(g, mx, my, shop);
                case "manage" -> managePopup(g, mx, my);
                case "add" -> addPopup(g, mx, my, shop);
                case "rename" -> renamePopup(g, mx, my);
                case "close" -> closePopup(g, mx, my);
                default -> popup = "";
            }
        }
        g.pose().popMatrix();
    }

    private void card(GuiGraphicsExtractor g, int mx, int my, Net.ShopListing l, int x, int y, int w, int h, boolean mine) {
        boolean hover = Draw.inside(mx, my, x, y, w, h) && my >= listTop && my < listTop + listHeight;
        int accent = l.selling() ? GREEN : GOLD;
        boolean out = l.selling() ? l.stock() < l.bundle() : l.funds() < l.price();
        Draw.framed(g, x, y, w, h, hover ? 0xF0181824 : 0xE0101016, hover ? Draw.opaque(accent) : Draw.argb(accent, 0x70));
        Draw.framed(g, x + 5, y + 5, 36, 36, 0xFF08080C, Draw.argb(accent, 0x60));
        g.pose().pushMatrix();
        g.pose().translate(x + 7, y + 7);
        g.pose().scale(2f, 2f);
        g.item(l.item(), 0, 0);
        g.pose().popMatrix();
        if (l.bundle() > 1) Draw.text(g, "<white><bold>x" + l.bundle(), x + 42 - Draw.font().width("x" + l.bundle()) - 2, y + 32);
        String name = l.item().getHoverName().getString();
        int maxW = w - 54;
        if (Draw.font().width(name) > maxW) name = Draw.font().plainSubstrByWidth(name, maxW - 6) + "…";
        Draw.text(g, "<white>" + name, x + 46, y + 5);
        String tag = l.selling() ? "<#69F0AE><bold>FOR SALE" : "<gold><bold>WANTED";
        Draw.scaled(g, tag, x + 46, y + 16, 0.75f, false);
        // Price: currency icon and how many
        g.pose().pushMatrix();
        g.pose().translate(x + 46, y + 25);
        g.pose().scale(0.75f, 0.75f);
        g.item(l.priceItem(), 0, 0);
        g.pose().popMatrix();
        Draw.text(g, "<white>" + l.price() + " <gray>" + (l.bundle() > 1 ? "per " + l.bundle() : "each"), x + 60, y + 27);
        String avail = l.selling() ? (out ? "<#FF5252>Sold out" : "<gray>Stock <white>" + l.stock())
                : (out ? "<#FF5252>Not buying" : "<gray>Wants <white>" + (l.funds() / l.price() * l.bundle()));
        Draw.scaled(g, avail, x + w - 6 - Draw.font().width(Text.strip(avail)) * 0.75f, y + 16, 0.75f, false);
        if (hover) {
            List<Component> tip = Screen.getTooltipFromItem(Minecraft.getInstance(), l.item());
            List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>();
            for (Component c : tip) lines.add(c.getVisualOrderText());
            lines.add(Text.mm(mine ? "<yellow>Click to manage" : l.selling() ? "<#69F0AE>Click to buy" : "<gold>Click to sell").getVisualOrderText());
            g.setTooltipForNextFrame(lines, mx, my);
        }
        final int id = l.id();
        buttons.add(new Btn(x, y, w, h, () -> {
            listingId = id;
            if (mine) {
                qty.set("");
                second.set("");
                priceInput.setNumber(l.price());
                bundle = l.bundle();
                currency = l.priceItem().copy();
                popup = "manage";
            } else {
                qty.setNumber(Math.min(l.bundle(), Math.max(l.bundle(), maxTrade(l))));
                qty.focus(false);
                popup = "trade";
            }
        }));
    }

    /** A box in the middle of the window for a popup. Returns {x, y, w, h}. */
    private int[] box(GuiGraphicsExtractor g, int w, int h, int color) {
        w = Math.min(w, pw - 20);
        h = Math.min(h, ph - 16);
        int x = px + (pw - w) / 2, y = py + (ph - h) / 2;
        Draw.panel(g, x - 1, y - 1, w + 2, h + 2, Draw.argb(color, 0x90));
        Draw.panel(g, x, y, w, h, 0xF8101016);
        Draw.hGradient(g, x + 1, y, w - 2, 2, Draw.opaque(color), Draw.opaque(Text.lerp(color, 0xFFFFFF, 0.5f)));
        return new int[]{x, y, w, h};
    }

    /** The amount chooser: - buttons, a number you can type, + buttons and Max. Changes qty in whole bundles. */
    private int stepper(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, TextInput field, int unit, int max, int color) {
        int[] steps = unit == 1 ? new int[]{64, 10, 1} : new int[]{16, 4, 1};
        int bw = 30, gapW = 2;
        int fieldW = w - 6 * (bw + gapW) - 40;
        int bx = x;
        for (int s : steps) {
            final int d = s * unit;
            button(g, mx, my, bx, y, bw, 18, "<white>-" + d, color, true, () -> field.setNumber(snap(field.number(0) - d, unit, max)));
            bx += bw + gapW;
        }
        field.draw(g, bx, y, fieldW, 18, color, "0");
        bx += fieldW + gapW;
        for (int k = steps.length - 1; k >= 0; k--) {
            final int d = steps[k] * unit;
            button(g, mx, my, bx, y, bw, 18, "<white>+" + d, color, true, () -> field.setNumber(snap(field.number(0) + d, unit, max)));
            bx += bw + gapW;
        }
        button(g, mx, my, bx, y, 38, 18, "<white><bold>Max", color, max > 0, () -> field.setNumber(snap(max, unit, max)));
        return y + 22;
    }

    private void tradePopup(GuiGraphicsExtractor g, int mx, int my, Net.ShopPayload shop) {
        Net.ShopListing l = listing();
        if (l == null) {
            popup = "";
            return;
        }
        int color = l.selling() ? GREEN : GOLD;
        int[] b = box(g, 330, 168, color);
        int x = b[0] + 10, y = b[1] + 8, w = b[2] - 20;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(2f, 2f);
        g.item(l.item(), 0, 0);
        g.pose().popMatrix();
        Draw.textFit(g, "<white><bold>" + (l.selling() ? "Buy " : "Sell ") + l.item().getHoverName().getString(), x + 40, y + 3, w - 40);
        Draw.textFit(g, "<gray>" + l.price() + " " + l.priceItem().getHoverName().getString() + (l.bundle() > 1 ? " for every " + l.bundle() : " each")
                + (l.selling() ? "  ·  " + l.stock() + " in stock" : "  ·  wants " + (l.funds() / l.price() * l.bundle())), x + 40, y + 16, w - 40);
        y += 40;

        int max = maxTrade(l);
        Draw.text(g, "<gray>How many?", x, y);
        y += 11;
        y = stepper(g, mx, my, x, y, w, qty, l.bundle(), max, color);
        int amount = snap(qty.number(0), l.bundle(), Integer.MAX_VALUE);
        int bundles = amount / l.bundle();
        long cost = (long) bundles * l.price();
        String money = l.priceItem().getHoverName().getString();
        if (l.selling()) {
            int have = countCurrency(l.priceItem());
            Draw.textFit(g, "<gray>You pay <white><bold>" + cost + " " + money + "</bold></white>  <dark_gray>(you have " + (have >= cost ? "<#69F0AE>" : "<#FF5252>") + have + "<dark_gray>)", x, y + 2, w);
        } else {
            int have = countSame(l.item());
            Draw.textFit(g, "<gray>You get <white><bold>" + cost + " " + money + "</bold></white>  <dark_gray>(you have " + (have >= amount ? "<#69F0AE>" : "<#FF5252>") + have
                    + " <dark_gray>to sell)", x, y + 2, w);
        }
        if (amount % Math.max(1, l.bundle()) != 0 || (qty.number(0) != amount && !qty.focused())) qty.setNumber(amount);
        if (l.bundle() > 1) Draw.scaled(g, "<dark_gray>Sold in bundles of " + l.bundle() + ", so amounts round down to a whole bundle.", x, y + 13, 0.75f, false);
        boolean ok = bundles > 0 && amount <= max;
        String why = bundles <= 0 ? "" : amount > max ? (l.selling() ? "Not enough money, stock or room" : "Not enough items or the shop can't pay") : "";
        if (!why.isEmpty()) Draw.text(g, "<#FF5252>" + why, x, y + 24);
        int by = b[1] + b[3] - 26;
        button(g, mx, my, x, by, (w - 8) / 2, 18, "<white><bold>" + (l.selling() ? "Buy " : "Sell ") + amount, color, ok, () -> {
            AbpsClient.send("shop", "trade|" + shop.owner() + "|" + l.id() + "|" + bundles);
            popup = "";
        });
        button(g, mx, my, x + (w - 8) / 2 + 8, by, (w - 8) / 2, 18, "Cancel", 0x777781, true, () -> popup = "");
    }

    private void managePopup(GuiGraphicsExtractor g, int mx, int my) {
        Net.ShopListing l = listing();
        if (l == null) {
            popup = "";
            return;
        }
        int color = l.selling() ? GREEN : GOLD;
        int[] b = box(g, 360, 206, color);
        int x = b[0] + 10, y = b[1] + 8, w = b[2] - 20;
        g.item(l.item(), x, y);
        Draw.textFit(g, "<white><bold>" + l.item().getHoverName().getString() + "</bold> <gray>" + (l.selling() ? "for sale" : "wanted"), x + 20, y + 4, w - 20);
        y += 20;
        int half = (w - 6) / 2;
        if (l.selling()) {
            int have = countSame(l.item());
            Draw.textFit(g, "<gray>In stock <white>" + l.stock() + "</white>   In your inventory <white>" + have, x, y, w);
            y += 11;
            y = stepper(g, mx, my, x, y, w, qty, 1, Math.max(have, l.stock()), GREEN);
            int n = qty.number(0);
            button(g, mx, my, x, y, half, 18, "<white>Add " + n + " to stock", GREEN, n > 0 && n <= have,
                    () -> AbpsClient.send("shop", "restock|" + l.id() + "|" + qty.number(0)));
            button(g, mx, my, x + half + 6, y, half, 18, "<white>Take " + Math.min(n, l.stock()) + " back", BLUE, n > 0 && l.stock() > 0,
                    () -> AbpsClient.send("shop", "withdraw|" + l.id() + "|" + Math.min(qty.number(0), l.stock())));
        } else {
            int canBuy = l.funds() / l.price();
            int afford = countCurrency(l.priceItem()) / l.price();
            String money = l.priceItem().getHoverName().getString();
            Draw.textFit(g, "<gray>Paid up for <white>" + canBuy + "</white> more trades <dark_gray>(" + l.funds() + " " + money + ")", x, y, w);
            y += 11;
            y = stepper(g, mx, my, x, y, w, qty, 1, afford, GOLD);
            int n = qty.number(0);
            button(g, mx, my, x, y, half, 18, "<white>Pay for " + n + " more", GOLD, n > 0 && n <= afford,
                    () -> AbpsClient.send("shop", "restock|" + l.id() + "|" + qty.number(0)));
            button(g, mx, my, x + half + 6, y, half, 18, "<white>Refund " + l.funds() + " " + money, BLUE, l.funds() > 0,
                    () -> AbpsClient.send("shop", "withdraw|" + l.id() + "|" + l.funds()));
        }
        y += 26;
        Draw.text(g, "<gray>Price", x, y);
        y += 11;
        y = pricePicker(g, mx, my, x, y, w);
        int by = b[1] + b[3] - 26;
        button(g, mx, my, x, by, 104, 18, "<#FF5252>Remove listing", RED, true, () -> {
            AbpsClient.send("shop", "remove|" + l.id());
            popup = "";
        });
        button(g, mx, my, x + 110, by, 104, 18, "<white><bold>Save price", BLUE, priceInput.number(0) > 0,
                () -> AbpsClient.send("shop", "price|" + l.id() + "|" + bundle + "|" + id(currency) + "|" + priceInput.number(1)));
        button(g, mx, my, x + w - 70, by, 70, 18, "Done", 0x777781, true, () -> popup = "");
    }

    /** Price amount, bundle size and currency in two rows. Right-clicking an inventory item (in the add popup) sets any currency. */
    private int pricePicker(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w) {
        priceInput.draw(g, x, y, 40, 18, BLUE, "1");
        g.item(currency, x + 44, y + 1);
        String cname = currency.getHoverName().getString();
        int nameW = Math.min(Draw.font().width(cname), w - 190);
        Draw.text(g, "<white>" + Draw.font().plainSubstrByWidth(cname, Math.max(20, nameW)), x + 62, y + 5);
        int bx = x + w - 124;
        Draw.text(g, "<gray>per", bx, y + 5);
        button(g, mx, my, bx + 20, y, 18, 18, "<white>-", BLUE, bundle > 1, () -> bundle = Math.max(1, bundle > 8 ? bundle / 2 : bundle - 1));
        Draw.centered(g, "<white><bold>" + bundle, bx + 52, y + 5);
        button(g, mx, my, bx + 66, y, 18, 18, "<white>+", BLUE, bundle < 64, () -> bundle = Math.min(64, bundle >= 8 ? bundle * 2 : bundle + 1));
        Draw.text(g, "<gray>item" + (bundle > 1 ? "s" : ""), bx + 88, y + 5);
        y += 21;
        int cx = x;
        for (String cid : CURRENCIES) {
            ItemStack st = new ItemStack(item(cid));
            boolean on = st.getItem() == currency.getItem();
            boolean hover = Draw.inside(mx, my, cx, y, 20, 20);
            Draw.framed(g, cx, y, 20, 20, on ? Draw.argb(BLUE, 0x60) : hover ? 0x40FFFFFF : 0xE0101016, on ? Draw.opaque(BLUE) : 0xFF33333D);
            g.item(st, cx + 2, y + 2);
            buttons.add(new Btn(cx, y, 20, 20, () -> currency = st));
            if (hover) g.setTooltipForNextFrame(st.getHoverName(), mx, my);
            cx += 22;
        }
        return y + 24;
    }

    private void openAdd() {
        pickSlot = -1;
        addSelling = true;
        bundle = 1;
        currency = new ItemStack(item("minecraft:diamond"));
        priceInput.setNumber(1);
        qty.set("");
        popup = "add";
    }

    private void addPopup(GuiGraphicsExtractor g, int mx, int my, Net.ShopPayload shop) {
        int[] b = box(g, 400, 214, GREEN);
        int x = b[0] + 10, y = b[1] + 8, w = b[2] - 20;
        Draw.text(g, "<white><bold>Add a listing", x, y);
        Draw.scaled(g, "<gray>Left-click the item to list. Right-click any item to price in it.", x + 80, y + 1, 0.75f, false);
        y += 13;
        // Left: your inventory, hotbar at the bottom like the real one
        int slotW = 18, gridTop = y;
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                int slot = row < 3 ? 9 + row * 9 + col : col;
                int sx = x + col * slotW, sy = gridTop + row * slotW + (row == 3 ? 3 : 0);
                ItemStack st = inv().getItem(slot);
                boolean picked = slot == pickSlot;
                boolean hover = Draw.inside(mx, my, sx, sy, slotW - 1, slotW - 1);
                g.fill(sx, sy, sx + slotW - 1, sy + slotW - 1, picked ? Draw.argb(GREEN, 0x80) : hover ? 0x50FFFFFF : 0xC0060609);
                if (!st.isEmpty()) {
                    g.item(st, sx, sy);
                    g.itemDecorations(Draw.font(), st, sx, sy);
                    final int s = slot;
                    buttons.add(new Btn(sx, sy, slotW - 1, slotW - 1, () -> pickSlot = s));
                    if (hover) g.setTooltipForNextFrame(st.getHoverName(), mx, my);
                }
            }
        }
        // Right: what was picked, sell or buy, and the price
        int rx = x + 9 * slotW + 10, rw = x + w - rx;
        ItemStack picked = pickSlot >= 0 ? inv().getItem(pickSlot) : ItemStack.EMPTY;
        int ry = gridTop;
        if (picked.isEmpty()) {
            Draw.wrapped(g, "<gray>Pick something from your inventory.", rx, ry + 4, rw, Draw.MUTED);
            ry += 20;
        } else {
            g.item(picked, rx, ry);
            String name = Draw.font().plainSubstrByWidth(picked.getHoverName().getString(), rw - 22);
            Draw.text(g, "<white>" + name, rx + 20, ry + 4);
            ry += 20;
        }
        int bw = (rw - 4) / 2;
        button(g, mx, my, rx, ry, bw, 18, (addSelling ? "<white><bold>" : "<gray>") + "Sell it", GREEN, true, () -> addSelling = true);
        button(g, mx, my, rx + bw + 4, ry, bw, 18, (!addSelling ? "<white><bold>" : "<gray>") + "Buy it", GOLD, true, () -> addSelling = false);
        ry += 21;
        Draw.scaled(g, addSelling ? "<dark_gray>You stock it, players buy it." : "<dark_gray>You pay up front, players sell it to you.", rx, ry, 0.75f, false);
        int y2 = gridTop + 4 * slotW + 7;
        y2 = pricePicker(g, mx, my, x, y2, w);

        int unit = Math.max(1, bundle);
        int max = 0;
        if (!picked.isEmpty()) max = addSelling ? countSame(picked) : countCurrency(currency) / Math.max(1, priceInput.number(1)) * unit;
        Draw.text(g, addSelling ? "<gray>How many to put in the shop" : "<gray>How many you want to buy", x, y2);
        if (!addSelling && qty.number(0) >= unit) {
            long cost = (long) (qty.number(0) / unit) * priceInput.number(1);
            String note = "<dark_gray>costs " + cost + " " + currency.getHoverName().getString() + " now, unspent money comes back";
            Draw.scaled(g, note, x + w - Draw.font().width(Text.strip(note)) * 0.75f, y2 + 1, 0.75f, false);
        }
        y2 = stepper(g, mx, my, x, y2 + 10, w, qty, unit, max, GREEN);
        int amount = snap(qty.number(0), unit, Integer.MAX_VALUE);
        int by = Math.max(y2 + 2, b[1] + b[3] - 26);
        boolean ok = !picked.isEmpty() && priceInput.number(0) > 0 && amount <= max && (addSelling ? amount > 0 : amount >= unit);
        final int amt = amount;
        button(g, mx, my, x, by, (w - 8) / 2, 18, "<white><bold>List it", GREEN, ok, () -> {
            int send = addSelling ? amt : amt / unit;
            AbpsClient.send("shop", "add|" + pickSlot + "|" + (addSelling ? 1 : 0) + "|" + bundle + "|" + id(currency) + "|" + priceInput.number(1) + "|" + send);
            popup = "";
        });
        button(g, mx, my, x + (w - 8) / 2 + 8, by, (w - 8) / 2, 18, "Cancel", 0x777781, true, () -> popup = "");
    }

    private void renamePopup(GuiGraphicsExtractor g, int mx, int my) {
        int[] b = box(g, 260, 84, BLUE);
        int x = b[0] + 10, y = b[1] + 10, w = b[2] - 20;
        Draw.text(g, "<white><bold>Rename your shop", x, y);
        nameInput.draw(g, x, y + 16, w, 18, BLUE, "Shop name");
        int by = b[1] + b[3] - 26;
        button(g, mx, my, x, by, (w - 8) / 2, 18, "<white><bold>Save", BLUE, !nameInput.text().isBlank(), () -> {
            AbpsClient.send("shop", "rename|" + nameInput.text().trim());
            popup = "";
        });
        button(g, mx, my, x + (w - 8) / 2 + 8, by, (w - 8) / 2, 18, "Cancel", 0x777781, true, () -> popup = "");
    }

    private void closePopup(GuiGraphicsExtractor g, int mx, int my) {
        int[] b = box(g, 280, 96, RED);
        int x = b[0] + 10, y = b[1] + 10, w = b[2] - 20;
        Draw.text(g, "<#FF5252><bold>Close your shop?", x, y);
        Draw.wrapped(g, "<gray>All stock, money and earnings come back to you. The opening fee is not refunded.", x, y + 14, w, Draw.MUTED);
        int by = b[1] + b[3] - 26;
        button(g, mx, my, x, by, (w - 8) / 2, 18, "<white><bold>Close it", RED, true, () -> {
            AbpsClient.send("shop", "close");
            Minecraft.getInstance().gui.setScreen(new MenuScreen("shops"));
        });
        button(g, mx, my, x + (w - 8) / 2 + 8, by, (w - 8) / 2, 18, "Keep it", 0x777781, true, () -> popup = "");
    }

    /** Development only: opens a popup directly, for screenshots. */
    void preview(String name, int id) {
        listingId = id;
        Net.ShopListing l = listing();
        if (name.equals("add")) {
            openAdd();
            pickSlot = inv().getSelectedSlot();
            qty.setNumber(16);
            return;
        }
        if (l != null) {
            priceInput.setNumber(l.price());
            bundle = l.bundle();
            currency = l.priceItem().copy();
            qty.setNumber(name.equals("trade") ? Math.max(l.bundle(), 34 / l.bundle() * l.bundle()) : 8);
        }
        popup = name;
    }

    // ================= Input =================

    private TextInput[] fields() {
        return new TextInput[]{qty, second, priceInput, nameInput};
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        // Right-click an inventory item in the add popup to price in it
        if (e.button() == 1 && popup.equals("add")) {
            for (Btn b : List.copyOf(buttons)) {
                if (!Draw.inside(e.x(), e.y(), b.x, b.y, b.w, b.h) || b.w != 17) continue;
                int[] slot = new int[]{pickSlot};
                b.action.run(); // selects the slot, then use it as the currency and restore the pick
                ItemStack st = inv().getItem(pickSlot);
                if (!st.isEmpty()) currency = new ItemStack(st.getItem());
                pickSlot = slot[0];
                click();
                return true;
            }
        }
        boolean typed = false;
        for (TextInput f : fields()) typed |= f.click(e.x(), e.y());
        if (typed) return true;
        if (e.button() == 0) {
            for (Btn b : List.copyOf(buttons)) {
                if (Draw.inside(e.x(), e.y(), b.x, b.y, b.w, b.h)) {
                    click();
                    b.action.run();
                    return true;
                }
            }
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        if (!popup.isEmpty()) return true;
        scroll = Math.max(0, Math.min(Math.max(0, contentHeight - listHeight), scroll - sy * 16));
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        for (TextInput f : fields()) if (f.charTyped(e)) return true;
        return super.charTyped(e);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        for (TextInput f : fields()) if (f.keyPressed(e)) return true;
        if (e.isEscape() && !popup.isEmpty()) {
            popup = "";
            return true;
        }
        if (AbpsClient.menuKey.matches(e) || Minecraft.getInstance().options.keyInventory.matches(e)) {
            onClose();
            return true;
        }
        return super.keyPressed(e);
    }
}
