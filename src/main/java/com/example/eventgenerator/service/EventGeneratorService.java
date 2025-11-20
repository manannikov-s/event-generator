package com.example.eventgenerator.service;

import com.example.eventgenerator.dto.UserEventDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;
import reactor.util.retry.Retry;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.Random;
import java.util.UUID;

@Service
public class EventGeneratorService {
    private static final Logger log = LoggerFactory.getLogger(EventGeneratorService.class);
    private final KafkaSender<String, String> kafkaSender;
    private final ObjectMapper objectMapper;
    private final String topic;
    private final Random random = new Random();

    public EventGeneratorService(KafkaSender<String, String> kafkaSender,
                                 ObjectMapper objectMapper,
                                 @Value("${app.kafka.topic}") String topic) {
        this.kafkaSender = kafkaSender;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    @PostConstruct
    public void startAutoGeneration() {
        Flux.interval(Duration.ofSeconds(5))
                .map(tick -> randomEvent())
                .flatMap(this::sendEventToKafka)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
                .onErrorContinue((ex, obj) ->
                        log.error("Error while sending auto event: {}", obj, ex))
                .subscribe();
    }

    public Mono<Void> sendManual(UserEventDto event) {
        UserEventDto enriched = eventWithDefaults(event);
        return sendEventToKafka(enriched);
    }

    private UserEventDto randomEvent() {
        UserEventDto.EventType[] types = UserEventDto.EventType.values();
        UserEventDto.EventType type = types[random.nextInt(types.length)];
        String userId = "user-" + (1 + random.nextInt(5));
        return UserEventDto.builder()
                .eventId(UUID.randomUUID())
                .userId(userId)
                .eventType(type)
                .timestamp(Instant.now())
                .metadata(UserEventDto.Metadata.builder()
                        .ipAddress("127.0.0." + (1 + random.nextInt(200)))
                        .userAgent("MockUserAgent/1.0")
                        .additionalData("auto-generated")
                        .build())
                .build();
    }

    private UserEventDto eventWithDefaults(UserEventDto event) {
        return UserEventDto.builder()
                .eventId(event.getEventId() != null ? event.getEventId() : UUID.randomUUID())
                .userId(event.getUserId())
                .eventType(event.getEventType())
                .timestamp(event.getTimestamp() != null ? event.getTimestamp() : Instant.now())
                .metadata(event.getMetadata())
                .build();
    }

    private Mono<Void> sendEventToKafka(UserEventDto event) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(event))
                .onErrorResume(JsonProcessingException.class, ex -> {
                    log.error("JSON serialization error for event {}", event, ex);
                    return Mono.empty();
                })
                .flatMap(json ->
                        kafkaSender.send(Flux.just(
                                        SenderRecord.create(
                                                new ProducerRecord<>(topic, event.getUserId(), json),
                                                event.getEventId() != null
                                                        ? event.getEventId().toString()
                                                        : null
                                        )))
                                .doOnNext(result ->
                                        log.info("Sent event {} to partition {}-offset {}",
                                                result.correlationMetadata(),
                                                result.recordMetadata().partition(),
                                                result.recordMetadata().offset())
                                )
                                .then()
                );
    }
}