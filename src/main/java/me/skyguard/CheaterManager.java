package me.skyguard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Хранит читеров, проигрывает анимацию вознесения, телепортирует на точку и возвращает обратно. */
public final class CheaterManager {

    public static final class Entry {
        public final String name;
        public Location back;
        public boolean released;
        public boolean pendingSpot;
        public boolean prize;

        Entry(String name, Location back) {
            this.name = name;
            this.back = back;
        }
    }

    private final SkyGuard plugin;
    private final File file;

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private final Set<UUID> processing = new HashSet<>();
    private final Map<UUID, BukkitTask> tasks = new HashMap<>();
    private final Map<UUID, Location> startLocs = new HashMap<>();
    private final Map<UUID, Boolean> prevInvulnerable = new HashMap<>();

    private Location spot;
    private long lastReminder = System.currentTimeMillis();

    public CheaterManager(SkyGuard plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "cheaters.yml");
    }

    // ---------- состояние ----------

    /** Игрок проходит анимацию или ждёт проверки. */
    public boolean isTracked(UUID id) {
        return processing.contains(id) || isInCheck(id);
    }

    /** Игрок уже на точке проверки (ждёт модератора). */
    public boolean isInCheck(UUID id) {
        Entry e = entries.get(id);
        return e != null && !e.released;
    }

    public boolean isProcessing(UUID id) {
        return processing.contains(id);
    }

    public Location spot() {
        return spot;
    }

    public Map<UUID, Entry> entries() {
        return entries;
    }

    public void setSpot(Location loc) {
        this.spot = loc.clone();
        save();
    }

    // ---------- отправка на проверку ----------

    public boolean send(Player p, String reason) {
        UUID id = p.getUniqueId();
        if (isTracked(id)) return false;

        if (spot == null || spot.getWorld() == null) {
            plugin.alertStaff(plugin.text("staff-no-spot", "%player%", p.getName()));
            return false;
        }

        final Location start = p.getLocation().clone();
        final Settings st = plugin.settings();

        processing.add(id);
        startLocs.put(id, start);
        prevInvulnerable.put(id, p.isInvulnerable());

        if (p.isInsideVehicle()) p.leaveVehicle();
        p.setInvulnerable(true);
        p.setGravity(false);
        p.setFallDistance(0f);

        // не поднимаем игрока выше потолка
        double free = 0;
        for (double h = 0.5; h <= st.animationHeight; h += 0.5) {
            if (!start.clone().add(0, h + 1.9, 0).getBlock().isPassable()) break;
            free = h;
        }
        final double height = free;
        final int total = st.animationTicks;

        p.sendMessage(plugin.text("caught-chat"));
        p.showTitle(Title.title(
                plugin.text("caught-title"),
                plugin.text("caught-subtitle"),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(600))));
        p.getWorld().playSound(start, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1f);

        BukkitTask task = new BukkitRunnable() {
            int tick = 0;
            float yaw = start.getYaw();

            @Override
            public void run() {
                if (!p.isOnline()) {
                    cancel();
                    return;
                }
                tick++;
                double t = Math.min(1.0, tick / (double) total);
                double ease = t * t; // медленный старт, резкий взлёт
                yaw += 10f + (float) (t * 40f);

                Location target = start.clone().add(0, height * ease, 0);
                target.setYaw(yaw);
                target.setPitch(-20f + (float) (t * 10f));
                p.teleport(target, TeleportCause.PLUGIN);
                p.setFallDistance(0f);

                World w = target.getWorld();
                double base = tick * 0.6;
                for (int k = 0; k < 3; k++) {
                    double a = base + k * Math.PI * 2 / 3;
                    w.spawnParticle(Particle.END_ROD,
                            target.getX() + Math.cos(a) * 0.9,
                            target.getY() + (tick % 20) * 0.1,
                            target.getZ() + Math.sin(a) * 0.9,
                            1, 0, 0, 0, 0);
                }
                w.spawnParticle(Particle.PORTAL, target.getX(), target.getY() + 1, target.getZ(),
                        10, 0.4, 0.8, 0.4, 0.3);
                if (tick % 4 == 0) {
                    w.playSound(target, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, (float) (0.5 + t * 1.5));
                }

                if (tick >= total) {
                    cancel();
                    tasks.remove(id);
                    finish(p, start, reason);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        tasks.put(id, task);
        return true;
    }

    private void finish(Player p, Location start, String reason) {
        UUID id = p.getUniqueId();
        processing.remove(id);
        startLocs.remove(id);
        restoreFlags(p);

        Location from = p.getLocation();
        World w = from.getWorld();
        w.strikeLightningEffect(from);
        w.spawnParticle(Particle.EXPLOSION_EMITTER, from, 1);
        w.spawnParticle(Particle.ELECTRIC_SPARK, from.getX(), from.getY() + 1, from.getZ(),
                80, 0.5, 1.0, 0.5, 0.3);

        Entry e = new Entry(p.getName(), start);
        entries.put(id, e);
        save();

        PlayerData d = plugin.data(p);
        d.exempt(5000);
        d.vl = 0;
        d.watched = false;

        p.teleport(spot, TeleportCause.PLUGIN);
        p.setFallDistance(0f);
        p.setVelocity(new Vector(0, 0, 0));
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.6f);
        p.sendMessage(plugin.text("arrived"));

        String name = p.getName();
        Component alert = plugin.text("staff-caught", "%player%", name, "%reason%", reason)
                .append(Component.text(" [ТП]", NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.runCommand("/tp " + name))
                        .hoverEvent(HoverEvent.showText(Component.text("Телепортироваться к игроку"))))
                .append(Component.text(" [Читов нет]", NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/nocheat " + name))
                        .hoverEvent(HoverEvent.showText(Component.text("/nocheat " + name))))
                .append(Component.text(" [Читы есть]", NamedTextColor.RED)
                        .clickEvent(ClickEvent.suggestCommand("/cheatconfirm " + name + " "))
                        .hoverEvent(HoverEvent.showText(Component.text("/cheatconfirm " + name))));
        plugin.alertStaff(alert);
    }

    private void restoreFlags(Player p) {
        p.setGravity(true);
        Boolean prev = prevInvulnerable.remove(p.getUniqueId());
        p.setInvulnerable(prev != null && prev);
    }

    // ---------- выход / вход ----------

    /** Игрок вышел посреди анимации - не даём ему остаться в воздухе без гравитации. */
    public void onQuit(Player p) {
        UUID id = p.getUniqueId();
        if (!processing.contains(id)) return;
        abort(p);
        plugin.alertStaff(plugin.text("staff-quit", "%player%", p.getName()));
        save();
    }

    private void abort(Player p) {
        UUID id = p.getUniqueId();
        BukkitTask t = tasks.remove(id);
        if (t != null) t.cancel();
        Location start = startLocs.remove(id);
        processing.remove(id);
        restoreFlags(p);
        if (start != null) {
            Entry e = new Entry(p.getName(), start);
            e.pendingSpot = true;
            entries.put(id, e);
            try {
                p.teleport(start, TeleportCause.PLUGIN);
            } catch (Exception ignored) {
                // не критично
            }
        }
    }

    public void onJoin(Player p) {
        UUID id = p.getUniqueId();
        if (!p.hasGravity() && !processing.contains(id)) p.setGravity(true);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player pl = Bukkit.getPlayer(id);
            Entry e = entries.get(id);
            if (pl == null || e == null) return;

            if (e.released) {
                applyRelease(pl, e, e.prize);
                entries.remove(id);
                save();
                return;
            }
            if (e.pendingSpot && spot != null && spot.getWorld() != null) {
                e.pendingSpot = false;
                pl.teleport(spot, TeleportCause.PLUGIN);
                save();
            }
            pl.sendMessage(plugin.text("caught-chat"));
            plugin.alertStaff(plugin.legacy("&6[SkyGuard] &f" + pl.getName() + " &eвернулся на проверку."));
        }, 5L);
    }

    // ---------- снятие проверки ----------

    /** /nocheat: читов нет. Возвращает false, если игрок не на проверке. */
    public boolean release(String name, boolean giveGifts) {
        UUID id = null;
        Entry e = null;
        for (Map.Entry<UUID, Entry> en : entries.entrySet()) {
            if (!en.getValue().released && en.getValue().name.equalsIgnoreCase(name)) {
                id = en.getKey();
                e = en.getValue();
                break;
            }
        }
        if (e == null) return false;

        Player p = Bukkit.getPlayer(id);
        if (p != null) {
            applyRelease(p, e, giveGifts);
            entries.remove(id);
        } else {
            // игрок офлайн - вернём и наградим при следующем входе
            e.released = true;
            e.prize = giveGifts;
        }
        save();
        return true;
    }

    private void applyRelease(Player p, Entry e, boolean gifts) {
        Location back = e.back;
        if (back == null || back.getWorld() == null) back = p.getWorld().getSpawnLocation();

        PlayerData d = plugin.data(p);
        d.exempt(5000);
        d.vl = 0;
        d.watched = false;

        p.teleport(back, TeleportCause.PLUGIN);
        p.setFallDistance(0f);
        p.sendMessage(plugin.text("released"));
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        if (gifts) givePrize(p);
    }

    private void givePrize(Player p) {
        Settings st = plugin.settings();
        for (String s : st.prizeItems) {
            String[] parts = s.split(":");
            Material m = Material.matchMaterial(parts[0].trim());
            if (m == null || m.isAir()) continue;
            int amount = 1;
            if (parts.length > 1) {
                try {
                    amount = Math.max(1, Integer.parseInt(parts[1].trim()));
                } catch (NumberFormatException ignored) {
                    // оставляем 1
                }
            }
            Map<Integer, ItemStack> left = p.getInventory().addItem(new ItemStack(m, amount));
            for (ItemStack it : left.values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), it);
            }
        }
        if (st.prizeLevels > 0) p.giveExpLevels(st.prizeLevels);
        for (String cmd : st.prizeCommands) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", p.getName()));
        }
        p.sendMessage(plugin.text("prize"));
    }

    // ---------- подтверждение читов ----------

    public void confirm(String name, String reason, String moderator) {
        UUID id = null;
        String exact = name;
        for (Map.Entry<UUID, Entry> en : entries.entrySet()) {
            if (en.getValue().name.equalsIgnoreCase(name)) {
                id = en.getKey();
                exact = en.getValue().name;
                break;
            }
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) exact = online.getName();

        for (String cmd : plugin.settings().confirmCommands) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    cmd.replace("%player%", exact).replace("%reason%", reason));
        }
        if (id != null) {
            entries.remove(id);
            save();
        }
        plugin.alertStaff(plugin.text("confirmed-staff", "%mod%", moderator, "%player%", exact));
    }

    // ---------- напоминания ----------

    public void startReminder() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            if (now - lastReminder < plugin.settings().reminderSeconds * 1000L) return;
            lastReminder = now;
            for (Map.Entry<UUID, Entry> en : entries.entrySet()) {
                if (en.getValue().released) continue;
                Player p = Bukkit.getPlayer(en.getKey());
                if (p != null) p.sendMessage(plugin.text("reminder"));
            }
        }, 20L, 20L);
    }

    // ---------- хранение ----------

    public void load() {
        if (!file.exists()) return;
        YamlConfiguration yc = YamlConfiguration.loadConfiguration(file);
        spot = yc.getLocation("spot");
        ConfigurationSection sec = yc.getConfigurationSection("entries");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                Entry e = new Entry(sec.getString(key + ".name", "?"), sec.getLocation(key + ".back"));
                e.released = sec.getBoolean(key + ".released");
                e.pendingSpot = sec.getBoolean(key + ".pending-spot");
                e.prize = sec.getBoolean(key + ".prize");
                entries.put(id, e);
            } catch (IllegalArgumentException ignored) {
                // битая запись
            }
        }
    }

    public void save() {
        YamlConfiguration yc = new YamlConfiguration();
        yc.set("spot", spot);
        for (Map.Entry<UUID, Entry> en : entries.entrySet()) {
            String k = "entries." + en.getKey();
            Entry e = en.getValue();
            yc.set(k + ".name", e.name);
            yc.set(k + ".back", e.back);
            yc.set(k + ".released", e.released);
            yc.set(k + ".pending-spot", e.pendingSpot);
            yc.set(k + ".prize", e.prize);
        }
        try {
            plugin.getDataFolder().mkdirs();
            yc.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Не удалось сохранить cheaters.yml: " + ex.getMessage());
        }
    }

    /** При выключении сервера - вернуть тех, кто в этот момент в анимации. */
    public void shutdown() {
        for (UUID id : new ArrayList<>(processing)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) abort(p);
        }
        save();
    }

    public List<String> namesInCheck() {
        List<String> out = new ArrayList<>();
        for (Entry e : entries.values()) {
            if (!e.released) out.add(e.name);
        }
        return out;
    }
}
