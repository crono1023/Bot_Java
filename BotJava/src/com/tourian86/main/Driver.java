package com.tourian86.main;

import com.tourian86.irc.Channel;

import java.io.*;
import java.net.Socket;
import java.sql.*;
import java.util.ArrayList;

@SuppressWarnings("BusyWait")
public class Driver {

    private static Connection conn = null;
    private static final String databaseURL = "jdbc:sqlite:data.db";
    private static final ArrayList<User> users = new ArrayList<>();
    private static final ArrayList<Integer> accessLevels = new ArrayList<>();
    private static BufferedWriter writer;
    private static final int delayTime = 1500;
    private static final String botNick = "Tourian";

    private static void sendPrivateMessage(User user, String message) throws IOException, InterruptedException
    {
        System.out.println("Fuk");
        writer.write("PRIVMSG " + user.getNick() + " :" + message + "\r\n");
        writer.flush();
        Thread.sleep(delayTime);
        String logLine = "<" + botNick + ">: " + message;
        writeToLog(logLine);
    }
    private static void writeToLog(String line) throws IOException {
        FileWriter logWriter = new FileWriter("test_log", true);
        logWriter.write(line + "\n");
        logWriter.close();
    }

    private static boolean isAuthenticatedUser(User user)
    {
        for(User currentUser : users)
        {
            if(user.getNick().toLowerCase().equals(currentUser.getNick().toLowerCase()))
            {
                // Make sure ident and hostname match
                if(user.getIdent().equals(currentUser.getIdent()))
                {
                    if(user.getHostname().equals(currentUser.getHostname()))
                    {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static void userListAdd(User user, int accessLevel)
    {
        // Add user to the users list and their access level to the access level list
        // Users will have the same index to both lists
        users.add(user);
        accessLevels.add(accessLevel);
    }

    public static void userListRemove(User user) {
        int userIndex = -1;
        for(int i = 0; i < users.size();i++){
            if(users.get(i) == user){
                userIndex = i;
                break;
            }
        }
        users.remove(userIndex);
        accessLevels.remove(userIndex);
    }

    private static User readOwner() throws SQLException {
        // Create local variables
        String sql;
        Statement statement;
        ResultSet results;
        User user;



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

    private static void createChannelsTable() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS channels (" +
                "id integer PRIMARY KEY, " +
                "name text NOT NULL, " +
                "owner_nick text NOT_NULL," +
                "owner_ident text NOT NULL, " +
                "owner_hostname text NOT NULL," +
                "topic text, " +
                "bans text," +
                "admins text);";

        Statement statement = conn.createStatement();
        statement.execute(sql);
        System.out.println("Channels table created.");

    }
    private static void addChannel(Channel channel) throws SQLException {
        String sql = "INSERT INTO channels(name, owner_nick, owner_ident, owner_hostname," +
            "topic, bans, admins) VALUES(?, ?, ?, ?, ?, ?, ?)";
        User channelOwner = channel.getOwner();
        conn = DriverManager.getConnection(databaseURL);
        PreparedStatement statement = conn.prepareStatement(sql);
        statement.setString(1, channel.getName());
        statement.setString(2, channelOwner.getNick());
        statement.setString(3, channelOwner.getIdent());
        statement.setString(4, channelOwner.getHostname());
        if(!channel.getTopic().equals("")  || channel.getTopic() != null) {
            statement.setString(5, channel.getTopic());
        } else {
            statement.setString(5, "");
        }
        statement.setString(6, channel.getBansList());
        statement.setString(7, channel.getAdminsList());



        statement.executeUpdate();
        conn.close();
        System.out.println("Wrote chnnale to channel database.");
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
                "password text NOT NULL," +
                "access integer NOT NULL);";
        Statement stmt = conn.createStatement();
        stmt.execute(sql);
    }

    private static void registerOwner(User user) throws SQLException {

        String sql = "INSERT INTO owner(nick, ident, hostname) VALUES(?,?,?)";

        conn = DriverManager.getConnection(databaseURL);
        PreparedStatement statement = conn.prepareStatement(sql);
        statement.setString(1, user.getNick());
        statement.setString(2, user.getIdent());
        statement.setString(3, user.getHostname());
        statement.executeUpdate();
        conn.close();
    }


    private static ArrayList<Channel> getChannels() throws SQLException {
        ArrayList<Channel> channels = new ArrayList<>();
        ResultSet rs;
        Channel currentChannel;

        String sql = "SELECT * FROM channels";
        Statement statement = conn.createStatement();
        rs = statement.executeQuery(sql);

        while(rs.next()){
            // Assemble channel owner as a user object
            User channelOwner = new User(rs.getString("owner_nick"),rs.getString("owner_ident"),
                    rs.getString("owner_hostname"));
            currentChannel = new Channel(rs.getString("name"), channelOwner);
            channels.add(currentChannel);
        }
        return channels;
    }

    private static void updateHostname(User user, String hostname) throws SQLException {
        String sql = "UPDATE owner SET hostname = ? WHERE nick = ?";
        conn = DriverManager.getConnection(databaseURL);
        PreparedStatement statement = conn.prepareStatement(sql);
        statement.setString(1, hostname);
        statement.setString(2, user.getNick());
        statement.executeUpdate();
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
    private static int getUserIndex(String nick){
        int userIndex = -1;
        for(int i = 0; i < Driver.users.size(); i++){
            if(Driver.users.get(i).getNick().equals(nick)){
                userIndex = i;
            }
        }
        return userIndex;
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
        String server = "tourian86.net";
        int port = 6667;

        String login = "chester";
        String realName = ":The last metroid is in captivity";
        ArrayList<String> commandSent;
        boolean ownerEstablished;
        User owner = null;
        ArrayList<Channel> channels = null;
        //String configurationPassword = "hereandnow";
        try {

            conn = DriverManager.getConnection(databaseURL);
            System.out.println("Connected to database data.db.");

            // Check if table called "owner" exists
            if (!ownerTableExists()) {
                // If table doesn't exist, create it and we can safely say the owner isn't
                // established yet
                createOwnerTable();
                ownerEstablished = false;
            } else {
                // If the table does exists, check to see if it is empty.
                // Owner table exists, but is not empty, therefore owner is established.
                // The owner table exists, but is empty, therefore owner is not established.
                if (!ownerTableEmpty()) {
                    owner = readOwner();
                    ownerEstablished = true;
                    System.out.println("Owner has been established from database.");
                    userListAdd(owner, 0);
                } else {
                    ownerEstablished = false;
                }
            }

            // Create user tables. Will only create a new table if the user table doesn't exist already
            createUsersTable();
            conn.close();
            conn = DriverManager.getConnection(databaseURL);
            createChannelsTable();
            channels = getChannels();
            // Close the database connection
            conn.close();
        }
        catch (SQLException except){
            except.printStackTrace();
            ownerEstablished = false;
        }


/*
        // Add default channel for debug purposes
        owner = new User("Crono", "crono", "balls.net");
        Channel mainChannel = new Channel("#crono", owner);
        owner = null;
        channels.add(mainChannel);
        */





        // Connect to the irc server
        try{







            // Create a socket and connect to the server.
            Socket socket = new Socket(server, port);

            // Create objects to read and write date to/from the socket
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Log on to the server
            writer.write("NICK " + botNick + "\r\n");
            writer.write("USER " + login + " * * " + realName + "\r\n");
            writer.write("PASS thepassword\r\n");
            writer.flush();

            // Read from the server until it tells us we have connected
            String line;
            while((line = reader.readLine()) != null){
                System.out.println(line);
                writeToLog(line);

                if (line.toLowerCase().startsWith("ping ")) {
                    // Must respond to pings to avoid disconnection
                    writer.write("PONG " + line.substring(5) + "\r\n");
                    System.out.println("I got pinged!");
                    writer.flush();
                }

                if(line.contains("004")){
                    // We are logged in
                    break;
                }
                else if(line.contains("433")){
                    System.out.println("Nickname already in use. Disconnecting.");
                    return;
                }
            }
            assert channels != null;
            for(Channel channel : channels){
                writer.write("JOIN " + channel.getName() + "\r\n");
                writer.flush();
            }

            // Keep reading lines from the server
            while((line = reader.readLine()) != null){
                System.out.println(line);
                writeToLog(line);
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
                        Thread.sleep(1500);
                        sendPrivateMessage(user, "Hello. You are now the administrator.");
                        sendPrivateMessage(user, "Normally this is where I would ask you to set a password, " +
                                "however, that functionality has not been completed yet.");
                        sendPrivateMessage(user, "You will be identified by nick, ident, and hostname " +
                                "for now.");
                        registerOwner(user);
                        owner = user;
                        userListAdd(owner, 0);
                        System.out.println("Owner has been established from IRC user.");

                        continue;

                    }
                    else if(commandSent.size() >= 2)
                    {
                        if(commandSent.get(1).equals("anotherpassword") && commandSent.get(0).equals("mask")) {
                        User userToUpdate = null;
                        boolean foundUser = false;
                        for (User currentUser : users) {
                            if (user.getNick().equals(currentUser.getNick())) {
                                userToUpdate = currentUser;
                                foundUser = true;
                            }
                        }
                        if (foundUser) {
                            updateHostname(userToUpdate, user.getHostname());
                            sendPrivateMessage(user, "Updated hostmask.");
                            userListRemove(userToUpdate);
                            userListAdd(user, 0);
                            owner = user;
                        } else {
                            sendPrivateMessage(user, "Didn't find you on the users list");
                        }
                    }

                    }

                    // Check if user sending potential command is an authenticated user
                    int userIndex = getUserIndex(user.getNick());
                    int userAccessLevel = accessLevels.get(userIndex);
                    if(isAuthenticatedUser(user) && userAccessLevel == 0)
                    {
                        switch(commandSent.get(0).toLowerCase())
                        {
                            case "die":
                                Thread.sleep(1500);
                                sendPrivateMessage(user, "Ok...");
                                writer.write("QUIT :I'M QUIT!\r\n");
                                writer.flush();
                                Thread.sleep(delayTime);
                                break;

                            case "whois":
                                assert owner != null;
                                Thread.sleep(1500);
                                sendPrivateMessage(user, "My owner is: " + owner.getNick());
                                sendPrivateMessage(user, "" + user.getIdent() +
                                        "@" + user.getHostname());
                                break;

                            case "yo":
                                Thread.sleep(1500);
                                sendPrivateMessage(user, "Yo!");
                                break;

                            case "hi!":
                                Thread.sleep(1500);
                                sendPrivateMessage(user, "Hi! I like shorts!");
                                sendPrivateMessage(user, "They're comfy and");
                                sendPrivateMessage(user, "easy to wear!");
                                break;

                            case "addchan":
                                Thread.sleep(1500);
                                Channel newChannel = new Channel(commandSent.get(1), user);
                                addChannel(newChannel);
                                sendPrivateMessage(user, "Added channel to database.");
                                break;

                            default:
                                break;


                        }
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