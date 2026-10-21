package com.nail.demo.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.nail.demo.common.AppointmentStatus;
import com.nail.demo.common.CurrentUser;
import com.nail.demo.common.CurrentUserHolder;
import com.nail.demo.dto.ScheduleRequest;
import com.nail.demo.dto.ScheduleResponse;
import com.nail.demo.dto.TimeSlotResponse;
import com.nail.demo.entity.Appointment;
import com.nail.demo.entity.StaffSchedule;
import com.nail.demo.entity.TimeSlot;
import com.nail.demo.repository.AppointmentRepository;
import com.nail.demo.repository.StaffProfileRepository;
import com.nail.demo.repository.StaffScheduleRepository;
import com.nail.demo.repository.TimeSlotRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private static final int SLOT_MINUTES = 15;
    private static final int CONFLICT_LOOKBACK_HOURS = 24;
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_AVAILABLE = "AVAILABLE";
    private static final String STATUS_BOOKED = "BOOKED";
    private static final String TYPE_NORMAL = "NORMAL";
    private static final String TYPE_LEAVE = "LEAVE";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_STAFF = "STAFF";
    private static final List<String> OCCUPYING_STATUSES = List.of(
            AppointmentStatus.CONFIRMED,
            AppointmentStatus.IN_SERVICE,
            AppointmentStatus.RESCHEDULING
    );

    private final StaffScheduleRepository staffScheduleRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final AppointmentRepository appointmentRepository;
    private final StaffProfileRepository staffProfileRepository;
    private final StaffService staffService;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<ScheduleResponse> list(Long staffId, LocalDate from, LocalDate to) {
        validateDateRange(from, to);
        Long resolvedStaffId = staffId == null ? staffService.getCurrentStaffId() : staffId;
        return staffScheduleRepository
                .findByStaffIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(resolvedStaffId, from, to)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ScheduleResponse create(ScheduleRequest req) {
        validateTimes(req.getStartTime(), req.getEndTime());

        Long staffId = staffService.resolveStaffId(req.getStaffId());
        lockStaff(staffId);
        ensureNoOverlap(staffId, req.getWorkDate(), req.getStartTime(), req.getEndTime(), null);

        StaffSchedule schedule = new StaffSchedule();
        schedule.setStaffId(staffId);
        schedule.setWorkDate(req.getWorkDate());
        schedule.setStartTime(req.getStartTime());
        schedule.setEndTime(req.getEndTime());
        schedule.setScheduleType(normalizeScheduleType(req.getScheduleType()));
        schedule.setStatus(STATUS_ACTIVE);
        schedule.setRemark(req.getRemark());

        StaffSchedule saved = staffScheduleRepository.save(schedule);
        rebuildTimeSlots(staffId, req.getWorkDate());
        return toResponse(saved);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ScheduleResponse update(Long id, ScheduleRequest req) {
        StaffSchedule snapshot = staffScheduleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("排班不存在"));
        checkCanModify(snapshot);

        Long staffId = snapshot.getStaffId();
        lockStaff(staffId);
        entityManager.clear();

        StaffSchedule schedule = staffScheduleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("排班不存在"));
        checkCanModify(schedule);
        validateTimes(req.getStartTime(), req.getEndTime());

        LocalDate oldWorkDate = schedule.getWorkDate();
        LocalDateTime oldStart = oldWorkDate.atTime(schedule.getStartTime());
        LocalDateTime oldEnd = oldWorkDate.atTime(schedule.getEndTime());
        LocalDateTime newStart = req.getWorkDate().atTime(req.getStartTime());
        LocalDateTime newEnd = req.getWorkDate().atTime(req.getEndTime());

        ensureNoConfirmedConflict(staffId, oldStart, oldEnd);
        ensureNoConfirmedConflict(staffId, newStart, newEnd);
        ensureNoOverlap(staffId, req.getWorkDate(),
                req.getStartTime(), req.getEndTime(), schedule.getId());

        schedule.setWorkDate(req.getWorkDate());
        schedule.setStartTime(req.getStartTime());
        schedule.setEndTime(req.getEndTime());
        schedule.setScheduleType(normalizeScheduleType(req.getScheduleType()));
        schedule.setRemark(req.getRemark());

        StaffSchedule saved = staffScheduleRepository.save(schedule);
        if (!oldWorkDate.equals(saved.getWorkDate())) {
            rebuildTimeSlots(saved.getStaffId(), oldWorkDate);
        }
        rebuildTimeSlots(saved.getStaffId(), saved.getWorkDate());
        return toResponse(saved);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(Long id) {
        StaffSchedule snapshot = staffScheduleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("排班不存在"));
        checkCanModify(snapshot);

        Long staffId = snapshot.getStaffId();
        lockStaff(staffId);
        entityManager.clear();

        StaffSchedule schedule = staffScheduleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("排班不存在"));
        checkCanModify(schedule);

        LocalDate workDate = schedule.getWorkDate();
        LocalDateTime start = workDate.atTime(schedule.getStartTime());
        LocalDateTime end = workDate.atTime(schedule.getEndTime());
        ensureNoConfirmedConflict(staffId, start, end);

        staffScheduleRepository.delete(schedule);
        rebuildTimeSlots(staffId, workDate);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void rebuildTimeSlots(Long staffId, LocalDate workDate) {
        // 所有调用方都必须持有该美甲师的锁；这里再次加锁以保证直接调用也安全。
        lockStaff(staffId);

        LocalDateTime dayStart = workDate.atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);

        List<Appointment> occupyingAppointments = appointmentRepository
                .findByStaffIdAndStatusInAndConfirmedStartBetween(
                        staffId,
                        OCCUPYING_STATUSES,
                        dayStart.minusHours(CONFLICT_LOOKBACK_HOURS),
                        dayEnd);

        timeSlotRepository.deleteByStaffIdAndSlotStartBetween(staffId, dayStart, dayEnd);
        // 先执行删除，避免同一事务内新槽插入排在旧槽删除之前触发唯一键冲突。
        timeSlotRepository.flush();

        List<StaffSchedule> schedules = staffScheduleRepository
                .findByStaffIdAndWorkDateOrderByStartTimeAsc(staffId, workDate);
        List<TimeSlot> slots = new ArrayList<>();

        for (StaffSchedule schedule : schedules) {
            // 休假班次不应生成可申请的时段。
            if (TYPE_LEAVE.equalsIgnoreCase(schedule.getScheduleType())) {
                continue;
            }

            LocalDateTime slotStart = workDate.atTime(schedule.getStartTime());
            LocalDateTime scheduleEnd = workDate.atTime(schedule.getEndTime());
            while (true) {
                LocalDateTime slotEnd = slotStart.plusMinutes(SLOT_MINUTES);
                if (slotEnd.isAfter(scheduleEnd)) {
                    break;
                }
                TimeSlot slot = new TimeSlot();
                slot.setStaffId(staffId);
                slot.setSlotStart(slotStart);
                slot.setSlotEnd(slotEnd);
                slot.setStatus(STATUS_AVAILABLE);
                slots.add(slot);
                slotStart = slotEnd;
            }
        }

        // 重建后恢复已确认/服务中预约的占用状态，避免已占用槽丢失。
        for (Appointment appointment : occupyingAppointments) {
            if (appointment.getConfirmedStart() == null
                    || appointment.getEstDurationMinutes() == null) {
                continue;
            }
            LocalDateTime appointmentStart = appointment.getConfirmedStart();
            LocalDateTime appointmentEnd = appointmentStart
                    .plusMinutes(appointment.getEstDurationMinutes());
            for (TimeSlot slot : slots) {
                if (!slot.getSlotStart().isBefore(appointmentStart)
                        && slot.getSlotStart().isBefore(appointmentEnd)) {
                    slot.setStatus(STATUS_BOOKED);
                }
            }
        }

        timeSlotRepository.saveAll(slots);
    }

    @Transactional(readOnly = true)
    public List<TimeSlotResponse> listSlots(Long staffId, LocalDate date) {
        if (staffId == null) {
            throw new IllegalArgumentException("美甲师不能为空");
        }
        if (date == null) {
            throw new IllegalArgumentException("日期不能为空");
        }

        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);
        return timeSlotRepository
                .findByStaffIdAndSlotStartGreaterThanEqualAndSlotStartLessThanOrderBySlotStartAsc(
                        staffId, dayStart, dayEnd)
                .stream()
                .map(this::toTimeSlotResponse)
                .toList();
    }

    private void lockStaff(Long staffId) {
        if (staffId == null) {
            throw new IllegalArgumentException("美甲师不能为空");
        }
        staffProfileRepository.findByIdForUpdate(staffId)
                .orElseThrow(() -> new IllegalArgumentException("美甲师不存在"));
    }

    private void ensureNoConfirmedConflict(Long staffId,
                                           LocalDateTime intervalStart,
                                           LocalDateTime intervalEnd) {
        LocalDateTime lookbackStart = intervalStart.minusHours(CONFLICT_LOOKBACK_HOURS);
        List<Appointment> candidates = appointmentRepository
                .findByStaffIdAndStatusInAndConfirmedStartBetween(
                        staffId, OCCUPYING_STATUSES, lookbackStart, intervalEnd);

        for (Appointment appointment : candidates) {
            if (appointment.getConfirmedStart() == null
                    || appointment.getEstDurationMinutes() == null) {
                continue;
            }
            LocalDateTime appointmentEnd = appointment.getConfirmedStart()
                    .plusMinutes(appointment.getEstDurationMinutes());
            if (overlaps(intervalStart, intervalEnd,
                    appointment.getConfirmedStart(), appointmentEnd)) {
                throw new IllegalArgumentException("该时间段存在已确认预约，请先取消或改约");
            }
        }
    }

    private boolean overlaps(LocalDateTime aStart, LocalDateTime aEnd,
                             LocalDateTime bStart, LocalDateTime bEnd) {
        return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("开始日期和结束日期不能为空");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("开始日期不能晚于结束日期");
        }
    }

    private void validateTimes(LocalTime startTime, LocalTime endTime) {
        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("开始时间必须早于结束时间");
        }
    }

    private void ensureNoOverlap(Long staffId, LocalDate workDate, LocalTime startTime,
                                 LocalTime endTime, Long excludeId) {
        List<StaffSchedule> existingSchedules =
                staffScheduleRepository.findByStaffIdAndWorkDateOrderByStartTimeAsc(staffId, workDate);

        for (StaffSchedule existing : existingSchedules) {
            if (excludeId != null && excludeId.equals(existing.getId())) {
                continue;
            }
            boolean overlaps = startTime.isBefore(existing.getEndTime())
                    && existing.getStartTime().isBefore(endTime);
            if (overlaps) {
                throw new IllegalArgumentException("该时间段与已有排班重叠");
            }
        }
    }

    private void checkCanModify(StaffSchedule schedule) {
        CurrentUser currentUser = CurrentUserHolder.get();
        if (currentUser == null) {
            throw new IllegalArgumentException("未登录或登录已过期");
        }
        if (ROLE_ADMIN.equals(currentUser.getRoleCode())) {
            return;
        }
        if (!ROLE_STAFF.equals(currentUser.getRoleCode())) {
            throw new IllegalArgumentException("无权操作");
        }

        Long currentStaffId = staffService.getCurrentStaffId();
        if (!schedule.getStaffId().equals(currentStaffId)) {
            throw new IllegalArgumentException("无权修改他人排班");
        }
    }

    private String normalizeScheduleType(String scheduleType) {
        if (!StringUtils.hasText(scheduleType)) {
            return TYPE_NORMAL;
        }
        String normalized = scheduleType.trim().toUpperCase(Locale.ROOT);
        if (!TYPE_NORMAL.equals(normalized) && !TYPE_LEAVE.equals(normalized)) {
            throw new IllegalArgumentException("班次类型不合法");
        }
        return normalized;
    }

    private ScheduleResponse toResponse(StaffSchedule schedule) {
        return ScheduleResponse.builder()
                .id(schedule.getId())
                .staffId(schedule.getStaffId())
                .workDate(schedule.getWorkDate())
                .startTime(schedule.getStartTime())
                .endTime(schedule.getEndTime())
                .scheduleType(schedule.getScheduleType())
                .status(schedule.getStatus())
                .remark(schedule.getRemark())
                .build();
    }

    private TimeSlotResponse toTimeSlotResponse(TimeSlot slot) {
        return TimeSlotResponse.builder()
                .id(slot.getId())
                .staffId(slot.getStaffId())
                .slotStart(slot.getSlotStart())
                .slotEnd(slot.getSlotEnd())
                .status(slot.getStatus())
                .build();
    }
}
