package com.edutrack.controller;

import com.edutrack.model.AttendanceRecord;
import com.edutrack.model.Student;
import com.edutrack.model.User;
import com.edutrack.repository.AttendanceRepository;
import com.edutrack.repository.StudentRepository;
import com.edutrack.service.EmailService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/attendance")
public class AttendanceController {

    // School cutoff times
    private static final LocalTime LATE_CUTOFF    = LocalTime.of(8, 0);
    private static final LocalTime DEPARTURE_START = LocalTime.of(13, 0);

    private final AttendanceRepository attendanceRepo;
    private final StudentRepository studentRepo;
    private final EmailService emailService;

    public AttendanceController(AttendanceRepository attendanceRepo,
                                StudentRepository studentRepo,
                                EmailService emailService) {
        this.attendanceRepo = attendanceRepo;
        this.studentRepo    = studentRepo;
        this.emailService   = emailService;
    }

    /**
     * POST /api/attendance/scan
     * Roles: ADMIN, SCANNER
     * Records arrival or departure via QR code token.
     */
    @PostMapping("/scan")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCANNER')")
    public ResponseEntity<?> scan(@RequestBody Map<String, String> body,
                                  Authentication auth) {

        String qrCode   = body.get("qrCode");
        String scanType = body.getOrDefault("scanType", "ARRIVAL");

        Student student = studentRepo.findByQrCode(qrCode)
                .orElseThrow(() -> new IllegalArgumentException("Unknown QR code"));

        // BUG FIX #10: Reject scans for soft-deleted students
        if (!student.isActive()) {
            throw new IllegalArgumentException("This student's QR code is no longer active");
        }

        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        AttendanceRecord record = attendanceRepo
                .findByStudentAndAttendanceDate(student, today)
                .orElseGet(() -> AttendanceRecord.builder()
                        .student(student)
                        .attendanceDate(today)
                        .status(AttendanceRecord.Status.ABSENT)
                        .scannedBy((User) auth.getPrincipal())
                        .build());

        if ("ARRIVAL".equalsIgnoreCase(scanType)) {
            record.setArrivalTime(now);
            record.setStatus(now.toLocalTime().isAfter(LATE_CUTOFF)
                    ? AttendanceRecord.Status.LATE
                    : AttendanceRecord.Status.PRESENT);
        } else {
            // BUG FIX #4: Departure scan must also clear ABSENT status.
            // A student leaving must have been present at some point.
            record.setDepartureTime(now);
            if (record.getStatus() == AttendanceRecord.Status.ABSENT) {
                record.setStatus(AttendanceRecord.Status.PRESENT);
            }
        }

        attendanceRepo.save(record);

        // BUG FIX #5: Only set parentNotified=true if email dispatch succeeds.
        // Previously, the flag was set even when the async email failed silently.
        if (!record.isParentNotified() &&
                (record.getStatus() == AttendanceRecord.Status.ABSENT ||
                 record.getStatus() == AttendanceRecord.Status.LATE)) {
            try {
                emailService.sendAttendanceAlert(student, record.getStatus());
                record.setParentNotified(true);
                attendanceRepo.save(record);
            } catch (Exception e) {
                // Email failed — leave parentNotified=false so it retries on next scan
            }
        }

        return ResponseEntity.ok(Map.of(
                "message", "Attendance recorded",
                "student", student.getFullName(),
                "status",  record.getStatus().name(),
                "time",    now.toString()
        ));
    }

    /**
     * GET /api/attendance/today
     * Returns today's attendance list (ADMIN, PRINCIPAL).
     */
    @GetMapping("/today")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRINCIPAL')")
    public ResponseEntity<List<AttendanceRecord>> today() {
        return ResponseEntity.ok(attendanceRepo.findByAttendanceDate(LocalDate.now()));
    }

    /**
     * GET /api/attendance/student/{id}?from=yyyy-MM-dd&to=yyyy-MM-dd
     */
    @GetMapping("/student/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRINCIPAL')")
    public ResponseEntity<List<AttendanceRecord>> studentHistory(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        Student student = studentRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));
        return ResponseEntity.ok(
                attendanceRepo.findByStudentAndAttendanceDateBetween(student, from, to));
    }
}
