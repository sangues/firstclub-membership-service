package com.firstclub.membership.events;

public interface DomainEventPublisher {
    void publish(Object event);
}
