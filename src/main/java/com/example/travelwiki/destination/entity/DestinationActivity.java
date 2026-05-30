package com.example.travelwiki.destination.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "destination_activities")
public class DestinationActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_id", nullable = false)
    private Destination destination;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 80)
    private String category;

    @Column(name = "estimated_cost_min", precision = 12, scale = 2)
    private BigDecimal estimatedCostMin;

    @Column(name = "estimated_cost_max", precision = 12, scale = 2)
    private BigDecimal estimatedCostMax;

    @Column(name = "recommended_duration_minutes")
    private Integer recommendedDurationMinutes;

    @Column(nullable = false)
    private int priority = 0;

    public Long getId() { return id; }

    public Destination getDestination() { return destination; }
    public void setDestination(Destination destination) { this.destination = destination; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public BigDecimal getEstimatedCostMin() { return estimatedCostMin; }
    public void setEstimatedCostMin(BigDecimal estimatedCostMin) { this.estimatedCostMin = estimatedCostMin; }

    public BigDecimal getEstimatedCostMax() { return estimatedCostMax; }
    public void setEstimatedCostMax(BigDecimal estimatedCostMax) { this.estimatedCostMax = estimatedCostMax; }

    public Integer getRecommendedDurationMinutes() { return recommendedDurationMinutes; }
    public void setRecommendedDurationMinutes(Integer recommendedDurationMinutes) {
        this.recommendedDurationMinutes = recommendedDurationMinutes;
    }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
}
