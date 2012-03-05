/**
 * Copyright (C) 2011 Jacob Scott <jascottytechie@gmail.com>
 * Description: listen to player activity and restrict or kick if not allowed
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package me.jascotty2.iplock;

import com.jascotty2.Str;
import java.util.Date;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent.Result;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class PListener implements Listener {

	// list of players that have not yet been verified
	static HashMap<String, PlayerPaswordWait> active = new HashMap<String, PlayerPaswordWait>();
	static HashMap<String, PasswordAttempt> attempts = new HashMap<String, PasswordAttempt>();
	private final IPLock plugin;

	public PListener(IPLock instance) {
		plugin = instance;
	}

	//Insert Player related code here
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerLogin(PlayerLoginEvent event) {
		if (attempts.containsKey(event.getPlayer().getName())
				&& attempts.get(event.getPlayer().getName()).tries >= IPLock.config.maxAttempts
				&& attempts.get(event.getPlayer().getName()).timePassed() < IPLock.config.retryTime) {

			event.disallow(Result.KICK_OTHER, "Too many login attempts!");
			return;
		}
		try {
			String name = event.getPlayer().getName();
			if (IPLock.config.listModeNone() || IPLock.config.checkUser(name)) {
				// Special Characters Addon
				if (IPLock.config.blockSpecialChars && !name.matches("[A-Za-z_0-9]+")) {
					plugin.getLogger().info(String.format("Kicked %s: special characters in name", name));
					event.disallow(Result.KICK_OTHER, "You cannot connect with special characters");
					return;
				} else if (IPLock.config.max_namelen > 0 && name.length() > IPLock.config.max_namelen) {
					plugin.getLogger().info(String.format("Kicked %s:: name not less than %d characters", name, IPLock.config.max_namelen));
					event.disallow(Result.KICK_OTHER, "Player names must be less than " + IPLock.config.max_namelen + " characters.");
				}
			}
		} catch (Exception e) {
			plugin.getLogger().log(Level.SEVERE, "exception running name checks", e);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerJoin(PlayerJoinEvent event) {
		try {
			String name = event.getPlayer().getName();
			if (IPLock.config.checkUser(name)) {
				String ip = IPLock.playerIP(event.getPlayer());
				if (IPLock.config.noOnlineIP
						&& plugin.ipIsOnline(event.getPlayer())) {
					//event.getPlayer().kickPlayer("This IP is already logged in at this server");
					(new KickPlayerWait(event.getPlayer(), "IP is already logged in at this server", 500)).start();
					event.setJoinMessage(null);
				} else if (!IPLock.users.isValid(name, ip, false)) {
					if (IPLock.users.isNameOnList(name)) {
						if (IPLock.config.passwordUpdate) {
							addPlayerPassWait(event.getPlayer());
						} else {
							//event.getPlayer().kickPlayer("You are trying to login to an account from the wrong IP");
							(new KickPlayerWait(event.getPlayer(), "Trying to login from the wrong IP", 500)).start();
						}
						return;
					} else {
						// new player
						if (IPLock.config.passwordLock) {
							addPlayerPassWait(event.getPlayer());
						} else {
							// allow since are new
							plugin.getLogger().info(String.format("Allowing %s because they are new!", name));
							IPLock.users.setUser(name, ip);
						}
					}
				}
			} else {
				plugin.getLogger().info(String.format("Ignoring ip check for %s", name));
			}
		} catch (Exception e) {
			plugin.getLogger().log(Level.SEVERE, "Exception while removing player ", e);
		}

	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerDropItem(PlayerDropItemEvent event) {
		if (active.containsKey(event.getPlayer().getName())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerPickupItem(PlayerPickupItemEvent event) {
		if (active.containsKey(event.getPlayer().getName())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerInteract(PlayerInteractEvent event) {
		if (active.containsKey(event.getPlayer().getName())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
		if (active.containsKey(event.getPlayer().getName())) {
			if (!Str.startIsIn(event.getMessage(), "/pass ,/passwd ,/password ")) {
				event.getPlayer().sendMessage("no interaction allowed until the password is entered");
				event.setCancelled(true);
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerTeleport(PlayerTeleportEvent event) {
		if (active.containsKey(event.getPlayer().getName())) {
			active.get(event.getPlayer().getName()).plLoc = event.getTo().clone();
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onEntityCombust(EntityCombustEvent event) {
		if (event.getEntity() instanceof Player) {
			if (active.containsKey(((Player) event.getEntity()).getName().toLowerCase())) {
				event.setCancelled(true);
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onEntityDamage(EntityDamageEvent event) {
		if (event.getEntity() instanceof Player) {
			if (active.containsKey(((Player) event.getEntity()).getName().toLowerCase())) {
				event.setCancelled(true);
			}
		}//else if(event.getCause().equals(DamageCause.ENTITY_ATTACK)) { }
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onEntityTarget(EntityTargetEvent event) {
		if (event.getTarget() instanceof Player) {
			if (active.containsKey(((Player) event.getTarget()).getName().toLowerCase())) {
				event.setCancelled(true);
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerChat(PlayerChatEvent event) {
		if (active.containsKey(event.getPlayer().getName())) {
			passwordTried(event.getPlayer(), event.getMessage());//pass);
			event.setCancelled(true);
		}
	}

	public void passwordTried(Player pl, String pass) {
		if (pass.equals(IPLock.config.defaultPass)) {
			removePlayerPassWait(pl);
			pl.sendMessage("Password Accepted");
			IPLock.users.setUser(pl.getName().toLowerCase(), IPLock.playerIP(pl));
		} else {
			pl.sendMessage("Incorrect Password");
			if (attempts.containsKey(pl.getName().toLowerCase())) {
				attempts.get(pl.getName().toLowerCase()).addAttempt();
				if (attempts.get(pl.getName().toLowerCase()).tries >= IPLock.config.maxAttempts) {
					pl.kickPlayer("Too many incorrect login attempts");
				}
			} else {
				attempts.put(pl.getName().toLowerCase(), new PasswordAttempt());
			}
			addPlayerPassWait(pl);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerQuit(PlayerQuitEvent event) {
		removePlayerPassWait(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerKick(PlayerKickEvent event) {
		removePlayerPassWait(event.getPlayer());
		if (event.getReason() != null && event.getReason().length() > 0) {
			event.setLeaveMessage(
					ChatColor.YELLOW + event.getPlayer().getDisplayName() + " was kicked: " + event.getReason());
		}
	}

	public void removePlayerPassWait(Player pl) {
		removePlayerPassWait(pl.getName());
	}

	public void removePlayerPassWait(String pl) {
		if (active.containsKey(pl.toLowerCase())) {
			active.get(pl.toLowerCase()).cancel();
			active.remove(pl.toLowerCase());
		}
	}

	public void removePlayerAttempts(String pl) {
		if (attempts.containsKey(pl.toLowerCase())) {
			attempts.remove(pl.toLowerCase());
		}
	}

	public void addPlayerPassWait(Player pl) {
		if (active.containsKey(pl.getName().toLowerCase())) {
			active.get(pl.getName().toLowerCase()).cancel();
		} else {
			if (IPLock.config.passTimeout > 0) {
				pl.sendMessage("You are locked out, and will be kicked in " + IPLock.config.passTimeout + "s if you don't enter the password");
			} else {
				pl.sendMessage("You are locked out. Enter the password to continue");
			}

		}
		active.put(pl.getName().toLowerCase(), new PlayerPaswordWait(pl, IPLock.config.defaultPass, IPLock.config.passTimeout * 1000));
	}

	static class PlayerPaswordWait extends TimerTask {

		Player player;
		String password;
		long passTimeout, startTime;
		Location plLoc = null;
		Timer kickWait = new Timer();

		public PlayerPaswordWait(Player pl, String pass, long waitTime) {
			player = pl;
			password = pass;
			passTimeout = waitTime;

			plLoc = pl.getLocation().clone();
			startTime = (new Date()).getTime();
			kickWait.scheduleAtFixedRate(this, 100, 200);
		}

		@Override
		public void run() {
			if (passTimeout > 0 && (new Date()).getTime() - startTime >= passTimeout) {
				// time's up: kick player
				player.kickPlayer("Password not Provided");
			} else if (plLoc.getX() != player.getLocation().getX()
					|| plLoc.getZ() != player.getLocation().getZ()) {
				plLoc.setPitch(player.getLocation().getPitch());
				plLoc.setYaw(player.getLocation().getYaw());
				player.teleport(plLoc);
			}//*/
			//System.out.println(player.getLocation());
			//System.out.println(plLoc);
		}
	}

	static class PasswordAttempt {

		public long time;
		public int tries;

		public PasswordAttempt() {
			time = (new Date()).getTime();
			tries = 1;
		}

		public int addAttempt() {
			return ++tries;
		}

		public long timePassed() {
			return ((new Date()).getTime() - time) / 1000;
		}
	}

	static class KickPlayerWait extends TimerTask {

		Player player;
		Timer kickWait = null;
		String kickMsg = null;
		long waitTime = 500;

		public KickPlayerWait(Player pl, String kickMessage, long waitTime) {
			player = pl;
			kickMsg = kickMessage;
			this.waitTime = waitTime;
		}

		public void start() {
			if (kickWait == null) {
				kickWait = new Timer();
				kickWait.schedule(this, waitTime);
			}
		}

		@Override
		public void run() {
			player.kickPlayer(kickMsg);
		}
	}
}
