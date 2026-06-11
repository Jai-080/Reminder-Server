package com.reminder.server.note;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.reminder.server.user.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "quick_notes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class QuickNote {

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

    @Column(name = "is_completed")
    private Boolean isCompleted;

    @Column(name = "position")
    private Integer position;

    @Column(name = "updated_at")
    private Long updatedAt;

    @Column(name = "deleted")
    private Boolean deleted;
}
