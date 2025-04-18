package com.ctgraphdep.notification.api;

import com.ctgraphdep.notification.events.NotificationEvent;

/**
 * Interface for publishing notification events to subscribers.
 * This decouples notification producers from consumers.
 */
public interface NotificationEventPublisher {

    /**
     * Publishes a notification event to all registered subscribers
     *
     * @param event The notification event to publish
     */
    void publishEvent(NotificationEvent event);

    /**
     * Registers a subscriber to receive notification events
     *
     * @param subscriber The subscriber to register
     */
    void registerSubscriber(NotificationEventSubscriber subscriber);

    /**
     * Unregisters a subscriber from receiving notification events
     *
     * @param subscriber The subscriber to unregister
     */
    void unregisterSubscriber(NotificationEventSubscriber subscriber);
}