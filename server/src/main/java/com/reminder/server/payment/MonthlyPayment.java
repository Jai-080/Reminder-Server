package com.reminder.server.payment;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.reminder.server.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.util.Calendar;

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

    @Column(name = "last_paid_at")
    private Long lastPaidAt;

    public Long getLastPaidAt() {
        return lastPaidAt;
    }

    @Column(name = "amount")
    private java.math.BigDecimal amount;

    @Column(name = "recurrence", nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private RecurrenceType recurrence = RecurrenceType.MONTHLY;

    @Column(name = "notification_offsets", nullable = false)
    @Builder.Default
    private String notificationOffsets = "0";

    @Column(name = "original_due_day")
    private Integer originalDueDay;

    @Column(name = "custom_interval_days")
    private Integer customIntervalDays;

    @Column(name = "created_at", nullable = false, updatable = false)
    private java.time.Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private java.time.Instant updatedAt;

    @Column(name = "deleted_at")
    private java.time.Instant deletedAt;

    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private Boolean deleted = false;

    public int getEffectiveOriginalDueDay() {
        if (originalDueDay != null && originalDueDay >= 1 && originalDueDay <= 31) {
            return originalDueDay;
        }
        if (dueDate != null && dueDate > 0) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(dueDate);
            return cal.get(Calendar.DAY_OF_MONTH);
        }
        return 1;
    }

    public int getEffectiveCustomIntervalDays() {
        if (customIntervalDays != null && customIntervalDays > 0 && customIntervalDays <= 365) {
            return customIntervalDays;
        }
        return 28;
    }
}
