package com.reminder.server.note;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoteRequest {

    @NotBlank(message = "Text cannot be blank")
    private String text;

    @NotNull(message = "isCompleted status is required")
    private Boolean isCompleted;

    @NotNull(message = "Position is required")
    private Integer position;

    private String updatedAt;
}
