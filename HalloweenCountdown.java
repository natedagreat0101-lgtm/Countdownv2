package me.halloweencountdown;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Counts down to the Halloween update with a boss bar at the top of every screen, announcements,
 * spooky music and creepy sounds, and a big finale at zero.
 */
public class HalloweenCountdown extends JavaPlugin implements Listener {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String[] AMBIENT = {
            "minecraft:ambient.cave", "minecraft:entity.vex.ambient", "minecraft:entity.ghast.ambient",
            "minecraft:entity.wither.ambient", "minecraft:entity.witch.ambient", "minecraft:entity.phantom.ambient",
            "minecraft:block.bell.use", "minecraft:entity.zombie.ambient"};

    private final Set<UUID> musicOff = new HashSet<>();
    private final Map<UUID, Long> nextPlay = new HashMap<>();
    private final Random random = new Random();
    private File dataFile;
    private ZonedDateTime target;
    private BossBar bar;
    private boolean barVisible = true;
    private boolean celebrated;
    private long marker = Long.MAX_VALUE;
    private long nextAmbient;

    // ------------------------------------------------------------ lifecycle

    @Override
    public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "data.yml");
        for (String s : YamlConfiguration.loadConfiguration(dataFile).getStringList("music-off")) {
            try {
                musicOff.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
            }
        }
        bar = BossBar.bossBar(Component.text("Halloween Update", NamedTextColor.GOLD), 1f,
                BossBar.Color.PURPLE, BossBar.Overlay.NOTCHED_10);
        loadTarget();
        Bukkit.getPluginManager().registerEvents(this, this);
        for (Player p : Bukkit.getOnlinePlayers()) p.showBossBar(bar);
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 20L, 20L);
    }

    @Override
    public void onDisable() {
        for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bar);
    }

    private void loadTarget() {
        try {
            ZoneId zone = ZoneId.of(getConfig().getString("timezone", "America/Toronto"));
            target = LocalDateTime.parse(getConfig().getString("target", "2026-10-25 12:00"), FMT).atZone(zone);
        } catch (Exception ex) {
            getLogger().warning("Bad target or timezone in config.yml: " + ex.getMessage());
            target = null;
            return;
        }
        long secs = secondsLeft();
        celebrated = secs <= 0; // if the time already passed, don't celebrate again after a restart
        marker = CountdownMath.initialMarker(secs);
        barVisible = !celebrated;
        getLogger().info("Counting down to " + target + " (" + CountdownMath.format(secs) + " left).");
    }

    private long secondsLeft() {
        return target == null ? -1 : CountdownMath.secondsLeft(target, ZonedDateTime.now(target.getZone()));
    }

    private String title() {
        return getConfig().getString("title", "Halloween Update");
    }

    // ------------------------------------------------------------ once per second

    private void tick() {
        if (target == null) return;
        long secs = secondsLeft();
        if (secs <= 0) {
            if (!celebrated) celebrate();
            return;
        }
        bar.name(Component.text(title() + " in " + CountdownMath.format(secs), NamedTextColor.GOLD));
        bar.progress(CountdownMath.progress(secs, getConfig().getInt("bar-window-days", 7)));

        long due = CountdownMath.dueMilestone(secs, marker);
        if (due > 0) {
            marker = due;
            announce(due);
        }
        music();
        ambient();
    }

    private void announce(long secsBefore) {
        if (secsBefore <= 10) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.showTitle(Title.title(Component.text(String.valueOf(secsBefore), NamedTextColor.RED),
                        Component.text(title() + "...", NamedTextColor.GOLD),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ZERO)));
                play(p, "minecraft:block.note_block.pling", 1f, 0.6f + (10 - secsBefore) * 0.08f);
            }
            return;
        }
        String when = CountdownMath.words(secsBefore);
        Bukkit.broadcast(Component.text("The " + title() + " arrives in " + when + "!", NamedTextColor.GOLD));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(Title.title(Component.text(when, NamedTextColor.GOLD),
                    Component.text("until the " + title(), NamedTextColor.RED),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(600))));
            play(p, "minecraft:entity.elder_guardian.curse", 0.5f, 0.8f);
        }
    }

    // ------------------------------------------------------------ the finale

    private void celebrate() {
        celebrated = true;
        bar.name(Component.text("THE " + title().toUpperCase() + " IS HERE!", NamedTextColor.RED));
        bar.progress(1f);
        bar.color(BossBar.Color.RED);
        Bukkit.broadcast(Component.text(getConfig().getString("finale-message",
                "The Halloween Update is HERE! Happy Halloween!"), NamedTextColor.GOLD));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.stopSound(getConfig().getString("music.sound", "minecraft:music_disc.13"));
            p.showTitle(Title.title(Component.text("IT'S HERE!", NamedTextColor.RED),
                    Component.text("The " + title() + " has arrived", NamedTextColor.GOLD),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(6), Duration.ofSeconds(1))));
            play(p, "minecraft:entity.wither.spawn", 0.7f, 1f);
            play(p, "minecraft:entity.player.levelup", 1f, 0.6f);
            play(p, "minecraft:entity.lightning_bolt.thunder", 1f, 1f);
            p.getWorld().strikeLightningEffect(p.getLocation().add(4, 0, 4));
            if (getConfig().getBoolean("finale-fireworks", true)) {
                for (int i = 0; i < 4; i++) firework(p.getLocation().add(random.nextInt(9) - 4, 0, random.nextInt(9) - 4));
            }
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            barVisible = false;
            for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bar);
        }, 20L * getConfig().getInt("finale-bar-seconds", 60));
    }

    private void firework(Location loc) {
        Firework fw = loc.getWorld().spawn(loc, Firework.class, f -> {
        });
        FireworkMeta meta = fw.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE).withColor(Color.ORANGE, Color.PURPLE)
                .withFade(Color.BLACK).flicker(true).build());
        meta.setPower(0);
        fw.setFireworkMeta(meta);
        fw.detonate();
    }

    // ------------------------------------------------------------ spooky sound

    private void play(Player p, String key, float volume, float pitch) {
        try {
            p.playSound(net.kyori.adventure.sound.Sound.sound(Key.key(key), net.kyori.adventure.sound.Sound.Source.MASTER,
                    volume, pitch), net.kyori.adventure.sound.Sound.Emitter.self());
        } catch (RuntimeException ignored) {
            // a bad sound key in config.yml must never break the timer
        }
    }

    /** Loops the music track for everyone who has it turned on. A player who joins hears it right away. */
    private void music() {
        if (!getConfig().getBoolean("music.enabled", true)) return;
        long now = System.currentTimeMillis() / 1000L;
        long length = Math.max(10, getConfig().getInt("music.length-seconds", 178));
        String key = getConfig().getString("music.sound", "minecraft:music_disc.13");
        float volume = (float) getConfig().getDouble("music.volume", 0.8);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (musicOff.contains(p.getUniqueId())) continue;
            if (now >= nextPlay.getOrDefault(p.getUniqueId(), 0L)) {
                nextPlay.put(p.getUniqueId(), now + length);
                try {
                    p.playSound(net.kyori.adventure.sound.Sound.sound(Key.key(key),
                            net.kyori.adventure.sound.Sound.Source.RECORD, volume, 1f),
                            net.kyori.adventure.sound.Sound.Emitter.self());
                } catch (RuntimeException ignored) {
                }
            }
        }
    }

    /** Now and then a creepy sound plays for one random player. */
    private void ambient() {
        if (!getConfig().getBoolean("ambient.enabled", true)) return;
        long now = System.currentTimeMillis() / 1000L;
        if (now < nextAmbient) return;
        int min = Math.max(5, getConfig().getInt("ambient.min-seconds", 40));
        int max = Math.max(min, getConfig().getInt("ambient.max-seconds", 90));
        nextAmbient = now + min + random.nextInt(max - min + 1);
        List<Player> listeners = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) if (!musicOff.contains(p.getUniqueId())) listeners.add(p);
        if (listeners.isEmpty()) return;
        Player p = listeners.get(random.nextInt(listeners.size()));
        play(p, AMBIENT[random.nextInt(AMBIENT.length)], 0.6f, 0.8f + random.nextFloat() * 0.3f);
    }

    // ------------------------------------------------------------ players

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (barVisible) p.showBossBar(bar);
        long secs = secondsLeft();
        if (target != null && secs > 0) {
            p.sendMessage(Component.text("The " + title() + " arrives in " + CountdownMath.format(secs)
                    + ". Spooky music: /ht music", NamedTextColor.GOLD));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        nextPlay.remove(e.getPlayer().getUniqueId());
    }

    private void saveData() {
        YamlConfiguration y = new YamlConfiguration();
        List<String> ids = new ArrayList<>();
        for (UUID id : musicOff) ids.add(id.toString());
        y.set("music-off", ids);
        try {
            getDataFolder().mkdirs();
            y.save(dataFile);
        } catch (IOException ex) {
            getLogger().warning("Could not save data.yml: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------ /halloweentimer  (/ht)

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase() : "";
        switch (sub) {
            case "music" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("Players only.");
                    return true;
                }
                boolean on = args.length > 1 ? args[1].equalsIgnoreCase("on") : musicOff.contains(p.getUniqueId());
                if (on) {
                    musicOff.remove(p.getUniqueId());
                    nextPlay.remove(p.getUniqueId()); // starts again on the next second
                    p.sendMessage(Component.text("Spooky music is ON.", NamedTextColor.GOLD));
                } else {
                    musicOff.add(p.getUniqueId());
                    p.stopSound(getConfig().getString("music.sound", "minecraft:music_disc.13"));
                    p.sendMessage(Component.text("Spooky music is OFF. Use /ht music to turn it back on.", NamedTextColor.GRAY));
                }
                saveData();
            }
            case "set" -> {
                if (!sender.hasPermission("halloweentimer.admin")) {
                    sender.sendMessage("You need to be an admin.");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage("Usage: /ht set <yyyy-MM-dd> <HH:mm>   for example /ht set 2026-10-25 12:00");
                    return true;
                }
                getConfig().set("target", args[1] + " " + args[2]);
                saveConfig();
                loadTarget();
                sender.sendMessage(target == null ? "That date or time is not valid."
                        : "Countdown set. Time left: " + CountdownMath.format(secondsLeft()));
            }
            case "reload" -> {
                if (!sender.hasPermission("halloweentimer.admin")) {
                    sender.sendMessage("You need to be an admin.");
                    return true;
                }
                reloadConfig();
                loadTarget();
                sender.sendMessage("HalloweenCountdown reloaded.");
            }
            default -> {
                long secs = secondsLeft();
                sender.sendMessage(Component.text(target == null ? "No valid countdown is set."
                        : secs > 0 ? "The " + title() + " arrives in " + CountdownMath.format(secs) + "."
                        : "The " + title() + " is here!", NamedTextColor.GOLD));
            }
        }
        return true;
    }
}
