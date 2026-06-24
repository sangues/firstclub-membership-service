package com.firstclub.membership.order;

import com.firstclub.membership.order.dto.OrderEventRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/order-events")
public class OrderEventController {
    private final OrderEventService service;
    public OrderEventController(OrderEventService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void ingest(@Valid @RequestBody OrderEventRequest req) {
        service.ingest(req);
    }
}
