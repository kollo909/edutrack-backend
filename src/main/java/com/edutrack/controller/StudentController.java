package com.edutrack.controller;

import com.edutrack.model.Student;
import com.edutrack.repository.StudentRepository;
import com.edutrack.service.QrCodeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/students")
@PreAuthorize("hasRole('ADMIN')")
public class StudentController {

    private final StudentRepository studentRepo;
    private final QrCodeService qrCodeService;

    public StudentController(StudentRepository studentRepo, QrCodeService qrCodeService) {
        this.studentRepo = studentRepo;
        this.qrCodeService = qrCodeService;
    }

    @GetMapping
    public ResponseEntity<List<Student>> all() {
        return ResponseEntity.ok(studentRepo.findByActiveTrue());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Student> getById(@PathVariable Long id) {
        return studentRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Student> create(@Valid @RequestBody Student student) {
        // Generate unique QR token
        String qrToken = UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        student.setQrCode(qrToken);

        // Generate QR image as Base64
        try {
            String base64 = qrCodeService.generateQrBase64(qrToken, 250);
            student.setQrImageBase64(base64);
        } catch (Exception e) {
            // Non-fatal: QR image can be regenerated later
        }

        return ResponseEntity.ok(studentRepo.save(student));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Student> update(@PathVariable Long id,
                                          @Valid @RequestBody Student updated) {
        return studentRepo.findById(id).map(s -> {
            s.setFullName(updated.getFullName());
            s.setGrade(updated.getGrade());
            s.setParentEmail(updated.getParentEmail());
            s.setParentPhone(updated.getParentPhone());
            s.setDateOfBirth(updated.getDateOfBirth());
            return ResponseEntity.ok(studentRepo.save(s));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        studentRepo.findById(id).ifPresent(s -> {
            s.setActive(false);
            studentRepo.save(s);
        });
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/students/{id}/regenerate-qr
     * Regenerates a fresh QR code for a student.
     */
    @PostMapping("/{id}/regenerate-qr")
    public ResponseEntity<Student> regenerateQr(@PathVariable Long id) throws Exception {
        Student s = studentRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));

        String newToken = UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        s.setQrCode(newToken);
        s.setQrImageBase64(qrCodeService.generateQrBase64(newToken, 250));
        return ResponseEntity.ok(studentRepo.save(s));
    }
}
