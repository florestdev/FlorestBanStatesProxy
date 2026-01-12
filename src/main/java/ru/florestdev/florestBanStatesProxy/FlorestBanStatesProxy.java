package ru.florestdev.florestBanStatesProxy;

import com.google.inject.Inject;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;
import org.spongepowered.configurate.ConfigurateException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Plugin(id = "florestbanstates", name = "FlorestBanStates", version = "1.0", authors = {"FlorestDev"})
public class FlorestBanStatesProxy {

    private final ProxyServer server;
    public final Logger log;
    private final Path dataDirectory;

    public List<String> bannedCountries;
    public List<String> whitelistPlayers;
    public List<String> unbannedRegions;
    public String bannedMessage;

    // Новые поля для MOTD
    public String bannedMotd;
    public String bannedVersionText;

    private Client client;

    @Inject
    public FlorestBanStatesProxy(ProxyServer server, Logger log, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.log = log;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        saveDefaultConfig();
        loadConfig();

        this.client = new Client(this);

        // Регистрация слушателей
        server.getEventManager().register(this, new PlayerJoinEventFL(this));

        server.getCommandManager().register("fbs", new FBSCommand());

        log.info("FlorestBanStates 1.0 (Velocity) enabled.");
    }

    @Subscribe
    public void onProxyPing(ProxyPingEvent event) {
        String ip = event.getConnection().getRemoteAddress().getAddress().getHostAddress();

        // Используем .join(), чтобы подождать результат запроса прямо здесь.
        // Это допустимо в ProxyPingEvent, так как он не блокирует основной поток тиков сервера.
        GeoInfo info;
        try {
            info = client.getInfo(ip)
                    .orTimeout(800, TimeUnit.MILLISECONDS)
                    .exceptionally(ex -> null)
                    .join();
        } catch (Exception e) {
            info = null;
        }


        // Проверяем на null (вдруг API упало или ошибка сети)
        if (info != null && bannedCountries.contains(info.countryCode)) {

            // Превращаем текст в Component
            Component motd = LegacyComponentSerializer.legacyAmpersand().deserialize(bannedMotd);

            event.setPing(event.getPing().asBuilder()
                    .description(motd)
                    // Устанавливаем фейковую версию и текст
                    .version(new ServerPing.Version(999, bannedVersionText))
                    // Скрываем кол-во игроков
                    .onlinePlayers(-1)
                    .build());

            log.info("[FBS] Скрыт реальный MOTD для заблокированного IP: {} (Страна: {})", ip, info.countryCode);
        }
    }

    public void loadConfig() {
        Path configPath = dataDirectory.resolve("config.yml");
        YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                .path(configPath)
                .build();

        try {
            ConfigurationNode config = loader.load();

            bannedCountries = config.node("banned_counties").getList(String.class, Collections.emptyList());
            whitelistPlayers = config.node("whitelist_players").getList(String.class, Collections.emptyList());
            unbannedRegions = config.node("unbanned_regions").getList(String.class, Collections.emptyList());

            // Сообщения
            bannedMessage = config.node("messages", "kick_reason").getString("Your country is banned.");
            bannedMotd = config.node("messages", "banned_motd").getString("&cAccess Denied.");
            bannedVersionText = config.node("messages", "banned_version_text").getString("&4CLOSED");

        } catch (ConfigurateException e) {
            log.error("Could not load config!", e);
        }
    }

    private void saveDefaultConfig() {
        if (!Files.exists(dataDirectory)) {
            try {
                Files.createDirectories(dataDirectory);
            } catch (IOException e) {
                log.error("Could not create data directory", e);
            }
        }

        Path configPath = dataDirectory.resolve("config.yml");
        if (!Files.exists(configPath)) {
            try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
                if (in != null) {
                    Files.copy(in, configPath);
                }
            } catch (IOException e) {
                log.error("Could not save default config", e);
            }
        }
    }

    public void reloadAll() {
        loadConfig();
        if (client != null) client.clearCache();
        log.info("Config + cache reloaded.");
    }

    public Client getClient() {
        return client;
    }

    private class FBSCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            if (!invocation.source().hasPermission("florestbanstates.admin")) {
                invocation.source().sendMessage(Component.text("§cNo permission."));
                return;
            }

            if (invocation.arguments().length == 1 && invocation.arguments()[0].equalsIgnoreCase("reload")) {
                reloadAll();
                invocation.source().sendMessage(Component.text("§aFlorestBanStates reloaded!"));
            } else {
                invocation.source().sendMessage(Component.text("§eUsage: /fbs reload"));
            }
        }
    }
}