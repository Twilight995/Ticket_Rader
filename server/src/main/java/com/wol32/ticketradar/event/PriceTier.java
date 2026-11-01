package com.wol32.ticketradar.event;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** 좌석 등급 하나의 공식 정가 (1장당, 원) + 출처 URL + 확인 날짜. */
@Entity
@Table(name = "price_tiers")
public class PriceTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false, length = 30)
    private String grade;

    @Column(nullable = false)
    private int price;

    @Column(nullable = false, length = 500)
    private String sourceUrl;

    @Column(nullable = false)
    private LocalDate checkedOn;

    protected PriceTier() {
    }

    public PriceTier(String grade, int price, String sourceUrl, LocalDate checkedOn) {
        this.grade = grade;
        this.price = price;
        this.sourceUrl = sourceUrl;
        this.checkedOn = checkedOn;
    }

    void attachTo(Event owner) {
        this.event = owner;
    }

    public Long getId() {
        return id;
    }

    public String getGrade() {
        return grade;
    }

    public int getPrice() {
        return price;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public LocalDate getCheckedOn() {
        return checkedOn;
    }
}
