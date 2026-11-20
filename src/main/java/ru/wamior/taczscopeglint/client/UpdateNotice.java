package ru.wamior.taczscopeglint.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import ru.wamior.taczscopeglint.Config;

/** Uses Forge/NeoForge's asynchronous checker, like Cadaver; never starts a network request on a tick. */
public final class UpdateNotice {
    private static final String RELEASES = "https://github.com/wamior/TaCZ-Scope-Glint/releases/latest";
    private static boolean shown;

    public static void show(LocalPlayer player, String current, String target, String url) {
        if (shown || player == null || !Config.UPDATE_NOTICES.get()) return;
        shown = true;
        // Only link to the project's own releases, rather than an arbitrary remote URL.
        Component link = ChatCompat.translate("message.taczscopeglint.update.link")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA).withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, RELEASES)));
        ChatCompat.system(player, ChatCompat.translate("message.taczscopeglint.update", current, target, link)
                .withStyle(ChatFormatting.GOLD));
    }

    private UpdateNotice() {
    }
}
