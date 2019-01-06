package net.phonex.soap.jsonEntities;


import com.google.gson.annotations.Expose;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by miroc on 4.5.15.
 */
public class Evtlog {

    @Expose
    private List<Event> events = new ArrayList<Event>();
    @Expose
    private String user;

    /**
     *
     * @return
     * The events
     */
    public List<Event> getEvents() {
        return events;
    }

    /**
     *
     * @param events
     * The events
     */
    public void setEvents(List<Event> events) {
        this.events = events;
    }

    /**
     *
     * @return
     * The user
     */
    public String getUser() {
        return user;
    }

    /**
     *
     * @param user
     * The user
     */
    public void setUser(String user) {
        this.user = user;
    }
}