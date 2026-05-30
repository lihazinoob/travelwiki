package com.example.travelwiki.destination.repository;

import com.example.travelwiki.destination.entity.DestinationActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DestinationActivityRepository extends JpaRepository<DestinationActivity, Long> {

    List<DestinationActivity> findByDestinationIdOrderByPriorityDesc(Long destinationId);
}
