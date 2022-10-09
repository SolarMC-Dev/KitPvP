package com.planetgallium.kitpvp;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.hakan.core.HCore;
import com.planetgallium.kitpvp.api.EventListener;
import com.planetgallium.kitpvp.command.AliasCommand;
import com.planetgallium.kitpvp.command.MainCommand;
import com.planetgallium.kitpvp.game.Arena;
import com.planetgallium.kitpvp.game.Infobase;
import com.planetgallium.kitpvp.listener.AbilityListener;
import com.planetgallium.kitpvp.listener.ArenaListener;
import com.planetgallium.kitpvp.listener.ArrowListener;
import com.planetgallium.kitpvp.listener.AttackListener;
import com.planetgallium.kitpvp.listener.ChatListener;
import com.planetgallium.kitpvp.listener.DeathListener;
import com.planetgallium.kitpvp.listener.HitListener;
import com.planetgallium.kitpvp.listener.ItemListener;
import com.planetgallium.kitpvp.listener.JoinListener;
import com.planetgallium.kitpvp.listener.LeaveListener;
import com.planetgallium.kitpvp.listener.MenuListener;
import com.planetgallium.kitpvp.listener.SignListener;
import com.planetgallium.kitpvp.listener.SoupListener;
import com.planetgallium.kitpvp.listener.TrackerListener;
import com.planetgallium.kitpvp.util.Metrics;
import com.planetgallium.kitpvp.util.Placeholders;
import com.planetgallium.kitpvp.util.Resources;
import com.planetgallium.kitpvp.util.Toolkit;
import com.planetgallium.kitpvp.util.Updater;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.TimeUnit;

public class Game extends JavaPlugin implements Listener {

    private static Game instance;
    private static String prefix = "None";

    private Arena arena;
    private Infobase database;
    private Resources resources;

    private String updateVersion = "Error";
    private boolean needsUpdate = false;
    private boolean hasPlaceholderAPI = false;
    private boolean hasWorldGuard = false;

    @Override
    public void onEnable() {

        Toolkit.printToConsole("&7[&b&lKIT-PVP&7] &7Enabling &bKitPvP &7version &b" + this.getDescription().getVersion() + "&7...");

        instance = this;
        resources = new Resources(this);
        prefix = resources.getMessages().getString("Messages.General.Prefix");
        database = new Infobase(this);
        arena = new Arena(this, resources);

        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(this, this);
        pm.registerEvents(new EventListener(this), this);
        pm.registerEvents(new ArenaListener(this), this);
        pm.registerEvents(new JoinListener(this), this);
        pm.registerEvents(new LeaveListener(this), this);
        pm.registerEvents(new ArrowListener(this), this);
        pm.registerEvents(new DeathListener(this), this);
        pm.registerEvents(new HitListener(this), this);
        pm.registerEvents(new AttackListener(this), this);
        pm.registerEvents(new ItemListener(this), this);
        pm.registerEvents(new SoupListener(this), this);
        pm.registerEvents(new ChatListener(this), this);
        pm.registerEvents(new SignListener(this), this);
        pm.registerEvents(new AliasCommand(this), this);
        pm.registerEvents(new AbilityListener(this), this);
        pm.registerEvents(new TrackerListener(this), this);
        pm.registerEvents(new MenuListener(this), this);
        pm.registerEvents(getArena().getKillStreaks(), this);

        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        getCommand("kitpvp").setExecutor(new MainCommand(this));

        new Metrics(this);

        new BukkitRunnable() {
            @Override
            public void run() {
                checkUpdate();
            }
        }.runTaskAsynchronously(this);

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            Bukkit.getConsoleSender().sendMessage(Toolkit.translate("[&b&lKIT-PVP&7] &7Hooking into &bPlaceholderAPI&7..."));
            new Placeholders(this).register();
            hasPlaceholderAPI = true;
        }

        if (Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            Bukkit.getConsoleSender().sendMessage(Toolkit.translate("[&b&lKIT-PVP&7] &7Hooking into &bWorldGuard&7..."));
            hasWorldGuard = true;
        }

        Toolkit.printToConsole("&7[&b&lKIT-PVP&7] &aDone!");



        HCore.asyncScheduler()
                .after(1, TimeUnit.SECONDS)
                .every(1, TimeUnit.SECONDS)
                .run(() -> {
                    for (HitListener.AssistCache assistCache : arena.getAssistCaches().values()) {
                        assistCache.getAttackers().forEach((attacker, value) -> {
                            if (System.currentTimeMillis() >= value)
                                assistCache.getAttackers().remove(attacker);
                        });
                    }
                });
    }

    private void checkUpdate() {

        Updater.of(this).resourceId(27107).handleResponse((versionResponse, version) -> {
            switch (versionResponse) {
                case FOUND_NEW:
                    Bukkit.getConsoleSender().sendMessage(Toolkit.translate("&7[&b&lKIT-PVP&7] &aNew version found! Please update to v" + version + " on the Spigot page."));
                    needsUpdate = true;
                    updateVersion = version;
                    break;
                case UNAVAILABLE:
                    Bukkit.getConsoleSender().sendMessage(Toolkit.translate("&7[&b&lKIT-PVP&7] &cUnable to perform an update check."));
                    break;
            }
        }).check();

    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {

        Player p = e.getPlayer();

        if (!resources.getConfig().contains("Items.Leave")) {
            return;
        }

        if (Toolkit.matchesConfigItem(Toolkit.getMainHandItem(p), resources.getConfig(), "Items.Leave")) {

            if (resources.getConfig().getBoolean("Items.Leave.Enabled")) {

                if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {

                    if (resources.getConfig().getBoolean("Items.Leave.BungeeCord.Enabled")) {

                        ByteArrayDataOutput out = ByteStreams.newDataOutput();
                        out.writeUTF("Connect");

                        String server = resources.getConfig().getString("Items.Leave.BungeeCord.Server");

                        out.writeUTF(server);
                        p.sendPluginMessage(this, "BungeeCord", out.toByteArray());

                    }

                    e.setCancelled(true);

                }

            }

        }

    }

    public boolean hasPlaceholderAPI() {
        return hasPlaceholderAPI;
    }

    public boolean hasWorldGuard() {
        return hasWorldGuard;
    }

    public boolean needsUpdate() {
        return needsUpdate;
    }

    public String getUpdateVersion() {
        return updateVersion;
    }

    public static Game getInstance() {
        return instance;
    }

    public Arena getArena() {
        return arena;
    }

    public Infobase getDatabase() {
        return database;
    }

    public static String getPrefix() {
        return prefix;
    }

    public Resources getResources() {
        return resources;
    }
}