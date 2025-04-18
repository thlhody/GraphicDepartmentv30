package com.ctgraphdep.notification.api;

import com.ctgraphdep.notification.events.NotificationEvent;

/**
 * Interface for components that want to receive notification events.
 */
public interface NotificationEventSubscriber {

    /**
     * Handles a notification event
     *
     * @param event The notification event to handle
     */
    void onNotificationEvent(NotificationEvent event);

    /**
     * Determines if this subscriber can handle the given event type
     *
     * @param eventType The class of the event
     * @return true if the subscriber can handle this event type, false otherwise
     */
    boolean canHandle(Class<? extends NotificationEvent> eventType);
}