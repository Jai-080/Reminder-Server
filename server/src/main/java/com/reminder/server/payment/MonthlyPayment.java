package com.reminder.server.payment;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.reminder.server.user.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "monthly_payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class MonthlyPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonBackReference
    @ToString.Exclude
    private User user;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "due_date")
    private Long dueDate;

    @Column(name = "completed")
    private Boolean completed;

    @Column(name = "amount")
    private java.math.BigDecimal amount;

    @Column(name = "recurrence", nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private RecurrenceType recurrence = RecurrenceType.MONTHLY;

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
