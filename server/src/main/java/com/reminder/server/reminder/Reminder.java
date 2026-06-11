package com.reminder.server.reminder;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.reminder.server.user.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "reminders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Reminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonBackReference
    @ToString.Exclude
    private User user;

    @Column(name = "text", columnDefinition = "TEXT")
    private String text;

    @Column(name = "reminder_time")
    private Long reminderTime;

    @Column(name = "is_expired")
    private Boolean isExpired;

    @Column(name = "snoozed_time")
    private Long snoozedTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private java.time.Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private java.time.Instant updatedAt;

    @Column(name = "deleted_at")
    private java.time.Instant deletedAt;

    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private Boolean deleted = false;
}
