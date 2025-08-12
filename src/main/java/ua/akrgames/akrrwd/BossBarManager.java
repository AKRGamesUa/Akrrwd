package ua.akrgames.akrrwd;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import net.md_5.bungee.api.ChatColor;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class BossBarManager {

    private final Akrrwd plugin;
    private final Map<Player, BossBar> activeBossBars;
    private BarColor barColor;
    private BarStyle barStyle;
    private String barText;
    private String pluginPrefix;

    public BossBarManager(Akrrwd plugin) {
        this.plugin = plugin;
        this.activeBossBars = new HashMap<>();
    }

    public void loadConfig(FileConfiguration config) {
        barColor = BarColor.valueOf(config.getString("bossbar.color", "BLUE").toUpperCase());
        barStyle = BarStyle.valueOf(config.getString("bossbar.style", "SOLID").toUpperCase());
        barText = config.getString("bossbar.text", "Cooldown: %days%, %hours%, %minutes%, %seconds%");
        pluginPrefix = config.getString("plugin-prefix", "&6[Akrrwd] ");
    }

    public boolean toggleBossBar(Player player, long currentTime, long lastUsed, long cooldownTime) {
        if (activeBossBars.containsKey(player)) {
            removeBossBar(player);
            return false;
        } else {
            showBossBar(player, currentTime, lastUsed, cooldownTime);
            return true;
        }
    }

    public boolean isBossBarActive(Player player) {
        return activeBossBars.containsKey(player);
    }

    public void showBossBar(Player player, long currentTime, long lastUsed, long cooldownTime) {
        BossBar bossBar = Bukkit.createBossBar("", barColor, barStyle);
        activeBossBars.put(player, bossBar);
        bossBar.addPlayer(player);
        updateBossBar(player, currentTime, lastUsed, cooldownTime);
    }

    public void updateBossBar(Player player, long currentTime, long lastUsed, long cooldownTime) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !activeBossBars.containsKey(player)) {
                    removeBossBar(player);
                    cancel();
                    return;
                }

                BossBar bossBar = activeBossBars.get(player);
                if (bossBar == null) {
                    bossBar = Bukkit.createBossBar("", barColor, barStyle);
                    activeBossBars.put(player, bossBar);
                    bossBar.addPlayer(player);
                }

                long remaining = cooldownTime - (System.currentTimeMillis() - lastUsed);
                bossBar.setProgress(remaining <= 0 ? 0 : Math.max(0, Math.min(1, (double) remaining / cooldownTime)));
                bossBar.setTitle(formatCooldown(remaining));
                if (remaining <= 0) {
                    removeBossBar(player);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // Update every second (20 ticks)
    }

    public void removeBossBar(Player player) {
        BossBar bossBar = activeBossBars.remove(player);
        if (bossBar != null) {
            bossBar.removeAll();
        }
    }

    public void removeAllBossBars() {
        activeBossBars.values().forEach(BossBar::removeAll);
        activeBossBars.clear();
    }

    public void reloadBossBars(FileConfiguration cooldownConfig, long cooldownTime) {
        Map<Player, Long> playerCooldowns = new HashMap<>();
        for (Player player : activeBossBars.keySet()) {
            if (player.isOnline()) {
                String uuid = player.getUniqueId().toString();
                long lastUsed = cooldownConfig.getLong(uuid + ".timestamp", 0);
                playerCooldowns.put(player, lastUsed);
            }
        }
        removeAllBossBars();
        long currentTime = System.currentTimeMillis();
        for (Map.Entry<Player, Long> entry : playerCooldowns.entrySet()) {
            Player player = entry.getKey();
            long lastUsed = entry.getValue();
            showBossBar(player, currentTime, lastUsed, cooldownTime);
        }
    }

    private String formatCooldown(long remainingMillis) {
        long seconds = remainingMillis / 1000;
        long days = seconds / 86400;
        seconds %= 86400;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;
        String text = barText.replace("%days%", String.valueOf(days < 0 ? 0 : days))
                .replace("%hours%", String.valueOf(hours < 0 ? 0 : hours))
                .replace("%minutes%", String.valueOf(minutes < 0 ? 0 : minutes))
                .replace("%seconds%", String.valueOf(seconds < 0 ? 0 : seconds));
        return colorize(text);
    }

    private String colorize(String message) {
        if (message == null) return "";
        String processedMessage = org.bukkit.ChatColor.translateAlternateColorCodes('&', message);
        processedMessage = processSolidColors(processedMessage);
        processedMessage = processGradients(processedMessage);
        processedMessage = processedMessage.replace("%prefix%", pluginPrefix != null ? pluginPrefix : "&6[Akrrwd] ");
        return processedMessage;
    }

    private String processSolidColors(String message) {
        Pattern pattern = Pattern.compile("<SOLID:#([0-9A-Fa-f]{6})>(.*?)(?=<SOLID:#[0-9A-Fa-f]{6}>|$)");
        var matcher = pattern.matcher(message);
        var result = new StringBuilder();

        int lastEnd = 0;
        while (matcher.find()) {
            result.append(message, lastEnd, matcher.start());
            String hexColor = "#" + matcher.group(1);
            String text = matcher.group(2);
            ChatColor color = ChatColor.of(hexColor);
            result.append(color.toString()).append(text);
            lastEnd = matcher.end();
        }

        result.append(message.substring(lastEnd));
        return result.toString();
    }

    private String processGradients(String message) {
        Pattern pattern = Pattern.compile("<GRADIENT:#([0-9A-Fa-f]{6})>(.*?)</GRADIENT:#([0-9A-Fa-f]{6})>");
        var matcher = pattern.matcher(message);
        var result = new StringBuilder();

        int lastEnd = 0;
        while (matcher.find()) {
            result.append(message, lastEnd, matcher.start());
            String startHex = "#" + matcher.group(1);
            String text = matcher.group(2);
            String endHex = "#" + matcher.group(3);
            String gradientText = createGradient(text, startHex, endHex);
            result.append(gradientText);
            lastEnd = matcher.end();
        }

        result.append(message.substring(lastEnd));
        return result.toString();
    }

    private String createGradient(String text, String startHex, String endHex) {
        String trimmedText = text.trim();
        if (trimmedText.isEmpty()) return text;

        int[] startColor = hexToRGB(startHex);
        int[] endColor = hexToRGB(endHex);

        int length = trimmedText.length();
        StringBuilder result = new StringBuilder();

        int leadingSpaces = text.indexOf(trimmedText);
        int trailingSpaces = text.length() - trimmedText.length() - leadingSpaces;
        result.append(" ".repeat(leadingSpaces));

        for (int i = 0; i < length; i++) {
            float fraction = length > 1 ? (float) i / (length - 1) : 0;
            int r = (int) lerp(startColor[0], endColor[0], fraction);
            int g = (int) lerp(startColor[1], endColor[1], fraction);
            int b = (int) lerp(startColor[2], endColor[2], fraction);
            ChatColor color = ChatColor.of(String.format("#%02x%02x%02x", r, g, b));
            result.append(color.toString()).append(trimmedText.charAt(i));
        }

        result.append(" ".repeat(trailingSpaces));
        return result.toString();
    }

    private float lerp(float start, float end, float fraction) {
        return start + fraction * (end - start);
    }

    private int[] hexToRGB(String hex) {
        String color = hex.startsWith("#") ? hex.substring(1) : hex;
        int r = Integer.parseInt(color.substring(0, 2), 16);
        int g = Integer.parseInt(color.substring(2, 4), 16);
        int b = Integer.parseInt(color.substring(4, 6), 16);
        return new int[]{r, g, b};
    }
}