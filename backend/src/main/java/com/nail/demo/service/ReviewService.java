package com.nail.demo.service;

import java.util.List;

import com.nail.demo.common.AppointmentStatus;
import com.nail.demo.common.CurrentUser;
import com.nail.demo.common.CurrentUserHolder;
import com.nail.demo.dto.ReviewCreateRequest;
import com.nail.demo.dto.ReviewResponse;
import com.nail.demo.entity.Appointment;
import com.nail.demo.entity.Review;
import com.nail.demo.entity.ServiceType;
import com.nail.demo.entity.StaffProfile;
import com.nail.demo.entity.SysUser;
import com.nail.demo.repository.AppointmentRepository;
import com.nail.demo.repository.ReviewRepository;
import com.nail.demo.repository.ServiceTypeRepository;
import com.nail.demo.repository.StaffProfileRepository;
import com.nail.demo.repository.SysUserRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_STAFF = "STAFF";
    private static final String ROLE_CUSTOMER = "CUSTOMER";

    private final ReviewRepository reviewRepository;
    private final AppointmentRepository appointmentRepository;
    private final StaffProfileRepository staffProfileRepository;
    private final SysUserRepository sysUserRepository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final StaffService staffService;
    private final EntityManager entityManager;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReviewResponse create(Long appointmentId, ReviewCreateRequest req) {
        CurrentUser currentUser = requireCurrentUser();
        if (!ROLE_CUSTOMER.equals(currentUser.getRoleCode())) {
            throw new IllegalArgumentException("无权操作");
        }
        validateRequest(req);

        Appointment snapshot = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("预约不存在"));
        if (!currentUser.getUserId().equals(snapshot.getCustomerId())) {
            throw new IllegalArgumentException("无权评价该预约");
        }
        if (!AppointmentStatus.COMPLETED.equals(snapshot.getStatus())) {
            throw new IllegalArgumentException("只有已完成的服务可以评价");
        }
        if (reviewRepository.existsByAppointmentId(appointmentId)) {
            throw new IllegalArgumentException("该预约已评价");
        }

        staffProfileRepository.findByIdForUpdate(snapshot.getStaffId())
                .orElseThrow(() -> new IllegalArgumentException("美甲师不存在"));
        entityManager.clear();
        Appointment appointment = appointmentRepository.findByIdForUpdate(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("预约不存在"));

        if (!currentUser.getUserId().equals(appointment.getCustomerId())) {
            throw new IllegalArgumentException("无权评价该预约");
        }
        if (!AppointmentStatus.COMPLETED.equals(appointment.getStatus())) {
            throw new IllegalArgumentException("只有已完成的服务可以评价");
        }
        if (reviewRepository.existsByAppointmentId(appointmentId)) {
            throw new IllegalArgumentException("该预约已评价");
        }

        Review review = new Review();
        review.setAppointmentId(appointment.getId());
        review.setCustomerId(appointment.getCustomerId());
        review.setStaffId(appointment.getStaffId());
        review.setRating(req.getRating());
        review.setContent(req.getContent().trim());
        List<String> imageUrls = cleanImages(req.getImageUrls(), 3, "评价图片最多上传3张");
        review.setImageUrls(imageUrls.isEmpty() ? null : String.join(",", imageUrls));
        reviewRepository.save(review);
        return toResponse(review);
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> listForCurrentUser() {
        CurrentUser currentUser = requireCurrentUser();
        List<Review> reviews;
        if (ROLE_ADMIN.equals(currentUser.getRoleCode())) {
            reviews = reviewRepository.findAllByOrderByCreatedAtDesc();
        } else if (ROLE_STAFF.equals(currentUser.getRoleCode())) {
            reviews = reviewRepository.findByStaffIdOrderByCreatedAtDesc(
                    staffService.getCurrentStaffId());
        } else if (ROLE_CUSTOMER.equals(currentUser.getRoleCode())) {
            reviews = reviewRepository.findByCustomerIdOrderByCreatedAtDesc(currentUser.getUserId());
        } else {
            throw new IllegalArgumentException("无权操作");
        }
        return reviews.stream().map(this::toResponse).toList();
    }

    public Review findByAppointmentIdOrNull(Long appointmentId) {
        return reviewRepository.findByAppointmentId(appointmentId).orElse(null);
    }

    public ReviewResponse toResponse(Review review) {
        Appointment appointment = appointmentRepository.findById(review.getAppointmentId()).orElse(null);
        SysUser customer = sysUserRepository.findById(review.getCustomerId()).orElse(null);
        StaffProfile staff = staffProfileRepository.findById(review.getStaffId()).orElse(null);
        SysUser staffUser = staff == null
                ? null
                : sysUserRepository.findById(staff.getUserId()).orElse(null);
        ServiceType serviceType = appointment == null
                ? null
                : serviceTypeRepository.findById(appointment.getServiceTypeId()).orElse(null);

        return ReviewResponse.builder()
                .id(review.getId())
                .appointmentId(review.getAppointmentId())
                .customerId(review.getCustomerId())
                .customerName(displayName(customer))
                .staffId(review.getStaffId())
                .staffName(displayName(staffUser))
                .serviceTypeName(serviceType == null ? null : serviceType.getName())
                .appointmentTime(appointment == null
                        ? null
                        : (appointment.getConfirmedStart() != null
                        ? appointment.getConfirmedStart()
                        : appointment.getRequestedStart()))
                .rating(review.getRating())
                .content(review.getContent())
                .imageUrls(splitImages(review.getImageUrls()))
                .createdAt(review.getCreatedAt())
                .build();
    }

    private void validateRequest(ReviewCreateRequest req) {
        if (req.getRating() == null || req.getRating() < 1 || req.getRating() > 5) {
            throw new IllegalArgumentException("评分必须在1到5星之间");
        }
        if (!StringUtils.hasText(req.getContent())) {
            throw new IllegalArgumentException("评价内容不能为空");
        }
        if (req.getContent().length() > 1000) {
            throw new IllegalArgumentException("评价内容不能超过1000个字符");
        }
    }

    private List<String> cleanImages(List<String> images, int max, String message) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        List<String> cleaned = images.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
        if (cleaned.size() > max) {
            throw new IllegalArgumentException(message);
        }
        return cleaned;
    }

    private List<String> splitImages(String imageUrls) {
        if (!StringUtils.hasText(imageUrls)) {
            return List.of();
        }
        return java.util.Arrays.stream(imageUrls.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private String displayName(SysUser user) {
        if (user == null) {
            return null;
        }
        return user.getRealName() != null ? user.getRealName() : user.getUsername();
    }

    private CurrentUser requireCurrentUser() {
        CurrentUser currentUser = CurrentUserHolder.get();
        if (currentUser == null) {
            throw new IllegalArgumentException("未登录或登录已过期");
        }
        return currentUser;
    }
}
