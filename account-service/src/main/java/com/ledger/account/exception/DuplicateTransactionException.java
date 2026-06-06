package com.ledger.account.exception;

public class DuplicateTransactionException extends RuntimeException {
    public DuplicateTransactionException(String eventId) {
        super("Duplicate transaction detected for event ID: " + eventId);
    }
}
