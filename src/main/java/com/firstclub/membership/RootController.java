package com.firstclub.membership;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Friendly root endpoint: hitting "/" returns a JSON index of the available APIs
 * instead of a Whitelabel 404. This is a REST service — there is no HTML home page.
 */
@RestController
public class RootController {

    @GetMapping("/")
    public Map<String, Object> index() {
        Map<String, String> endpoints = new LinkedHashMap<>();
        endpoints.put("GET /api/plans", "List membership plans");
        endpoints.put("GET /api/tiers", "List tiers with their unlocked benefits");
        endpoints.put("GET /api/benefits", "Benefit catalog");
        endpoints.put("POST /api/users/{userId}/subscription",
                "Subscribe (body: planType, tier; optional Idempotency-Key header)");
        endpoints.put("GET /api/users/{userId}/subscription",
                "Current membership (plan, purchased/earned/effective tier, status, expiry)");
        endpoints.put("PATCH /api/users/{userId}/subscription/tier",
                "Upgrade/downgrade tier (body: targetTier)");
        endpoints.put("DELETE /api/users/{userId}/subscription", "Cancel subscription");
        endpoints.put("GET /api/users/{userId}/benefits", "Effective benefits for the user's tier");
        endpoints.put("GET /api/users/{userId}/subscription/history", "Tier/status movement history");
        endpoints.put("POST /api/order-events",
                "Ingest an order event (body: orderId, userId, amount, occurredAt)");
        endpoints.put("POST /api/users/{userId}/tier-evaluation", "Force tier re-evaluation (demo/admin)");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "FirstClub Membership Program");
        body.put("status", "UP");
        body.put("repository", "https://github.com/sangues/firstclub-membership-service");
        body.put("endpoints", endpoints);
        return body;
    }
}
