package com.wol32.ticketradar.event;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 공식 입장·양도 규정 한 문장 + 종류 + 출처 URL + 확인 날짜. */
@Entity
@Table(name = "event_rules")
public class EventRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 40)
    private RuleType type;

    @Column(nullable = false, length = 500)
    private String text;

    @Column(nullable = false, length = 500)
    private String sourceUrl;

    @Column(nullable = false)
    private LocalDate checkedOn;

    protected EventRule() {
    }

    public EventRule(RuleType type, String text, String sourceUrl, LocalDate checkedOn) {
        this.type = type;
        this.text = text;
        this.sourceUrl = sourceUrl;
        this.checkedOn = checkedOn;
    }

    void attachTo(Event owner) {
        this.event = owner;
    }

    public Long getId() {
        return id;
    }

    public RuleType getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public LocalDate getCheckedOn() {
        return checkedOn;
    }
}
