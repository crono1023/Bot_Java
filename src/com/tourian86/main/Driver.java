package com.tourian86.main;

import java.io.*;
import java.net.Socket;
import java.sql.*;
import java.util.ArrayList;

public class Driver {

    private static Connection conn = null;
    private static final String databaseURL = "jdbc:sqlite:data.db";

    public static void addSampleUsers() throws SQLException {
        // Open database connection
        conn = DriverManager.getConnection(databaseURL);

        // Create sql query
        String sql = "INSERT INTO users(username, password) VALUES (?,?)";

        // Create prepared statement and add parameters safely
        PreparedStatement preparedStatement = conn.prepareStatement(sql);
        preparedStatement.setString(1, "Chester Golden");
        preparedStatement.setString(2, "password");

        // Execute query
        preparedStatement.executeUpdate();

        // Close database connection
        conn.close();
    }

    private static void createOwnerTable() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS owner (" +
                "id integer PRIMARY KEY, " +
                "nick text NOT NULL, " +
                "ident text NOT NULL, " +
                "hostname text NOT NULL);";
        Statement statement = conn.createStatement();
        statement.execute(sql);
        System.out.println("Owner table created");
    }

    private static void createUsersTable() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS users (" +
                "id integer PRIMARY KEY," +
                "username text NOT NULL," +
                "password text NOT NULL);";
        Statement stmt = conn.createStatement();
        stmt.execute(sql);
    }

    private static boolean ownerTableExists() throws SQLException{
        String sql = "SELECT name FROM sqlite_master WHERE type='table' AND name='owner'";

        Statement statement = conn.createStatement();
        ResultSet rs;
        rs = statement.executeQuery(sql);

        if (rs.next()) {
            System.out.println("Found table");
            return true;
        } else {
            System.out.println("Didn't find table");
            return false;
        }
    }

    private static boolean ownerTableEmpty() throws SQLException {
        String sql = "SELECT * from owner";
        Statement statement = conn.createStatement();
        ResultSet rs = statement.executeQuery(sql);
        // Returns true if owner table is empty, false if a record is found.
        return !rs.next();
    }

    public static ArrayList<String> parseUserInfo(String line){
        // Create ArrayList to return to the main method that will contain
        // three pieces of information, nick, ident, and hostname respectively.
        ArrayList<String> userInfo = new ArrayList<>();

        // Create a temporary buffer (String array) to split the initial command by a
        // delimiter.
        // First split the entire command from the IRC server by a space (' ')
        String[] words = line.split(" ");

        // To separate the user's nickname, split the first part of the command by
        // a delimter of '!'
        String[] buffer = words[0].split("!");

        // Remove the leading ':' from the nickname stored in buffer[0]
        buffer[0] = buffer[0].substring(1);

        // Add the nickname to the userInfo ArrayList
        userInfo.add(buffer[0]);

        // Now for the ident, split the remainder of buffer (first part of the
        // command from the IRC server that came after the '!' that separates the nickname
        // from the ident & hostname. Using '@' as the delimter as this is what separates
        // the ident from the hostname.
        buffer = buffer[1].split("@");

        // Store the portion prior to the '@' as the ident
        userInfo.add(buffer[0]);
        // Store the portion preceding the '@' as the hostname.
        userInfo.add(buffer[1]);

        // Return the userInfo ArrayList to the main method.
        return userInfo;
    }

    private static ArrayList<String> parseCommandSent(@NotNull String command) throws Exception {
        // Create array list to return to the main method
        ArrayList<String> commandSent = new ArrayList<>();

        // Create string array for temporary use. Splitting initial command
        // into an array with a space ' ' as the delimiter.
        String[] buffer = command.split(" ");

        if(buffer.length <= 2){
            throw new Exception("Buffer does not contain command");
        }
        // Loop through buffer array
        for (int i = 0; i < buffer.length; i++){
            // Skip over the user info and privmsg command
            if (i >= 3) {
                // If this is the first part of the command sent, remove the ':'
                if(i == 3){
                    buffer[i] = buffer[i].substring(1);
                }
                // Store each part of the command in the commandSet ArrayList
                commandSent.add(buffer[i]);
            }
        }
        // Return commandSent to main method
        return commandSent;

    }


    public static void main(String[] args) throws Exception {

        // Server and connection details
        String server = "irc.freenode.net";
        int port = 6667;
        String nick = "Tourian";
        String login = "chester";
        String channel = "#crono";
        String realName = ":The last metroid is in captivity";
        ArrayList<String> commandSent;
        boolean ownerEstablished;
        //String configurationPassword = "hereandnow";




        // Connect to the irc server
        try{

            conn = DriverManager.getConnection(databaseURL);
            System.out.println("Connected to database data.db.");

            // Check if table called "owner" exists
            if(!ownerTableExists())
            {
                // If table doesn't exist, create it and we can safely say the owner isn't
                // established yet
                createOwnerTable();
                ownerEstablished = false;
            }
            else
            {
                // If the table does exists, check to see if it is empty.
                if(!ownerTableEmpty()){
                    // Owner table exists, but is not empty, therefore owner is established.
                    ownerEstablished = true;
                }
                else{
                    // The owner table exists, but is empty, therefore owner is not established.
                    ownerEstablished = false;
                }
            }

            // Create user tables. Will only create a new table if the user table doesn't exist already
            createUsersTable();

            // Close the database connection
            conn.close();

            // Create a socket and connect to the server.
            Socket socket = new Socket(server, port);

            // Create objects to read and write date to/from the socket
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Log on to the server
            writer.write("NICK " + nick + "\r\n");
            writer.write("USER " + login + " * * " + realName + "\r\n");
            writer.flush();

            // Read from the server until it tells us we have connected
            String line;
            while((line = reader.readLine()) != null){
                System.out.println(line);
                if(line.contains("004")){
                    // We are logged in
                    break;
                }
                else if(line.contains("433")){
                    System.out.println("Nickname already in use. Disconnecting.");
                    return;
                }
            }

            // Join the channel
            writer.write("JOIN " + channel + "\r\n");
            writer.flush();

            // Keep reading lines from the server
            while((line = reader.readLine()) != null){
                System.out.println(line);
                if (line.toLowerCase().startsWith("ping ")) {
                    // Must respond to pings to avoid disconnection
                    writer.write("PONG " + line.substring(5) + "\r\n");
                    writer.write("PRIVMSG " + channel + " :I got pinged!\r\n");
                    writer.flush();
                } else if (line.split(" ")[1].equals("PRIVMSG")){
                    // Parse current line for user info and the potential command sent by the user.
                    ArrayList<String> userInfo = parseUserInfo(line);
                    commandSent = parseCommandSent(line);

                    if(userInfo.get(0).equals("crono")){
                        System.out.println("Redundancy eliminated!!!!");
                    }
                    if (!ownerEstablished && commandSent.get(0).toLowerCase().equals("hello")){
                        writer.write("PRIVMSG crono :It's working!\r\n");
                        writer.flush();
                    }
                    else if (commandSent.get(0).equals("die")){
                        // check if the command came from crono (temporary check until
                        // owner / user database functionality is completed.
                        if(userInfo.get(0).toLowerCase().equals("crono")){

                            // Send quit command to the server
                            // This will close the connection which cause the program to
                            // exit the main loop.
                            writer.write("PRIVMSG crono :Ok...\r\n");
                            writer.write("QUIT :I'M OUT!\r\n");
                            writer.flush();
                        }

                    }
                    else if(line.toLowerCase().contains("privmsg tourian :yo")) {
                        writer.write("PRIVMSG Crono :Yo!\r\n");
                        writer.flush();
                    } else if(line.substring(1, 6).toLowerCase().equals("crono")){
                        String[] words = line.split(" ");
                        if(words[1].toUpperCase().equals("PRIVMSG")) {
                            switch (words[3].toLowerCase()) {
                                case ":hey":
                                    writer.write("PRIVMSG crono :Hey!\r\n");
                                    writer.flush();
                                    userInfo = parseUserInfo(line);
                                    System.out.println("Nick: " + userInfo.get(0));
                                    System.out.println("Ident: " + userInfo.get(1));
                                    System.out.println("Host: " + userInfo.get(2));
                                    break;
                                case ":addsample":
                                    addSampleUsers();
                                    System.out.println("Added sample user to the database.");
                                    writer.write("PRIVMSG crono :Added sample user to the database.\r\n");
                                    writer.flush();
                                    break;
                            }
                        }
                    }
                }
            }
        } catch (IOException | SQLException except) {
            except.printStackTrace();
        }
    }
}
