package com.tourian86.main;

import java.io.*;
import java.net.Socket;
import java.sql.*;
import java.util.ArrayList;

@SuppressWarnings("BusyWait")
public class Driver {

    private static Connection conn = null;
    private static final String databaseURL = "jdbc:sqlite:data.db";
    private static final ArrayList<User> users = new ArrayList<>();

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

    private static User readOwner() throws SQLException {
        // Create local variables
        String sql;
        Statement statement;
        ResultSet results;
        User user;

        // Open database connection
        conn = DriverManager.getConnection(databaseURL);

        sql = "SELECT * FROM owner";
        statement = conn.createStatement();
        results = statement.executeQuery(sql);

        if(results.next()){
            user = new User(results.getString("nick"), results.getString("ident"),
                    results.getString("hostname"));
            return user;
        }
        else
        {
            throw new SQLException("Owner table error. Empty check succeded but can't pull owner info!");
        }

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

    private static void registerOwner(String nick, String ident, String hostname) throws SQLException {

        String sql = "INSERT INTO owner(nick, ident, hostname) VALUES(?,?,?)";

        conn = DriverManager.getConnection(databaseURL);
        PreparedStatement statement = conn.prepareStatement(sql);
        statement.setString(1, nick);
        statement.setString(2, ident);
        statement.setString(3, hostname);
        statement.executeUpdate();
        conn.close();
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

    public static User parseUserInfo(String line){
        String nick, ident, hostname;

        // Create a temporary buffer (String array) to split the initial command by a
        // delimiter.
        // First split the entire command from the IRC server by a space (' ')
        String[] words = line.split(" ");

        // To separate the user's nickname, split the first part of the command by
        // a delimiter of '!'
        String[] buffer = words[0].split("!");

        // Remove the leading ':' from the nickname stored in buffer[0]
        buffer[0] = buffer[0].substring(1);

        // Add the nickname to the userInfo ArrayList
        nick = buffer[0];

        // Now for the ident, split the remainder of buffer (first part of the
        // command from the IRC server that came after the '!' that separates the nickname
        // from the ident & hostname. Using '@' as the delimter as this is what separates
        // the ident from the hostname.
        buffer = buffer[1].split("@");

        // Store the portion prior to the '@' as the ident
        ident = buffer[0];
        // Store the portion preceding the '@' as the hostname.
        hostname = buffer[1];

        // Return the userInfo ArrayList to the main method.
        return new User(nick, ident, hostname);
    }

    private static ArrayList<String> parseCommandSent(String command) throws Exception {
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
        User owner = null;
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
                // Owner table exists, but is not empty, therefore owner is established.
                // The owner table exists, but is empty, therefore owner is not established.
                if(!ownerTableEmpty())
                {
                    owner = readOwner();
                    ownerEstablished = true;
                    System.out.println("Owner has been established from database.");
                    users.add(owner);
                }
                else
                {
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
                    System.out.println("I got pinged!");
                    writer.flush();
                }
                // If command is a private message (potential command)
                else if (line.split(" ")[1].equals("PRIVMSG")){
                    // Parse current line for user info and the potential command sent by the user.
                    User user  = parseUserInfo(line);
                    commandSent = parseCommandSent(line);


                    if (!ownerEstablished && commandSent.get(0).toLowerCase().equals("hello")){
                        writer.write("PRIVMSG crono :Hello. You are now the administrator.\r\n");
                        writer.flush();
                        Thread.sleep(2000);

                        writer.write("PRIVMSG crono :Normally this is where I would ask you to set a password, however, " +
                                "that functionality hasn't been completed yet.\r\n");
                        writer.flush();
                        Thread.sleep(2000);

                        writer.write("PRIVMSG crono :You will be identified by nick, ident, and hostname for now.\r\n");
                        writer.flush();
                        registerOwner(user.getNick(), user.getIdent(), user.getHostname());
                        owner = user;
                        users.add(owner);
                        System.out.println("Owner has been established from IRC user.");

                        // This line is here to hush-up a warning about the user list not being
                        // queried.
                        for(User user1 : users){
                            if(user1.getNick().equals("hello"))
                            {
                                System.out.println("I've never see. A 1:15.");
                            }
                        }

                    }
                    else if (commandSent.get(0).equals("die")){
                        // check if the command came from crono (temporary check until
                        // owner / user database functionality is completed.
                        if(user.getNick().toLowerCase().equals("crono")){

                            // Send quit command to the server
                            // This will close the connection which cause the program to
                            // exit the main loop.
                            writer.write("PRIVMSG crono :Ok...\r\n");
                            writer.write("QUIT :I'M OUT!\r\n");
                            writer.flush();
                        }
                    }
                    // debug - check owner user object
                    else if(commandSent.get(0).equals("whois") && user.getNick().toLowerCase().equals("crono"))
                    {
                        assert owner != null;
                        writer.write("PRIVMSG crono :My owner is: " + owner.getNick() + "\r\n");
                        Thread.sleep(1000);
                    }
                }
            }
        }
        catch (IOException | SQLException except)
        {
            except.printStackTrace();
        }
    }
}
