package com.planetgallium.kitpvp.listener;

import com.cryptomorin.xseries.XSound;
import com.planetgallium.kitpvp.Game;
import com.planetgallium.kitpvp.game.Arena;
import com.planetgallium.kitpvp.util.Resource;
import com.planetgallium.kitpvp.util.Toolkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HitListener implements Listener {

    private final Arena arena;
    private final Resource config;
    private final XSound.Record hitSound;

    public HitListener(Game plugin) {
        this.arena = plugin.getArena();
        this.config = plugin.getResources().getConfig();

        String soundString = config.getString("Combat.HitSound.Sound") + ", 1, " + config.getInt("Combat.HitSound.Pitch");
        this.hitSound = XSound.parse(soundString);
    }

    @EventHandler
    public void onHit(EntityDamageByEntityEvent e) {

        if (e.getEntity() instanceof Player && e.getDamager() instanceof Player) {

            Player damager = (Player) e.getDamager();
            Player damagedPlayer = (Player) e.getEntity();

            if (Toolkit.inArena(damagedPlayer)) {

                arena.getHitCache().put(damagedPlayer.getName(), damager.getName());

                AssistCache assistCache = arena.getAssistCaches().get(damagedPlayer.getUniqueId());
                if (assistCache == null) {
                    assistCache = new AssistCache(damagedPlayer.getUniqueId());
                    arena.getAssistCaches().put(damagedPlayer.getUniqueId(), assistCache);
                }
                assistCache.addAttacker(damager.getUniqueId());

                if (config.getBoolean("Combat.HitSound.Enabled")) {
                    hitSound.forPlayer(damagedPlayer).play();
                    hitSound.forPlayer(damager).play();
                }
            }
        }
    }


    public class AssistCache {

        private final UUID victim;
        private final Map<UUID, Long> attackers;

        public AssistCache(UUID victim) {
            this.victim = victim;
            this.attackers = new HashMap<>();
        }

        public UUID getVictim() {
            return victim;
        }

        public Map<UUID, Long> getAttackers() {
            return attackers;
        }

        public void addAttacker(UUID attacker) {
            if (attackers.size() >= config.getInt("Death.Assist.Max-Assisters"))
                return;
            attackers.put(attacker, System.currentTimeMillis() + config.getInt("Death.Assist.Time") * 1000L);
        }
    }
}