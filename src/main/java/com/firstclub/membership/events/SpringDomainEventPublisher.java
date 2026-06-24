package com.firstclub.membership.events;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class SpringDomainEventPublisher implements DomainEventPublisher {
    private final ApplicationEventPublisher delegate;
    public SpringDomainEventPublisher(ApplicationEventPublisher delegate) { this.delegate = delegate; }
    @Override public void publish(Object event) { delegate.publishEvent(event); }
}
