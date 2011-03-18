package net.gamerservices.iplock;

import org.bukkit.event.Event.Priority;
import org.bukkit.event.Event;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.PluginManager;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

public class iplock extends JavaPlugin {

    protected final static Logger logger = Logger.getLogger("Minecraft");
    public static final String name = "IpLock";
    private final PListener playerListener = new PListener(this);
    public static iplockconfig config = new iplockconfig();
    public static IpLockUsers users = null;

    public void onDisable() {
        System.out.println("iplock disabled");
    }

    public void onEnable() {
        Log("Starting up..");
        // Register events
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvent(Event.Type.PLAYER_LOGIN, playerListener, Priority.Normal, this);
        pm.registerEvent(Event.Type.PLAYER_JOIN, playerListener, Priority.Normal, this);
        pm.registerEvent(Event.Type.PLAYER_QUIT, playerListener, Priority.Normal, this);

        users = new IpLockUsers();
        if (!users.load()) {
            Log(Level.SEVERE, "Failed to load users database!");
            //this.setEnabled(false);
            //return;
        }

        PluginDescriptionFile pdfFile = this.getDescription();
        Log(pdfFile.getName() + " version " + pdfFile.getVersion() + " is enabled!");
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
                        users = new IpLockUsers();
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
                        sender.sendMessage(args[1] + " IP was reset");
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
        }
        return false;
    }

    public static void Log(String txt) {
        logger.log(Level.INFO, String.format("[%s] %s", name, txt));
    }

    public static void Log(String txt, Object params) {
        logger.log(Level.INFO, String.format("[%s] %s", name, txt == null ? "" : txt), params);
    }

    public static void Log(Level loglevel, String txt) {
        logger.log(loglevel, String.format("[%s] %s", name, txt == null ? "" : txt));
    }

    public static void Log(Level loglevel, String txt, Object params) {
        logger.log(loglevel, String.format("[%s] %s", name, txt == null ? "" : txt), params);
    }

    public static void Log(Level loglevel, String txt, Exception params) {
        if (txt == null) {
            Log(loglevel, params);
        } else {
            logger.log(loglevel, String.format("[%s] %s", name, txt == null ? "" : txt), (Exception) params);
        }
    }

    public static void Log(Level loglevel, String txt, Object[] params) {
        logger.log(loglevel, String.format("[%s] %s", name, txt == null ? "" : txt), params);
    }

    public static void Log(Level loglevel, Exception err) {
        logger.log(loglevel, String.format("[%s] %s", name, err == null ? "? unknown exception ?" : err.getMessage()), err);
    }

    static void Log(Object e) {
        if (e instanceof Exception) {
            Log(Level.INFO, (Exception) e);
        } else {
            logger.log(Level.INFO, String.format("[%s] %s", name), e);
        }
    }
}
