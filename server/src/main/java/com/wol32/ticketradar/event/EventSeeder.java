package com.wol32.ticketradar.event;

import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/** 서버 시작 시 DB가 비어 있으면 seed/event.json 을 넣는다. 이미 있으면 아무것도 하지 않는다 (두 번 실행해도 중복 없음). */
@Component
public class EventSeeder implements ApplicationRunner {

    static final String SEED_PATH = "seed/event.json";

    private static final Logger log = LoggerFactory.getLogger(EventSeeder.class);

    private final EventRepository eventRepository;
    private final EventService eventService;
    private final ObjectMapper objectMapper;

    public EventSeeder(EventRepository eventRepository, EventService eventService, ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.eventService = eventService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (eventRepository.count() > 0) {
            return;
        }
        try (InputStream in = new ClassPathResource(SEED_PATH).getInputStream()) {
            eventService.replaceEvent(objectMapper.readValue(in, EventUpsertRequest.class));
        }
        log.info("Seeded the event from {}", SEED_PATH);
    }
}
