package com.example.travelwiki.common.exception;

public class DestinationNotSupportedException extends RuntimeException {

    private final String destination;

    public DestinationNotSupportedException(String destination) {
        super("Destination not supported: " + destination);
        this.destination = destination;
    }

    public String getDestination() {
        return destination;
    }
}
