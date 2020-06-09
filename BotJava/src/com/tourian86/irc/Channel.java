package com.tourian86.irc;

import com.tourian86.main.User;

import java.util.ArrayList;

public class Channel {
    // Fields
    private final String name;
    private User owner;
    private String topic;
    private ArrayList<User> bannedUsers;
    private ArrayList<User> channelAdmins;


    public Channel(String name, User owner){
        this.name = name;
        this.owner = owner;
        topic = "";
        bannedUsers = new ArrayList<>();
        channelAdmins = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public ArrayList<User> getBannedUsers() {
        return bannedUsers;
    }

    public ArrayList<User> getChannelAdmins() {
        return channelAdmins;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getBansList() {
        String list = "";
        for(int i = 0; i < bannedUsers.size(); i++) {
            list += bannedUsers.get(i).getNick();
            if(i != bannedUsers.size() - 1){
                list += ",";
            }
        }
        return list;
    }

    public String getAdminsList() {
        String list = "";
        for(int i = 0; i < channelAdmins.size(); i++) {
            list += channelAdmins.get(i);
            if(i != channelAdmins.size() - 1){
                list += ",";
            }
        }
        return list;
    }
}
