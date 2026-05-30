package com.example.travelwiki.destination.repository;

import com.example.travelwiki.destination.entity.Destination;
import com.example.travelwiki.destination.entity.DestinationAlias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface DestinationAliasRepository extends JpaRepository<DestinationAlias, Long> {

    // Returns the Destination directly to avoid a separate lazy-load round-trip.
    @Query("SELECT a.destination FROM DestinationAlias a WHERE a.alias = :alias")
    Optional<Destination> findDestinationByAlias(@Param("alias") String alias);
}
