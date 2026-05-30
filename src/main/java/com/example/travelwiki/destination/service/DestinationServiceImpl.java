package com.example.travelwiki.destination.service;

import com.example.travelwiki.common.exception.DestinationNotSupportedException;
import com.example.travelwiki.common.exception.NotFoundException;
import com.example.travelwiki.destination.dto.DestinationContext;
import com.example.travelwiki.destination.entity.Destination;
import com.example.travelwiki.destination.entity.DestinationActivity;
import com.example.travelwiki.destination.repository.DestinationActivityRepository;
import com.example.travelwiki.destination.repository.DestinationAliasRepository;
import com.example.travelwiki.destination.repository.DestinationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class DestinationServiceImpl implements DestinationService {

    private final DestinationRepository destinationRepository;
    private final DestinationActivityRepository activityRepository;
    private final DestinationAliasRepository aliasRepository;

    public DestinationServiceImpl(
        DestinationRepository destinationRepository,
        DestinationActivityRepository activityRepository,
        DestinationAliasRepository aliasRepository
    ) {
        this.destinationRepository = destinationRepository;
        this.activityRepository = activityRepository;
        this.aliasRepository = aliasRepository;
    }

    @Override
    public Destination resolveDestination(String userInput) {
        String normalized = userInput.trim().toLowerCase();
        Destination destination = aliasRepository.findDestinationByAlias(normalized)
            .orElseThrow(() -> new DestinationNotSupportedException(userInput));
        if (!destination.isActive()) {
            throw new DestinationNotSupportedException(userInput);
        }
        return destination;
    }

    @Override
    public DestinationContext loadContext(Long destinationId) {
        Destination destination = destinationRepository.findById(destinationId)
            .orElseThrow(() -> new NotFoundException("Destination not found: " + destinationId));
        List<DestinationActivity> activities =
            activityRepository.findByDestinationIdOrderByPriorityDesc(destinationId);
        return new DestinationContext(destination, activities);
    }
}
