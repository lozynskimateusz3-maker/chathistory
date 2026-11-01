package pl.chathistory;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.text.SimpleDateFormat;
import java.util.*;

public class HistoryScreen extends Screen {
    private record Line(OrderedText text, int color) {}

    private static final SimpleDateFormat FMT = new SimpleDateFormat("HH:mm:ss dd.MM");
    private TextFieldWidget search;
    private String selected = null;
    private double scroll = 0;
    private final List<Line> lines = new ArrayList<>();
    private Set<String> online = new HashSet<>();

    public HistoryScreen() { super(Text.literal("Chat History")); }

    @Override
    protected void init() {
        online = ChatHistoryMod.onlineNames();
        search = new TextFieldWidget(textRenderer, width / 2 - 100, 12, 200, 18, Text.literal("Szukaj"));
        search.setPlaceholder(Text.literal("Wpisz nick gracza..."));
        search.setChangedListener(s -> scroll = 0);
        addDrawableChild(search);
        setInitialFocus(search);
        search.setVisible(selected == null);
    }

    @Override
    public boolean shouldPause() { return false; }

    private int left() { return width / 2 - 150; }
    private int right() { return width / 2 + 150; }
    private int top() { return 38; }
    private int bottom() { return height - 10; }

    private List<String> filtered() {
        Set<String> all = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        all.addAll(online);
        all.addAll(ChatLog.knownNames());
        String q = search.getText().trim().toLowerCase();
        List<String> out = new ArrayList<>();
        for (String n : all) if (q.isEmpty() || n.toLowerCase().contains(q)) out.add(n);
        return out;
    }

    private void openHistory(String name) {
        selected = name;
        scroll = 0;
        search.setVisible(false);
        lines.clear();
        List<ChatLog.Entry> entries = ChatLog.get(name);
        for (int i = entries.size() - 1; i >= 0; i--) { // od najnowszego do najstarszego
            ChatLog.Entry e = entries.get(i);
            String prefix = "[" + FMT.format(new Date(e.time)) + "] ";
            int color = e.command ? 0xFFFF55 : 0xFFFFFF;
            for (OrderedText ot : textRenderer.wrapLines(Text.literal(prefix + e.text), right() - left() - 8))
                lines.add(new Line(ot, color));
        }
        if (lines.isEmpty())
            lines.add(new Line(Text.literal("Brak zapisanych wiadomości tego gracza.").asOrderedText(), 0xAAAAAA));
    }

    private void closeHistory() {
        selected = null;
        scroll = 0;
        search.setVisible(true);
        setFocused(search);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        int l = left(), r = right(), t = top(), b = bottom();
        ctx.fill(l - 4, t - 4, r + 4, b + 4, 0xB0000000);
        ctx.enableScissor(l, t, r, b);
        if (selected == null) {
            List<String> names = filtered();
            int rowH = 14;
            double max = Math.max(0, names.size() * rowH - (b - t));
            scroll = Math.max(0, Math.min(scroll, max));
            for (int i = 0; i < names.size(); i++) {
                int y = t + i * rowH - (int) scroll;
                if (y + rowH < t || y > b) continue;
                String n = names.get(i);
                boolean hover = mx >= l && mx <= r && my >= y && my < y + rowH && my >= t && my <= b;
                if (hover) ctx.fill(l, y, r, y + rowH, 0x40FFFFFF);
                boolean on = online.contains(n);
                int count = ChatLog.get(n).size();
                ctx.drawTextWithShadow(textRenderer, Text.literal(n + "  (" + count + ")" + (on ? "" : "  [offline]")),
                    l + 4, y + 3, on ? 0xFFFFFF : 0x888888);
            }
        } else {
            double max = Math.max(0, lines.size() * 10 - (b - t));
            scroll = Math.max(0, Math.min(scroll, max));
            for (int i = 0; i < lines.size(); i++) {
                int y = t + i * 10 - (int) scroll;
                if (y + 10 < t || y > b) continue;
                ctx.drawTextWithShadow(textRenderer, lines.get(i).text(), l + 4, y, lines.get(i).color());
            }
        }
        ctx.disableScissor();
        if (selected != null) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Historia: " + selected + "  (Esc = powrót)"), width / 2, 16, 0xFFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (selected == null && button == 0 && mx >= left() && mx <= right() && my >= top() && my <= bottom()) {
            int idx = (int) ((my - top() + scroll) / 14);
            List<String> names = filtered();
            if (idx >= 0 && idx < names.size()) {
                openHistory(names.get(idx));
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        scroll -= v * 20;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && selected != null) { // Esc
            closeHistory();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
