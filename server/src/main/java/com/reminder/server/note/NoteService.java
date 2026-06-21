package com.reminder.server.note;

import com.reminder.server.config.ResourceNotFoundException;
import com.reminder.server.user.User;
import com.reminder.server.websocket.WebSocketEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NoteService {

    private final QuickNoteRepository quickNoteRepository;
    private final WebSocketEventPublisher webSocketEventPublisher;

    public List<NoteResponse> getAllNotes(User user) {
        return quickNoteRepository.findByUserIdAndDeletedFalseOrderByPositionAsc(user.getId())
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public Page<NoteResponse> getAllNotesPaginated(User user, Pageable pageable) {
        Sort sort = pageable.getSort().isSorted() ? pageable.getSort() : Sort.by("position").ascending();
        Pageable sortedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                sort
        );
        return quickNoteRepository.findByUserIdAndDeletedFalse(user.getId(), sortedPageable)
                .map(this::mapToResponse);
    }

    public NoteResponse getNoteById(User user, Long id) {
        QuickNote note = quickNoteRepository.findByIdAndUserIdAndDeletedFalse(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("QuickNote not found with id: " + id));
        return mapToResponse(note);
    }

    @Transactional
    public NoteResponse createNote(User user, NoteRequest request) {
        Instant now = Instant.now();
        QuickNote note = QuickNote.builder()
                .user(user)
                .text(request.getText())
                .isCompleted(request.getIsCompleted())
                .position(request.getPosition())
                .createdAt(now)
                .updatedAt(now)
                .deleted(false)
                .build();
        QuickNote saved = quickNoteRepository.save(note);
        NoteResponse response = mapToResponse(saved);
        webSocketEventPublisher.publish("NOTE", "NOTE_CREATED", saved.getId(), user.getUsername(), saved.getUpdatedAt());
        return response;
    }

    @Transactional
    public NoteResponse updateNote(User user, Long id, NoteRequest request) {
        QuickNote note = quickNoteRepository.findByIdAndUserIdAndDeletedFalse(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("QuickNote not found with id: " + id));

        Instant incomingUpdatedAt = parseInstant(request.getUpdatedAt());
        Instant existingUpdatedAt = note.getUpdatedAt();

        if (incomingUpdatedAt.isAfter(existingUpdatedAt)) {
            System.out.println("[LWW]\n" +
                    "Entity=Note\n" +
                    "Id=" + id + "\n" +
                    "ServerUpdatedAt=" + existingUpdatedAt.toEpochMilli() + "\n" +
                    "IncomingUpdatedAt=" + incomingUpdatedAt.toEpochMilli() + "\n" +
                    "Decision=ACCEPTED");

            note.setText(request.getText());
            note.setIsCompleted(request.getIsCompleted());
            note.setPosition(request.getPosition());
            note.setUpdatedAt(incomingUpdatedAt);
            QuickNote saved = quickNoteRepository.save(note);
            webSocketEventPublisher.publish("NOTE", "NOTE_UPDATED", saved.getId(), user.getUsername(), saved.getUpdatedAt());
            return mapToResponse(saved);
        } else {
            System.out.println("[LWW]\n" +
                    "Entity=Note\n" +
                    "Id=" + id + "\n" +
                    "ServerUpdatedAt=" + existingUpdatedAt.toEpochMilli() + "\n" +
                    "IncomingUpdatedAt=" + incomingUpdatedAt.toEpochMilli() + "\n" +
                    "Decision=REJECTED");

            return mapToResponse(note);
        }
    }

    @Transactional
    public void deleteNote(User user, Long id) {
        QuickNote note = quickNoteRepository.findByIdAndUserIdAndDeletedFalse(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("QuickNote not found with id: " + id));

        note.setDeleted(true);
        note.setDeletedAt(Instant.now());
        QuickNote saved = quickNoteRepository.save(note);
        webSocketEventPublisher.publish("NOTE", "NOTE_DELETED", id, user.getUsername(), saved.getDeletedAt());
    }

    private NoteResponse mapToResponse(QuickNote note) {
        return NoteResponse.builder()
                .id(note.getId())
                .text(note.getText())
                .isCompleted(note.getIsCompleted())
                .position(note.getPosition())
                .createdAt(note.getCreatedAt())
                .updatedAt(note.getUpdatedAt())
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
