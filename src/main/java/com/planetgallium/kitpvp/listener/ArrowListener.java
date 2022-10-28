package com.planetgallium.kitpvp.listener;

import com.cryptomorin.xseries.XMaterial;
import com.planetgallium.kitpvp.Game;
import com.planetgallium.kitpvp.game.Arena;
import com.planetgallium.kitpvp.util.Resource;
import com.planetgallium.kitpvp.util.Toolkit;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

public class ArrowListener implements Listener {

    private Resource config;
    private final Game plugin;
    private Arena arena;

    public ArrowListener(Game plugin) {
        this.arena = plugin.getArena();
        this.config = plugin.getResources().getConfig();
        this.plugin = plugin;
    }

    @EventHandler
    public void onShot(EntityDamageByEntityEvent e) {

        if (Toolkit.inArena(e.getEntity()) && config.getBoolean("Combat.ArrowHit.Enabled")) {

            if (e.getEntity() instanceof Player && e.getDamager() instanceof Arrow) {

                Player damagedPlayer = (Player) e.getEntity();
                Arrow arrow = (Arrow) e.getDamager();

                if (arrow.getShooter() != null && arrow.getShooter() instanceof Player) {

                    Player shooter = (Player) arrow.getShooter();

                    // ARROW HEALTH MESSAGE

                    if (damagedPlayer.getName() != shooter.getName()) {

                        new BukkitRunnable() {

                            @Override
                            public void run() {

                                double health = Math.round(damagedPlayer.getHealth() * 10.0) / 10.0;

                                if (shooter.hasPermission("kp.arrowmessage")) {

                                    if (health != 20.0) {

                                        shooter.sendMessage(config.getString("Combat.ArrowHit.Message").replace("%player%", damagedPlayer.getName()).replace("%health%", String.valueOf(health)));

                                    }

                                }

                                HitListener.AssistCache assistCache = arena.getAssistCaches().get(damagedPlayer.getUniqueId());
                                if (assistCache == null) {
                                    assistCache = new HitListener.AssistCache(plugin, damagedPlayer.getUniqueId());
                                    arena.getAssistCaches().put(damagedPlayer.getUniqueId(), assistCache);
                                }
                                assistCache.addAttacker(shooter.getUniqueId());

                            }

                        }.runTaskLater(Game.getInstance(), 2L);

                    }

                    // ARROW RETURN

                    ItemStack arrowToAdd = new ItemStack(Material.ARROW);

                    if (config.getBoolean("Combat.ArrowReturn.Enabled")) {

                        for (ItemStack items : shooter.getInventory().getContents()) {

                            if (items != null && items.getType() == XMaterial.ARROW.parseMaterial() && items.getAmount() < 64) {

                                if (shooter.hasPermission("kp.arrowreturn")) {

                                    shooter.getInventory().addItem(arrowToAdd);
                                    shooter.getInventory().addItem(arrowToAdd);

                                    return;

                                }

                            }

                        }

                        if (shooter.getInventory().firstEmpty() == -1) {

                            shooter.sendMessage(config.getString("Combat.ArrowReturn.NoSpace"));

                        } else {

                            shooter.getInventory().addItem(arrowToAdd);
                            shooter.getInventory().addItem(arrowToAdd);

                        }

                    }

                }

            }

        }

    }

}
