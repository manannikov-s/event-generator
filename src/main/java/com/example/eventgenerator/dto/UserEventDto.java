package com.example.eventgenerator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEventDto {

    private UUID eventId;
    private String userId;
    private EventType eventType;
    private Instant timestamp;
    private Metadata metadata;

    public enum EventType {
        LOGIN, LOGOUT, PURCHASE, VIEW
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Metadata {
        private String ipAddress;
        private String userAgent;
        private String additionalData;
    }
}