package com.ctgraphdep.notification.service;

import com.ctgraphdep.notification.api.NotificationEventPublisher;
import com.ctgraphdep.notification.api.NotificationEventSubscriber;
import com.ctgraphdep.notification.events.NotificationEvent;
import com.ctgraphdep.utils.LoggerUtil;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Implementation of NotificationEventPublisher using a simple event bus pattern.
 * This manages subscribers and delivers events to them asynchronously.
 */
@Component
public class NotificationEventBus implements NotificationEventPublisher {

    private final Set<NotificationEventSubscriber> subscribers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final ExecutorService executorService;

    public NotificationEventBus() {
        // Use a dedicated thread pool for notification events
        this.executorService = Executors.newFixedThreadPool(2);
        LoggerUtil.initialize(this.getClass(), null);
    }

    @Override
    public void publishEvent(NotificationEvent event) {
        if (event == null) {
            LoggerUtil.warn(this.getClass(), "Attempted to publish null event");
            return;
        }

        LoggerUtil.info(this.getClass(),
                String.format("Publishing notification event: %s for user: %s",
                        event.getClass().getSimpleName(), event.getUsername()));

        // Find subscribers that can handle this event type
        Class<? extends NotificationEvent> eventType = event.getClass();

        // Deliver to matching subscribers asynchronously
        for (NotificationEventSubscriber subscriber : subscribers) {
            if (subscriber.canHandle(eventType)) {
                executorService.submit(() -> {
                    try {
                        subscriber.onNotificationEvent(event);
                    } catch (Exception e) {
                        LoggerUtil.error(this.getClass(),
                                String.format("Error delivering event to subscriber %s: %s",
                                        subscriber.getClass().getSimpleName(),
                                        e.getMessage()), e);
                    }
                });
            }
        }
    }

    @Override
    public void registerSubscriber(NotificationEventSubscriber subscriber) {
        if (subscriber != null) {
            subscribers.add(subscriber);
            LoggerUtil.info(this.getClass(),
                    "Registered subscriber: " + subscriber.getClass().getSimpleName());
        }
    }

    @Override
    public void unregisterSubscriber(NotificationEventSubscriber subscriber) {
        if (subscriber != null) {
            subscribers.remove(subscriber);
            LoggerUtil.info(this.getClass(),
                    "Unregistered subscriber: " + subscriber.getClass().getSimpleName());
        }
    }

    /**
     * Shutdown method to clean up resources
     */
    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
    }
}