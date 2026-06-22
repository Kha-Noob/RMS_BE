package web.restaurant.swp.modules.hr.service;

import web.restaurant.swp.modules.auth.model.*;
import web.restaurant.swp.modules.auth.repository.*;
import web.restaurant.swp.modules.auth.service.*;
import web.restaurant.swp.modules.pos.model.*;
import web.restaurant.swp.modules.pos.repository.*;
import web.restaurant.swp.modules.pos.service.*;
import web.restaurant.swp.modules.inventory.model.*;
import web.restaurant.swp.modules.inventory.repository.*;
import web.restaurant.swp.modules.inventory.service.*;
import web.restaurant.swp.modules.procurement.model.*;
import web.restaurant.swp.modules.procurement.repository.*;
import web.restaurant.swp.modules.procurement.service.*;
import web.restaurant.swp.modules.hr.model.*;
import web.restaurant.swp.modules.hr.repository.*;
import web.restaurant.swp.modules.hr.service.*;
import web.restaurant.swp.modules.loyalty.model.*;
import web.restaurant.swp.modules.loyalty.repository.*;
import web.restaurant.swp.modules.loyalty.service.*;
import web.restaurant.swp.modules.promotion.model.*;
import web.restaurant.swp.modules.promotion.repository.*;
import web.restaurant.swp.modules.promotion.service.*;
import web.restaurant.swp.modules.analytics.service.*;
import web.restaurant.swp.modules.branch.model.*;
import web.restaurant.swp.modules.branch.repository.*;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class HRService {
    private final EmployeeRepository employeeRepository;
    private final EmployeeShiftAssignmentRepository employeeShiftAssignmentRepository;
    private final EmployeeAttendanceRepository employeeAttendanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final ForgotClockRequestRepository forgotClockRequestRepository;
    private final PayrollRunRepository payrollRunRepository;
    private final PayrollEntryRepository payrollEntryRepository;

    // REQ-HR-03: Rota scheduling and validation checks
    @Transactional
    public EmployeeShiftAssignment assignShift(Long employeeId, Long shiftTemplateId, LocalDate date) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        ShiftTemplate template = ShiftTemplate.builder().id(shiftTemplateId).build();

        // Business rule check: limit to max 2 shifts per employee on the same day
        List<EmployeeShiftAssignment> dayAssignments = employeeShiftAssignmentRepository.findByEmployeeIdAndDate(employeeId, date);
        if (dayAssignments.size() >= 2) {
            throw new RuntimeException("Không được xếp nhân viên làm việc quá 2 ca liên tiếp trong cùng một ngày!");
        }

        EmployeeShiftAssignment assignment = EmployeeShiftAssignment.builder()
                .employee(employee)
                .shiftTemplate(template)
                .date(date)
                .build();
        return employeeShiftAssignmentRepository.save(assignment);
    }

    @Transactional
    public void assignShiftBulk(List<Long> employeeIds, Long shiftTemplateId, LocalDate date) {
        if (employeeIds == null || employeeIds.isEmpty()) {
            throw new RuntimeException("Danh sách nhân viên trống.");
        }
        for (Long employeeId : employeeIds) {
            assignShift(employeeId, shiftTemplateId, date);
        }
    }

    // REQ-HR-04: Clock In / Clock Out
    @Transactional
    public EmployeeAttendance clockIn(Long employeeId) {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hồ sơ nhân viên."));

        // Check if there is an active check-in (where clockOut is null)
        Optional<EmployeeAttendance> activeOpt = employeeAttendanceRepository.findFirstByEmployeeIdAndClockOutIsNull(employeeId);
        if (activeOpt.isPresent()) {
            throw new RuntimeException("Bạn đang trong ca làm việc, vui lòng check-out ca hiện tại trước.");
        }

        // Find assignments from yesterday and today to support overnight/split shifts
        LocalDate yesterday = today.minusDays(1);
        List<EmployeeShiftAssignment> assignments = new java.util.ArrayList<>();
        assignments.addAll(employeeShiftAssignmentRepository.findByEmployeeIdAndDate(employeeId, yesterday));
        assignments.addAll(employeeShiftAssignmentRepository.findByEmployeeIdAndDate(employeeId, today));

        // Find the next scheduled shift that is active now and doesn't have an attendance record
        EmployeeShiftAssignment targetAssignment = null;
        for (EmployeeShiftAssignment assignment : assignments) {
            Optional<EmployeeAttendance> existingAtt = employeeAttendanceRepository.findByShiftAssignmentId(assignment.getId());
            if (existingAtt.isEmpty()) {
                LocalTime startLocal = LocalTime.parse(assignment.getShiftTemplate().getStartTime());
                LocalTime endLocal = LocalTime.parse(assignment.getShiftTemplate().getEndTime());
                
                LocalDateTime startDateTime = LocalDateTime.of(assignment.getDate(), startLocal);
                LocalDateTime endDateTime = endLocal.isBefore(startLocal)
                        ? LocalDateTime.of(assignment.getDate().plusDays(1), endLocal)
                        : LocalDateTime.of(assignment.getDate(), endLocal);
                
                if (now.isAfter(startDateTime.minusHours(2)) && now.isBefore(endDateTime.plusHours(2))) {
                    targetAssignment = assignment;
                    break;
                }
            }
        }

        // If no active shift is found in the current time frame, fallback to the first unassigned assignment of today
        if (targetAssignment == null) {
            List<EmployeeShiftAssignment> todayAssignments = employeeShiftAssignmentRepository.findByEmployeeIdAndDate(employeeId, today);
            for (EmployeeShiftAssignment assignment : todayAssignments) {
                Optional<EmployeeAttendance> existingAtt = employeeAttendanceRepository.findByShiftAssignmentId(assignment.getId());
                if (existingAtt.isEmpty()) {
                    targetAssignment = assignment;
                    break;
                }
            }
        }

        // Determine if late
        boolean isLate = false;
        if (targetAssignment != null) {
            ShiftTemplate template = targetAssignment.getShiftTemplate();
            LocalTime shiftStart = LocalTime.parse(template.getStartTime());
            LocalDateTime shiftStartDateTime = LocalDateTime.of(targetAssignment.getDate(), shiftStart);
            if (now.isAfter(shiftStartDateTime.plusMinutes(15))) { // Grace period of 15m
                isLate = true;
            }
        }

        EmployeeAttendance attendance = EmployeeAttendance.builder()
                .employee(employee)
                .date(today)
                .clockIn(now)
                .shiftAssignment(targetAssignment)
                .isLate(isLate)
                .build();

        return employeeAttendanceRepository.save(attendance);
    }

    @Transactional
    public EmployeeAttendance clockOut(Long employeeId) {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        EmployeeAttendance attendance = employeeAttendanceRepository.findFirstByEmployeeIdAndClockOutIsNull(employeeId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy ca check-in chưa hoàn thành."));

        attendance.setClockOut(now);

        // Determine early leave
        boolean isEarlyLeave = false;
        if (attendance.getShiftAssignment() != null) {
            ShiftTemplate template = attendance.getShiftAssignment().getShiftTemplate();
            LocalTime shiftEnd = LocalTime.parse(template.getEndTime());
            LocalTime shiftStart = LocalTime.parse(template.getStartTime());
            
            LocalDateTime shiftEndDateTime = shiftEnd.isBefore(shiftStart)
                    ? LocalDateTime.of(attendance.getShiftAssignment().getDate().plusDays(1), shiftEnd)
                    : LocalDateTime.of(attendance.getShiftAssignment().getDate(), shiftEnd);
                    
            if (now.isBefore(shiftEndDateTime.minusMinutes(10))) {
                isEarlyLeave = true;
            }
        }
        attendance.setEarlyLeave(isEarlyLeave);

        // Calculate hours worked
        if (attendance.getClockIn() != null) {
            double hours = Duration.between(attendance.getClockIn(), now).toMinutes() / 60.0;
            attendance.setHoursWorked(Math.round(hours * 100.0) / 100.0);
        }

        return employeeAttendanceRepository.save(attendance);
    }

    // REQ-HR-07: Forgot Clock and Leave Approvals
    @Transactional
    public void approveForgotClock(Long requestId) {
        ForgotClockRequest request = forgotClockRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));
        request.setStatus("APPROVED");
        forgotClockRequestRepository.save(request);

        // Inject / Update attendance record
        List<EmployeeAttendance> todayAtts = employeeAttendanceRepository.findAllByEmployeeIdAndDate(request.getEmployee().getId(), request.getDate());
        LocalTime time = LocalTime.parse(request.getTimeProposed());
        LocalDateTime dateTime = LocalDateTime.of(request.getDate(), time);

        EmployeeAttendance att;
        if ("CLOCK_IN".equalsIgnoreCase(request.getClockType())) {
            att = todayAtts.stream()
                    .filter(a -> a.getClockIn() == null)
                    .findFirst()
                    .orElseGet(() -> {
                        // Find a shift assignment today that doesn't have attendance
                        List<EmployeeShiftAssignment> assignments = employeeShiftAssignmentRepository
                                .findByEmployeeIdAndDate(request.getEmployee().getId(), request.getDate());
                        EmployeeShiftAssignment targetAssignment = null;
                        for (EmployeeShiftAssignment ass : assignments) {
                            boolean hasAtt = todayAtts.stream()
                                    .anyMatch(a -> a.getShiftAssignment() != null && a.getShiftAssignment().getId().equals(ass.getId()));
                            if (!hasAtt) {
                                targetAssignment = ass;
                                break;
                            }
                        }
                        return EmployeeAttendance.builder()
                                .employee(request.getEmployee())
                                .date(request.getDate())
                                .shiftAssignment(targetAssignment)
                                .hoursWorked(0.0)
                                .build();
                    });
            att.setClockIn(dateTime);
            if (att.getShiftAssignment() != null) {
                LocalTime shiftStart = LocalTime.parse(att.getShiftAssignment().getShiftTemplate().getStartTime());
                att.setLate(time.isAfter(shiftStart.plusMinutes(15)));
            }
        } else {
            att = todayAtts.stream()
                    .filter(a -> a.getClockOut() == null)
                    .findFirst()
                    .orElseGet(() -> {
                        if (!todayAtts.isEmpty()) {
                            return todayAtts.get(todayAtts.size() - 1);
                        }
                        return EmployeeAttendance.builder()
                                .employee(request.getEmployee())
                                .date(request.getDate())
                                .clockIn(dateTime.minusHours(8))
                                .hoursWorked(8.0)
                                .build();
                    });
            att.setClockOut(dateTime);
            if (att.getShiftAssignment() != null) {
                LocalTime shiftEnd = LocalTime.parse(att.getShiftAssignment().getShiftTemplate().getEndTime());
                att.setEarlyLeave(time.isBefore(shiftEnd.minusMinutes(10)));
            }
        }

        if (att.getClockIn() != null && att.getClockOut() != null) {
            double hours = Duration.between(att.getClockIn(), att.getClockOut()).toMinutes() / 60.0;
            att.setHoursWorked(Math.max(0.0, Math.round(hours * 100.0) / 100.0));
        }

        employeeAttendanceRepository.save(att);
    }

    @Transactional
    public void approveLeaveRequest(Long requestId) {
        LeaveRequest request = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Leave request not found"));
        request.setStatus("APPROVED");
        leaveRequestRepository.save(request);

        // Create absent attendance records
        LocalDate current = request.getStartDate();
        while (!current.isAfter(request.getEndDate())) {
            EmployeeAttendance att = EmployeeAttendance.builder()
                    .employee(request.getEmployee())
                    .date(current)
                    .hoursWorked(0.0)
                    .build();
            employeeAttendanceRepository.save(att);
            current = current.plusDays(1);
        }
    }

    // REQ-HR-08: Monthly Payroll Calculations
    @Transactional
    public PayrollRun runMonthlyPayroll(String period, String runBy) {
        PayrollRun run = PayrollRun.builder()
                .period(period)
                .runDate(LocalDateTime.now())
                .runBy(runBy)
                .build();
        run = payrollRunRepository.save(run);

        // Parse year and month
        String[] parts = period.split("-");
        int year = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]);
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        List<Employee> allEmployees = employeeRepository.findAll();
        for (Employee emp : allEmployees) {
            List<EmployeeAttendance> attendanceList = employeeAttendanceRepository.findByEmployeeIdAndDateBetween(emp.getId(), start, end);
            
            double totalHours = attendanceList.stream().mapToDouble(EmployeeAttendance::getHoursWorked).sum();
            long lateCount = attendanceList.stream().filter(EmployeeAttendance::isLate).count();

            double baseSalary = emp.getBaseSalary();
            double allowances = 0.0;
            double deductions = lateCount * 50000.0; // Deduct 50,000 VND per late checkout

            double calculatedNetPay = 0.0;
            if ("Hourly".equalsIgnoreCase(emp.getSalaryType())) {
                calculatedNetPay = (totalHours * baseSalary) + allowances - deductions;
            } else if ("PerShift".equalsIgnoreCase(emp.getSalaryType())) {
                long totalShifts = attendanceList.stream().filter(a -> a.getHoursWorked() > 3.0).count();
                calculatedNetPay = (totalShifts * baseSalary) + allowances - deductions;
            } else { // Fixed
                calculatedNetPay = baseSalary + allowances - deductions;
            }

            calculatedNetPay = Math.max(0.0, calculatedNetPay);

            PayrollEntry entry = PayrollEntry.builder()
                    .payrollRun(run)
                    .employee(emp)
                    .basePay(baseSalary)
                    .allowances(allowances)
                    .deductions(deductions)
                    .netPay(calculatedNetPay)
                    .build();
            payrollEntryRepository.save(entry);
        }

        return run;
    }
}
