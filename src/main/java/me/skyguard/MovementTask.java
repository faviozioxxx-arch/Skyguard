package me.skyguard;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;

/** Раз в 5 тиков проверяет скорость (окно 1 сек реального времени) и "зависание" в воздухе. */
public final class MovementTask extends BukkitRunnable {

    private static final double BASE_WALK_BPS = 4.317;

    private static final Set<Material> ICE = EnumSet.of(
            Material.ICE, Material.PACKED_ICE, Material.BLUE_ICE, Material.FROSTED_ICE);

    private static final Set<Material> SUPPORT = EnumSet.of(
            Material.COBWEB, Material.POWDER_SNOW, Material.BUBBLE_COLUMN, Material.HONEY_BLOCK,
            Material.SCAFFOLDING, Material.SWEET_BERRY_BUSH, Material.WATER, Material.LAVA);

    private final SkyGuard plugin;
    private boolean errorLogged;

    public MovementTask(SkyGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        long nowMs = System.currentTimeMillis();
        long nowNs = System.nanoTime();
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                check(p, nowMs, nowNs);
            } catch (Exception ex) {
                if (!errorLogged) {
                    errorLogged = true;
                    plugin.getLogger().log(Level.WARNING, "Ошибка проверки движения (сообщается один раз)", ex);
                }
            }
        }
    }

    private void check(Player p, long nowMs, long nowNs) {
        Settings st = plugin.settings();
        PlayerData d = plugin.data(p);
        Location loc = p.getLocation();

        if (skip(p, d, nowMs)) {
            d.reset(loc, nowNs);
            return;
        }

        boolean strict = d.watched && nowMs < d.watchedUntil;

        // лёд даёт сильное ускорение - запоминаем на пару секунд
        Block feet = loc.getBlock();
        if (ICE.contains(feet.getType())
                || ICE.contains(feet.getRelative(0, -1, 0).getType())
                || ICE.contains(feet.getRelative(0, -2, 0).getType())) {
            d.iceUntil = nowMs + 2000;
        }

        // ---------- Speed ----------
        if (d.anchor == null || !Objects.equals(d.anchor.getWorld(), loc.getWorld())) {
            d.reset(loc, nowNs);
        } else if (st.speedEnabled) {
            double elapsed = (nowNs - d.anchorNs) / 1_000_000_000.0;
            if (elapsed >= 1.0) {
                double dist = Math.hypot(loc.getX() - d.anchor.getX(), loc.getZ() - d.anchor.getZ());
                double bps = dist / elapsed;
                double max = maxSpeed(p, d, nowMs, strict, st);
                if (bps > max) {
                    double over = bps / max;
                    plugin.flag(p, "Speed", over > 1.8 ? 3 : (over > 1.3 ? 2 : 1),
                            String.format(Locale.US, "%.1f б/с (лимит %.1f)", bps, max));
                }
                d.anchor = loc;
                d.anchorNs = nowNs;
            }
        }

        // ---------- Fly / Hover ----------
        if (!st.flyEnabled || flyExempt(p)) {
            d.hover = 0;
            d.lastY = loc.getY();
            return;
        }
        if (hasSupport(p, d, nowMs)) {
            d.hover = 0;
        } else {
            double dy = loc.getY() - d.lastY;
            if (dy > -0.1) d.hover++;
            else d.hover = 0;

            if (d.hover >= (strict ? 3 : 4)) {
                plugin.flag(p, "Fly", 1.5, "завис/поднимается в воздухе без опоры");
                d.hover = 2;
            }
        }
        d.lastY = loc.getY();
    }

    private double maxSpeed(Player p, PlayerData d, long nowMs, boolean strict, Settings st) {
        double ratio = 1.0;
        AttributeInstance a = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (a != null) ratio = Math.max(1.0, a.getValue() / 0.1); // учитывает зелья, спринт, Soul Speed
        double max = BASE_WALK_BPS * ratio * 1.35 * st.speedTolerance;
        if (nowMs < d.iceUntil) max *= st.iceMultiplier;
        if (strict) max *= st.strictFactor;
        return max;
    }

    private boolean skip(Player p, PlayerData d, long now) {
        if (p.hasPermission("skyguard.bypass")) return true;
        if (plugin.cheaters().isTracked(p.getUniqueId())) return true;
        if (p.isDead() || p.isSleeping()) return true;
        GameMode gm = p.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return true;
        if (p.getAllowFlight() || p.isFlying()) return true;
        if (p.isGliding() || p.isRiptiding() || p.isInsideVehicle()) return true;
        if (now < d.exemptUntil) return true;
        return p.hasPotionEffect(PotionEffectType.DOLPHINS_GRACE);
    }

    private boolean flyExempt(Player p) {
        return p.hasPotionEffect(PotionEffectType.LEVITATION)
                || p.hasPotionEffect(PotionEffectType.SLOW_FALLING)
                || p.hasPotionEffect(PotionEffectType.JUMP_BOOST);
    }

    /** Есть ли под/вокруг игрока что-то, на что можно опереться или что гасит падение. */
    private boolean hasSupport(Player p, PlayerData d, long nowMs) {
        BoundingBox box = p.getBoundingBox().expand(0.3, 0, 0.3).expandDirectional(0, -0.8, 0);
        World w = p.getWorld();
        int minX = (int) Math.floor(box.getMinX()), maxX = (int) Math.floor(box.getMaxX());
        int minY = (int) Math.floor(box.getMinY()), maxY = (int) Math.floor(box.getMaxY());
        int minZ = (int) Math.floor(box.getMinZ()), maxZ = (int) Math.floor(box.getMaxZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block b = w.getBlockAt(x, y, z);
                    Material m = b.getType();
                    if (m.isAir()) continue;
                    if (m == Material.SLIME_BLOCK || Tag.BEDS.isTagged(m)) {
                        d.exempt(4000); // отскок
                        return true;
                    }
                    if (b.isLiquid() || !b.isPassable() || SUPPORT.contains(m) || Tag.CLIMBABLE.isTagged(m)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
