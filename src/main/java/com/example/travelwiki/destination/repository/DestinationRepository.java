package com.example.travelwiki.destination.repository;

import com.example.travelwiki.destination.entity.Destination;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DestinationRepository extends JpaRepository<Destination, Long> {
}
