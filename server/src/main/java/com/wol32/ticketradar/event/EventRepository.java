package com.wol32.ticketradar.event;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** 저장 코드는 여기 한 곳 (ADR 4). JPA 표준만 쓴다. */
public interface EventRepository extends JpaRepository<Event, Long> {

    /** 공연은 1건이므로 "첫 번째"가 곧 그 공연이다. */
    Optional<Event> findFirstByOrderByIdAsc();
}
