package com.reminder.server.note;

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
@RequestMapping("/api/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;

    @GetMapping
    public ResponseEntity<?> getNotes(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        User user = principal.getUser();
        if (page != null && size != null) {
            return ResponseEntity.ok(noteService.getAllNotesPaginated(user, PageRequest.of(page, size)));
        }
        return ResponseEntity.ok(noteService.getAllNotes(user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NoteResponse> getNoteById(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(noteService.getNoteById(principal.getUser(), id));
    }

    @PostMapping
    public ResponseEntity<NoteResponse> createNote(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody NoteRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(noteService.createNote(principal.getUser(), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<NoteResponse> updateNote(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody NoteRequest request
    ) {
        return ResponseEntity.ok(noteService.updateNote(principal.getUser(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNote(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable Long id
    ) {
        noteService.deleteNote(principal.getUser(), id);
        return ResponseEntity.noContent().build();
    }
}
