package com.wol32.ticketradar.event;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 공연 1건. 정가 등급과 규정을 소유한다 (공연을 지우면 같이 지워진다). */
@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private LocalDateTime startsAt;

    @Column(nullable = false, length = 100)
    private String venue;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private EventCategory category;

    @Column(nullable = false, length = 500)
    private String officialUrl;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private final List<PriceTier> priceTiers = new ArrayList<>();

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private final List<EventRule> rules = new ArrayList<>();

    protected Event() {
    }

    /** Replaces every field. Old tiers/rules are removed (orphanRemoval) and the given ones inserted. */
    void replaceWith(String name, LocalDateTime startsAt, String venue, EventCategory category, String officialUrl,
            List<PriceTier> newTiers, List<EventRule> newRules) {
        this.name = name;
        this.startsAt = startsAt;
        this.venue = venue;
        this.category = category;
        this.officialUrl = officialUrl;
        priceTiers.clear();
        newTiers.forEach(tier -> {
            tier.attachTo(this);
            priceTiers.add(tier);
        });
        rules.clear();
        newRules.forEach(rule -> {
            rule.attachTo(this);
            rules.add(rule);
        });
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public LocalDateTime getStartsAt() {
        return startsAt;
    }

    public String getVenue() {
        return venue;
    }

    public EventCategory getCategory() {
        return category;
    }

    public String getOfficialUrl() {
        return officialUrl;
    }

    public List<PriceTier> getPriceTiers() {
        return List.copyOf(priceTiers);
    }

    public List<EventRule> getRules() {
        return List.copyOf(rules);
    }
}
