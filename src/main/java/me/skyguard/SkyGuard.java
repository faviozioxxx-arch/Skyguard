package me.skyguard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class SkyGuard extends JavaPlugin {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final Map<UUID, PlayerData> data = new HashMap<>();
    private Settings settings;
    private CheaterManager cheaters;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = new Settings(getConfig());

        cheaters = new CheaterManager(this);
        cheaters.load();
        cheaters.startReminder();

        Bukkit.getPluginManager().registerEvents(new CheckListener(this), this);
        Bukkit.getPluginManager().registerEvents(new CheaterProtectionListener(this), this);
        Bukkit.getPluginManager().registerEvents(new XRayListener(this), this);
        new MovementTask(this).runTaskTimer(this, 40L, 5L);

        Commands cmds = new Commands(this);
        for (String name : List.of("setcheater", "nocheat", "cheatconfirm", "skyguard")) {
            PluginCommand c = getCommand(name);
            if (c != null) {
                c.setExecutor(cmds);
                c.setTabCompleter(cmds);
            }
        }
        getLogger().info("SkyGuard включён.");
    }

    @Override
    public void onDisable() {
        if (cheaters != null) cheaters.shutdown();
    }

    public void reload() {
        reloadConfig();
        settings = new Settings(getConfig());
    }

    public Settings settings() {
        return settings;
    }

    public CheaterManager cheaters() {
        return cheaters;
    }

    public PlayerData data(Player p) {
        return data.computeIfAbsent(p.getUniqueId(), id -> new PlayerData());
    }

    public void removeData(UUID id) {
        data.remove(id);
    }

    public Map<UUID, PlayerData> allData() {
        return data;
    }

    // ---------- тексты ----------

    public Component legacy(String s) {
        return LEGACY.deserialize(s);
    }

    /** Сообщение из config.yml с заменой плейсхолдеров: text("key", "%a%", "1", "%b%", "2"). */
    public Component text(String key, String... repl) {
        String s = settings.message(key);
        for (int i = 0; i + 1 < repl.length; i += 2) {
            s = s.replace(repl[i], repl[i + 1]);
        }
        return LEGACY.deserialize(s);
    }

    public void alertStaff(Component c) {
        for (Player s : Bukkit.getOnlinePlayers()) {
            if (s.hasPermission("skyguard.alerts")) s.sendMessage(c);
        }
        Bukkit.getConsoleSender().sendMessage(c);
    }

    // ---------- нарушения ----------

    /**
     * Зарегистрировать срабатывание проверки.
     * 0 -> NORMAL, watch-vl -> СЛЕЖКА, punish-vl (во время слежки) -> отправка на проверку.
     */
    public void flag(Player p, String check, double add, String info) {
        if (p.hasPermission("skyguard.bypass")) return;
        if (cheaters.isTracked(p.getUniqueId())) return;

        PlayerData d = data(p);
        long now = System.currentTimeMillis();

        double seconds = (now - d.lastVlUpdate) / 1000.0;
        d.vl = Math.max(0, d.vl - seconds * settings.decayPerSecond);
        d.lastVlUpdate = now;
        if (d.watched && now > d.watchedUntil) d.watched = false;

        d.vl += add;

        if (!d.watched && d.vl >= settings.watchVl) {
            d.watched = true;
            alertStaff(text("staff-watch", "%player%", p.getName(), "%check%", check));
        }

        if (d.watched) {
            d.watchedUntil = now + settings.watchSeconds * 1000L;
            if (now - d.lastAlert > 1000) {
                d.lastAlert = now;
                alertStaff(text("staff-flag",
                        "%player%", p.getName(),
                        "%check%", check,
                        "%info%", info,
                        "%vl%", String.format(Locale.US, "%.1f", d.vl)));
            }
            if (d.vl >= settings.punishVl) {
                if (cheaters.send(p, check)) {
                    d.vl = 0;
                    d.watched = false;
                } else {
                    d.vl = settings.watchVl; // например, не задана точка - не спамим
                }
            }
        }
    }
}
