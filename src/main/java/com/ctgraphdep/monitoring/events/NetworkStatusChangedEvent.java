package com.ctgraphdep.monitoring.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Event published when network status changes.
 * Can be used by any service that needs to react to network availability changes.
 */
@Getter
public class NetworkStatusChangedEvent extends ApplicationEvent {
    private final boolean networkAvailable;
    private final String reason;

    public NetworkStatusChangedEvent(Object source, boolean networkAvailable, String reason) {
        super(source);
        this.networkAvailable = networkAvailable;
        this.reason = reason;
    }

    @Override
    public String toString() {
        return String.format("NetworkStatusChangedEvent{available=%s, reason='%s'}", networkAvailable, reason);
    }
}
