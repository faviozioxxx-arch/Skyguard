package me.skyguard;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class Commands implements CommandExecutor, TabCompleter {

    private final SkyGuard plugin;

    public Commands(SkyGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "setcheater" -> setCheater(sender);
            case "nocheat" -> noCheat(sender, args);
            case "cheatconfirm" -> confirm(sender, args);
            case "skyguard" -> admin(sender, args);
            default -> {
                return false;
            }
        }
        return true;
    }

    // ---------- /setcheater ----------

    private void setCheater(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Эту команду можно использовать только в игре.");
            return;
        }
        Location l = p.getLocation();
        plugin.cheaters().setSpot(l);
        String where = String.format(Locale.US, "%s %.1f %.1f %.1f", l.getWorld().getName(), l.getX(), l.getY(), l.getZ());
        p.sendMessage(plugin.text("spot-set", "%loc%", where));
    }

    // ---------- /nocheat <ник> [noprize] ----------

    private void noCheat(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(plugin.legacy("&cИспользование: /nocheat <ник> [noprize]"));
            return;
        }
        boolean prize = !(args.length > 1 && args[1].equalsIgnoreCase("noprize"));
        if (!plugin.cheaters().release(args[0], prize)) {
            sender.sendMessage(plugin.text("not-in-check"));
            return;
        }
        plugin.alertStaff(plugin.text("cleared-staff", "%mod%", sender.getName(), "%player%", args[0]));
    }

    // ---------- /cheatconfirm <ник> [причина] ----------

    private void confirm(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(plugin.legacy("&cИспользование: /cheatconfirm <ник> [причина]"));
            return;
        }
        String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "читы";
        plugin.cheaters().confirm(args[0], reason, sender.getName());
    }

    // ---------- /skyguard ----------

    private void admin(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(plugin.legacy("&e/skyguard reload &7| &e/skyguard list &7| &e/skyguard vl <ник> &7| &e/skyguard send <ник>"));
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reload();
                sender.sendMessage(plugin.text("reloaded"));
            }
            case "list" -> {
                sender.sendMessage(plugin.legacy("&6Под наблюдением:"));
                long now = System.currentTimeMillis();
                boolean any = false;
                for (Map.Entry<UUID, PlayerData> en : plugin.allData().entrySet()) {
                    PlayerData d = en.getValue();
                    Player p = Bukkit.getPlayer(en.getKey());
                    if (p != null && d.watched && now < d.watchedUntil) {
                        any = true;
                        sender.sendMessage(plugin.legacy(String.format(Locale.US, " &f%s &7VL %.1f", p.getName(), d.vl)));
                    }
                }
                if (!any) sender.sendMessage(plugin.legacy(" &7никого"));
                sender.sendMessage(plugin.legacy("&6На проверке:"));
                List<String> names = plugin.cheaters().namesInCheck();
                sender.sendMessage(plugin.legacy(names.isEmpty() ? " &7никого" : " &f" + String.join("&7, &f", names)));
            }
            case "vl" -> {
                if (args.length < 2) {
                    sender.sendMessage(plugin.legacy("&cИспользование: /skyguard vl <ник>"));
                    return;
                }
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) {
                    sender.sendMessage(plugin.legacy("&cИгрок не в сети."));
                    return;
                }
                PlayerData d = plugin.data(t);
                sender.sendMessage(plugin.legacy(String.format(Locale.US, "&e%s: &fVL %.1f, наблюдение: %s",
                        t.getName(), d.vl, d.watched ? "да" : "нет")));
            }
            case "send" -> {
                if (args.length < 2) {
                    sender.sendMessage(plugin.legacy("&cИспользование: /skyguard send <ник> (ручной тест анимации)"));
                    return;
                }
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) {
                    sender.sendMessage(plugin.legacy("&cИгрок не в сети."));
                    return;
                }
                if (!plugin.cheaters().send(t, "ручная отправка: " + sender.getName())) {
                    sender.sendMessage(plugin.legacy("&cНе удалось (точка не задана или игрок уже на проверке)."));
                }
            }
            default -> sender.sendMessage(plugin.legacy("&cНеизвестная подкоманда."));
        }
    }

    // ---------- автодополнение ----------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        if (name.equals("nocheat") || name.equals("cheatconfirm")) {
            if (args.length == 1) out.addAll(plugin.cheaters().namesInCheck());
            else if (name.equals("nocheat") && args.length == 2) out.add("noprize");
        } else if (name.equals("skyguard")) {
            if (args.length == 1) {
                out.addAll(List.of("reload", "list", "vl", "send"));
            } else if (args.length == 2 && (args[0].equalsIgnoreCase("vl") || args[0].equalsIgnoreCase("send"))) {
                for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            }
        }
        String prefix = args.length > 0 ? args[args.length - 1].toLowerCase(Locale.ROOT) : "";
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }
}
