package com.planetgallium.kitpvp.listener;

import com.cryptomorin.xseries.XSound;
import com.cryptomorin.xseries.messages.Titles;
import com.planetgallium.kitpvp.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import com.planetgallium.kitpvp.Game;
import com.planetgallium.kitpvp.game.Arena;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DeathListener implements Listener {

	private final Game plugin;
	private final Arena arena;
	private final Resources resources;
	private final Resource config;
	
	public DeathListener(Game plugin) {
		this.plugin = plugin;
		this.arena = plugin.getArena();
		this.resources = plugin.getResources();
		this.config = resources.getConfig();
	}

	@EventHandler
	public void onDeath(PlayerDeathEvent e) {

		// investigate possible memory leak when FancyDeath is enabled
		if (Toolkit.inArena(e.getEntity())) {

			Player victim = e.getEntity();
			e.setDeathMessage("");

			if (config.getBoolean("Arena.PreventDeathDrops")) {
				e.getDrops().clear();
			}

			respawnPlayer(victim);
			setDeathMessage(victim);
			creditWithKill(victim, victim.getKiller());

			AssistCache cache = AssistCache.assistCache.get(victim);
			if (cache != null) {

				int reward = config.getInt("Death.Assist.Reward");
				for (Map.Entry<Player, Long> entry : cache.getAttackers().entrySet()) {
					Player attacker = entry.getKey();

					if (attacker == null)
						continue;
					else if (attacker.equals(e.getEntity().getKiller()))
						continue;

					VaultHook.ECONOMY.depositPlayer(attacker, reward);
					attacker.sendMessage(resources.getMessages().fetchString("Death.Assist.Message")
							.replace("%reward%", String.valueOf(reward))
							.replace("%victim%", victim.getName()));

					for (Player assister : cache.getAttackers().keySet()) {
						arena.getStats().addToStat("assists", assister.getName(), 1);
					}
					AssistCache.assistCache.remove(victim);
				}

				arena.getStats().addToStat("deaths", victim.getName(), 1);
				arena.getStats().setStat("killstreak", victim.getName(), 0);
				arena.getStats().removeExperience(victim.getName(),
						resources.getLevels().getInt("Levels.Options.Experience-Taken-On-Death"));

				if (config.getBoolean("Arena.DeathParticle")) {
					victim.getWorld().playEffect(victim.getLocation().add(0.0D, 1.0D, 0.0D), Effect.STEP_SOUND, 152);
				}

				Toolkit.runCommands(victim, config.getStringList("Death.Commands"), "%victim%", victim.getName());

				broadcast(victim.getWorld(),
						XSound.matchXSound(config.fetchString("Death.Sound.Sound")).get().parseSound(),
						1,
						config.getInt("Death.Sound.Pitch"));
			}
		}

	}

	@EventHandler
	public void onRespawn(PlayerRespawnEvent e) {
		if (Toolkit.inArena(e.getPlayer())) {
			if (!config.getBoolean("Arena.FancyDeath")) {
				Player p = e.getPlayer();

				new BukkitRunnable() {
					@Override
					public void run() {
						arena.toSpawn(p, p.getWorld().getName());
					}
				}.runTaskLater(plugin, 1L);
			}
		}
	}

	private void respawnPlayer(Player victim) {
		if (!victim.isOnline()) {
			return;
		}

		if (config.getBoolean("Arena.FancyDeath")) {
			Location deathLocation = victim.getLocation();

			new BukkitRunnable() {
				@Override
				public void run() {
					victim.spigot().respawn();
					victim.teleport(deathLocation);
					victim.setGameMode(GameMode.SPECTATOR);
				}
			}.runTaskLater(plugin, 1L);

			arena.removePlayer(victim);
			
			new BukkitRunnable() {
				int time = config.getInt("Death.Title.Time");

				@Override
				public void run() {
					if (time != 0) {
						Titles.sendTitle(victim, 0, 21, 0,
								config.fetchString("Death.Title.Title"),
								config.fetchString("Death.Title.Subtitle")
										.replace("%seconds%", String.valueOf(time)));
						XSound.play(victim, "UI_BUTTON_CLICK, 1, 1");
						time--;
					} else {
						if (config.getBoolean("Arena.ClearInventoryOnRespawn")) {
							victim.getInventory().clear();
							victim.getInventory().setArmorContents(null);
						}

						arena.addPlayer(victim, true, config.getBoolean("Arena.GiveItemsOnRespawn"));

						victim.sendMessage(config.fetchString("Death.Title.Message"));
						victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0));
						XSound.play(victim, "ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1");

						Toolkit.runCommands(victim, config.getStringList("Respawn.Commands"), "none", "none");

						cancel();
					}
				}
			}.runTaskTimer(plugin, 0L, 20L);

		} else {
			arena.removePlayer(victim);

			if (config.getBoolean("Arena.ClearInventoryOnRespawn")) {
				victim.getInventory().clear();
				victim.getInventory().setArmorContents(null);
			}

			new BukkitRunnable() {
				@Override
				public void run() {
					arena.addPlayer(victim, true, config.getBoolean("Arena.GiveItemsOnRespawn"));
					Toolkit.runCommands(victim, config.getStringList("Respawn.Commands"),
							"none", "none");
				}
			}.runTaskLater(plugin, 1L);
		}
	}

	private void setDeathMessage(Player victim) {

		if (victim.getLastDamageCause() == null) {
			broadcast(victim.getWorld(), getDeathMessage(victim, null, "Unknown"));
			return;
		}

		DamageCause cause = victim.getLastDamageCause().getCause();

		if (cause == DamageCause.PROJECTILE && getShooter(victim.getLastDamageCause()).getType() == EntityType.PLAYER) {

			Player killer = (Player) getShooter(victim.getLastDamageCause());

			broadcast(victim.getWorld(), getDeathMessage(victim, killer, "Shot"));

//		} else if (cause == DamageCause.ENTITY_ATTACK) {
//
//			broadcast(victim.getWorld(), config.fetchString("Death.Messages.Player").replace("%victim%", victim.getName()).replace("%killer%", victim.getKiller().getName()));
//			creditWithKill(victim, victim.getKiller());

		} else if (victim.getKiller() != null) {

			Player killer = victim.getKiller();

			broadcast(victim.getWorld(), getDeathMessage(victim, killer, "Player"));

		} else if (arena.getHitCache().get(victim.getName()) != null) {

			String killerName = arena.getHitCache().get(victim.getName());
			Player killer = Toolkit.getPlayer(victim.getWorld(), killerName);

			broadcast(victim.getWorld(), getDeathMessage(victim, killer, "Player"));

		} else if ((cause == DamageCause.BLOCK_EXPLOSION || cause == DamageCause.ENTITY_EXPLOSION) && getExplodedEntity(victim.getLastDamageCause()).getType() == EntityType.PRIMED_TNT) {

			String bomberName = getExplodedEntity(victim.getLastDamageCause()).getCustomName();
			Player killer = Toolkit.getPlayer(victim.getWorld(), bomberName);

			broadcast(victim.getWorld(), getDeathMessage(victim, killer, "Player"));

		} else if (cause == DamageCause.VOID) {

			broadcast(victim.getWorld(), getDeathMessage(victim, null, "Void"));

		} else if (cause == DamageCause.FALL) {

			broadcast(victim.getWorld(), getDeathMessage(victim, null, "Fall"));

		} else if (cause == DamageCause.FIRE || cause == DamageCause.FIRE_TICK || cause == DamageCause.LAVA) {

			broadcast(victim.getWorld(), getDeathMessage(victim, null, "Fire"));

		} else if (cause == DamageCause.BLOCK_EXPLOSION || cause == DamageCause.ENTITY_EXPLOSION) {

			broadcast(victim.getWorld(), getDeathMessage(victim, null, "Explosion"));

		} else {

			broadcast(victim.getWorld(), getDeathMessage(victim, null, "Unknown"));

		}
		
	}

	private Entity getShooter(EntityDamageEvent e) {

		EntityDamageByEntityEvent shotEvent = (EntityDamageByEntityEvent) e;
		Projectile damager = (Projectile) shotEvent.getDamager();

		return (Entity) damager.getShooter();

	}

	private Entity getExplodedEntity(EntityDamageEvent e) {

//		if (e instanceof EntityDamageByBlockEvent) {
//			EntityDamageByBlockEvent blownUpEvent2 = (EntityDamageByBlockEvent) e;
//			return blownUpEvent2.getDamager();
		/*} else if (e instanceof EntityDamageByEntityEvent) { */
			EntityDamageByEntityEvent blownUpEvent = (EntityDamageByEntityEvent) e;
			return blownUpEvent.getDamager();
		/*}*/

	}

	private void creditWithKill(Player victim, Player killer) {
		if (victim == null || killer == null) return;
		if (victim.getName().equals(killer.getName())) return;

		int victimStreak = arena.getStats().getStat("killstreak", victim.getName());
		if (victimStreak >= 25) {
			victim.getWorld().strikeLightningEffect(victim.getLocation());
		}

		arena.getStats().addToStat("kills", killer.getName(), 1);
		arena.getStats().addToStat("killstreak", killer.getName(), 1);
		killer.addPotionEffect(PotionEffectType.REGENERATION.createEffect(100, 4));
		plugin.getServer().getScheduler().runTaskLater(plugin, () -> killer.getWorld().playSound(killer.getLocation(), Sound.valueOf(config.getString("Arena.KillSound")), 1, 1)
		, 5);
		arena.getStats().addExperience(killer, resources.getLevels().getInt("Levels.Options.Experience-Given-On-Kill"));

		List<String> killCommands = config.getStringList("Kill.Commands");
		killCommands = Toolkit.replaceInList(killCommands, "%victim%", victim.getName());
		Toolkit.runCommands(killer, killCommands, "%killer%", killer.getName());

		if (resources.getScoreboard().getBoolean("Scoreboard.General.Enabled")) {
			new BukkitRunnable() {
				@Override
				public void run() {
					arena.updateScoreboards(killer, false);
				}
			}.runTaskLater(plugin, 20L);
		}
	}

	private String getDeathMessage(Player victim, Player killer, String type) {

		String deathMessage = config.fetchString("Death.Messages." + type);

		if (victim != null && killer != null) {
			if (victim.getName().equals(killer.getName())) {
				deathMessage = config.fetchString("Death.Messages.Suicide");
			}
		}

		if (killer != null) {
			deathMessage = deathMessage.replace("%killer%", killer.getName())
					.replace("%killer_health%", String.valueOf(Toolkit.round(killer.getHealth(), 2)));
		} else {
			deathMessage = config.fetchString("Death.Messages.Unknown"); // if killer is null (left the server, or some other unknown reason)
		}

		if (victim != null) {
			deathMessage = deathMessage.replace("%victim%", victim.getName());
		}

		return deathMessage;

	}

	private void broadcast(World world, String message) {
		if (config.getBoolean("Death.Messages.Enabled")) {
			for (Player all : world.getPlayers()) {
				all.sendMessage(Toolkit.translate(message));
			}
		}
	}

	private void broadcast(World world, Sound sound, int volume, int pitch) {
		if (config.getBoolean("Death.Sound.Enabled")) {
			for (Player all : world.getPlayers()) {
				XSound.play(all, String.format("%s, %d, %d", sound.toString(), volume, pitch));
			}
		}
	}

}
