package ru.florestdev.florestBanStatesProxy;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class PlayerJoinEventFL {

    private final FlorestBanStatesProxy plugin;

    public PlayerJoinEventFL(FlorestBanStatesProxy plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        String playerName = event.getUsername();
        String ip = event.getConnection().getRemoteAddress()
                .getAddress().getHostAddress();

        // Белый список
        if (plugin.whitelistPlayers.contains(playerName)) {
            event.setResult(PreLoginEvent.PreLoginComponentResult.allowed());
            return;
        }

        plugin.getClient().getInfo(ip).thenAccept(info -> {
            if (info != null && plugin.bannedCountries.contains(info.countryCode)) {

                Component kickReason = LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(plugin.bannedMessage);

                event.setResult(
                        PreLoginEvent.PreLoginComponentResult.denied(kickReason)
                );

                plugin.log.info(
                        "[FBS] Игрок {} ({}) из заблокированной страны: {}",
                        playerName, ip, info.countryCode
                );
            } else {
                event.setResult(
                        PreLoginEvent.PreLoginComponentResult.allowed()
                );
            }
        }).exceptionally(ex -> {
            plugin.log.error(
                    "[FBS] Ошибка при проверке IP для игрока {}",
                    playerName, ex
            );

            // При ошибке — пускаем
            event.setResult(
                    PreLoginEvent.PreLoginComponentResult.allowed()
            );
            return null;
        });
    }
}