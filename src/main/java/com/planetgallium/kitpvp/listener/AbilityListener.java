package com.planetgallium.kitpvp.listener;

import com.planetgallium.kitpvp.api.Ability;
import com.planetgallium.kitpvp.api.Kit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import com.planetgallium.kitpvp.api.PlayerAbilityEvent;
import com.planetgallium.kitpvp.game.Arena;
import com.planetgallium.kitpvp.util.Resources;
import com.planetgallium.kitpvp.util.Toolkit;

public class AbilityListener implements Listener {
	
	private Arena arena;
	private Resources resources;
	
	public AbilityListener(Arena arena, Resources resources) {
		this.arena = arena;
		this.resources = resources;
	}
	
	@EventHandler
	public void onAbility(PlayerAbilityEvent e) {
		
		Player p = e.getPlayer();
		Kit kit = arena.getKits().getKitOfPlayer(p.getName());
		Ability ability = e.getAbility();

		if (p.hasPermission("kp.ability." + kit.getName().toLowerCase())) {

			if (ability.getMessage() != null)
				p.sendMessage(Toolkit.translate(ability.getMessage()));

			if (ability.getSound() != null)
				p.playSound(p.getLocation(), ability.getSound(), ability.getSoundVolume(), ability.getSoundPitch());

			if (ability.getEffects().size() > 0)
				ability.getEffects().stream().forEach(effect -> p.addPotionEffect(effect));

			if (ability.getCommands().size() > 0)
				Toolkit.runCommands(p, ability.getCommands(), "none", "none");
			
			if (Toolkit.getMainHandItem(p).getAmount() == 1) {
				ItemStack emptyItem = Toolkit.getMainHandItem(p);
				emptyItem.setAmount(0);
				Toolkit.setMainHandItem(p, emptyItem);
			} else {
				Toolkit.getMainHandItem(p).setAmount(Toolkit.getMainHandItem(p).getAmount() - 1);
			}
			
		} else {
			
			p.sendMessage(resources.getMessages().getString("Messages.General.Permission"));
			
		}
		
	}
	
}
