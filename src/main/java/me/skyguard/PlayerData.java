package me.skyguard;

import org.bukkit.Location;

import java.util.ArrayDeque;

/** Состояние античита для одного игрока. */
public final class PlayerData {

    // окно проверки скорости
    public Location anchor;
    public long anchorNs;

    // проверка полёта
    public double lastY;
    public int hover;

    // нарушения и слежка
    public double vl;
    public long lastVlUpdate = System.currentTimeMillis();
    public boolean watched;
    public long watchedUntil;
    public long lastAlert;

    // исключения
    public long exemptUntil = System.currentTimeMillis() + 3000;
    public long iceUntil;

    // X-Ray
    public long xrayWindowStart;
    public int rock;
    public int hiddenOres;
    public Location lastOre;
    public long lastOreMs;

    // клики
    public final ArrayDeque<Long> clicks = new ArrayDeque<>();

    /** Временно не проверять игрока (телепорт, отбрасывание, элитры и т.д.). */
    public void exempt(long ms) {
        exemptUntil = Math.max(exemptUntil, System.currentTimeMillis() + ms);
    }

    public void reset(Location loc, long nowNs) {
        anchor = loc;
        anchorNs = nowNs;
        lastY = loc.getY();
        hover = 0;
    }
}
