package me.skyguard;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Locale;
import java.util.UUID;

/** Пока игрок ждёт проверки: бессмертен, не может бить/ломать/строить, не может уйти с точки. */
public final class CheaterProtectionListener implements Listener {

    private final SkyGuard plugin;

    public CheaterProtectionListener(SkyGuard plugin) {
        this.plugin = plugin;
    }

    /** Анимация или ожидание проверки. */
    private boolean locked(Entity e) {
        return e instanceof Player p && plugin.cheaters().isTracked(p.getUniqueId());
    }

    private void blocked(Player p) {
        p.sendMessage(plugin.text("blocked"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamaged(EntityDamageEvent e) {
        if (locked(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDealDamage(EntityDamageByEntityEvent e) {
        Entity damager = e.getDamager();
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Entity shooter) damager = shooter;
        if (locked(damager)) {
            e.setCancelled(true);
            blocked((Player) damager);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent e) {
        if (locked(e.getPlayer())) {
            e.setCancelled(true);
            blocked(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlace(BlockPlaceEvent e) {
        if (locked(e.getPlayer())) {
            e.setCancelled(true);
            blocked(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent e) {
        if (locked(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (locked(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent e) {
        if (locked(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPickup(EntityPickupItemEvent e) {
        if (locked(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFood(FoodLevelChangeEvent e) {
        if (locked(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!locked(p)) return;
        String msg = e.getMessage();
        if (msg.length() < 2) return;
        String label = msg.substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) label = label.substring(colon + 1);
        if (!plugin.settings().allowedCommands.contains(label)) {
            e.setCancelled(true);
            blocked(p);
        }
    }

    /** Держим игрока на точке проверки (только когда он уже прибыл, не во время анимации). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!e.hasChangedPosition()) return;
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        if (!plugin.cheaters().isInCheck(id) || plugin.cheaters().isProcessing(id)) return;

        double r = plugin.settings().quarantineRadius;
        Location spot = plugin.cheaters().spot();
        if (r <= 0 || spot == null || spot.getWorld() == null) return;

        Location to = e.getTo();
        if (to.getWorld() != spot.getWorld() || to.distanceSquared(spot) > r * r) {
            e.setTo(spot.clone());
        }
    }
}
