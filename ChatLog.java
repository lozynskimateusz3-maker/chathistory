package pl.chathistory;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatLog {
    public static class Entry {
        public long time;
        public String text;
        public boolean command;
        public Entry(long time, String text, boolean command) {
            this.time = time; this.text = text; this.command = command;
        }
    }

    private static final Gson GSON = new Gson();
    private static final int MAX_PER_PLAYER = 500;
    private static Map<String, List<Entry>> data = new HashMap<>();
    private static String key = "default";
    private static boolean dirty = false;

    // "[Ranga] Nick: /komenda", "Nick issued server command: /komenda" (command spy z pluginów serwera)
    private static final Pattern CMD = Pattern.compile(
        "^(?:\\[[^\\]]*\\]\\s*)*<?([A-Za-z0-9_]{3,16})>?\\s*(?:issued server command:|executed command:|used command:|wpisał komendę:|użył komendy:|:|»|>)\\s*(/.*)$",
        Pattern.CASE_INSENSITIVE);
    // "<Nick> wiadomość", "Nick: wiadomość", "[Ranga] Nick » wiadomość"
    private static final Pattern CHAT = Pattern.compile(
        "^(?:\\[[^\\]]*\\]\\s*)*(?:<([A-Za-z0-9_]{3,16})>|([A-Za-z0-9_]{3,16})\\s*(?::|»|>))\\s*(.+)$");

    private static Path file() {
        String safe = key.replaceAll("[^A-Za-z0-9._-]", "_");
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("chathistory");
        try { Files.createDirectories(dir); } catch (Exception ignored) {}
        return dir.resolve(safe + ".json");
    }

    public static synchronized void load(String serverKey) {
        save();
        key = serverKey;
        data = new HashMap<>();
        try {
            Path f = file();
            if (Files.exists(f)) {
                Map<String, List<Entry>> m = GSON.fromJson(Files.readString(f),
                    new TypeToken<Map<String, List<Entry>>>() {}.getType());
                if (m != null) data = m;
            }
        } catch (Exception e) { e.printStackTrace(); }
        dirty = false;
    }

    public static synchronized void save() {
        if (!dirty) return;
        try {
            Files.writeString(file(), GSON.toJson(data));
            dirty = false;
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static synchronized void add(String player, String text, boolean command) {
        List<Entry> l = data.computeIfAbsent(player, k -> new ArrayList<>());
        l.add(new Entry(System.currentTimeMillis(), text, command));
        while (l.size() > MAX_PER_PLAYER) l.remove(0);
        dirty = true;
    }

    public static synchronized List<Entry> get(String player) {
        return new ArrayList<>(data.getOrDefault(player, List.of()));
    }

    public static synchronized Set<String> knownNames() {
        return new HashSet<>(data.keySet());
    }

    /** Wiadomość systemowa/pluginowa (nie podpisana) – próbujemy wyłuskać nick i treść. */
    public static void onGameMessage(String raw, Set<String> online) {
        String s = raw.replaceAll("§.", "").trim();
        Matcher m = CMD.matcher(s);
        if (m.matches() && online.contains(m.group(1))) {
            add(m.group(1), m.group(2), true);
            return;
        }
        m = CHAT.matcher(s);
        if (m.matches()) {
            String name = m.group(1) != null ? m.group(1) : m.group(2);
            if (online.contains(name)) add(name, m.group(3), m.group(3).startsWith("/"));
        }
    }
}
