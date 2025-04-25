package com.example.async.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskRequest {
    private String correlationId;
    private String taskName;
    private int numberOfSubtasks;

    @Override
    public String toString() {
        return "TaskRequest{" +
                "correlationId='" + correlationId + '\'' +
                ", taskName='" + taskName + '\'' +
                ", numberOfSubtasks=" + numberOfSubtasks +
                '}';
    }
}