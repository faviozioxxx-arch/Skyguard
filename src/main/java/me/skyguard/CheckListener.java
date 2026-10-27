package me.skyguard;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.util.BoundingBox;

import java.util.Locale;

/** Исключения для проверок (чтобы не банить за отбрасывание, телепорт и т.д.), а также Reach и AutoClicker. */
public final class CheckListener implements Listener {

    private final SkyGuard plugin;

    public CheckListener(SkyGuard plugin) {
        this.plugin = plugin;
    }

    // ---------- вход / выход ----------

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        plugin.data(e.getPlayer()).exempt(3000);
        plugin.cheaters().onJoin(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.cheaters().onQuit(e.getPlayer());
        plugin.removeData(e.getPlayer().getUniqueId());
    }

    // ---------- исключения ----------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent e) {
        plugin.data(e.getPlayer()).exempt(2500);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        plugin.data(e.getPlayer()).exempt(3000);
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent e) {
        plugin.data(e.getPlayer()).exempt(3000);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVelocity(PlayerVelocityEvent e) {
        // отбрасывание, взрывы, ветряной заряд, удочка и т.п.
        plugin.data(e.getPlayer()).exempt(2500);
    }

    @EventHandler
    public void onGlide(EntityToggleGlideEvent e) {
        if (e.getEntity() instanceof Player p) plugin.data(p).exempt(3000);
    }

    @EventHandler
    public void onRiptide(PlayerRiptideEvent e) {
        plugin.data(e.getPlayer()).exempt(4000);
    }

    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent e) {
        plugin.data(e.getPlayer()).exempt(3000);
    }

    @EventHandler
    public void onGameMode(PlayerGameModeChangeEvent e) {
        plugin.data(e.getPlayer()).exempt(3000);
    }

    @EventHandler
    public void onVehicleExit(VehicleExitEvent e) {
        if (e.getExited() instanceof Player p) plugin.data(p).exempt(2500);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamaged(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p) plugin.data(p).exempt(800);
    }

    // ---------- AutoClicker ----------

    @EventHandler(ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent e) {
        if (e.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Player p = e.getPlayer();
        PlayerData d = plugin.data(p);

        // Копья 1.21.11 (рывок/Lunge) двигают игрока - не считаем это скоростью
        if (holdsSpear(p)) d.exempt(2500);

        if (!plugin.settings().cpsEnabled) return;
        if (p.hasPermission("skyguard.bypass")) return;

        long now = System.currentTimeMillis();
        d.clicks.addLast(now);
        while (!d.clicks.isEmpty() && now - d.clicks.peekFirst() > 1000) d.clicks.pollFirst();

        if (d.clicks.size() > plugin.settings().maxCps) {
            plugin.flag(p, "AutoClicker", 1, d.clicks.size() + " CPS");
            d.clicks.clear();
        }
    }

    // ---------- Reach ----------

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        Settings st = plugin.settings();
        if (!st.reachEnabled) return;
        if (!(e.getDamager() instanceof Player p)) return;
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        if (p.hasPermission("skyguard.bypass")) return;
        GameMode gm = p.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return;
        if (holdsSpear(p)) return; // у копья своя дальность

        Entity victim = e.getEntity();
        if (victim.getWorld() != p.getWorld()) return;

        double allowed = 3.0;
        AttributeInstance a = p.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        if (a != null) allowed = a.getValue();

        double dist = distanceToBox(p.getEyeLocation(), victim.getBoundingBox());
        if (dist > allowed + st.reachTolerance) {
            plugin.flag(p, "Reach", 1.5,
                    String.format(Locale.US, "%.2f блока (лимит %.2f)", dist, allowed + st.reachTolerance));
            if (st.reachCancel) e.setCancelled(true);
        }
    }

    private static double distanceToBox(Location eye, BoundingBox bb) {
        double x = Math.max(bb.getMinX(), Math.min(eye.getX(), bb.getMaxX()));
        double y = Math.max(bb.getMinY(), Math.min(eye.getY(), bb.getMaxY()));
        double z = Math.max(bb.getMinZ(), Math.min(eye.getZ(), bb.getMaxZ()));
        double dx = eye.getX() - x, dy = eye.getY() - y, dz = eye.getZ() - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static boolean holdsSpear(Player p) {
        return p.getInventory().getItemInMainHand().getType().name().endsWith("_SPEAR");
    }
}
