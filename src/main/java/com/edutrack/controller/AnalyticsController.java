package com.edutrack.controller;

import com.edutrack.model.AttendanceRecord;
import com.edutrack.model.Student;
import com.edutrack.repository.AttendanceRepository;
import com.edutrack.repository.StudentRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/analytics")
@PreAuthorize("hasAnyRole('ADMIN', 'PRINCIPAL')")
public class AnalyticsController {

    private final AttendanceRepository attendanceRepo;
    private final StudentRepository studentRepo;

    public AnalyticsController(AttendanceRepository attendanceRepo, StudentRepository studentRepo) {
        this.attendanceRepo = attendanceRepo;
        this.studentRepo    = studentRepo;
    }

    /**
     * GET /api/analytics/summary
     * Today's headline stats for the dashboard.
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary() {
        LocalDate today = LocalDate.now();
        long total   = studentRepo.findByActiveTrue().size();

        // BUG FIX #6: Count present/late only among ACTIVE students,
        // so soft-deleted students don't corrupt the absent count.
        long present = attendanceRepo.countByDateAndStatusAndActiveStudent(today, AttendanceRecord.Status.PRESENT);
        long late    = attendanceRepo.countByDateAndStatusAndActiveStudent(today, AttendanceRecord.Status.LATE);
        long absent  = total - present - late;

        double pct = total > 0 ? ((present + late) * 100.0 / total) : 0;

        return ResponseEntity.ok(Map.of(
                "totalStudents",  total,
                "presentToday",   present,
                "lateToday",      late,
                "absentToday",    Math.max(absent, 0),
                "attendancePct",  Math.round(pct * 10.0) / 10.0,
                "date",           today.toString()
        ));
    }

    /**
     * GET /api/analytics/trend?days=10
     * Daily attendance % for the past N school days (exactly N days).
     */
    @GetMapping("/trend")
    public ResponseEntity<List<Map<String, Object>>> trend(
            @RequestParam(defaultValue = "10") int days) {

        LocalDate to   = LocalDate.now();
        // BUG FIX #11: Use (days - 1) so that ?days=10 returns exactly 10 rows,
        // not 11. Previously minusDays(days) included an extra day.
        LocalDate from = to.minusDays(days - 1);

        List<Object[]> rows = attendanceRepo.getDailyTrend(from, to);

        // BUG FIX #10: Build a map keyed by date so we can fill gaps.
        // Days with no records at all were previously missing from the chart,
        // causing the trend line to skip dates and mislead the viewer.
        Map<LocalDate, Object[]> byDate = new java.util.LinkedHashMap<>();
        for (Object[] row : rows) {
            byDate.put((LocalDate) row[0], row);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            Object[] row = byDate.get(cursor);
            long present = row != null ? ((Number) row[1]).longValue() : 0;
            long late    = row != null ? ((Number) row[2]).longValue() : 0;
            long absent  = row != null ? ((Number) row[3]).longValue() : 0;
            long total   = present + late + absent;
            double pct   = total > 0 ? ((present + late) * 100.0 / total) : 0;

            result.add(Map.of(
                    "date",    cursor.toString(),
                    "present", present,
                    "late",    late,
                    "absent",  absent,
                    "pct",     Math.round(pct * 10.0) / 10.0
            ));
            cursor = cursor.plusDays(1);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/analytics/class-comparison
     * Today's attendance % per grade/class.
     */
    @GetMapping("/class-comparison")
    public ResponseEntity<List<Map<String, Object>>> classComparison() {
        // BUG FIX #12: getClassAttendanceForDate now returns (grade, present, late).
        // We divide by total active students per grade (from studentRepo) to get
        // the true attendance %, not just % of those who happened to be scanned.
        List<Object[]> rows = attendanceRepo.getClassAttendanceForDate(LocalDate.now());

        // Build grade -> total-enrolled map
        Map<String, Long> enrolled = new HashMap<>();
        studentRepo.findByActiveTrue().forEach(s ->
            enrolled.merge(s.getGrade(), 1L, Long::sum));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            String grade   = (String) row[0];
            long present   = ((Number) row[1]).longValue();
            long late      = ((Number) row[2]).longValue();
            long total     = enrolled.getOrDefault(grade, present + late);
            double pct     = total > 0 ? ((present + late) * 100.0 / total) : 0;
            result.add(Map.of(
                    "grade", grade,
                    "pct",   Math.round(pct * 10.0) / 10.0
            ));
        }
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/analytics/high-risk?since=yyyy-MM-dd
     * BUG FIX #7: Previously, students with ZERO attendance records were invisible
     * because the query only joined students who had ≥1 ABSENT record.
     * Now we use a LEFT JOIN from the students table so completely absent students appear.
     */
    @GetMapping("/high-risk")
    public ResponseEntity<List<Map<String, Object>>> highRisk(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate since) {

        // BUG FIX #11 applied here too: use getDays() + 1 for inclusive range
        long totalDays = since.until(LocalDate.now()).getDays() + 1;

        List<Object[]> rows = attendanceRepo.findHighRiskStudentsIncludingUnscanned(since);
        List<Map<String, Object>> result = new ArrayList<>();

        for (Object[] row : rows) {
            Student s     = (Student) row[0];
            long absences = ((Number) row[1]).longValue();
            double pct    = totalDays > 0 ? ((totalDays - absences) * 100.0 / totalDays) : 100;

            if (pct < 80) {
                result.add(Map.of(
                        "studentId",      s.getStudentId(),
                        "name",           s.getFullName(),
                        "grade",          s.getGrade(),
                        "absences",       absences,
                        "attendancePct",  Math.round(pct * 10.0) / 10.0,
                        "parentEmail",    s.getParentEmail()
                ));
            }
        }
        return ResponseEntity.ok(result);
    }
}
