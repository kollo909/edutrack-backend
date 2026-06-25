package com.edutrack.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "attendance_records",
       indexes = {
           @Index(name = "idx_att_student_date", columnList = "student_id, attendance_date"),
           @Index(name = "idx_att_date",         columnList = "attendance_date")
       })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(nullable = false)
    private LocalDate attendanceDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    private LocalDateTime arrivalTime;

    private LocalDateTime departureTime;

    /** Which user performed the scan */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scanned_by")
    private User scannedBy;

    private boolean parentNotified = false;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() { this.createdAt = LocalDateTime.now(); }

    public enum Status {
        PRESENT, LATE, ABSENT
    }
}

// Note: ScanType is used in ScanRequest DTO
// (not stored in DB — we derive status from scan time)
