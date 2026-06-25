package com.edutrack.repository;

import com.edutrack.model.AttendanceRecord;
import com.edutrack.model.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRepository extends JpaRepository<AttendanceRecord, Long> {

    Optional<AttendanceRecord> findByStudentAndAttendanceDate(Student student, LocalDate date);

    List<AttendanceRecord> findByAttendanceDate(LocalDate date);

    List<AttendanceRecord> findByStudent(Student student);

    List<AttendanceRecord> findByStudentAndAttendanceDateBetween(Student student, LocalDate from, LocalDate to);

    List<AttendanceRecord> findByAttendanceDateBetween(LocalDate from, LocalDate to);

    /**
     * BUG FIX #6: Only count attendance for ACTIVE students.
     * The original query counted all students including soft-deleted ones,
     * which made absentToday go negative when inactive students had records.
     */
    @Query("SELECT COUNT(a) FROM AttendanceRecord a WHERE a.attendanceDate = :date AND a.status = :status AND a.student.active = true")
    long countByDateAndStatusAndActiveStudent(@Param("date") LocalDate date, @Param("status") AttendanceRecord.Status status);

    /**
     * BUG FIX #7: Use LEFT JOIN from Student so students with ZERO records
     * (never scanned, 0% attendance) are included in the high-risk report.
     * The original query only included students with at least one ABSENT record.
     */
    @Query("""
        SELECT s, COUNT(a) as absences
        FROM Student s
        LEFT JOIN AttendanceRecord a
          ON a.student = s
          AND a.status = 'ABSENT'
          AND a.attendanceDate >= :since
        WHERE s.active = true
        GROUP BY s
        ORDER BY absences DESC
        """)
    List<Object[]> findHighRiskStudentsIncludingUnscanned(@Param("since") LocalDate since);

    /**
     * BUG FIX #10: The original query excluded dates with no attendance records,
     * creating invisible gaps in the Analytics trend chart.
     * JPQL cannot generate a date series, so the gap-filling is handled in
     * AnalyticsController.trend() which iterates the full date range and inserts
     * zero-rows for any date not returned by this query.
     */
    @Query("""
        SELECT a.attendanceDate,
               SUM(CASE WHEN a.status = 'PRESENT' THEN 1 ELSE 0 END) as present,
               SUM(CASE WHEN a.status = 'LATE'    THEN 1 ELSE 0 END) as late,
               SUM(CASE WHEN a.status = 'ABSENT'  THEN 1 ELSE 0 END) as absent
        FROM AttendanceRecord a
        WHERE a.attendanceDate BETWEEN :from AND :to
        GROUP BY a.attendanceDate
        ORDER BY a.attendanceDate
        """)
    List<Object[]> getDailyTrend(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * BUG FIX #12: The original query divided by COUNT(a) — the number of
     * students who have a record today. On a day where some students haven't
     * been scanned yet, this inflates the percentage (e.g. 3 present out of
     * 4 scanned = 75%, but there are actually 30 students in the class).
     * Fixed to count only PRESENT+LATE students (numerator) and let the
     * AnalyticsController divide by total active students per grade instead.
     * The query now returns (grade, presentCount, lateCount) tuples.
     */
    @Query("""
        SELECT a.student.grade,
               SUM(CASE WHEN a.status = 'PRESENT' THEN 1 ELSE 0 END) as present,
               SUM(CASE WHEN a.status = 'LATE'    THEN 1 ELSE 0 END) as late
        FROM AttendanceRecord a
        WHERE a.attendanceDate = :date AND a.student.active = true
        GROUP BY a.student.grade
        ORDER BY a.student.grade
        """)
    List<Object[]> getClassAttendanceForDate(@Param("date") LocalDate date);

    List<AttendanceRecord> findByParentNotifiedFalseAndStatus(AttendanceRecord.Status status);
}
