package ua.akrgames.akrrwd;

import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.ChatMessageType;
import org.bstats.bukkit.Metrics;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

public class Akrrwd extends JavaPlugin {

    private File cooldownFile;
    private FileConfiguration cooldownConfig;
    private File messagesFile;
    private FileConfiguration messagesConfig;
    private Map<String, Reward> actions;
    private long cooldownTime;
    private BossBarManager bossBarManager;
    private String pluginPrefix;
    private boolean titleEnabled;
    private String titleText;
    private String subtitleText;
    private int titleFadeIn;
    private int titleStay;
    private int titleFadeOut;
    private boolean actionBarEnabled;
    private String actionBarText;
    private boolean cooldownExpiredMessageEnabled;
    private String cooldownExpiredMessage;
    private Sound successSound;
    private float successSoundVolume;
    private float successSoundPitch;
    private Sound failureSound;
    private float failureSoundVolume;
    private float failureSoundPitch;
    private String language;
    private boolean bStatsEnabled;

    private static class Reward {
        List<String> commands;
        String description;

        Reward(List<String> commands, String description) {
            this.commands = commands;
            this.description = description;
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        setupMessagesFile();
        bossBarManager = new BossBarManager(this);
        loadConfig();
        setupCooldownFile();
        getCommand("randomreward").setExecutor(new RandomRewardCommand());
        getCommand("randomreward").setTabCompleter(new CmdTabCompleter());
        getCommand("akrrwd").setExecutor(new ReloadCommand());
        getCommand("akrrwd").setTabCompleter(new CmdTabCompleter());
        startCooldownCheckTask();

        // Initialize bStats if enabled
        if (bStatsEnabled) {
            Metrics metrics = new Metrics(this, 26837); // Replace 12345 with your bStats plugin ID
            getLogger().info("bStats metrics enabled.");
        } else {
            getLogger().info("bStats metrics disabled in config.");
        }
    }

    private void startCooldownCheckTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!cooldownExpiredMessageEnabled) return;
                long currentTime = System.currentTimeMillis();
                for (Player player : getServer().getOnlinePlayers()) {
                    String uuid = player.getUniqueId().toString();
                    long lastUsed = cooldownConfig.getLong(uuid + ".timestamp", 0);
                    if (lastUsed > 0 && (currentTime - lastUsed >= cooldownTime)) {
                        String playerName = cooldownConfig.getString(uuid + ".playerName", player.getName());
                        String message = getMessage("cooldown-expired", "%player%", playerName);
                        player.sendMessage(message);
                        // Clear the timestamp to prevent repeated messages
                        cooldownConfig.set(uuid + ".timestamp", null);
                        saveCooldownFile();
                    }
                }
            }
        }.runTaskTimer(this, 20L, 20L); // Check every second (20 ticks)
    }

    private void loadConfig() {
        actions = new HashMap<>();
        FileConfiguration config = getConfig();
        cooldownTime = config.getLong("cooldown", 3600) * 1000; // Convert to milliseconds
        for (String key : config.getConfigurationSection("actions").getKeys(false)) {
            List<String> commands = config.getStringList("actions." + key + ".commands");
            String description = config.getString("actions." + key + ".description", key);
            actions.put(key, new Reward(commands, description));
        }
        bossBarManager.loadConfig(config);
        language = config.getString("language", "en").toLowerCase();
        if (!List.of("en", "ua", "pl").contains(language)) {
            getLogger().warning("Invalid language '" + language + "' in config.yml. Defaulting to 'en'.");
            language = "en";
        }
        String prefixFromConfig = messagesConfig.getString(language + ".plugin-prefix");
        pluginPrefix = colorize(prefixFromConfig != null ? prefixFromConfig : "&6[Akrrwd] ");
        titleEnabled = messagesConfig.getBoolean(language + ".title-enabled", true);
        titleText = messagesConfig.getString(language + ".title-text", "&aReward Received!");
        subtitleText = messagesConfig.getString(language + ".subtitle-text", "<GRADIENT:#00FF00>%reward%</GRADIENT:#00FFFF>");
        titleFadeIn = config.getInt("title.fade-in", 10);
        titleStay = config.getInt("title.stay", 70);
        titleFadeOut = config.getInt("title.fade-out", 20);
        actionBarEnabled = messagesConfig.getBoolean(language + ".actionbar-enabled", true);
        actionBarText = messagesConfig.getString(language + ".actionbar-text", "<GRADIENT:#00FF00>You received: %reward%!</GRADIENT:#00FFFF>");
        cooldownExpiredMessageEnabled = messagesConfig.getBoolean(language + ".cooldown-expired-enabled", true);
        cooldownExpiredMessage = messagesConfig.getString(language + ".cooldown-expired", "<GRADIENT:#00FF00>%player%, your cooldown has expired! Use /randomreward to claim a reward!</GRADIENT:#00FFFF>");

        // Load bStats configuration
        bStatsEnabled = config.getBoolean("bstats-enabled", true);

        // Load sound configurations
        String successSoundName = config.getString("sounds.success.sound", "ENTITY_EXPERIENCE_ORB_PICKUP");
        try {
            successSound = Sound.valueOf(successSoundName.toUpperCase());
        } catch (IllegalArgumentException e) {
            getLogger().warning("Invalid success sound: " + successSoundName + ". Defaulting to ENTITY_EXPERIENCE_ORB_PICKUP.");
            successSound = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
        }
        successSoundVolume = (float) config.getDouble("sounds.success.volume", 1.0);
        successSoundPitch = (float) config.getDouble("sounds.success.pitch", 1.0);

        String failureSoundName = config.getString("sounds.failure.sound", "BLOCK_ANVIL_LAND");
        try {
            failureSound = Sound.valueOf(failureSoundName.toUpperCase());
        } catch (IllegalArgumentException e) {
            getLogger().warning("Invalid failure sound: " + failureSoundName + ". Defaulting to BLOCK_ANVIL_LAND.");
            failureSound = Sound.BLOCK_ANVIL_LAND;
        }
        failureSoundVolume = (float) config.getDouble("sounds.failure.volume", 1.0);
        failureSoundPitch = (float) config.getDouble("sounds.failure.pitch", 1.0);
    }

    private void setupCooldownFile() {
        cooldownFile = new File(getDataFolder(), "cooldowns.yml");
        if (!cooldownFile.exists()) {
            try {
                cooldownFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        cooldownConfig = YamlConfiguration.loadConfiguration(cooldownFile);
    }

    private void setupMessagesFile() {
        messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
    }

    private void saveCooldownFile() {
        try {
            cooldownConfig.save(cooldownFile);
        } catch (IOException e) {
            e.printStackTrace();
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
        return "%days% days, %hours% hours, %minutes% minutes, %seconds% seconds"
                .replace("%days%", String.valueOf(days < 0 ? 0 : days))
                .replace("%hours%", String.valueOf(hours < 0 ? 0 : hours))
                .replace("%minutes%", String.valueOf(minutes < 0 ? 0 : minutes))
                .replace("%seconds%", String.valueOf(seconds < 0 ? 0 : seconds));
    }

    private String getMessage(String key, String... replacements) {
        String message = messagesConfig.getString(language + "." + key);
        if (message == null) {
            message = messagesConfig.getString("en." + key, "Message not found: " + key);
            if (message == null) {
                message = "Message not found: " + key;
                getLogger().warning("Message key '" + key + "' not found in language '" + language + "' or fallback 'en'.");
            }
        }
        // Replace placeholders before colorizing
        for (int i = 0; i < replacements.length; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }
        return colorize(message);
    }

    private String colorize(String message) {
        if (message == null) return "";
        String processedMessage = ChatColor.translateAlternateColorCodes('&', message);
        processedMessage = processSolidColors(processedMessage);
        processedMessage = processGradients(processedMessage);
        processedMessage = processedMessage.replace("%prefix%", pluginPrefix != null ? pluginPrefix : "&6[Akrrwd] ");
        return processedMessage;
    }

    private String processSolidColors(String message) {
        Pattern pattern = Pattern.compile("<SOLID:#([0-9A-Fa-f]{6})>(.*?)(?=<SOLID:#[0-9A-Fa-f]{6}>|$)");
        var matcher = pattern.matcher(message);
        StringBuilder result = new StringBuilder();

        int lastEnd = 0;
        while (matcher.find()) {
            result.append(message, lastEnd, matcher.start());
            String hexColor = "#" + matcher.group(1);
            String text = matcher.group(2);
            net.md_5.bungee.api.ChatColor color = net.md_5.bungee.api.ChatColor.of(hexColor);
            result.append(color.toString()).append(text);
            lastEnd = matcher.end();
        }

        result.append(message.substring(lastEnd));
        return result.toString();
    }

    private String processGradients(String message) {
        Pattern pattern = Pattern.compile("<GRADIENT:#([0-9A-Fa-f]{6})>(.*?)</GRADIENT:#([0-9A-Fa-f]{6})>");
        var matcher = pattern.matcher(message);
        StringBuilder result = new StringBuilder();

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
            net.md_5.bungee.api.ChatColor color = net.md_5.bungee.api.ChatColor.of(String.format("#%02x%02x%02x", r, g, b));
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

    class RandomRewardCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(getMessage("player-only"));
                return true;
            }

            Player player = (Player) sender;
            String uuid = player.getUniqueId().toString();
            String playerName = player.getName();
            long currentTime = System.currentTimeMillis();
            long lastUsed = cooldownConfig.getLong(uuid + ".timestamp", 0);

            if (currentTime - lastUsed < cooldownTime) {
                long remaining = cooldownTime - (currentTime - lastUsed);
                player.sendMessage(getMessage("cooldown", "%time%", formatCooldown(remaining)));
                player.playSound(player.getLocation(), failureSound, failureSoundVolume, failureSoundPitch);
                return true;
            }

            String[] actionKeys = actions.keySet().toArray(new String[0]);
            String randomAction = actionKeys[ThreadLocalRandom.current().nextInt(actionKeys.length)];
            Reward reward = actions.get(randomAction);

            for (String cmd : reward.commands) {
                getServer().dispatchCommand(getServer().getConsoleSender(), cmd.replace("%player%", playerName));
            }

            // Send title and subtitle if enabled
            if (titleEnabled) {
                String formattedTitle = getMessage("title-text", "%player%", playerName, "%reward%", reward.description);
                String formattedSubtitle = getMessage("subtitle-text", "%player%", playerName, "%reward%", reward.description);
                player.sendTitle(formattedTitle, formattedSubtitle, titleFadeIn, titleStay, titleFadeOut);
            }

            // Send action bar message if enabled
            if (actionBarEnabled) {
                String formattedActionBar = getMessage("actionbar-text", "%player%", playerName, "%reward%", reward.description);
                BaseComponent[] components = TextComponent.fromLegacyText(formattedActionBar);
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, components);
            }

            // Play success sound
            player.playSound(player.getLocation(), successSound, successSoundVolume, successSoundPitch);

            player.sendMessage(getMessage("reward-received", "%reward%", colorize(reward.description)));
            cooldownConfig.set(uuid + ".timestamp", currentTime);
            cooldownConfig.set(uuid + ".playerName", playerName);
            cooldownConfig.set(uuid + ".formattedTime", formatCooldown(cooldownTime));
            saveCooldownFile();
            bossBarManager.updateBossBar(player, currentTime, lastUsed, cooldownTime);

            return true;
        }
    }

    class ReloadCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (args.length == 0) {
                sender.sendMessage(getMessage("invalid-usage"));
                return true;
            }

            if (args[0].equalsIgnoreCase("help")) {
                sender.sendMessage(getMessage("help"));
                return true;
            }

            if (args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("akrrwd.reload")) {
                    sender.sendMessage(getMessage("no-permission"));
                    return true;
                }
                reloadConfig();
                setupMessagesFile();
                setupCooldownFile();
                loadConfig();
                bossBarManager.reloadBossBars(cooldownConfig, cooldownTime);
                sender.sendMessage(getMessage("reload-success"));
                return true;
            }

            if (args[0].equalsIgnoreCase("reset") && args.length == 2) {
                if (!sender.hasPermission("akrrwd.reset")) {
                    sender.sendMessage(getMessage("no-permission"));
                    return true;
                }
                String target = args[1];
                if (target.equalsIgnoreCase("all")) {
                    cooldownConfig.getKeys(false).forEach(key -> cooldownConfig.set(key, null));
                    saveCooldownFile();
                    bossBarManager.removeAllBossBars();
                    sender.sendMessage(getMessage("cooldown-reset-all"));
                    return true;
                }

                String targetPlayer = target;
                Player onlinePlayer = getServer().getPlayerExact(targetPlayer);
                String uuid = null;
                if (onlinePlayer != null) {
                    uuid = onlinePlayer.getUniqueId().toString();
                } else {
                    for (String key : cooldownConfig.getKeys(false)) {
                        if (cooldownConfig.getString(key + ".playerName", "").equalsIgnoreCase(targetPlayer)) {
                            uuid = key;
                            break;
                        }
                    }
                }

                if (uuid == null) {
                    sender.sendMessage(getMessage("player-not-found", "%player%", targetPlayer));
                    return true;
                }

                cooldownConfig.set(uuid, null);
                saveCooldownFile();
                bossBarManager.removeBossBar(onlinePlayer);
                sender.sendMessage(getMessage("cooldown-reset", "%player%", targetPlayer));
                return true;
            }

            if (args[0].equalsIgnoreCase("settime") && args.length == 3) {
                if (!sender.hasPermission("akrrwd.settime")) {
                    sender.sendMessage(getMessage("no-permission"));
                    return true;
                }
                String targetPlayer = args[1];
                String timeInput = args[2];
                String[] timeParts = timeInput.split(",");
                if (timeParts.length != 4) {
                    sender.sendMessage(getMessage("invalid-time-format"));
                    return true;
                }

                try {
                    long days = Long.parseLong(timeParts[0].trim());
                    long hours = Long.parseLong(timeParts[1].trim());
                    long minutes = Long.parseLong(timeParts[2].trim());
                    long seconds = Long.parseLong(timeParts[3].trim());
                    long totalMillis = ((days * 86400) + (hours * 3600) + (minutes * 60) + seconds) * 1000;

                    if (totalMillis < 0) {
                        sender.sendMessage(getMessage("invalid-time-negative"));
                        return true;
                    }

                    Player onlinePlayer = getServer().getPlayerExact(targetPlayer);
                    String uuid = null;
                    if (onlinePlayer != null) {
                        uuid = onlinePlayer.getUniqueId().toString();
                    } else {
                        for (String key : cooldownConfig.getKeys(false)) {
                            if (cooldownConfig.getString(key + ".playerName", "").equalsIgnoreCase(targetPlayer)) {
                                uuid = key;
                                break;
                            }
                        }
                    }

                    if (uuid == null) {
                        sender.sendMessage(getMessage("player-not-found", "%player%", targetPlayer));
                        return true;
                    }

                    long currentTime = System.currentTimeMillis();
                    long newLastUsed = currentTime - (cooldownTime - totalMillis);
                    cooldownConfig.set(uuid + ".timestamp", newLastUsed);
                    cooldownConfig.set(uuid + ".playerName", targetPlayer);
                    cooldownConfig.set(uuid + ".formattedTime", formatCooldown(totalMillis));
                    saveCooldownFile();
                    if (onlinePlayer != null && bossBarManager.isBossBarActive(onlinePlayer)) {
                        bossBarManager.removeBossBar(onlinePlayer);
                        bossBarManager.updateBossBar(onlinePlayer, currentTime, newLastUsed, cooldownTime);
                    }
                    sender.sendMessage(getMessage("cooldown-set", "%player%", targetPlayer, "%time%", formatCooldown(totalMillis)));
                    return true;
                } catch (NumberFormatException e) {
                    sender.sendMessage(getMessage("invalid-time-format"));
                    return true;
                }
            }

            if (args[0].equalsIgnoreCase("toggle") && args.length == 1) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(getMessage("player-only"));
                    return true;
                }
                Player player = (Player) sender;
                String uuid = player.getUniqueId().toString();
                long currentTime = System.currentTimeMillis();
                long lastUsed = cooldownConfig.getLong(uuid + ".timestamp", 0);

                if (bossBarManager.toggleBossBar(player, currentTime, lastUsed, cooldownTime)) {
                    sender.sendMessage(getMessage("bossbar-enabled"));
                } else {
                    sender.sendMessage(getMessage("bossbar-disabled"));
                }
                return true;
            }

            sender.sendMessage(getMessage("invalid-usage"));
            return true;
        }
    }
}