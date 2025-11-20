package com.example.eventgenerator.controller;

import com.example.eventgenerator.dto.UserEventDto;
import com.example.eventgenerator.service.EventGeneratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class EventController {
    private final EventGeneratorService service;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<Void> sendEvent(@RequestBody UserEventDto event) {
        return service.sendManual(event);
    }
}