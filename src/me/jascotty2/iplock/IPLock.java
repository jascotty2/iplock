/**
 * Copyright (C) 2011 Jacob Scott <jascottytechie@gmail.com>
 * Description: fork project from 
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
import java.net.InetAddress;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.PluginManager;
import java.util.logging.Level;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class IPLock extends JavaPlugin {

	public static final String name = "IpLock";
	private final PListener playerListener = new PListener(this);
	protected static IPLockConfig config = null; // new IPLockConfig(this);
	protected static IPLockUsers users = null;

	public IPLock() {
		if (config == null) {
			config = new IPLockConfig(this);
			users = new IPLockUsers(this);
		}
	}

	public void onEnable() {
		// Register events
		PluginManager pm = getServer().getPluginManager();
		pm.registerEvents(playerListener, this);

		if (!users.load()) {
			getLogger().log(Level.SEVERE, "Failed to load users database!");
			//this.setEnabled(false);
			//return;
		}

		PluginDescriptionFile pdfFile = this.getDescription();
		getLogger().info(String.format("version %s is enabled!", pdfFile.getVersion()));
	}

	public void onDisable() {
		getLogger().info("disabled");
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command,
			String commandLabel, String[] args) {
		if (command.getName().equalsIgnoreCase("iplock")) {
			if (sender.isOp()) {
				if (args.length == 0) {
					return false;
				} else if (args[0].equalsIgnoreCase("reload")) {
					if (config.load()) {
						sender.sendMessage("Cofiguration Reloaded");
						users = new IPLockUsers(this);
						if (!users.load()) {
							sender.sendMessage("Failed to load users database!");
						} else {
							sender.sendMessage("Users Database Reloaded");
						}
					} else {
						sender.sendMessage("Failed to load plugin configuration!");
					}
				} else if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
					if (users.setUser(args[1], "")) {
						//playerListener.removePlayerPassWait(args[1]);
						playerListener.removePlayerAttempts(args[1]);
						sender.sendMessage(args[1] + " login information was reset");
					} else {
						sender.sendMessage("IP Reset Error");
					}
				} else {
					return false;
				}
			} else {
				sender.sendMessage("IpLock is for OPs only!");
			}
			return true;
		} else if (command.getName().equalsIgnoreCase("passwd")) {
			if (sender instanceof Player) {
				if (PListener.active.containsKey(((Player) sender).getName())) {
					playerListener.passwordTried((Player) sender, Str.argStr(args));
				} else {
					sender.sendMessage("You are not locked");
				}
			} else {
				sender.sendMessage("password unlock for players only");
			}

			return true;
		}
		return false;
	}

	public boolean ipIsOnline(InetAddress ip) {
		for (Player p : this.getServer().getOnlinePlayers()) {
			if (p.getAddress().getAddress().equals(ip)) {
				return true;
			}
		}
		return false;
	}

	public boolean ipIsOnline(Player pl) {
		InetAddress ip = pl.getAddress().getAddress();
		for (Player p : this.getServer().getOnlinePlayers()) {
			if (!pl.equals(p) && p.getAddress().getAddress().equals(ip)) {
				return true;
			}
		}
		return false;
	}

	public static String playerIP(Player pl) {
		String ip = pl.getAddress().toString();
		ip = ip.substring(0, ip.indexOf(":")).replaceAll("\\/", "");
		return ip;
	}
}
