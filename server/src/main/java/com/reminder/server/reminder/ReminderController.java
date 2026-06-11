package com.reminder.server.reminder;

import com.reminder.server.security.CustomUserPrincipal;
import com.reminder.server.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reminders")
@RequiredArgsConstructor
public class ReminderController {

    private final ReminderService reminderService;

    @GetMapping
    public ResponseEntity<?> getReminders(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @RequestParam(required = false) Boolean expired,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        User user = principal.getUser();
        if (page != null && size != null) {
            return ResponseEntity.ok(reminderService.getAllRemindersPaginated(user, expired, PageRequest.of(page, size)));
        }
        return ResponseEntity.ok(reminderService.getAllReminders(user, expired));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReminderResponse> getReminderById(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(reminderService.getReminderById(principal.getUser(), id));
    }

    @PostMapping
    public ResponseEntity<ReminderResponse> createReminder(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody ReminderRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reminderService.createReminder(principal.getUser(), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ReminderResponse> updateReminder(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody ReminderRequest request
    ) {
        return ResponseEntity.ok(reminderService.updateReminder(principal.getUser(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReminder(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable Long id
    ) {
        reminderService.deleteReminder(principal.getUser(), id);
        return ResponseEntity.noContent().build();
    }
}
