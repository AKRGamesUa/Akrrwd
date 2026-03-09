package ua.akrgames.akrrwd;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Logger;

public class UpdateChecker implements Listener {

    // Replace with your actual SpigotMC resource ID.
    // Find it in your resource URL: https://www.spigotmc.org/resources/<name>.<ID>/
    private static final int SPIGOT_RESOURCE_ID = 0;

    private static final String SPIGOT_API_URL =
            "https://api.spigotmc.org/legacy/update.php?resource=" + SPIGOT_RESOURCE_ID;
    private static final String SPIGOT_PAGE_URL =
            "https://www.spigotmc.org/resources/" + SPIGOT_RESOURCE_ID;

    private final Akrrwd plugin;
    private final Logger logger;
    private final String currentVersion;

    // Cached result so we only hit the API once on startup
    private String latestVersion = null;
    private UpdateStatus status  = UpdateStatus.UNKNOWN;

    public enum UpdateStatus {
        UP_TO_DATE,
        UPDATE_AVAILABLE,
        UNKNOWN
    }

    public UpdateChecker(Akrrwd plugin) {
        this.plugin         = plugin;
        this.logger         = plugin.getLogger();
        this.currentVersion = plugin.getDescription().getVersion();
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Starts the async update check and registers the join listener.
     * Call this once from onEnable().
     */
    public void start() {
        if (SPIGOT_RESOURCE_ID == 0) {
            logger.warning("[AkrRwd] Update checker: SPIGOT_RESOURCE_ID is not set. " +
                    "Please set the correct resource ID in UpdateChecker.java.");
            return;
        }

        // Run the HTTP request off the main thread to avoid blocking startup
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            checkForUpdate();

            // Log result back on the main thread so we can safely read messagesConfig
            Bukkit.getScheduler().runTask(plugin, () -> logResult());
        });

        // Notify ops/admins with permission on join
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public UpdateStatus getStatus()         { return status; }
    public String       getLatestVersion()  { return latestVersion; }
    public String       getCurrentVersion() { return currentVersion; }

    // ─── Join notification ────────────────────────────────────────────────────

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (status != UpdateStatus.UPDATE_AVAILABLE) return;

        Player player = event.getPlayer();
        if (!player.hasPermission("akrrwd.update") && !player.isOp()) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            player.sendMessage(getMessage("update-notify-1"));
            player.sendMessage(getMessage("update-notify-2",
                    "%current%", currentVersion,
                    "%latest%",  latestVersion));
            player.sendMessage(getMessage("update-notify-3",
                    "%url%", SPIGOT_PAGE_URL));
        }, 40L);
    }

    // ─── Logging ─────────────────────────────────────────────────────────────

    private void logResult() {
        switch (status) {
            case UPDATE_AVAILABLE:
                logger.warning(getRawMessage("update-available-log-1"));
                logger.warning(getRawMessage("update-available-log-2"));
                logger.warning(getRawMessage("update-available-log-3",
                        "%current%", currentVersion));
                logger.warning(getRawMessage("update-available-log-4",
                        "%latest%",  latestVersion));
                logger.warning(getRawMessage("update-available-log-5",
                        "%url%", SPIGOT_PAGE_URL));
                logger.warning(getRawMessage("update-available-log-6"));
                break;
            case UP_TO_DATE:
                logger.info(getRawMessage("update-up-to-date",
                        "%current%", currentVersion));
                break;
            case UNKNOWN:
                logger.warning(getRawMessage("update-unknown"));
                break;
        }
    }

    // ─── Core HTTP check ─────────────────────────────────────────────────────

    private void checkForUpdate() {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(SPIGOT_API_URL).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("User-Agent", "AkrRwd-UpdateChecker/" + currentVersion);

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                logger.warning("[AkrRwd] Update checker: SpigotMC returned HTTP " + responseCode + ".");
                status = UpdateStatus.UNKNOWN;
                return;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream()))) {
                latestVersion = reader.readLine().trim();
            }

            if (latestVersion == null || latestVersion.isEmpty()) {
                status = UpdateStatus.UNKNOWN;
                return;
            }

            status = isNewerVersion(latestVersion, currentVersion)
                    ? UpdateStatus.UPDATE_AVAILABLE
                    : UpdateStatus.UP_TO_DATE;

        } catch (Exception e) {
            status = UpdateStatus.UNKNOWN;
            logger.warning("[AkrRwd] Update checker failed: " + e.getMessage());
        }
    }

    // ─── Message helpers ──────────────────────────────────────────────────────

    /**
     * Reads a message from messages.yml using the plugin's active language,
     * applies color codes and placeholders, then returns it ready for
     * player.sendMessage().
     */
    private String getMessage(String key, String... replacements) {
        return plugin.getMessagePublic(key, replacements);
    }

    /**
     * Same as getMessage() but strips Minecraft color codes — suitable for
     * logger output which does not render color codes.
     */
    private String getRawMessage(String key, String... replacements) {
        String colored = plugin.getMessagePublic(key, replacements);
        // Strip §x color codes so the console output is clean
        return colored.replaceAll("§[0-9a-fk-orA-FK-OR]", "")
                .replaceAll("§x(§[0-9a-fA-F]){6}", "");
    }

    // ─── Version comparison ───────────────────────────────────────────────────

    /**
     * Returns true if {@code remote} is strictly newer than {@code local}.
     */
    private boolean isNewerVersion(String remote, String local) {
        try {
            int[] r = parseVersion(remote);
            int[] l = parseVersion(local);
            int maxLen = Math.max(r.length, l.length);
            for (int i = 0; i < maxLen; i++) {
                int rv = i < r.length ? r[i] : 0;
                int lv = i < l.length ? l[i] : 0;
                if (rv > lv) return true;
                if (rv < lv) return false;
            }
            return false;
        } catch (NumberFormatException e) {
            return !remote.equalsIgnoreCase(local);
        }
    }

    private int[] parseVersion(String version) {
        if (version.startsWith("v") || version.startsWith("V")) {
            version = version.substring(1);
        }
        String[] parts = version.split("[.\\-]");
        int[] nums = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            nums[i] = Integer.parseInt(parts[i].trim());
        }
        return nums;
    }
}
