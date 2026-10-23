package com.nail.demo.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.nail.demo.common.AppointmentStatus;
import com.nail.demo.common.CurrentUser;
import com.nail.demo.common.CurrentUserHolder;
import com.nail.demo.common.PrepStatus;
import com.nail.demo.dto.PrepCustomerView;
import com.nail.demo.dto.PrepResponse;
import com.nail.demo.dto.PrepTodayResponse;
import com.nail.demo.dto.PrepUpdateRequest;
import com.nail.demo.entity.Appointment;
import com.nail.demo.entity.AppointmentPrep;
import com.nail.demo.entity.ServiceType;
import com.nail.demo.entity.StaffProfile;
import com.nail.demo.entity.SysUser;
import com.nail.demo.repository.AppointmentPrepRepository;
import com.nail.demo.repository.AppointmentRepository;
import com.nail.demo.repository.ServiceTypeRepository;
import com.nail.demo.repository.StaffProfileRepository;
import com.nail.demo.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrepService {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_STAFF = "STAFF";
    private static final List<String> PREP_APPOINTMENT_STATUSES = List.of(
            AppointmentStatus.CONFIRMED,
            AppointmentStatus.IN_SERVICE,
            AppointmentStatus.RESCHEDULING
    );

    private final AppointmentPrepRepository appointmentPrepRepository;
    private final AppointmentRepository appointmentRepository;
    private final StaffService staffService;
    private final StaffProfileRepository staffProfileRepository;
    private final SysUserRepository sysUserRepository;
    private final ServiceTypeRepository serviceTypeRepository;

    @Transactional
    public void createForAppointment(Long appointmentId) {
        if (appointmentPrepRepository.findByAppointmentId(appointmentId).isPresent()) {
            return;
        }
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("预约不存在"));
        AppointmentPrep prep = new AppointmentPrep();
        prep.setAppointmentId(appointment.getId());
        prep.setPrepStatus(PrepStatus.PENDING);
        appointmentPrepRepository.save(prep);
    }

    @Transactional(readOnly = true)
    public PrepResponse getByAppointmentId(Long appointmentId) {
        Appointment appointment = requireAppointment(appointmentId);
        assertCanViewInternal(appointment);
        AppointmentPrep prep = appointmentPrepRepository.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("工单不存在"));
        return toResponse(prep);
    }

    @Transactional
    public PrepResponse update(Long appointmentId, PrepUpdateRequest req) {
        Appointment appointment = requireAppointment(appointmentId);
        assertCanViewInternal(appointment);
        AppointmentPrep prep = appointmentPrepRepository.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("工单不存在"));

        if (req.getStyleDescription() != null) {
            prep.setStyleDescription(req.getStyleDescription());
        }
        if (req.getEffectImageUrl() != null) {
            prep.setEffectImageUrl(req.getEffectImageUrl());
        }
        if (req.getMaterialJson() != null) {
            prep.setMaterialJson(req.getMaterialJson());
        }
        if (req.getStaffNote() != null) {
            prep.setStaffNote(req.getStaffNote());
        }

        boolean ready = StringUtils.hasText(prep.getStyleDescription())
                && StringUtils.hasText(prep.getEffectImageUrl())
                && StringUtils.hasText(prep.getMaterialJson());
        prep.setPrepStatus(ready ? PrepStatus.READY : PrepStatus.PENDING);
        appointmentPrepRepository.save(prep);
        return toResponse(prep);
    }

    @Transactional(readOnly = true)
    public PrepCustomerView getCustomerView(Long appointmentId) {
        Appointment appointment = requireAppointment(appointmentId);
        CurrentUser currentUser = requireCurrentUser();
        boolean allowed = ROLE_ADMIN.equals(currentUser.getRoleCode())
                || currentUser.getUserId().equals(appointment.getCustomerId());
        if (!allowed) {
            throw new IllegalArgumentException("无权查看");
        }
        AppointmentPrep prep = appointmentPrepRepository.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("工单不存在"));
        return toCustomerView(prep);
    }

    /**
     * 今天起 {@code days} 天内已确认预约的物料工单（含今天）。
     * 管理员物料页默认看未来 7 天：美甲师往往提前几天备料，
     * 只看今天会漏掉跨天的更新。
     *
     * @param days 天数，取值会被收敛到 1–30
     * @return 按预约时间升序的工单
     */
    @Transactional(readOnly = true)
    public List<PrepTodayResponse> listUpcoming(int days) {
        CurrentUser currentUser = requireCurrentUser();
        if (!ROLE_ADMIN.equals(currentUser.getRoleCode())) {
            throw new IllegalArgumentException("无权操作");
        }

        int span = Math.min(Math.max(days, 1), 30);
        LocalDate today = LocalDate.now();
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime dayEnd = today.plusDays(span).atStartOfDay();
        List<Appointment> appointments = appointmentRepository
                .findByConfirmedStartBetweenAndStatusInOrderByConfirmedStartAsc(
                        dayStart, dayEnd, PREP_APPOINTMENT_STATUSES);
        if (appointments.isEmpty()) {
            return List.of();
        }

        List<Long> appointmentIds = appointments.stream().map(Appointment::getId).toList();
        Map<Long, AppointmentPrep> prepMap = appointmentPrepRepository
                .findByAppointmentIdIn(appointmentIds)
                .stream()
                .collect(Collectors.toMap(AppointmentPrep::getAppointmentId, Function.identity()));

        return appointments.stream()
                .filter(appointment -> prepMap.containsKey(appointment.getId()))
                .map(appointment -> toTodayResponse(appointment, prepMap.get(appointment.getId())))
                .toList();
    }

    public void onAppointmentCancelled(Long appointmentId) {
        // 阶段 4 MVP：预约取消后工单保留，状态不变。
    }

    public AppointmentPrep findEntityOrNull(Long appointmentId) {
        return appointmentPrepRepository.findByAppointmentId(appointmentId).orElse(null);
    }

    public PrepResponse toResponse(AppointmentPrep prep) {
        if (prep == null) {
            return null;
        }
        return PrepResponse.builder()
                .id(prep.getId())
                .appointmentId(prep.getAppointmentId())
                .styleDescription(prep.getStyleDescription())
                .effectImageUrl(prep.getEffectImageUrl())
                .materialJson(prep.getMaterialJson())
                .staffNote(prep.getStaffNote())
                .prepStatus(prep.getPrepStatus())
                .createdAt(prep.getCreatedAt())
                .updatedAt(prep.getUpdatedAt())
                .build();
    }

    public PrepCustomerView toCustomerView(AppointmentPrep prep) {
        if (prep == null) {
            return null;
        }
        return PrepCustomerView.builder()
                .appointmentId(prep.getAppointmentId())
                .styleDescription(prep.getStyleDescription())
                .effectImageUrl(prep.getEffectImageUrl())
                .build();
    }

    private PrepTodayResponse toTodayResponse(Appointment appointment, AppointmentPrep prep) {
        SysUser customer = sysUserRepository.findById(appointment.getCustomerId()).orElse(null);
        StaffProfile staff = staffProfileRepository.findById(appointment.getStaffId()).orElse(null);
        SysUser staffUser = staff == null
                ? null
                : sysUserRepository.findById(staff.getUserId()).orElse(null);
        ServiceType serviceType = serviceTypeRepository.findById(appointment.getServiceTypeId()).orElse(null);

        return PrepTodayResponse.builder()
                .id(prep.getId())
                .appointmentId(appointment.getId())
                .customerName(displayName(customer))
                .staffName(displayName(staffUser))
                .serviceTypeName(serviceType == null ? null : serviceType.getName())
                .appointmentTime(appointment.getConfirmedStart())
                .styleDescription(prep.getStyleDescription())
                .effectImageUrl(prep.getEffectImageUrl())
                .materialJson(prep.getMaterialJson())
                .staffNote(prep.getStaffNote())
                .prepStatus(prep.getPrepStatus())
                .createdAt(prep.getCreatedAt())
                .updatedAt(prep.getUpdatedAt())
                .build();
    }

    private void assertCanViewInternal(Appointment appointment) {
        CurrentUser currentUser = requireCurrentUser();
        if (ROLE_ADMIN.equals(currentUser.getRoleCode())) {
            return;
        }
        if (ROLE_STAFF.equals(currentUser.getRoleCode())) {
            Long currentStaffId = staffService.getCurrentStaffId();
            if (appointment.getStaffId().equals(currentStaffId)) {
                return;
            }
        }
        throw new IllegalArgumentException("无权查看");
    }

    private Appointment requireAppointment(Long appointmentId) {
        return appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("预约不存在"));
    }

    private CurrentUser requireCurrentUser() {
        CurrentUser currentUser = CurrentUserHolder.get();
        if (currentUser == null) {
            throw new IllegalArgumentException("未登录或登录已过期");
        }
        return currentUser;
    }

    private String displayName(SysUser user) {
        if (user == null) {
            return null;
        }
        return user.getRealName() != null ? user.getRealName() : user.getUsername();
    }
}
