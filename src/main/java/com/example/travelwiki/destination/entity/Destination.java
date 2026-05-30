package com.example.travelwiki.destination.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "destinations")
public class Destination {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 80)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 80)
    private String country;

    @Column(length = 120)
    private String region;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "destination_type", length = 80)
    private String destinationType;

    @Column(name = "recommended_min_days")
    private Integer recommendedMinDays;

    @Column(name = "recommended_max_days")
    private Integer recommendedMaxDays;

    @Column(name = "best_for", columnDefinition = "TEXT")
    private String bestFor;

    @Column(name = "local_tips", columnDefinition = "TEXT")
    private String localTips;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getDestinationType() { return destinationType; }
    public void setDestinationType(String destinationType) { this.destinationType = destinationType; }

    public Integer getRecommendedMinDays() { return recommendedMinDays; }
    public void setRecommendedMinDays(Integer recommendedMinDays) { this.recommendedMinDays = recommendedMinDays; }

    public Integer getRecommendedMaxDays() { return recommendedMaxDays; }
    public void setRecommendedMaxDays(Integer recommendedMaxDays) { this.recommendedMaxDays = recommendedMaxDays; }

    public String getBestFor() { return bestFor; }
    public void setBestFor(String bestFor) { this.bestFor = bestFor; }

    public String getLocalTips() { return localTips; }
    public void setLocalTips(String localTips) { this.localTips = localTips; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
