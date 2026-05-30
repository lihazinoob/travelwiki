package com.example.travelwiki.destination.dto;

import com.example.travelwiki.destination.entity.Destination;
import com.example.travelwiki.destination.entity.DestinationActivity;
import java.util.List;

public record DestinationContext(
    Destination destination,
    List<DestinationActivity> activities
) {}
