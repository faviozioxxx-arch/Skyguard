package me.skyguard;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Locale;

/**
 * Статистический анти-XRay.
 * Считает, сколько СКРЫТЫХ руд (закрытых породой со всех сторон, кроме тоннеля игрока)
 * игрок добыл относительно количества сломанной породы. Честный игрок находит скрытые руды редко,
 * а с X-Ray - почти каждый блок прицельно.
 */
public final class XRayListener implements Listener {

    private static final BlockFace[] FACES = {
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH,
            BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private final SkyGuard plugin;

    public XRayListener(SkyGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Settings st = plugin.settings();
        if (!st.xrayEnabled) return;

        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.SURVIVAL) return;
        if (p.hasPermission("skyguard.bypass")) return;
        if (plugin.cheaters().isTracked(p.getUniqueId())) return;

        Block b = e.getBlock();
        Material m = b.getType();
        PlayerData d = plugin.data(p);
        long now = System.currentTimeMillis();

        // окно статистики
        if (now - d.xrayWindowStart > st.xrayWindowSeconds * 1000L) {
            d.xrayWindowStart = now;
            d.rock = 0;
            d.hiddenOres = 0;
        }

        // порода
        if (Tag.BASE_STONE_OVERWORLD.isTagged(m) || Tag.BASE_STONE_NETHER.isTagged(m)) {
            d.rock++;
            return;
        }

        // не интересующий нас блок
        if (!st.xrayOres.contains(m)) return;

        // одна жила = одна руда (иначе жила из 5 алмазов дала бы 5 "находок")
        Location loc = b.getLocation();
        boolean sameVein = d.lastOre != null
                && d.lastOre.getWorld() == loc.getWorld()
                && d.lastOre.distanceSquared(loc) <= 25
                && now - d.lastOreMs < 60_000;
        d.lastOre = loc;
        d.lastOreMs = now;
        if (sameVein) return;

        // скрытая руда: открыта максимум с одной стороны (откуда копал игрок)
        int open = 0;
        for (BlockFace f : FACES) {
            Block n = b.getRelative(f);
            if (n.getType().isAir() || n.isLiquid() || n.isPassable()) open++;
        }
        if (open > 1) return;

        d.hiddenOres++;
        double ratio = d.hiddenOres / (double) Math.max(d.rock, 1);

        if (d.hiddenOres >= st.xrayMinOres && ratio > st.xrayMaxRatio) {
            plugin.flag(p, "XRay", 2.5, String.format(Locale.US,
                    "скрытых руд: %d на %d блоков породы (%.1f%%)",
                    d.hiddenOres, d.rock, ratio * 100));
        }
    }
}
