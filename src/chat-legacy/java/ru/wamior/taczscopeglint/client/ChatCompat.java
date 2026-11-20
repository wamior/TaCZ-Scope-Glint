package ru.wamior.taczscopeglint.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.Util;

public final class ChatCompat {
    public static MutableComponent translate(String key, Object... args) {
        return new TranslatableComponent(key, args);
    }
    public static void system(LocalPlayer player, Component message) {
        player.sendMessage(message, Util.NIL_UUID);
    }
    private ChatCompat() {
    }
}
