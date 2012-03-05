/**
 * Copyright (C) 2011 Jacob Scott <jascottytechie@gmail.com>
 * Description: class for working with a MySQL server
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
package com.jascotty2.MySQL;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;


public class MySQL_UserIp {

    // local copy of current connection info
    private String sql_database = "minecraft", sql_tableName = "iplockusers";
    // DB connection
    public MySQL MySQLdatabase = new MySQL();

    public MySQL_UserIp(String database, String tableName, String username, String password, String hostName, String portNum) throws SQLException, Exception {

        MySQLdatabase.connect(database, username, password, hostName, portNum);
        sql_database = database;
        sql_tableName = tableName;
        // now check & create table
        if (!MySQLdatabase.tableExists(tableName)) {
            createUserlistTable(tableName);
        }
    } // end default constructor

    public MySQL getMySQLconnection() {
        return MySQLdatabase;
    }

    public final boolean connect() throws SQLException, Exception {
        if (MySQLdatabase == null) {
            return false;
        }
        return MySQLdatabase.connect();
    }

    public final boolean connect(String database, String tableName, String username, String password, String hostName, String portNum) throws SQLException, Exception {
        try {
            MySQLdatabase.connect(database, username, password, hostName, portNum);
            sql_database = database;
            sql_tableName = tableName;
        } catch (SQLException ex) {
            throw new SQLException("Error connecting to MySQL database", ex);
        } catch (Exception e) {
            throw new Exception("Failed to start database connection", e);
        }
        // now check if table is there
        boolean exst = false;
        try {
            exst = MySQLdatabase.tableExists(tableName);
        } catch (SQLException ex) {
            throw new SQLException("Error while retrieving table list", ex);
        } catch (Exception e) {
            throw new Exception("unexpected database error", e);
        }
        if (!exst) {
            // table does not exist, so create it
            createUserlistTable(tableName);
        }
        return true;
    }

    public void disconnect() {
        MySQLdatabase.disconnect();
    }

    // manually force database to save
    public void commit() throws SQLException {
        if (MySQLdatabase.IsConnected()) {
            try {
                MySQLdatabase.commit();
            } catch (SQLException ex) {
                throw new SQLException("failed to run COMMIT on database", ex);
            }
        }
    }

    public String GetUserIP(String name) throws SQLException, Exception {
        if (MySQLdatabase.IsConnected()) {
            try {
                ResultSet table = MySQLdatabase.GetQuery(
                        String.format("SELECT * FROM %s WHERE NAME='%s';", sql_tableName, name));
                if (table.first()) {
                    return table.getString(2);
                }
            } catch (SQLException ex) {
                throw new SQLException("Error executing SELECT on " + sql_tableName, ex);
            }
        } else {
            throw new Exception("Is Not connected to database: not checking for item");
        }
        return null;
    }

    public boolean SetUser(String userName, String ip) throws SQLException, Exception {
        if (GetUserIP(userName) != null) {
            try {
                MySQLdatabase.RunUpdate(String.format(
                        "UPDATE %s SET IP4='%s' WHERE NAME='%s';", sql_tableName, ip, userName.toLowerCase()));
                return true;
            } catch (SQLException ex) {
                throw new SQLException("Error executing UPDATE on " + sql_tableName, ex);
            }
        } else {
            try {
                MySQLdatabase.RunUpdate(String.format(
                        "INSERT INTO %s VALUES('%s', '%s');", sql_tableName, userName.toLowerCase(), ip));
                return true;
            } catch (SQLException ex) {
                throw new SQLException("Error executing INSERT on " + sql_tableName, ex);
            }
        }
    }

    public boolean RemoveUser(String name) throws SQLException {
        if (MySQLdatabase.IsConnected()) {
            try {
                return MySQLdatabase.RunUpdate(String.format(
                        "DELETE FROM %s WHERE NAME='%s';", sql_tableName, name.toLowerCase())) > 0;
            } catch (SQLException ex) {
                throw new SQLException("Error executing DELETE on " + sql_tableName, ex);
            }
        }
        return false;
    }

    public HashMap<String, String> GetFullList() throws SQLException, Exception {
        HashMap<String, String> tableDat = new HashMap<String, String>();
        if (MySQLdatabase.IsConnected()) {
            try {
                ResultSet table = MySQLdatabase.GetQuery(
                        "SELECT * FROM " + sql_tableName + ";");
                for (table.beforeFirst(); table.next();) {
                    tableDat.put(table.getString(1).toLowerCase(), table.getString(2));
                }
            } catch (SQLException ex) {
                throw new SQLException("Error executing SELECT on " + sql_tableName, ex);
            }
        } else {
            throw new Exception("Error: MySQL DB not connected");
        }
        return tableDat;
    }

    public boolean IsConnected() {
        return MySQLdatabase.IsConnected();
    }

    private boolean createUserlistTable(String tableName) throws SQLException {
        if (!MySQLdatabase.IsConnected() || tableName.contains(" ")) {
            return false;
        }
        try {
            MySQLdatabase.RunUpdate("CREATE TABLE " + sql_database + "." + tableName
                    + "(NAME  VARCHAR(40)  NOT NULL,"
                    + "IP4   VARCHAR(15)  NOT NULL,"
                    + "PRIMARY KEY (NAME));");
        } catch (SQLException e) {
            throw new SQLException("Error while creating table", e);
        }
        return true;
    }

    public String GetDatabaseName() {
        return sql_database;
    }

    public String GetTableName() {
        return sql_tableName;
    }

    public String GetUserName() {
        return MySQLdatabase.GetUserName();
    }

    public String GetHostName() {
        return MySQLdatabase.GetHostName();
    }

    public String GetPortNum() {
        return MySQLdatabase.GetPortNum();
    }
} // end class MySQL_UserIp

