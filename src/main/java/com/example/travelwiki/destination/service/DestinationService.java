package com.example.travelwiki.destination.service;

import com.example.travelwiki.destination.dto.DestinationContext;
import com.example.travelwiki.destination.entity.Destination;

public interface DestinationService {

    Destination resolveDestination(String userInput);

    DestinationContext loadContext(Long destinationId);
}
