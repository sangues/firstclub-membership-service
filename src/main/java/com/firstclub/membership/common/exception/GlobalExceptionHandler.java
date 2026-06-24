package com.firstclub.membership.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({PlanNotFoundException.class, UserNotFoundException.class, SubscriptionNotFoundException.class})
    public ProblemDetail notFound(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({AlreadySubscribedException.class, InvalidSubscriptionStateException.class})
    public ProblemDetail conflict(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    // @Version backstop: the striped lock serializes same-user writes on one node, so
    // this is rare, but a multi-node deployment (or the read-only path) can still race.
    // Map it to a clean, retryable 409 instead of a raw 500.
    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public ProblemDetail concurrentModification(org.springframework.orm.ObjectOptimisticLockingFailureException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Concurrent modification detected; please retry");
    }

    @ExceptionHandler(InvalidTierTransitionException.class)
    public ProblemDetail unprocessable(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(PaymentFailedException.class)
    public ProblemDetail paymentRequired(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.PAYMENT_REQUIRED, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validation(MethodArgumentNotValidException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                e.getBindingResult().getAllErrors().stream().findFirst()
                 .map(err -> err.getDefaultMessage()).orElse("Validation failed"));
    }
}
