/**
 * Copyright (C) 2011 Jacob Scott <jascottytechie@gmail.com>
 * Description: ( TODO )
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

public class IPLockUsers {

	private HashMap<String, String> iplockUsers = new HashMap<String, String>();
	// last time the cache was updated
	protected Date lastCacheUpdate = null;
	// db info
	protected String iplockusersFilename = "iplock.users";
	File iplockUsersFile = null;
	MySQL_UserIp MySQL_list = null;
	private final IPLock plugin;

	public IPLockUsers(IPLock instance) {
		plugin = instance;
	} // end default constructor

	public boolean load() {
		if (IPLock.config.useFlatfile()) {
			return loadFile(IPLock.config.tableName + ".csv");
		} else {
			try {
				return loadMySQL(IPLock.config.sql_database,
						IPLock.config.tableName,
						IPLock.config.sql_username,
						IPLock.config.sql_password,
						IPLock.config.sql_hostName,
						IPLock.config.sql_portNum);
			} catch (SQLException ex) {
				plugin.getLogger().log(Level.SEVERE, null, ex);
			} catch (Exception ex) {
				plugin.getLogger().log(Level.SEVERE, null, ex);
			}
		}
		return false;
	}

	public boolean reload() {
		if (IPLock.config.useFlatfile()) {
			return loadFile(iplockusersFilename);
		} else {
			if (MySQL_list != null) {
				try {
					return MySQL_list.connect();
				} catch (SQLException ex) {
					plugin.getLogger().log(Level.SEVERE, null, ex);
				} catch (Exception ex) {
					plugin.getLogger().log(Level.SEVERE, null, ex);
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
		iplockUsersFile = new File(IPLockConfig.configFolder.getAbsolutePath() + File.separator + filename);
		if (!iplockUsersFile.exists()) {
			try {
				plugin.getLogger().info("Creating iplock user file");
				iplockUsersFile.createNewFile();
				return true;
			} catch (IOException e) {
				plugin.getLogger().log(Level.SEVERE, "Error creating users file " + filename, e);
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
			plugin.getLogger().log(Level.SEVERE, "Error connecting to MySQL database or while retrieving table list", ex);
		} catch (Exception ex) {
			plugin.getLogger().log(Level.SEVERE, "Failed to start database connection", ex);
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
				|| (((new Date()).getTime() - lastCacheUpdate.getTime()) < IPLock.config.cacheTTL * 1000)) {
			if (IPLock.config.useMySQL() && MySQL_list != null) {
				try {
					iplockUsers = MySQL_list.GetFullList();
					return true;
				} catch (SQLException ex) {
					plugin.getLogger().log(Level.SEVERE, null, ex);
				} catch (Exception ex) {
					plugin.getLogger().log(Level.SEVERE, null, ex);
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
										plugin.getLogger().log(Level.WARNING, String.format("unexpected line at %d in %s", (n + 1), iplockUsersFile.getName()));
									}
								}
							} finally {
								in.close();
							}
						} catch (IOException ex) {
							plugin.getLogger().log(Level.SEVERE, "Error opening " + iplockUsersFile.getName() + " for reading", ex);
						} finally {
							try {
								fstream.close();
							} catch (IOException ex) {
								plugin.getLogger().log(Level.SEVERE, "Error closing " + iplockUsersFile.getName(), ex);
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

	public boolean isValid(String name, String ip, boolean allowNew) {
		if (!isNameOnList(name.toLowerCase())) {
			if (allowNew) {
				// create user
				plugin.getLogger().info(String.format("Allowing %s because they are new!", name));
				setUser(name, ip);
				return true;
			} else {
				return false;
			}
		} else {
			String lastip = iplockUsers.get(name.toLowerCase());
			if (lastip.equals(ip)) {
				plugin.getLogger().info(String.format("Allowing %s because their ip matches", name));
				return true;
			} else if (IPLock.config.checkSubnet) {
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
					plugin.getLogger().info(String.format("Allowing %s from %s because they are on same subnet", name, ip));
					setUser(name, ip);
					return true;
				}
				//} catch (UnknownHostException e1) {
				//    plugin.getLogger().log(Level.SEVERE, "Error retrieving user ip");
				//    plugin.getLogger().log(e1);
				//}
				plugin.getLogger().info(String.format("DENIED %s from %s because they are on the list but not on the same subnet", name, ip));
				return false;
			}
		}
		plugin.getLogger().info(String.format("DENIED %s from %s (ip mismatch)", name, ip));
		return false;

	}

	public boolean setUser(String name, String ip) {
		if (IPLock.config.useMySQL() && MySQL_list != null) {
			try {
				if (ip.length() == 0) {
					MySQL_list.RemoveUser(name);
				} else {
					MySQL_list.SetUser(name.toLowerCase(), ip);
				}
				return true;
			} catch (Exception ex) {
				plugin.getLogger().log(Level.SEVERE, ex.getMessage(), ex);
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
						plugin.getLogger().log(Level.SEVERE, "Error opening " + iplockUsersFile.getName() + " for writing", ex);
					} finally {
						try {
							fstream.close();
						} catch (IOException ex) {
							plugin.getLogger().log(Level.SEVERE, "Error closing " + iplockUsersFile.getName(), ex);
						}
					}
					return true;
				}
			}
		}
		return false;
	}
} // end class IPLockUsers

