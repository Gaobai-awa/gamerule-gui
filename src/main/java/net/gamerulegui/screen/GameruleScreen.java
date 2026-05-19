package net.gamerulegui.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.gamerulegui.GameruleGuiMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class GameruleScreen extends Screen {

    private RuleListWidget list;
    private TextFieldWidget search;
    private Map<String, String> rules;
    private final List<RuleEntry> all = new ArrayList<>();

    public GameruleScreen() {
        super(Text.translatable("text.gamerulegui.title"));
    }

    public void updateRules(Map<String, String> r) {
        rules = r;
        rebuild();
    }

    private void rebuild() {
        all.clear();
        if (rules == null) return;

        var keys = new ArrayList<>(rules.keySet());
        keys.sort((a, b) -> {
            boolean ai = isInt(rules.get(a));
            boolean bi = isInt(rules.get(b));
            if (ai != bi) return ai ? 1 : -1;
            return a.compareTo(b);
        });

        for (String k : keys)
            all.add(new RuleEntry(k, rules.get(k), isInt(rules.get(k))));

        filter();
    }

    private void filter() {
        if (search == null || list == null) return;

        String q = search.getText().toLowerCase(Locale.ROOT).trim();
        List<RuleEntry> out;

        if (q.isEmpty()) {
            out = new ArrayList<>(all);
        } else {
            out = all.stream().filter(e -> {
                if (e.name.toLowerCase(Locale.ROOT).contains(q)) return true;
                try {
                    String t = Text.translatable("gamerule." + e.name).getString();
                    if (t.toLowerCase(Locale.ROOT).contains(q)) return true;
                } catch (Exception ignored) {}
                return false;
            }).collect(Collectors.toList());
        }

        list.clear();
        for (var e : out) list.add(e);
    }

    private static boolean isInt(String v) {
        return v != null && !v.equals("true") && !v.equals("false");
    }

    // ─── init ────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();

        int sy = 25;
        search = new TextFieldWidget(textRenderer, (width - 180) / 2, sy, 180, 18,
            Text.translatable("text.gamerulegui.search"));
        search.setPlaceholder(Text.translatable("text.gamerulegui.search"));
        search.setChangedListener(s -> filter());
        addDrawableChild(search);

        int lw = Math.min(420, width - 12);
        int lx = (width - lw) / 2;
        list = new RuleListWidget(MinecraftClient.getInstance(),
            lw, height - sy - 57, sy + 24, height - 33, 30);
        list.setLeftPos(lx);

        if (rules != null) filter();
        addDrawableChild(list);

        addDrawableChild(ButtonWidget.builder(
            Text.translatable("text.gamerulegui.close"), btn -> close()
        ).dimensions((width - 60) / 2, height - 26, 60, 20).build());
    }

    // ─── input ───────────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(int kc, int sc, int mod) {
        if (search != null && search.isFocused()) {
            if (kc == 256) { search.setFocused(false); return true; }
            return search.keyPressed(kc, sc, mod);
        }
        if (kc == 256) { close(); return true; }
        return super.keyPressed(kc, sc, mod);
    }

    @Override
    public boolean charTyped(char c, int mod) {
        if (search != null && search.isFocused())
            return search.charTyped(c, mod);
        return super.charTyped(c, mod);
    }

    // ─── render ──────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx);
        super.render(ctx, mx, my, delta);

        int sx = search.getX(), sy = search.getY();
        int sw = search.getWidth(), sh = search.getHeight();
        int bc = search.isFocused() ? 0xFFFFFFFF : 0xFF888888;
        ctx.fill(sx - 1, sy - 1, sx + sw + 1, sy, bc);
        ctx.fill(sx - 1, sy + sh, sx + sw + 1, sy + sh + 1, bc);
        ctx.fill(sx - 1, sy, sx, sy + sh, bc);
        ctx.fill(sx + sw, sy, sx + sw + 1, sy + sh, bc);

        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.literal("§l").append(Text.translatable("text.gamerulegui.title")),
            width / 2, 8, 0xFFFFFF);
    }

    // ─── entry ───────────────────────────────────────────────────────────────

    public class RuleEntry extends AlwaysSelectedEntryListWidget.Entry<RuleEntry> {
        public final String name;
        public final boolean isInt;
        public String val;
        public final ButtonWidget btn;
        public final TextFieldWidget tf;

        public RuleEntry(String name, String value, boolean isInt) {
            this.name = name;
            this.isInt = isInt;
            val = value;

            if (isInt) {
                btn = null;
                tf = new TextFieldWidget(MinecraftClient.getInstance().textRenderer,
                    0, 0, 54, 16, Text.literal(name));
                tf.setText(value);
                tf.setMaxLength(9);
                tf.setTextPredicate(t -> t.isEmpty() || t.equals("-")
                    || t.chars().allMatch(c -> c == '-' || Character.isDigit(c)));
            } else {
                tf = null;
                boolean on = Boolean.parseBoolean(value);
                btn = ButtonWidget.builder(on
                    ? Text.literal("§a✔ " + Text.translatable("text.gamerulegui.on").getString())
                    : Text.literal("§c✘ " + Text.translatable("text.gamerulegui.off").getString()),
                    b -> toggle()
                ).dimensions(0, 0, 60, 16).build();
            }
        }

        private void toggle() {
            boolean nv = !Boolean.parseBoolean(val);
            val = String.valueOf(nv);
            btn.setMessage(Text.literal(
                nv ? "§a✔ " + Text.translatable("text.gamerulegui.on").getString()
                   : "§c✘ " + Text.translatable("text.gamerulegui.off").getString()));
            send();
        }

        public void send() {
            PacketByteBuf buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
            buf.writeString(name);
            buf.writeString(val);
            ClientPlayNetworking.send(GameruleGuiMod.UPDATE_RULE, buf);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (btn != null && mx >= btn.getX() && mx <= btn.getX() + btn.getWidth()
                && my >= btn.getY() && my <= btn.getY() + btn.getHeight())
                return btn.mouseClicked(mx, my, button);
            if (tf != null && mx >= tf.getX() && mx <= tf.getX() + tf.getWidth()
                && my >= tf.getY() && my <= tf.getY() + tf.getHeight()) {
                tf.setFocused(true);
                return tf.mouseClicked(mx, my, button);
            }
            return false;
        }

        @Override
        public boolean keyPressed(int kc, int sc, int mod) {
            if (tf != null && tf.isFocused()) {
                if (kc == 257 || kc == 335) {
                    String nv = tf.getText();
                    tf.setFocused(false);
                    if (!nv.equals(val)) { val = nv; send(); }
                    return true;
                }
                if (kc == 256) { tf.setFocused(false); return true; }
                return tf.keyPressed(kc, sc, mod);
            }
            return false;
        }

        @Override
        public boolean charTyped(char c, int mod) {
            return tf != null && tf.isFocused() && tf.charTyped(c, mod);
        }

        @Override
        public Text getNarration() {
            return Text.translatable("gamerule." + name);
        }

        @Override
        public void render(DrawContext ctx, int index, int y, int x, int ew, int eh,
                           int mx, int my, boolean hovered, float delta) {
            ctx.drawTextWithShadow(textRenderer, Text.translatable("gamerule." + name), x + 3, y + 2, 0xFFFFFF);
            ctx.drawTextWithShadow(textRenderer, Text.literal("§7" + name + " §8→ §e" + val), x + 3, y + 13, 0x888888);

            int wx = x + ew - 72;
            int wy = y + 6;

            if (isInt && tf != null) {
                tf.setX(wx); tf.setY(wy); tf.setWidth(54);
                tf.render(ctx, mx, my, delta);
            } else if (btn != null) {
                btn.setX(wx); btn.setY(wy);
                btn.render(ctx, mx, my, delta);
            }
        }
    }

    // ─── list widget ─────────────────────────────────────────────────────────

    public class RuleListWidget extends AlwaysSelectedEntryListWidget<RuleEntry> {
        public RuleListWidget(MinecraftClient c, int w, int h, int top, int bot, int ih) {
            super(c, w, h, top, bot, ih);
        }

        @Override
        public int getRowWidth() { return width - 6; }

        @Override
        protected int getScrollbarPositionX() { return left + width - 6; }

        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            for (var e : children()) {
                if (e.tf != null && e.tf.isFocused()) {
                    e.tf.setFocused(false);
                    String nt = e.tf.getText();
                    if (!nt.equals(e.val)) { e.val = nt; e.send(); }
                }
            }

            int header = top + 4 - (int) getScrollAmount();
            int row = ((int) my - header) / itemHeight;
            if (row >= 0 && row < children().size()) {
                RuleEntry e = children().get(row);
                int wx = getRowLeft() + getRowWidth() - 72;
                int wy = getRowTop(row) + 6;

                if (e.tf != null && e.isInt) {
                    e.tf.setX(wx); e.tf.setY(wy); e.tf.setWidth(54);
                    if (mx >= wx && mx <= wx + 54 && my >= wy && my <= wy + 16) {
                        e.tf.setFocused(true);
                        return e.tf.mouseClicked(mx, my, btn);
                    }
                }
                if (e.btn != null) {
                    e.btn.setX(wx); e.btn.setY(wy);
                    if (mx >= wx && mx <= wx + e.btn.getWidth() && my >= wy && my <= wy + 16)
                        return e.btn.mouseClicked(mx, my, btn);
                }
            }
            return super.mouseClicked(mx, my, btn);
        }

        @Override
        public boolean keyPressed(int kc, int sc, int mod) {
            for (var e : children())
                if (e.tf != null && e.tf.isFocused()) return e.keyPressed(kc, sc, mod);
            return super.keyPressed(kc, sc, mod);
        }

        @Override
        public boolean charTyped(char c, int mod) {
            for (var e : children())
                if (e.tf != null && e.tf.isFocused()) return e.charTyped(c, mod);
            return super.charTyped(c, mod);
        }

        public void clear() {
            var ch = children();
            while (!ch.isEmpty()) removeEntry(ch.get(0));
            setSelected(null);
        }

        public int add(RuleEntry e) { return super.addEntry(e); }
    }
}
