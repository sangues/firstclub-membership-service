package com.firstclub.membership.subscription;

import com.firstclub.membership.subscription.dto.SubscribeRequest;
import com.firstclub.membership.subscription.dto.SubscriptionResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/{userId}/subscription")
public class SubscriptionController {
    private final SubscriptionService service;
    public SubscriptionController(SubscriptionService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionResponse subscribe(@PathVariable Long userId,
                                          @Valid @RequestBody SubscribeRequest req,
                                          @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return service.subscribe(userId, req, key);
    }

    @GetMapping
    public SubscriptionResponse current(@PathVariable Long userId) {
        return service.getCurrent(userId);
    }
}
