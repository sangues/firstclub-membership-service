package com.firstclub.membership.subscription;

import com.firstclub.membership.subscription.dto.SubscribeRequest;
import com.firstclub.membership.subscription.dto.SubscriptionResponse;
import com.firstclub.membership.subscription.dto.MovementResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

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

    @org.springframework.web.bind.annotation.PatchMapping("/tier")
    public SubscriptionResponse changeTier(@PathVariable Long userId,
            @jakarta.validation.Valid @RequestBody com.firstclub.membership.subscription.dto.ChangeTierRequest req) {
        return service.changeTier(userId, req.targetTier());
    }

    @org.springframework.web.bind.annotation.DeleteMapping
    public SubscriptionResponse cancel(@PathVariable Long userId) {
        return service.cancel(userId);
    }

    @GetMapping("/history")
    public List<MovementResponse> history(@PathVariable Long userId) {
        return service.history(userId);
    }
}
