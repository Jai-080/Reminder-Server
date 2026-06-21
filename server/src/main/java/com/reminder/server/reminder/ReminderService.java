package com.reminder.server.reminder;

import com.reminder.server.config.ResourceNotFoundException;
import com.reminder.server.user.User;
import com.reminder.server.websocket.WebSocketEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReminderService {

    private final ReminderRepository reminderRepository;
    private final WebSocketEventPublisher webSocketEventPublisher;

    public List<ReminderResponse> getAllReminders(User user, Boolean expired) {
        List<Reminder> reminders;
        if (expired == null) {
            reminders = reminderRepository.findByUserIdAndDeletedFalse(user.getId());
        } else {
            reminders = reminderRepository.findByUserIdAndDeletedFalseAndIsExpired(user.getId(), expired);
        }
        return reminders.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public Page<ReminderResponse> getAllRemindersPaginated(User user, Boolean expired, Pageable pageable) {
        Page<Reminder> reminders;
        if (expired == null) {
            reminders = reminderRepository.findByUserIdAndDeletedFalse(user.getId(), pageable);
        } else {
            reminders = reminderRepository.findByUserIdAndDeletedFalseAndIsExpired(user.getId(), expired, pageable);
        }
        return reminders.map(this::mapToResponse);
    }

    public ReminderResponse getReminderById(User user, Long id) {
        Reminder reminder = reminderRepository.findByIdAndUserIdAndDeletedFalse(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Reminder not found with id: " + id));
        return mapToResponse(reminder);
    }

    @Transactional
    public ReminderResponse createReminder(User user, ReminderRequest request) {
        Instant now = Instant.now();
        Reminder reminder = Reminder.builder()
                .user(user)
                .text(request.getText())
                .reminderTime(request.getReminderTime())
                .isExpired(request.getIsExpired())
                .snoozedTime(request.getSnoozedTime())
                .createdAt(now)
                .updatedAt(now)
                .deleted(false)
                .build();
        Reminder saved = reminderRepository.save(reminder);
        ReminderResponse response = mapToResponse(saved);
        webSocketEventPublisher.publish("REMINDER", "REMINDER_CREATED", saved.getId(), user.getEmail(), saved.getUpdatedAt());
        return response;
    }

    @Transactional
    public ReminderResponse updateReminder(User user, Long id, ReminderRequest request) {
        Reminder reminder = reminderRepository.findByIdAndUserIdAndDeletedFalse(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Reminder not found with id: " + id));

        Instant incomingUpdatedAt = parseInstant(request.getUpdatedAt());
        Instant existingUpdatedAt = reminder.getUpdatedAt();

        if (incomingUpdatedAt.isAfter(existingUpdatedAt)) {
            System.out.println("[LWW]\n" +
                    "Entity=Reminder\n" +
                    "Id=" + id + "\n" +
                    "ServerUpdatedAt=" + existingUpdatedAt.toEpochMilli() + "\n" +
                    "IncomingUpdatedAt=" + incomingUpdatedAt.toEpochMilli() + "\n" +
                    "Decision=ACCEPTED");

            reminder.setText(request.getText());
            reminder.setReminderTime(request.getReminderTime());
            reminder.setIsExpired(request.getIsExpired());
            reminder.setSnoozedTime(request.getSnoozedTime());
            reminder.setUpdatedAt(incomingUpdatedAt);
            Reminder saved = reminderRepository.save(reminder);
            webSocketEventPublisher.publish("REMINDER", "REMINDER_UPDATED", saved.getId(), user.getEmail(), saved.getUpdatedAt());
            return mapToResponse(saved);
        } else {
            System.out.println("[LWW]\n" +
                    "Entity=Reminder\n" +
                    "Id=" + id + "\n" +
                    "ServerUpdatedAt=" + existingUpdatedAt.toEpochMilli() + "\n" +
                    "IncomingUpdatedAt=" + incomingUpdatedAt.toEpochMilli() + "\n" +
                    "Decision=REJECTED");

            return mapToResponse(reminder);
        }
    }

    @Transactional
    public void deleteReminder(User user, Long id) {
        Reminder reminder = reminderRepository.findByIdAndUserIdAndDeletedFalse(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Reminder not found with id: " + id));

        reminder.setDeleted(true);
        reminder.setDeletedAt(Instant.now());
        Reminder saved = reminderRepository.save(reminder);
        webSocketEventPublisher.publish("REMINDER", "REMINDER_DELETED", id, user.getEmail(), saved.getDeletedAt());
    }

    private ReminderResponse mapToResponse(Reminder reminder) {
        return ReminderResponse.builder()
                .id(reminder.getId())
                .text(reminder.getText())
                .reminderTime(reminder.getReminderTime())
                .isExpired(reminder.getIsExpired())
                .snoozedTime(reminder.getSnoozedTime())
                .createdAt(reminder.getCreatedAt())
                .updatedAt(reminder.getUpdatedAt())
                .build();
    }

    private Instant parseInstant(String instantStr) {
        if (instantStr == null || instantStr.isEmpty()) {
            return Instant.now();
        }
        try {
            return Instant.parse(instantStr);
        } catch (Exception e) {
            try {
                return Instant.ofEpochMilli(Long.parseLong(instantStr));
            } catch (Exception ex) {
                return Instant.now();
            }
        }
    }
}
