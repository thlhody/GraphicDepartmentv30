package com.ctgraphdep.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class SessionEndEvent extends ApplicationEvent {
    private final String username;
    private final Integer userId;
    private final Integer finalMinutes;

    public SessionEndEvent(Object source, String username, Integer userId, Integer finalMinutes) {
        super(source);
        this.username = username;
        this.userId = userId;
        this.finalMinutes = finalMinutes;
    }
}