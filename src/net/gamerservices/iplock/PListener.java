package net.gamerservices.iplock;

import com.jascotty2.Str;
import java.util.Date;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityListener;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent.Result;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class PListener extends PlayerListener {

    // list of players that have not yet been verified
    static HashMap<String, PlayerPaswordWait> active = new HashMap<String, PlayerPaswordWait>();
    static HashMap<String, PasswordAttempt> attempts = new HashMap<String, PasswordAttempt>();
    static PlayerDamage damageListener = new PlayerDamage();

    //private final IPLock plugin;
    public PListener(IPLock instance) {
        //plugin = instance;
    }

    //Insert Player related code here
    @Override
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
                    IPLock.Log("Kicked " + name + ": special characters in name");
                    event.disallow(Result.KICK_OTHER, "You cannot connect with special characters");
                    return;
                } else if (IPLock.config.max_namelen > 0 && name.length() > IPLock.config.max_namelen) {
                    IPLock.Log("Kicked " + name + ": name not less than " + IPLock.config.max_namelen + " characters");
                    event.disallow(Result.KICK_OTHER, "Player names must be less than " + IPLock.config.max_namelen + " characters.");
                }
            }
        } catch (Exception e) {
            IPLock.Log(Level.SEVERE, "exception running name checks", e);
        }
    }

    @Override
    public void onPlayerJoin(PlayerJoinEvent event) {
        try {
            String name = event.getPlayer().getName();
            if (IPLock.config.checkUser(name)) {
                String ip = playerIP(event.getPlayer());
                if (!IPLock.users.isValid(name, ip, false)) {
                    if (IPLock.users.isNameOnList(name)) {
                        if (IPLock.config.passwordUpdate) {
                            addPlayerPassWait(event.getPlayer());
                        } else {
                            event.getPlayer().kickPlayer("You are trying to login to an account from the wrong IP");
                        }
                        return;
                    } else {
                        // new player
                        if (IPLock.config.passwordLock) {
                            addPlayerPassWait(event.getPlayer());
                        } else {
                            // allow since are new
                            IPLock.Log("Allowing " + name + " because they are new!");
                            IPLock.users.setUser(name, ip);
                        }
                    }
                }
            } else {
                IPLock.Log("Ignoring ip check for " + name);
            }
        } catch (Exception e) {
            IPLock.Log("Exception while removing player ");
            IPLock.Log(e);
        }

    }

    @Override
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (active.containsKey(event.getPlayer().getName())) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        if (active.containsKey(event.getPlayer().getName())) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (active.containsKey(event.getPlayer().getName())) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (active.containsKey(event.getPlayer().getName())) {
            if (!Str.startIsIn(event.getMessage(), "/pass ,/passwd ,/password ")) {
                event.getPlayer().sendMessage("no interaction allowed until the password is entered");
                event.setCancelled(true);
            }
        }
    }

    @Override
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (active.containsKey(event.getPlayer().getName())) {
            active.get(event.getPlayer().getName()).plLoc = event.getTo().clone();
        }
    }

    public static class PlayerDamage extends EntityListener {

        @Override
        public void onEntityCombust(EntityCombustEvent event) {
            if (event.getEntity() instanceof Player) {
                if (active.containsKey(((Player) event.getEntity()).getName().toLowerCase())) {
                    event.setCancelled(true);
                }
            }
        }

        @Override
        public void onEntityDamage(EntityDamageEvent event) {
            if (event.getEntity() instanceof Player) {
                if (active.containsKey(((Player) event.getEntity()).getName().toLowerCase())) {
                    event.setCancelled(true);
                }
            }//else if(event.getCause().equals(DamageCause.ENTITY_ATTACK)) { }
        }

        @Override
        public void onEntityTarget(EntityTargetEvent event) {
            if (event.getTarget() instanceof Player) {
                if (active.containsKey(((Player) event.getTarget()).getName().toLowerCase())) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @Override
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
            IPLock.users.setUser(pl.getName().toLowerCase(), playerIP(pl));
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

    public static String playerIP(Player pl) {
        String ip = pl.getAddress().toString();
        ip = ip.substring(0, ip.indexOf(":")).replaceAll("\\/", "");
        return ip;
    }

    @Override
    public void onPlayerQuit(PlayerQuitEvent event) {
        removePlayerPassWait(event.getPlayer());
    }

    @Override
    public void onPlayerKick(PlayerKickEvent event) {
        removePlayerPassWait(event.getPlayer());
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
}
