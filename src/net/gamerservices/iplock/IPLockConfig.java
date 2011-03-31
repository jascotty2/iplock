/**
 * Programmer: Jacob Scott
 * Program Name: IPLockConfig
 * Description: adds configurable options to IPLock
 * Date: Mar 17, 2011
 */
package net.gamerservices.iplock;

import com.jascotty2.CheckInput;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.logging.Level;
import org.bukkit.util.config.Configuration;
import org.bukkit.util.config.ConfigurationNode;

public class IPLockConfig {

    public final static File configFolder = new File("plugins", "iplock"); //  getDataFolder();
    public final static File configfile = new File(configFolder, "config.yml");
    public boolean checkSubnet = true;
    public String tableName = "iplockusers";
    public String sql_username = "root", sql_password = "root", sql_database = "minecraft", sql_hostName = "localhost", sql_portNum = "3306";
    public boolean blockSpecialChars = true;
    public int max_namelen = 20;
    public int cacheTTL = 10; // how long cache considered current (seconds)
    private DBType databaseType = DBType.FLATFILE;
    protected ArrayList<String> userList = new ArrayList<String>();
    protected UserListAction userListMode = UserListAction.NONE;

    public boolean passwordUpdate = false, passwordLock = false;
    public String defaultPass = "password";
    public long passTimeout = 30, retryTime = 3600;
    public int maxAttempts = 3;

    public enum DBType {

        MYSQL, SQLITE, FLATFILE
    }

    public enum UserListAction {

        IGNORE, CHECK, NONE
    }

    public boolean useMySQL() {
        return databaseType == DBType.MYSQL;
    }

    public boolean useFlatfile() {
        return databaseType == DBType.FLATFILE;
    }

    public IPLockConfig() {
        load();
    } // end default constructor

    public final boolean load() {
        // Create configuration file
        if (!configFolder.exists()) {
            System.out.print("Creating iplock config folder");
            configFolder.mkdir();
        }
        if (!configfile.exists()) {
            try {
                IPLock.Log(Level.INFO, configfile.getName() + " not found. Creating new file.");
                configfile.createNewFile();
                InputStream res = IPLockConfig.class.getResourceAsStream("/config.yml");
                FileWriter tx = new FileWriter(configfile);
                try {
                    for (int i = 0; (i = res.read()) > 0;) {
                        tx.write(i);
                    }
                } finally {
                    tx.flush();
                    tx.close();
                    res.close();
                }
            } catch (IOException ex) {
                IPLock.Log(Level.SEVERE, "Failed creating new config file ", ex);
                return false;
            }
        } else {
            Configuration config = new Configuration(configfile);
            config.load();
            cacheTTL = config.getInt("tempCacheTTL", cacheTTL);

            databaseType = config.getBoolean("useMySQLUserDB", false) ? DBType.MYSQL : DBType.FLATFILE;
            String um = config.getString("checkOption", "");
            if (um.equalsIgnoreCase("include")) {
                userListMode = UserListAction.CHECK;
            } else if (um.equalsIgnoreCase("exclude")) {
                userListMode = UserListAction.IGNORE;
            } else {
                userListMode = UserListAction.NONE;
            }
            String users = config.getString("users");
            if (users != null) {
                userList.clear();
                for (String u : users.replaceAll(",", " ").split(" ")) {
                    if (u.length() > 0) {
                        userList.add(u.toLowerCase());
                    }
                }
            }
            if (databaseType == DBType.MYSQL) {
                ConfigurationNode n = config.getNode("MySQL");
                if (n != null) {
                    sql_username = n.getString("username", sql_username);
                    sql_password = n.getString("password", sql_password);
                    sql_database = n.getString("database", sql_database).replace(" ", "_");
                    sql_hostName = n.getString("Hostname", sql_hostName);
                    sql_portNum = n.getString("Port", sql_portNum);
                } else {
                    IPLock.Log(Level.WARNING, "MySQL section in " + configfile.getName() + " is missing");
                }
            }
            
            passwordUpdate = config.getBoolean("passwordUpdate", passwordUpdate);
            passwordLock = config.getBoolean("passwordLock", passwordLock);
            defaultPass = config.getString("defaultPass", defaultPass);
            maxAttempts = config.getInt("maxAttempts", maxAttempts);
            String t = config.getString("passTimeout");
            if(t!=null){
                try {
                    passTimeout = CheckInput.GetBigInt_TimeSpanInSec(t, 's').longValue();
                } catch (Exception ex) {
                    IPLock.logger.log(Level.WARNING, "passTimeout has an illegal value", ex);
                }
            }
            t = config.getString("retryTime");
            if(t!=null){
                try {
                    retryTime = CheckInput.GetBigInt_TimeSpanInSec(t, 's').longValue();
                } catch (Exception ex) {
                    IPLock.logger.log(Level.WARNING, "retryTime has an illegal value", ex);
                }
            }
            

        }
        return true;
    }

    public boolean listModeNone(){
        return userListMode == UserListAction.NONE;
    }
    public boolean checkUser(String name) {
        if (userListMode == UserListAction.IGNORE) {
            return !userList.contains(name.toLowerCase());
        } else if (userListMode == UserListAction.CHECK) {
            return userList.contains(name.toLowerCase());
        }
        return true;
    }
} // end class IPLockConfig

