package pl.chathistory;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;

public class ChatHistoryMod implements ClientModInitializer {
    private static KeyBinding openKey;
    private int ticks = 0;

    public static Set<String> onlineNames() {
        Set<String> s = new HashSet<>();
        var h = MinecraftClient.getInstance().getNetworkHandler();
        if (h != null) for (PlayerListEntry e : h.getPlayerList()) s.add(e.getProfile().getName());
        return s;
    }

    @Override
    public void onInitializeClient() {
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.chathistory.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.chathistory"));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            String k = client.getCurrentServerEntry() != null
                ? client.getCurrentServerEntry().address : "singleplayer";
            ChatLog.load(k);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ChatLog.save());
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> ChatLog.save());

        // podpisane wiadomości graczy (vanilla chat)
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, ts) -> {
            if (sender == null) return;
            String text = signed != null ? signed.getSignedContent() : message.getString();
            ChatLog.add(sender.getName(), text, false);
        });

        // wiadomości z pluginów serwera (większość serwerów, w tym anarchia, wysyła czat tak)
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) ChatLog.onGameMessage(message.getString(), onlineNames());
        });

        // własne komendy
        ClientSendMessageEvents.COMMAND.register(cmd -> {
            var p = MinecraftClient.getInstance().player;
            if (p != null) ChatLog.add(p.getName().getString(), "/" + cmd, true);
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.currentScreen == null && client.player != null)
                    client.setScreen(new HistoryScreen());
            }
            if (++ticks % 1200 == 0) ChatLog.save(); // autozapis co minutę
        });
    }
}
