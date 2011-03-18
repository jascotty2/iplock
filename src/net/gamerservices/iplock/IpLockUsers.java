/**
 * Programmer: Jacob Scott
 * Program Name: IpLockUsers
 * Description:
 * Date: Mar 17, 2011
 */
package net.gamerservices.iplock;

import com.jascotty2.MySQL.MySQL_UserIp;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.logging.Level;

public class IpLockUsers {

    private HashMap<String, String> iplockUsers = new HashMap<String, String>();
    // last time the cache was updated
    protected Date lastCacheUpdate = null;
    // db info
    protected String iplockusersFilename = "iplock.users";
    File iplockUsersFile = null;
    MySQL_UserIp MySQL_list = null;

    public IpLockUsers() {
    } // end default constructor

    public boolean load() {
        if (iplock.config.useFlatfile()) {
            return loadFile(iplock.config.tableName + ".csv");
        } else {
            try {
                return loadMySQL(iplock.config.sql_database,
                        iplock.config.tableName,
                        iplock.config.sql_username,
                        iplock.config.sql_password,
                        iplock.config.sql_hostName,
                        iplock.config.sql_portNum);
            } catch (SQLException ex) {
                iplock.Log(Level.SEVERE, null, ex);
            } catch (Exception ex) {
                iplock.Log(Level.SEVERE, null, ex);
            }
        }
        return false;
    }

    public boolean reload() {
        if (iplock.config.useFlatfile()) {
            return loadFile(iplockusersFilename);
        } else {
            if (MySQL_list != null) {
                try {
                    return MySQL_list.connect();
                } catch (SQLException ex) {
                    iplock.Log(Level.SEVERE, null, ex);
                } catch (Exception ex) {
                    iplock.Log(Level.SEVERE, null, ex);
                }
            }
            return false;
        }
    }

    public final boolean loadFile(String filename) {
        if (MySQL_list != null) {
            MySQL_list.disconnect();
            MySQL_list = null;
        }
        iplockusersFilename = filename;
        iplockUsersFile = new File(iplockconfig.configFolder.getAbsolutePath() + File.separator + filename);
        if (!iplockUsersFile.exists()) {
            try {
                iplock.Log("Creating iplock user file");
                iplockUsersFile.createNewFile();
                return true;
            } catch (IOException e) {
                iplock.Log(Level.SEVERE, "Error creating users file " + filename);
                iplock.Log(Level.SEVERE, e);
                return false;
            }
        } else {
            // no caching here
            return true;
        }
    }

    public final boolean loadMySQL(String database, String tableName, String username, String password, String hostName, String portNum) throws SQLException, Exception {
        try {
            if (MySQL_list == null) {
                MySQL_list = new MySQL_UserIp(database, tableName, username, password, hostName, portNum);
            } else {
                MySQL_list.connect(database, tableName, username, password, hostName, portNum);
            }
        } catch (SQLException ex) {
            iplock.Log(Level.SEVERE, "Error connecting to MySQL database or while retrieving table list", ex);
        } catch (Exception ex) {
            iplock.Log(Level.SEVERE, "Failed to start database connection", ex);
        }
        if (MySQL_list == null || !MySQL_list.IsConnected()) {
            MySQL_list = null;
        }
        return MySQL_list != null;
    }

    public boolean updateCache() {
        return updateCache(false);
    }

    public boolean updateCache(boolean forceUpdate) {
        if (forceUpdate || lastCacheUpdate == null
                || (((new Date()).getTime() - lastCacheUpdate.getTime()) < iplock.config.cacheTTL * 1000)) {
            if (iplock.config.useMySQL() && MySQL_list != null) {
                try {
                    iplockUsers = MySQL_list.GetFullList();
                    return true;
                } catch (SQLException ex) {
                    iplock.Log(Level.SEVERE, null, ex);
                } catch (Exception ex) {
                    iplock.Log(Level.SEVERE, null, ex);
                }
                return false;
            } else { // databaseType == DBType.FLATFILE
                if (iplockUsersFile != null) {
                    iplockUsers.clear();
                    if (iplockUsersFile.exists()) {
                        FileReader fstream = null;
                        try {
                            fstream = new FileReader(iplockUsersFile.getAbsolutePath());
                            BufferedReader in = new BufferedReader(fstream);
                            try {
                                int n = 0;
                                for (String line = null; (line = in.readLine()) != null && line.length() > 0; ++n) {
                                    // if was edited in openoffice, will instead have semicolins..
                                    String fields[] = line.replace(";", ",").replace(",,", ", ,").split(",");
                                    if (fields.length >= 2) {
                                        iplockUsers.put(fields[0].toLowerCase(), fields[1]);
                                    } else {
                                        iplock.Log(Level.WARNING, String.format("unexpected line at %d in %s", (n + 1), iplockUsersFile.getName()));
                                    }
                                }
                            } finally {
                                in.close();
                            }
                        } catch (IOException ex) {
                            iplock.Log(Level.SEVERE, "Error opening " + iplockUsersFile.getName() + " for reading", ex);
                        } finally {
                            try {
                                fstream.close();
                            } catch (IOException ex) {
                                iplock.Log(Level.SEVERE, "Error closing " + iplockUsersFile.getName(), ex);
                            }
                        }
                        return true;
                    }
                }
            }
        }
        return true;
    }

    public boolean isIPOnList(String ip) {
        if (updateCache()) {
            return iplockUsers.containsValue(ip);
        } else {
            return false;
        }
    }

    public boolean isNameOnList(String name) {
        if (updateCache()) {
            return iplockUsers.containsKey(name.toLowerCase());
        } else {
            return false;
        }
    }

    public boolean isValid(String name, String ip) {
        if (!isNameOnList(name.toLowerCase())) {
            // create user
            iplock.Log("Allowing " + name + " because they are new!");
            setUser(name, ip);
            return true;
        } else {
            String lastip = iplockUsers.get(name.toLowerCase());
            if (lastip.equals(ip)) {
                iplock.Log("Allowing " + name + " because their ip matches");
                return true;
            } else if (iplock.config.checkSubnet) {
                // check if player is on the same subnet
                //java.net.InetAddress inetAddthem;
                //try {
                //inetAddthem = java.net.InetAddress.getByName(lastip);
                //String theirip = inetAddthem.getHostAddress();
                //System.out.println("IP Address is : " + theirip);
                //String ippart1 = theirip.split("\\.")[0];
                //String ippart2 = theirip.split("\\.")[1];
                String ippart1 = lastip.split("\\.")[0];
                String ippart2 = lastip.split("\\.")[1];

                if (ip.split("\\.")[0].equalsIgnoreCase(ippart1) && ip.split("\\.")[1].equalsIgnoreCase(ippart2)) {
                    iplock.Log("Allowing " + name + " from " + ip + " because they are on same subnet");
                    setUser(name, ip);
                    return true;
                }
                //} catch (UnknownHostException e1) {
                //    iplock.Log(Level.SEVERE, "Error retrieving user ip");
                //    iplock.Log(e1);
                //}
                iplock.Log("DENIED " + name + " from " + ip + " because they are on the list but not on the same subnet");
                return false;
            }
        }
        iplock.Log("DENIED " + name + " from " + ip + " (ip mismatch)");
        return false;

    }

    public boolean setUser(String name, String ip) {
        if (iplock.config.useMySQL() && MySQL_list != null) {
            try {
                if (ip.length() == 0) {
                    MySQL_list.RemoveUser(name);
                } else {
                    MySQL_list.SetUser(name.toLowerCase(), ip);
                }
                return true;
            } catch (SQLException ex) {
                iplock.Log(Level.SEVERE, null, ex);
            } catch (Exception ex) {
                iplock.Log(Level.SEVERE, null, ex);
            }
        } else { // databaseType == DBType.FLATFILE
            if (iplockUsersFile != null) {
                if (iplockUsersFile.exists()) {
                    updateCache(true);
                    if (ip.length() == 0) {
                        if (iplockUsers.containsKey(name.toLowerCase())) {
                            iplockUsers.remove(name);
                        }
                    } else {
                        iplockUsers.put(name.toLowerCase(), ip);
                    }
                    FileWriter fstream = null;
                    try {
                        fstream = new FileWriter(iplockUsersFile.getAbsolutePath());
                        BufferedWriter out = new BufferedWriter(fstream);
                        try {
                            for (String n : iplockUsers.keySet()) {
                                out.write(n + "," + iplockUsers.get(n));
                                out.newLine();
                            }
                        } finally {
                            out.close();
                        }
                    } catch (IOException ex) {
                        iplock.Log(Level.SEVERE, "Error opening " + iplockUsersFile.getName() + " for writing", ex);
                    } finally {
                        try {
                            fstream.close();
                        } catch (IOException ex) {
                            iplock.Log(Level.SEVERE, "Error closing " + iplockUsersFile.getName(), ex);
                        }
                    }
                    return true;
                }
            }
        }
        return false;
    }
} // end class IpLockUsers

