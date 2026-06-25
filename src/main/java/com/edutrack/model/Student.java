package com.edutrack.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "students")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String studentId;          // e.g. "MC2024-0042"

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String grade;              // e.g. "10A"

    @Column(nullable = false)
    private String parentEmail;

    private String parentPhone;

    @Column(nullable = false)
    private String qrCode;             // unique token used in QR image

    private String qrImageBase64;      // Base64-encoded QR PNG (generated on student create)

    private LocalDate dateOfBirth;

    @Column(nullable = false)
    private boolean active = true;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() { this.createdAt = LocalDateTime.now(); }
}
