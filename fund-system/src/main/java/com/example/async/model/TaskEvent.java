package com.example.async.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskEvent {
    private String correlationId;
    private String status; // PROCESSING, COMPLETED, FAILED
    private String message;
    private Object result;
    private boolean finalEvent;
}