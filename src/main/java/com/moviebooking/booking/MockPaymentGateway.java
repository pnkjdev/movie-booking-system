package com.moviebooking.booking;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Deterministic mock PSP: succeeds unless the caller asks for a simulated
 * decline, which keeps failure paths exercisable from tests and demos.
 */
@Slf4j
@Component
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public ChargeResult charge(ChargeCommand command) {
        if (command.simulateFailure()) {
            log.info("Mock gateway declining {} {} by request", command.currency(), command.amount());
            return ChargeResult.failed("Card declined by issuing bank (simulated)");
        }
        String transactionId = "txn_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        log.info("Mock gateway charged {} {} via {} -> {}",
                command.currency(), command.amount(), command.method(), transactionId);
        return ChargeResult.succeeded(transactionId);
    }
}
