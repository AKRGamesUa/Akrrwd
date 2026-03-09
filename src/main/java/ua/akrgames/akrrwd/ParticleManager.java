package ua.akrgames.akrrwd;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Logger;

public class ParticleManager {

    private final Akrrwd plugin;
    private final Logger logger;

    // Main toggle
    private boolean particlesEnabled;
    private boolean useTemplate;
    private String templateName;

    // Custom particle settings (used when use_template: false)
    private Particle customParticleType;
    private int customCount;
    private double customOffsetX;
    private double customOffsetY;
    private double customOffsetZ;
    private double customSpeed;

    // Templates config
    private File templatesFile;
    private FileConfiguration templatesConfig;

    public ParticleManager(Akrrwd plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    // ─── Config loading ───────────────────────────────────────────────────────

    public void loadConfig(FileConfiguration config) {
        particlesEnabled = config.getBoolean("particles.enabled", true);
        useTemplate      = config.getBoolean("particles.use_template", true);
        templateName     = config.getString("particles.template", "star_burst").toLowerCase();

        String typeName = config.getString("particles.custom.type", "FLAME").toUpperCase();
        customParticleType = parseParticle(typeName, "FLAME");
        customCount   = config.getInt("particles.custom.count", 60);
        customOffsetX = config.getDouble("particles.custom.offset_x", 0.5);
        customOffsetY = config.getDouble("particles.custom.offset_y", 1.0);
        customOffsetZ = config.getDouble("particles.custom.offset_z", 0.5);
        customSpeed   = config.getDouble("particles.custom.speed", 0.1);
    }

    public void loadTemplatesFile() {
        templatesFile = new File(plugin.getDataFolder(), "templates.yml");
        if (!templatesFile.exists()) {
            writeDefaultTemplatesFile(templatesFile);
        }
        templatesConfig = YamlConfiguration.loadConfiguration(templatesFile);
    }

    public void reloadTemplatesFile() {
        if (templatesFile == null) {
            loadTemplatesFile();
        } else {
            templatesConfig = YamlConfiguration.loadConfiguration(templatesFile);
        }
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    public void spawnRewardParticles(Player player) {
        if (!particlesEnabled) return;

        if (useTemplate) {
            spawnTemplate(player, templateName);
        } else {
            spawnCustom(player);
        }
    }

    // ─── Template spawning ────────────────────────────────────────────────────

    private void spawnTemplate(Player player, String name) {
        if (templatesConfig == null) {
            logger.warning("[AkrRwd] templates.yml is not loaded. Falling back to custom particles.");
            spawnCustom(player);
            return;
        }

        String path = "templates." + name;
        if (!templatesConfig.contains(path)) {
            logger.warning("[AkrRwd] Template '" + name + "' not found in templates.yml. Falling back to custom particles.");
            spawnCustom(player);
            return;
        }

        String   style    = templatesConfig.getString(path + ".style", "burst").toLowerCase();
        String   typeName = templatesConfig.getString(path + ".type", "FLAME").toUpperCase();
        Particle particle = parseParticle(typeName, "FLAME");
        int      count    = templatesConfig.getInt(path + ".count", 60);
        double   speed    = templatesConfig.getDouble(path + ".speed", 0.05);
        double   radius   = templatesConfig.getDouble(path + ".radius", 1.0);
        double   height   = templatesConfig.getDouble(path + ".height", 1.0);
        int      ticks    = templatesConfig.getInt(path + ".duration_ticks", 0);

        switch (style) {
            case "burst":  spawnBurst(player, particle, count, radius, height, speed);         break;
            case "spiral": spawnSpiral(player, particle, count, radius, height, speed, ticks); break;
            case "ring":   spawnRing(player, particle, count, radius, height, speed);          break;
            case "rain":   spawnRain(player, particle, count, radius, height, speed);          break;
            case "pillar": spawnPillar(player, particle, count, radius, height, speed);        break;
            case "orbit":  spawnOrbit(player, particle, count, radius, height, speed, ticks);  break;
            default:
                logger.warning("[AkrRwd] Unknown template style '" + style + "'. Falling back to burst.");
                spawnBurst(player, particle, count, radius, height, speed);
        }
    }

    // ─── Custom spawning ──────────────────────────────────────────────────────

    private void spawnCustom(Player player) {
        Location center = player.getLocation().add(0, customOffsetY, 0);
        player.getWorld().spawnParticle(
                customParticleType,
                center,
                customCount,
                customOffsetX,
                customOffsetY,
                customOffsetZ,
                customSpeed
        );
    }

    // ─── Template styles ──────────────────────────────────────────────────────

    /** BURST – explodes a sphere of particles from the player's centre. */
    private void spawnBurst(Player player, Particle particle, int count, double radius, double height, double speed) {
        Location center = player.getLocation().add(0, height, 0);
        for (int i = 0; i < count; i++) {
            double theta = Math.random() * 2 * Math.PI;
            double phi   = Math.acos(2 * Math.random() - 1);
            double x = radius * Math.sin(phi) * Math.cos(theta);
            double y = radius * Math.cos(phi);
            double z = radius * Math.sin(phi) * Math.sin(theta);
            player.getWorld().spawnParticle(particle, center.clone().add(x, y, z), 1, 0, 0, 0, speed);
        }
    }

    /** SPIRAL – particles trace an upward spiral over time. */
    private void spawnSpiral(Player player, Particle particle, int count, double radius, double height, double speed, int durationTicks) {
        final int steps = Math.max(durationTicks, 40);
        final int[] tick = {0};
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || tick[0] >= steps) { cancel(); return; }
                double progress = (double) tick[0] / steps;
                double angle    = progress * 6 * Math.PI;
                double y        = progress * height * 2;
                double x = radius * Math.cos(angle);
                double z = radius * Math.sin(angle);
                player.getWorld().spawnParticle(particle, player.getLocation().add(x, y, z), Math.max(1, count / steps), 0, 0, 0, speed);
                tick[0]++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** RING – a flat horizontal ring of particles at waist height. */
    private void spawnRing(Player player, Particle particle, int count, double radius, double height, double speed) {
        Location center = player.getLocation().add(0, height, 0);
        for (int i = 0; i < count; i++) {
            double angle = (2 * Math.PI / count) * i;
            double x = radius * Math.cos(angle);
            double z = radius * Math.sin(angle);
            player.getWorld().spawnParticle(particle, center.clone().add(x, 0, z), 1, 0, 0, 0, speed);
        }
    }

    /** RAIN – particles fall from above the player. */
    private void spawnRain(Player player, Particle particle, int count, double radius, double height, double speed) {
        Location base = player.getLocation().add(0, height + 2, 0);
        for (int i = 0; i < count; i++) {
            double x = (Math.random() * 2 - 1) * radius;
            double z = (Math.random() * 2 - 1) * radius;
            player.getWorld().spawnParticle(particle, base.clone().add(x, 0, z), 1, 0, 0, 0, speed);
        }
    }

    /** PILLAR – particles shoot straight upward from the player's feet. */
    private void spawnPillar(Player player, Particle particle, int count, double radius, double height, double speed) {
        Location base  = player.getLocation();
        int levels     = Math.max(1, (int) (height * 4));
        int perLevel   = Math.max(1, count / levels);
        for (double y = 0; y <= height; y += 0.25) {
            for (int i = 0; i < perLevel; i++) {
                double angle = Math.random() * 2 * Math.PI;
                double r     = Math.random() * radius;
                player.getWorld().spawnParticle(particle, base.clone().add(r * Math.cos(angle), y, r * Math.sin(angle)), 1, 0, 0, 0, speed);
            }
        }
    }

    /** ORBIT – particles orbit around the player for a set duration. */
    private void spawnOrbit(Player player, Particle particle, int count, double radius, double height, double speed, int durationTicks) {
        final int steps        = Math.max(durationTicks, 60);
        final int spotsPerTick = Math.max(1, count / 20);
        final int[] tick       = {0};
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || tick[0] >= steps) { cancel(); return; }
                double angle    = (2 * Math.PI / 20.0) * tick[0];
                Location center = player.getLocation().add(0, height, 0);
                for (int i = 0; i < spotsPerTick; i++) {
                    double a = angle + (2 * Math.PI / spotsPerTick) * i;
                    double x = radius * Math.cos(a);
                    double z = radius * Math.sin(a);
                    player.getWorld().spawnParticle(particle, center.clone().add(x, 0, z), 1, 0, 0, 0, speed);
                }
                tick[0]++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Safely parse a Particle enum, with a fallback for version differences.
     */
    private Particle parseParticle(String name, String fallbackName) {
        try {
            return Particle.valueOf(name);
        } catch (IllegalArgumentException e) {
            logger.warning("[AkrRwd] Particle '" + name + "' is not available on this server version. Falling back to " + fallbackName + ".");
            try {
                return Particle.valueOf(fallbackName);
            } catch (IllegalArgumentException ex) {
                return Particle.FLAME;
            }
        }
    }

    /**
     * Writes the default templates.yml by building a string directly — no
     * JAR resource required. This avoids the saveResource() crash when the
     * file is not bundled inside the plugin JAR.
     */
    private void writeDefaultTemplatesFile(File file) {
        file.getParentFile().mkdirs();

        String nl = System.lineSeparator();
        String content =
                "# AkrRwd - Particle Templates" + nl +
                        "# All 6 templates are defined here and can be freely edited." + nl +
                        "#" + nl +
                        "# Template fields:" + nl +
                        "#   style          - Spawn pattern. Options: burst, spiral, ring, rain, pillar, orbit" + nl +
                        "#   type           - Bukkit Particle name." + nl +
                        "#                    See: https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/Particle.html" + nl +
                        "#                    Note: some particles changed names across versions." + nl +
                        "#                    Safe choices for all versions (1.16+): FLAME, END_ROD, CRIT, ENCHANT," + nl +
                        "#                    TOTEM_OF_UNDYING, SOUL_FIRE_FLAME" + nl +
                        "#   count          - Total particles to spawn (or per-cycle for spiral/orbit)" + nl +
                        "#   speed          - Extra velocity on each particle (0.0 = no drift)" + nl +
                        "#   radius         - Horizontal spread radius around the player (blocks)" + nl +
                        "#   height         - Vertical centre offset above the player's feet (blocks)" + nl +
                        "#   duration_ticks - (spiral & orbit only) Animation length. 20 ticks = 1 second." + nl +
                        nl +
                        "templates:" + nl +
                        nl +
                        "  # 1. STAR BURST - explosive sphere of glowing rods" + nl +
                        "  star_burst:" + nl +
                        "    style: burst" + nl +
                        "    type: END_ROD" + nl +
                        "    count: 80" + nl +
                        "    speed: 0.15" + nl +
                        "    radius: 1.2" + nl +
                        "    height: 1.0" + nl +
                        nl +
                        "  # 2. FLAME SPIRAL - fire rises in a spiral (~2 seconds)" + nl +
                        "  flame_spiral:" + nl +
                        "    style: spiral" + nl +
                        "    type: FLAME" + nl +
                        "    count: 60" + nl +
                        "    speed: 0.0" + nl +
                        "    radius: 0.8" + nl +
                        "    height: 2.0" + nl +
                        "    duration_ticks: 40" + nl +
                        nl +
                        "  # 3. MAGIC RING - flat ring of enchantment glyphs at mid-body" + nl +
                        "  magic_ring:" + nl +
                        "    style: ring" + nl +
                        "    type: ENCHANT" + nl +
                        "    count: 48" + nl +
                        "    speed: 0.05" + nl +
                        "    radius: 1.0" + nl +
                        "    height: 0.8" + nl +
                        nl +
                        "  # 4. SPARKLE RAIN - totem sparks rain down around the player" + nl +
                        "  sparkle_rain:" + nl +
                        "    style: rain" + nl +
                        "    type: TOTEM_OF_UNDYING" + nl +
                        "    count: 70" + nl +
                        "    speed: 0.02" + nl +
                        "    radius: 1.5" + nl +
                        "    height: 1.0" + nl +
                        nl +
                        "  # 5. SOUL PILLAR - column of soul-fire flame rises from the feet" + nl +
                        "  soul_pillar:" + nl +
                        "    style: pillar" + nl +
                        "    type: SOUL_FIRE_FLAME" + nl +
                        "    count: 80" + nl +
                        "    speed: 0.0" + nl +
                        "    radius: 0.4" + nl +
                        "    height: 2.5" + nl +
                        nl +
                        "  # 6. ORBIT STARS - crit particles orbit the player for 3 seconds" + nl +
                        "  orbit_stars:" + nl +
                        "    style: orbit" + nl +
                        "    type: CRIT" + nl +
                        "    count: 40" + nl +
                        "    speed: 0.04" + nl +
                        "    radius: 1.0" + nl +
                        "    height: 1.0" + nl +
                        "    duration_ticks: 60" + nl;

        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
            logger.info("[AkrRwd] Default templates.yml created successfully.");
        } catch (IOException e) {
            logger.severe("[AkrRwd] Failed to create default templates.yml: " + e.getMessage());
        }
    }
}
