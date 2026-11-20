package ru.wamior.taczscopeglint.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class ChatCompat {
    public static MutableComponent translate(String key, Object... args) {
        return Component.translatable(key, args);
    }
    public static void system(LocalPlayer player, Component message) {
        player.sendSystemMessage(message);
    }
    private ChatCompat() {
    }
}
