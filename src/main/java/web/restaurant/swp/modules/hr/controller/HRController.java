package web.restaurant.swp.modules.hr.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

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

import org.springframework.web.bind.annotation.RequestBody;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
public class HRController {

    private final EmployeeRepository employeeRepository;
    private final EmployeeShiftAssignmentRepository employeeShiftAssignmentRepository;
    private final EmployeeAttendanceRepository employeeAttendanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final ForgotClockRequestRepository forgotClockRequestRepository;
    private final ShiftTemplateRepository shiftTemplateRepository;
    private final UserRepository userRepository;
    private final HRService hrService;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final RoleRepository roleRepository;
    private final BranchRepository branchRepository;

    private User getLoggedInUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }

    private String getActiveBranchId() {
        return web.restaurant.swp.config.BranchContext.getActiveBranchId(getLoggedInUser());
    }

    @PostMapping("/api/hr/schedule/assign")
    public ResponseEntity<?> assignShift(@RequestBody ShiftAssignmentRequest request) {
        try {
            EmployeeShiftAssignment assignment = hrService.assignShift(
                request.getEmployeeId(), 
                request.getShiftTemplateId(), 
                request.getDate()
            );
            return ResponseEntity.ok(assignment);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/api/hr/schedule/assign-bulk")
    public ResponseEntity<?> assignShiftBulk(@RequestBody BulkShiftAssignmentRequest request) {
        try {
            hrService.assignShiftBulk(
                request.getEmployeeIds(),
                request.getShiftTemplateId(),
                request.getDate()
            );
            return ResponseEntity.ok("Xếp ca hàng loạt thành công!");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/api/hr/schedule/range")
    public ResponseEntity<?> getScheduleRange(@RequestParam String start, @RequestParam String end) {
        try {
            LocalDate startDate = LocalDate.parse(start);
            LocalDate endDate = LocalDate.parse(end);
            String branchId = getActiveBranchId();
            List<EmployeeShiftAssignment> assignments = employeeShiftAssignmentRepository
                    .findByEmployeeBranchBranchIdAndDateBetween(branchId, startDate, endDate);
            
            List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
            for (EmployeeShiftAssignment assignment : assignments) {
                java.util.Map<String, Object> map = new java.util.HashMap<>();
                map.put("id", assignment.getId());
                map.put("employeeId", assignment.getEmployee().getId());
                map.put("employeeName", assignment.getEmployee().getUser().getName());
                map.put("employeeTitle", assignment.getEmployee().getTitle());
                map.put("shiftTemplateId", assignment.getShiftTemplate().getId());
                map.put("shiftTemplateName", assignment.getShiftTemplate().getName());
                map.put("startTime", assignment.getShiftTemplate().getStartTime());
                map.put("endTime", assignment.getShiftTemplate().getEndTime());
                map.put("date", assignment.getDate().toString());
                result.add(map);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/api/hr/schedule/my-schedule")
    public ResponseEntity<?> getMySchedule(@RequestParam String month) {
        try {
            User user = getLoggedInUser();
            if (user == null) {
                return ResponseEntity.status(401).body("Chưa đăng nhập.");
            }
            Optional<Employee> empOpt = employeeRepository.findByUserId(user.getId());
            if (empOpt.isEmpty()) {
                return ResponseEntity.badRequest().body("Tài khoản không được liên kết với hồ sơ nhân sự.");
            }
            Employee employee = empOpt.get();
            
            String[] parts = month.split("-");
            int year = Integer.parseInt(parts[0]);
            int mVal = Integer.parseInt(parts[1]);
            LocalDate startDate = LocalDate.of(year, mVal, 1);
            LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
            
            List<EmployeeShiftAssignment> assignments = employeeShiftAssignmentRepository
                    .findByEmployeeIdAndDateBetween(employee.getId(), startDate, endDate);
            
            List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
            for (EmployeeShiftAssignment assignment : assignments) {
                java.util.Map<String, Object> map = new java.util.HashMap<>();
                map.put("id", assignment.getId());
                map.put("shiftTemplateId", assignment.getShiftTemplate().getId());
                map.put("shiftTemplateName", assignment.getShiftTemplate().getName());
                map.put("startTime", assignment.getShiftTemplate().getStartTime());
                map.put("endTime", assignment.getShiftTemplate().getEndTime());
                map.put("date", assignment.getDate().toString());
                result.add(map);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @lombok.Data
    public static class ShiftAssignmentRequest {
        private Long employeeId;
        private Long shiftTemplateId;
        private LocalDate date;
    }

    @lombok.Data
    public static class BulkShiftAssignmentRequest {
        private List<Long> employeeIds;
        private Long shiftTemplateId;
        private LocalDate date;
    }

    @PostMapping("/api/employee/clock-in")
    public ResponseEntity<?> clockInUser() {
        try {
            User user = getLoggedInUser();
            Optional<Employee> empOpt = employeeRepository.findByUserId(user.getId());
            if (empOpt.isPresent()) {
                EmployeeAttendance att = hrService.clockIn(empOpt.get().getId());
                return ResponseEntity.ok(att);
            }
            return ResponseEntity.badRequest().body("No Employee Profile mapped");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/api/employee/clock-out")
    public ResponseEntity<?> clockOutUser() {
        try {
            User user = getLoggedInUser();
            Optional<Employee> empOpt = employeeRepository.findByUserId(user.getId());
            if (empOpt.isPresent()) {
                EmployeeAttendance att = hrService.clockOut(empOpt.get().getId());
                return ResponseEntity.ok(att);
            }
            return ResponseEntity.badRequest().body("No Employee Profile mapped");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/api/employee/leave-request")
    public ResponseEntity<?> submitLeaveRequest(@RequestBody LeaveRequestSubmit request) {
        try {
            User user = getLoggedInUser();
            if (user == null) {
                return ResponseEntity.status(401).body("Chưa đăng nhập");
            }
            Employee employee = employeeRepository.findByUserId(user.getId())
                    .orElseThrow(() -> new RuntimeException("Tài khoản chưa được liên kết với hồ sơ nhân sự"));

            if (request.getStartDate() == null || request.getEndDate() == null) {
                throw new RuntimeException("Vui lòng nhập ngày bắt đầu và kết thúc");
            }
            if (request.getStartDate().isAfter(request.getEndDate())) {
                throw new RuntimeException("Ngày bắt đầu không được sau ngày kết thúc");
            }
            
            LeaveRequest leave = LeaveRequest.builder()
                    .employee(employee)
                    .startDate(request.getStartDate())
                    .endDate(request.getEndDate())
                    .leaveType(request.getLeaveType() != null ? request.getLeaveType().trim() : "ANNUAL")
                    .reason(request.getReason() != null ? request.getReason().trim() : "")
                    .status("PENDING")
                    .build();
            
            leaveRequestRepository.save(leave);
            return ResponseEntity.ok("Successfully submitted leave request");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/api/employee/forgot-clock-request")
    public ResponseEntity<?> submitForgotClockRequest(@RequestBody ForgotClockSubmit request) {
        try {
            User user = getLoggedInUser();
            if (user == null) {
                return ResponseEntity.status(401).body("Chưa đăng nhập");
            }
            Employee employee = employeeRepository.findByUserId(user.getId())
                    .orElseThrow(() -> new RuntimeException("Tài khoản chưa được liên kết với hồ sơ nhân sự"));

            if (request.getDate() == null) {
                throw new RuntimeException("Vui lòng nhập ngày cần bổ sung công");
            }
            if (request.getClockType() == null || request.getClockType().trim().isEmpty()) {
                throw new RuntimeException("Vui lòng chọn loại check-in hoặc check-out");
            }
            if (request.getTimeProposed() == null || request.getTimeProposed().trim().isEmpty()) {
                throw new RuntimeException("Vui lòng nhập giờ đề nghị bổ sung");
            }
            
            ForgotClockRequest clockReq = ForgotClockRequest.builder()
                    .employee(employee)
                    .date(request.getDate())
                    .clockType(request.getClockType().trim().toUpperCase())
                    .timeProposed(request.getTimeProposed().trim())
                    .reason(request.getReason() != null ? request.getReason().trim() : "")
                    .status("PENDING")
                    .build();
            
            forgotClockRequestRepository.save(clockReq);
            return ResponseEntity.ok("Successfully submitted forgot clock request");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/api/hr/shifts")
    public ResponseEntity<?> getShiftTemplates() {
        return ResponseEntity.ok(shiftTemplateRepository.findAll());
    }

    @PostMapping("/api/hr/shifts/save")
    public ResponseEntity<?> saveShiftTemplate(@RequestBody ShiftTemplateRequest request) {
        try {
            ShiftTemplate template;
            if (request.getId() != null) {
                template = shiftTemplateRepository.findById(request.getId())
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy ca làm việc."));
                template.setName(request.getName());
                template.setStartTime(request.getStartTime());
                template.setEndTime(request.getEndTime());
                template.setDurationHours(request.getDurationHours());
            } else {
                template = ShiftTemplate.builder()
                        .name(request.getName())
                        .startTime(request.getStartTime())
                        .endTime(request.getEndTime())
                        .durationHours(request.getDurationHours())
                        .build();
            }
            return ResponseEntity.ok(shiftTemplateRepository.save(template));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/api/hr/shifts/{id}")
    public ResponseEntity<?> deleteShiftTemplate(@PathVariable Long id) {
        try {
            // Check if there are assignments using this template
            long count = employeeShiftAssignmentRepository.findAll().stream()
                    .filter(a -> a.getShiftTemplate().getId().equals(id))
                    .count();
            if (count > 0) {
                return ResponseEntity.badRequest().body("Ca làm việc này đang được sử dụng để phân lịch, không thể xóa!");
            }
            shiftTemplateRepository.deleteById(id);
            return ResponseEntity.ok("Xóa ca làm việc thành công!");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @lombok.Data
    public static class ShiftTemplateRequest {
        private Long id;
        private String name;
        private String startTime;
        private String endTime;
        private Double durationHours;
    }

    @lombok.Data
    public static class LeaveRequestSubmit {
        private LocalDate startDate;
        private LocalDate endDate;
        private String leaveType;
        private String reason;
    }

    @lombok.Data
    public static class ForgotClockSubmit {
        private LocalDate date;
        private String clockType;
        private String timeProposed;
        private String reason;
    }

    @PostMapping("/api/hr/employees/add")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> addEmployee(
            @RequestParam String name,
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam Long roleId,
            @RequestParam String department,
            @RequestParam String title,
            @RequestParam Double baseSalary,
            @RequestParam String salaryType,
            @RequestParam(required = false) String branchId) {
        try {
            User loggedIn = getLoggedInUser();
            if (loggedIn == null) {
                return ResponseEntity.status(401).body("Chưa đăng nhập");
            }

            if (userRepository.findByEmail(email).isPresent()) {
                return ResponseEntity.badRequest().body("Email đã tồn tại trong hệ thống.");
            }

            Branch branch = null;
            if (web.restaurant.swp.config.BranchContext.canSwitchBranch(loggedIn)) {
                String targetBranchId = (branchId != null && !branchId.trim().isEmpty()) ? branchId : getActiveBranchId();
                branch = branchRepository.findById(targetBranchId).orElse(null);
            } else if (loggedIn.getBranch() != null) {
                branch = loggedIn.getBranch();
            }

            Role role = roleRepository.findById(roleId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy vai trò."));

            User newUser = User.builder()
                    .name(name)
                    .email(email)
                    .password(passwordEncoder.encode(password))
                    .isActive(true)
                    .branch(branch)
                    .tenant(loggedIn.getTenant())
                    .roles(java.util.Set.of(role))
                    .build();

            newUser = userRepository.save(newUser);

            Employee employee = Employee.builder()
                    .user(newUser)
                    .department(department)
                    .title(title)
                    .hireDate(LocalDate.now())
                    .baseSalary(baseSalary)
                    .salaryType(salaryType)
                    .branch(branch)
                    .build();

            employeeRepository.save(employee);
            return ResponseEntity.ok("Thêm nhân viên thành công.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/api/hr/employees/update")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> updateEmployee(
            @RequestParam Long id,
            @RequestParam String name,
            @RequestParam String email,
            @RequestParam(required = false) String password,
            @RequestParam Long roleId,
            @RequestParam String department,
            @RequestParam String title,
            @RequestParam Double baseSalary,
            @RequestParam String salaryType,
            @RequestParam(required = false) String branchId) {
        try {
            User loggedIn = getLoggedInUser();
            if (loggedIn == null) {
                return ResponseEntity.status(401).body("Chưa đăng nhập");
            }

            Employee employee = employeeRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy nhân viên."));

            User user = employee.getUser();
            
            if (!user.getEmail().equalsIgnoreCase(email) && userRepository.findByEmail(email).isPresent()) {
                return ResponseEntity.badRequest().body("Email đã tồn tại.");
            }

            Branch branch = null;
            if (web.restaurant.swp.config.BranchContext.canSwitchBranch(loggedIn)) {
                String targetBranchId = (branchId != null && !branchId.trim().isEmpty()) ? branchId : getActiveBranchId();
                branch = branchRepository.findById(targetBranchId).orElse(null);
            } else if (loggedIn.getBranch() != null) {
                branch = loggedIn.getBranch();
            }

            Role role = roleRepository.findById(roleId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy vai trò."));

            user.setName(name);
            user.setEmail(email);
            if (password != null && !password.trim().isEmpty()) {
                user.setPassword(passwordEncoder.encode(password));
            }
            user.setBranch(branch);
            user.setRoles(java.util.Set.of(role));
            userRepository.save(user);

            employee.setDepartment(department);
            employee.setTitle(title);
            employee.setBaseSalary(baseSalary);
            employee.setSalaryType(salaryType);
            employee.setBranch(branch);
            employeeRepository.save(employee);

            return ResponseEntity.ok("Cập nhật nhân viên thành công.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/api/hr/employees/delete/{id}")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> deleteEmployee(@PathVariable Long id) {
        try {
            User loggedIn = getLoggedInUser();
            if (loggedIn == null) {
                return ResponseEntity.status(401).body("Chưa đăng nhập");
            }

            Employee employee = employeeRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy nhân viên."));

            User user = employee.getUser();
            
            if (loggedIn.getId().equals(user.getId())) {
                return ResponseEntity.badRequest().body("Bạn không thể tự xóa tài khoản của chính mình!");
            }

            if (!web.restaurant.swp.config.BranchContext.canSwitchBranch(loggedIn) && loggedIn.getBranch() != null && (employee.getBranch() == null || !employee.getBranch().getBranchId().equals(loggedIn.getBranch().getBranchId()))) {
                return ResponseEntity.status(403).body("Không có quyền xóa nhân viên của chi nhánh khác.");
            }

            employeeShiftAssignmentRepository.deleteByEmployeeId(id);
            employeeAttendanceRepository.deleteByEmployeeId(id);
            leaveRequestRepository.deleteByEmployeeId(id);
            forgotClockRequestRepository.deleteByEmployeeId(id);
            
            employeeRepository.delete(employee);
            userRepository.delete(user);

            return ResponseEntity.ok(java.util.Map.of("message", "Xóa nhân viên thành công."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/api/hr/leave-requests/approve/{id}")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> approveLeaveRequest(@PathVariable Long id) {
        try {
            User loggedIn = getLoggedInUser();
            if (loggedIn == null) {
                return ResponseEntity.status(401).body("Chưa đăng nhập");
            }
            LeaveRequest req = leaveRequestRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn xin nghỉ phép"));
            
            if (!web.restaurant.swp.config.BranchContext.canSwitchBranch(loggedIn) && loggedIn.getBranch() != null && (req.getEmployee().getBranch() == null || !req.getEmployee().getBranch().getBranchId().equals(loggedIn.getBranch().getBranchId()))) {
                return ResponseEntity.status(403).body("Không có quyền phê duyệt cho nhân viên chi nhánh khác.");
            }

            hrService.approveLeaveRequest(id);
            return ResponseEntity.ok(java.util.Map.of("message", "Đã phê duyệt đơn xin nghỉ phép thành công."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/api/hr/leave-requests/reject/{id}")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> rejectLeaveRequest(@PathVariable Long id) {
        try {
            User loggedIn = getLoggedInUser();
            if (loggedIn == null) {
                return ResponseEntity.status(401).body("Chưa đăng nhập");
            }
            LeaveRequest req = leaveRequestRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn xin nghỉ phép"));

            if (!web.restaurant.swp.config.BranchContext.canSwitchBranch(loggedIn) && loggedIn.getBranch() != null && (req.getEmployee().getBranch() == null || !req.getEmployee().getBranch().getBranchId().equals(loggedIn.getBranch().getBranchId()))) {
                return ResponseEntity.status(403).body("Không có quyền phê duyệt cho nhân viên chi nhánh khác.");
            }

            req.setStatus("REJECTED");
            leaveRequestRepository.save(req);
            return ResponseEntity.ok(java.util.Map.of("message", "Đã từ chối đơn xin nghỉ phép."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/api/hr/forgot-clock/approve/{id}")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> approveForgotClock(@PathVariable Long id) {
        try {
            User loggedIn = getLoggedInUser();
            if (loggedIn == null) {
                return ResponseEntity.status(401).body("Chưa đăng nhập");
            }
            ForgotClockRequest req = forgotClockRequestRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn bổ sung công"));

            if (!web.restaurant.swp.config.BranchContext.canSwitchBranch(loggedIn) && loggedIn.getBranch() != null && (req.getEmployee().getBranch() == null || !req.getEmployee().getBranch().getBranchId().equals(loggedIn.getBranch().getBranchId()))) {
                return ResponseEntity.status(403).body("Không có quyền phê duyệt cho nhân viên chi nhánh khác.");
            }

            hrService.approveForgotClock(id);
            return ResponseEntity.ok(java.util.Map.of("message", "Đã phê duyệt bổ sung công thành công."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/api/hr/forgot-clock/reject/{id}")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> rejectForgotClock(@PathVariable Long id) {
        try {
            User loggedIn = getLoggedInUser();
            if (loggedIn == null) {
                return ResponseEntity.status(401).body("Chưa đăng nhập");
            }
            ForgotClockRequest req = forgotClockRequestRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn bổ sung công"));

            if (!web.restaurant.swp.config.BranchContext.canSwitchBranch(loggedIn) && loggedIn.getBranch() != null && (req.getEmployee().getBranch() == null || !req.getEmployee().getBranch().getBranchId().equals(loggedIn.getBranch().getBranchId()))) {
                return ResponseEntity.status(403).body("Không có quyền phê duyệt cho nhân viên chi nhánh khác.");
            }

            req.setStatus("REJECTED");
            forgotClockRequestRepository.save(req);
            return ResponseEntity.ok(java.util.Map.of("message", "Đã từ chối đơn bổ sung công."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
