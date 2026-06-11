package com.reminder.server.note;

import lombok.*;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoteResponse {
    private Long id;
    private String text;
    private Boolean isCompleted;
    private Integer position;
    private Instant createdAt;
    private Instant updatedAt;
}
