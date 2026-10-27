package me.skyguard;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Кэш значений из config.yml. */
public final class Settings {

    public final double watchVl, punishVl, decayPerSecond, strictFactor;
    public final int watchSeconds;

    public final boolean speedEnabled, flyEnabled, reachEnabled, cpsEnabled, reachCancel;
    public final double speedTolerance, iceMultiplier, reachTolerance;
    public final int maxCps;

    public final boolean xrayEnabled;
    public final int xrayWindowSeconds, xrayMinOres;
    public final double xrayMaxRatio;
    public final Set<Material> xrayOres = new HashSet<>();

    public final int animationTicks;
    public final double animationHeight;

    public final double quarantineRadius;
    public final int reminderSeconds;
    public final Set<String> allowedCommands = new HashSet<>();

    public final List<String> prizeItems, prizeCommands, confirmCommands;
    public final int prizeLevels;

    private final FileConfiguration cfg;

    public Settings(FileConfiguration c) {
        this.cfg = c;
        watchVl = c.getDouble("detection.watch-vl", 3.0);
        punishVl = c.getDouble("detection.punish-vl", 8.0);
        decayPerSecond = c.getDouble("detection.decay-per-second", 0.1);
        watchSeconds = c.getInt("detection.watch-seconds", 300);
        strictFactor = c.getDouble("detection.strict-factor", 0.85);

        speedEnabled = c.getBoolean("checks.speed.enabled", true);
        speedTolerance = c.getDouble("checks.speed.tolerance", 1.2);
        iceMultiplier = c.getDouble("checks.speed.ice-multiplier", 2.5);
        flyEnabled = c.getBoolean("checks.fly.enabled", true);
        reachEnabled = c.getBoolean("checks.reach.enabled", true);
        reachTolerance = c.getDouble("checks.reach.tolerance", 1.0);
        reachCancel = c.getBoolean("checks.reach.cancel-hit", true);
        cpsEnabled = c.getBoolean("checks.autoclicker.enabled", true);
        maxCps = c.getInt("checks.autoclicker.max-cps", 25);

        xrayEnabled = c.getBoolean("checks.xray.enabled", true);
        xrayWindowSeconds = Math.max(60, c.getInt("checks.xray.window-seconds", 900));
        xrayMinOres = Math.max(2, c.getInt("checks.xray.min-hidden-ores", 4));
        xrayMaxRatio = c.getDouble("checks.xray.max-ratio", 0.08);
        for (String o : c.getStringList("checks.xray.ores")) {
            Material m = Material.matchMaterial(o.trim());
            if (m != null) xrayOres.add(m);
        }

        animationTicks = Math.max(20, c.getInt("animation.ticks", 60));
        animationHeight = c.getDouble("animation.height", 7.0);

        quarantineRadius = c.getDouble("quarantine.radius", 6.0);
        reminderSeconds = Math.max(10, c.getInt("quarantine.reminder-seconds", 60));
        for (String s : c.getStringList("quarantine.allowed-commands")) {
            allowedCommands.add(s.toLowerCase(Locale.ROOT));
        }

        prizeItems = c.getStringList("prize.items");
        prizeCommands = c.getStringList("prize.commands");
        prizeLevels = c.getInt("prize.xp-levels", 0);
        confirmCommands = c.getStringList("confirm-commands");
    }

    public String message(String key) {
        return cfg.getString("messages." + key, "&cНет сообщения: " + key);
    }
}
