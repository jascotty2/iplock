package net.gamerservices.iplock;

import java.util.logging.Level;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent.Result;

public class PListener extends PlayerListener {

    //private final iplock plugin;
    public PListener(iplock instance) {
        //plugin = instance;
    }

    //Insert Player related code here
    @Override
    public void onPlayerLogin(PlayerLoginEvent event) {

        try {
            String name = event.getPlayer().getName();

            // Special Characters Addon
            if (iplock.config.blockSpecialChars && !name.matches("[A-Za-z0-9]+")) {
                iplock.Log("Kicked " + name + ": special characters in name");
                event.disallow(Result.KICK_OTHER, "You cannot connect with special characters");
                return;
            } else if (name.length() > iplock.config.max_namelen) {
                iplock.Log("Kicked " + name + ": name not less than " + iplock.config.max_namelen + " characters");
                event.disallow(Result.KICK_OTHER, "Player names must be less than " + iplock.config.max_namelen + " characters.");
            }
        } catch (Exception e) {
            iplock.Log(Level.SEVERE, "exception running name checks", e);
        }
    }

    @Override
    public void onPlayerJoin(PlayerEvent event) {
        try {
            String name = event.getPlayer().getName();
            if (iplock.config.checkUser(name)) {
                String ip = event.getPlayer().getAddress().toString(); // .getHostName()
                // will look like: /00.00.00.00:0000
                ip = ip.substring(0, ip.indexOf(":")).replaceAll("\\/", "");
                System.out.println(ip);
                if (!iplock.users.isValid(name, ip)) {
                    event.getPlayer().kickPlayer("You are trying to login to an account from the wrong IP");
                    return;
                }
            }else{
                iplock.Log("Ignoring ip check for " + name);
            }
        } catch (Exception e) {
            iplock.Log("Exception while removing player ");
            iplock.Log(e);
        }

    }
}
