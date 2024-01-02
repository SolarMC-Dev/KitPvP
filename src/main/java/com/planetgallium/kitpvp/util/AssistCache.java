package com.planetgallium.kitpvp.util;

import com.planetgallium.kitpvp.Game;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class AssistCache {

    public static final Map<Player, AssistCache> assistCache = new HashMap<>();

    private final Map<Player, Long> attackers;
    private final Resource config;

    public AssistCache(Game game) {
        this.attackers = new HashMap<>();
        this.config = game.getResources().getConfig();
    }

    public Map<Player, Long> getAttackers() {
        return attackers;
    }

    public void addAttacker(Player attacker) {
        if (attackers.size() >= config.getInt("Death.Assist.Max-Assisters"))
            return;
        attackers.put(attacker, System.currentTimeMillis() + config.getInt("Death.Assist.Time") * 1000L);
    }

}