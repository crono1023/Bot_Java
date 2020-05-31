package com.tourian86.main;

public class User {

    private String nick, ident, hostname;

    public User(String nick, String ident, String hostname)
    {
        this.nick = nick;
        this.ident = ident;
        this.hostname = hostname;
    }

    public String getNick() {
        return nick;
    }

    public void setNick(String nick) {
        this.nick = nick;
    }

    public String getIdent() {
        return ident;
    }

    public void setIdent(String ident) {
        this.ident = ident;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }
}
