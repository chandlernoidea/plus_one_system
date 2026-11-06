package com.nail.demo.solo.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.nail.demo.exception.BusinessException;
import com.nail.demo.solo.common.SoloBookingChangeType;
import com.nail.demo.solo.common.SoloBookingEventAction;
import com.nail.demo.solo.common.SoloBookingSource;
import com.nail.demo.solo.common.SoloBookingStatus;
import com.nail.demo.solo.common.SoloChangeDepositAction;
import com.nail.demo.solo.common.SoloChangeInitiator;
import com.nail.demo.solo.dto.BookingCancelRequest;
import com.nail.demo.solo.dto.BookingCreateRequest;
import com.nail.demo.solo.dto.BookingItemRequest;
import com.nail.demo.solo.dto.BookingItemResponse;
import com.nail.demo.solo.dto.BookingRescheduleRequest;
import com.nail.demo.solo.dto.BookingRescheduleResponse;
import com.nail.demo.solo.dto.BookingResponse;
import com.nail.demo.solo.dto.BookingShiftPlanResponse;
import com.nail.demo.solo.dto.BookingShiftRequest;
import com.nail.demo.solo.dto.PendingChangeResponse;
import com.nail.demo.solo.entity.Booking;
import com.nail.demo.solo.entity.BookingChange;
import com.nail.demo.solo.entity.BookingItem;
import com.nail.demo.solo.entity.Client;
import com.nail.demo.solo.entity.Style;
import com.nail.demo.solo.exception.SoloConflictException;
import com.nail.demo.solo.repository.BookingChangeRepository;
import com.nail.demo.solo.repository.BookingItemRepository;
import com.nail.demo.solo.repository.BookingRepository;
import com.nail.demo.solo.repository.ClientRepository;
import com.nail.demo.solo.repository.StyleRepository;
import com.nail.demo.solo.support.DepositLedger;
import com.nail.demo.solo.support.SoloScheduleRules;
import com.nail.demo.solo.support.TimeWindow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 预约主流程：建单 / 插队 / 改期 / 取消 / 状态流转 / 顺延。
 *
 * <p>规则来源见 docs/solo/00-brief.md「P2 后端核心规则」。
 */
@Service
@RequiredArgsConstructor
public class BookingService {

    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;
    private final BookingChangeRepository bookingChangeRepository;
    private final ClientRepository clientRepository;
    private final StyleRepository styleRepository;
    private final BookingGuard guard;
    private final SlotAvailabilityService slotAvailability;
    private final DepositService depositService;
    private final SoloSettingService settingService;

    // ------------------------------------------------------------------ 查询

    @Transactional(readOnly = true)
    public BookingResponse get(Long id) {
        return toResponses(List.of(guard.requireBooking(id))).get(0);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> list(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            throw new BusinessException("请提供排期时间范围 from 与 to");
        }
        if (!to.isAfter(from)) {
            throw new BusinessException("结束时间必须晚于开始时间");
        }
        return toResponses(bookingRepository.findByStartAtBetweenOrderByStartAtAsc(from, to));
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> listByClient(Long clientId) {
        return toResponses(bookingRepository.findByClientIdOrderByStartAtDesc(clientId));
    }

    // ------------------------------------------------------------------ 建单

    /**
     * 常规建单。占用 = 各项目时长之和 + 缓冲；已定档要过冲突校验。
     */
    @Transactional
    public BookingResponse create(BookingCreateRequest request) {
        return persist(request, SoloBookingSource.NORMAL, resolveItems(request.getItems()));
    }

    /**
     * 插队建单：只允许插进取消 / 改期释放出来的空档，且「时长 + 缓冲」必须塞得下。
     */
    @Transactional
    public BookingResponse jump(BookingCreateRequest request) {
        List<ResolvedItem> items = resolveItems(request.getItems());
        int planned = plannedMinutes(items);
        int buffer = resolveBuffer(request);
        LocalDateTime start = request.getStartAt();
        LocalDate date = start.toLocalDate();

        TimeWindow gap = slotAvailability.gapContaining(date, start, null)
                .orElseThrow(() -> new SoloConflictException(
                        "该时刻不在空档内：已被占用或不在营业时间里",
                        Map.of("startAt", start, "date", date.toString())));

        if (!SoloScheduleRules.fits(gap, start, planned, buffer)) {
            long available = Duration.between(start, gap.end()).toMinutes();
            throw new SoloConflictException(
                    "空档不足：从该时刻起只剩 %d 分钟，本单需要 %d 分钟（含缓冲 %d 分钟）"
                            .formatted(available, planned + buffer, buffer),
                    Map.of("availableMinutes", available,
                            "requiredMinutes", planned + buffer,
                            "gapEnd", gap.end()));
        }
        return persist(request, SoloBookingSource.JUMP, items);
    }

    private BookingResponse persist(BookingCreateRequest request, String source, List<ResolvedItem> items) {
        Client client = clientRepository.findById(request.getClientId())
                .orElseThrow(() -> new BusinessException(404, "客户不存在"));
        if (Integer.valueOf(1).equals(client.getDeleted())) {
            throw new BusinessException("该客户已删除，不能建单");
        }

        int planned = plannedMinutes(items);
        int buffer = resolveBuffer(request);
        LocalDateTime start = request.getStartAt();
        LocalDateTime end = SoloScheduleRules.endOf(start, planned, buffer);
        String status = resolveCreateStatus(request.getStatus());

        // 待定金不占档，定档才校验冲突（收定金时会再校验一次）。
        // force=true 是老板确认过的强排：跳过校验，冲突由前端标红并提示。
        if (SoloBookingStatus.BOOKED.equals(status) && !Boolean.TRUE.equals(request.getForce())) {
            guard.assertNoConflict(start, end, null);
        }

        Booking booking = new Booking();
        booking.setClientId(client.getId());
        booking.setStartAt(start);
        booking.setPlannedMinutes(planned);
        booking.setBufferMinutes(buffer);
        booking.setEndAt(end);
        booking.setStatus(status);
        booking.setSource(resolveSource(request.getSource(), source));
        booking.setGroupId(request.getGroupId());
        booking.setFreeRescheduleUsed(0);
        booking.setRemark(request.getRemark());
        Booking saved = bookingRepository.save(booking);

        saveItems(saved.getId(), items);
        guard.recordEvent(saved.getId(), SoloBookingEventAction.CREATE, null, status,
                "建单 %d 分钟（含缓冲 %d 分钟）".formatted(planned + buffer, buffer));
        return toResponses(List.of(saved)).get(0);
    }

    // ------------------------------------------------------------------ 改期

    /**
     * 改期。距开始 ≥48 小时且未用过免费改期 → 免费改一次；否则停在「需扣定金」等老板选择。
     */
    @Transactional
    public BookingRescheduleResponse reschedule(Long id, BookingRescheduleRequest request) {
        Booking booking = guard.requireBooking(id);
        requireStatus(booking, SoloBookingStatus.WAIT_DEPOSIT, SoloBookingStatus.BOOKED);

        LocalDateTime now = LocalDateTime.now();
        long hoursBefore = SoloScheduleRules.hoursBefore(now, booking.getStartAt());
        boolean freeUsed = Integer.valueOf(1).equals(booking.getFreeRescheduleUsed());
        int freeHours = settingService.freeRescheduleHours();
        boolean free = SoloScheduleRules.isFreeReschedule(hoursBefore, freeUsed, freeHours);
        String decision = depositService.normalizeDecision(request.getDepositDecision());

        if (!free && SoloChangeDepositAction.NONE.equals(decision)) {
            return BookingRescheduleResponse.builder()
                    .changed(false)
                    .free(false)
                    .needDepositDeduction(true)
                    .hoursBefore(hoursBefore)
                    .message("距开始 %d 小时，%s，改期需扣 %s 元定金：请选择退还（REFUND）/ 没收（FORFEIT）/ 仍免费（FREE）"
                            .formatted(hoursBefore,
                                    freeUsed ? "免费改期已经用过" : "不足 %d 小时".formatted(settingService.freeRescheduleHours()),
                                    settingService.depositAmount().toPlainString()))
                    .booking(toResponses(List.of(booking)).get(0))
                    .build();
        }

        LocalDateTime oldStart = booking.getStartAt();
        LocalDateTime newStart = request.getNewStart();
        LocalDateTime newEnd = SoloScheduleRules.endOf(newStart, booking.getPlannedMinutes(),
                booking.getBufferMinutes());
        guard.assertNoConflict(newStart, newEnd, id);

        String depositAction;
        if (free) {
            booking.setFreeRescheduleUsed(1);
            depositAction = SoloChangeDepositAction.FREE;
        } else {
            depositAction = decision;
            depositService.applyDecision(id, decision, request.getReason());
        }

        booking.setStartAt(newStart);
        booking.setEndAt(newEnd);
        bookingRepository.save(booking);

        saveChange(id, SoloBookingChangeType.RESCHEDULE, oldStart, newStart,
                request.getInitiator(), (int) hoursBefore, depositAction, request.getReason());
        guard.recordEvent(id, SoloBookingEventAction.RESCHEDULE, booking.getStatus(), booking.getStatus(),
                "改期 %s → %s".formatted(oldStart.format(DATETIME), newStart.format(DATETIME)));

        return BookingRescheduleResponse.builder()
                .changed(true)
                .free(free)
                .needDepositDeduction(!free)
                .hoursBefore(hoursBefore)
                .message(free
                        ? "距开始 %d 小时，本次免费改期（该单免费额度已用完）".formatted(hoursBefore)
                        : "已按「%s」处理定金后完成改期".formatted(labelOfDepositAction(depositAction)))
                .booking(toResponses(List.of(booking)).get(0))
                .build();
    }

    // ------------------------------------------------------------------ 状态流转

    @Transactional
    public BookingResponse cancel(Long id, BookingCancelRequest request) {
        Booking booking = guard.requireBooking(id);
        requireStatus(booking, SoloBookingStatus.WAIT_DEPOSIT, SoloBookingStatus.BOOKED,
                SoloBookingStatus.ARRIVED);

        String decision = depositService.normalizeDecision(request.getDepositDecision());
        depositService.applyDecision(id, decision, request.getReason());

        String from = booking.getStatus();
        booking.setStatus(SoloBookingStatus.CANCELLED);
        bookingRepository.save(booking);
        guard.recordEvent(id, SoloBookingEventAction.CANCEL, from, SoloBookingStatus.CANCELLED,
                request.getReason());
        return toResponses(List.of(booking)).get(0);
    }

    @Transactional
    public BookingResponse arrive(Long id) {
        Booking booking = guard.requireBooking(id);
        requireStatus(booking, SoloBookingStatus.BOOKED);
        return transit(booking, SoloBookingStatus.ARRIVED, SoloBookingEventAction.ARRIVE, null);
    }

    @Transactional
    public BookingResponse start(Long id) {
        Booking booking = guard.requireBooking(id);
        requireStatus(booking, SoloBookingStatus.BOOKED, SoloBookingStatus.ARRIVED);
        return transit(booking, SoloBookingStatus.IN_SERVICE, SoloBookingEventAction.START, null);
    }

    @Transactional
    public BookingResponse complete(Long id) {
        Booking booking = guard.requireBooking(id);
        requireStatus(booking, SoloBookingStatus.ARRIVED, SoloBookingStatus.IN_SERVICE);
        BookingResponse response = transit(booking, SoloBookingStatus.COMPLETED,
                SoloBookingEventAction.COMPLETE, null);
        touchClientVisit(booking.getClientId(), booking.getStartAt().toLocalDate());
        return response;
    }

    @Transactional
    public BookingResponse noShow(Long id) {
        Booking booking = guard.requireBooking(id);
        requireStatus(booking, SoloBookingStatus.WAIT_DEPOSIT, SoloBookingStatus.BOOKED);
        return transit(booking, SoloBookingStatus.NO_SHOW, SoloBookingEventAction.NO_SHOW, null);
    }

    // ------------------------------------------------------------------ 顺延

    /** 只算不改：返回「影响 N 单」清单，供老板确认。 */
    @Transactional(readOnly = true)
    public BookingShiftPlanResponse previewShift(BookingShiftRequest request) {
        List<Booking> targets = shiftTargets(request);
        return buildShiftPlan(targets, request.getMinutes(), false);
    }

    /** 确认后逐单平移，并写一条待通知的变更记录（不自动通知客户）。 */
    @Transactional
    public BookingShiftPlanResponse shift(BookingShiftRequest request) {
        List<Booking> targets = shiftTargets(request);
        if (targets.isEmpty()) {
            return BookingShiftPlanResponse.builder().count(0).applied(true).items(List.of()).build();
        }

        Duration offset = Duration.ofMinutes(request.getMinutes());
        assertShiftHasNoConflict(targets, offset, request.getDate());

        // 先按平移前的时间生成清单，再逐单落库，避免二次加偏移。
        BookingShiftPlanResponse plan = buildShiftPlan(targets, request.getMinutes(), false);

        for (Booking booking : targets) {
            LocalDateTime oldStart = booking.getStartAt();
            LocalDateTime newStart = oldStart.plus(offset);
            long hoursBefore = SoloScheduleRules.hoursBefore(LocalDateTime.now(), oldStart);
            booking.setStartAt(newStart);
            booking.setEndAt(booking.getEndAt().plus(offset));
            bookingRepository.save(booking);
            saveChange(booking.getId(), SoloBookingChangeType.SHIFT, oldStart, newStart,
                    SoloChangeInitiator.OWNER, (int) hoursBefore, SoloChangeDepositAction.NONE,
                    request.getRemark());
            guard.recordEvent(booking.getId(), SoloBookingEventAction.SHIFT,
                    booking.getStatus(), booking.getStatus(),
                    "顺延 %d 分钟：%s → %s".formatted(request.getMinutes(),
                            oldStart.format(DATETIME), newStart.format(DATETIME)));
        }
        plan.setApplied(true);
        return plan;
    }

    // ------------------------------------------------------------------ 待通知

    /** 待通知清单：顺延 / 改期后还没跟客户说的变更，按创建时间倒序。 */
    @Transactional(readOnly = true)
    public List<PendingChangeResponse> pendingChanges() {
        List<BookingChange> changes = bookingChangeRepository.findByNotifiedOrderByCreatedAtDesc(0);
        if (changes.isEmpty()) {
            return List.of();
        }
        Map<Long, Booking> bookings = bookingRepository
                .findAllById(changes.stream().map(BookingChange::getBookingId).distinct().toList())
                .stream().collect(Collectors.toMap(Booking::getId, Function.identity()));
        Map<Long, Client> clients = clientRepository
                .findAllById(bookings.values().stream().map(Booking::getClientId).distinct().toList())
                .stream().collect(Collectors.toMap(Client::getId, Function.identity()));

        return changes.stream().map(change -> {
            Booking booking = bookings.get(change.getBookingId());
            Client client = booking == null ? null : clients.get(booking.getClientId());
            return PendingChangeResponse.builder()
                    .id(change.getId())
                    .bookingId(change.getBookingId())
                    .type(change.getType())
                    .clientCode(client == null ? null : client.getCode())
                    .clientNickname(client == null ? "客户已删除" : client.getNickname())
                    .oldStart(change.getOldStart())
                    .newStart(change.getNewStart())
                    .createdAt(change.getCreatedAt())
                    .remark(change.getRemark())
                    .build();
        }).toList();
    }

    /** 老板发完微信后点掉，清单里不再出现。 */
    @Transactional
    public void markNotified(Long changeId) {
        BookingChange change = bookingChangeRepository.findById(changeId)
                .orElseThrow(() -> new BusinessException(404, "变更记录不存在"));
        change.setNotified(1);
        bookingChangeRepository.save(change);
    }

    private List<Booking> shiftTargets(BookingShiftRequest request) {
        LocalDate date = request.getDate();
        LocalDateTime from = request.getFromTime() != null ? request.getFromTime() : date.atStartOfDay();
        if (!from.toLocalDate().equals(date)) {
            throw new BusinessException("顺延起点必须落在顺延日期当天");
        }
        return bookingRepository.findByStatusInAndStartAtBetweenOrderByStartAtAsc(
                List.of(SoloBookingStatus.WAIT_DEPOSIT, SoloBookingStatus.BOOKED),
                from,
                date.plusDays(1).atStartOfDay());
    }

    private void assertShiftHasNoConflict(List<Booking> targets, Duration offset, LocalDate date) {
        List<Long> targetIds = targets.stream().map(Booking::getId).toList();
        List<TimeWindow> fixedBusy = bookingRepository
                .findByStatusInAndStartAtBetweenOrderByStartAtAsc(
                        BookingGuard.OCCUPY_STATUSES,
                        date.atStartOfDay(),
                        date.plusDays(1).atStartOfDay())
                .stream()
                .filter(booking -> !targetIds.contains(booking.getId()))
                .map(booking -> new TimeWindow(booking.getStartAt(), booking.getEndAt()))
                .toList();

        for (Booking booking : targets) {
            TimeWindow shifted = new TimeWindow(
                    booking.getStartAt().plus(offset),
                    booking.getEndAt().plus(offset));
            TimeWindow conflict = SoloScheduleRules.firstOverlap(fixedBusy, shifted).orElse(null);
            if (conflict != null) {
                throw new SoloConflictException(
                        "顺延后 %s 会撞上其他预约（%s–%s），已取消本次顺延".formatted(
                                shifted.start().format(DATETIME),
                                conflict.start().format(TIME),
                                conflict.end().format(TIME)),
                        Map.of("bookingId", booking.getId(),
                                "newStart", shifted.start(),
                                "conflictStart", conflict.start(),
                                "conflictEnd", conflict.end()));
            }
        }
    }

    private BookingShiftPlanResponse buildShiftPlan(List<Booking> targets, int minutes, boolean applied) {
        Map<Long, Client> clients = targetClients(targets);
        List<BookingShiftPlanResponse.ShiftItem> items = targets.stream().map(booking -> {
            Client client = clients.get(booking.getClientId());
            return BookingShiftPlanResponse.ShiftItem.builder()
                    .bookingId(booking.getId())
                    .clientCode(client == null ? null : client.getCode())
                    .clientNickname(client == null ? "客户已删除" : client.getNickname())
                    .oldStart(booking.getStartAt())
                    .newStart(booking.getStartAt().plusMinutes(minutes))
                    .build();
        }).toList();
        return BookingShiftPlanResponse.builder()
                .count(items.size())
                .applied(applied)
                .items(items)
                .build();
    }

    // ------------------------------------------------------------------ 装配 / 工具

    /** 批量装配响应：客户、项目、定金台账一次取完，避免 N+1。 */
    @Transactional(readOnly = true)
    public List<BookingResponse> toResponses(List<Booking> bookings) {
        if (bookings.isEmpty()) {
            return List.of();
        }
        List<Long> bookingIds = bookings.stream().map(Booking::getId).toList();
        List<Long> clientIds = bookings.stream().map(Booking::getClientId).distinct().toList();

        Map<Long, Client> clients = clientRepository.findAllById(clientIds).stream()
                .collect(Collectors.toMap(Client::getId, Function.identity()));
        Map<Long, List<BookingItem>> items = bookingItemRepository
                .findByBookingIdInOrderBySeqAsc(bookingIds).stream()
                .collect(Collectors.groupingBy(BookingItem::getBookingId));
        Map<Long, DepositLedger> ledgers = depositService.ledgersOf(bookingIds);

        return bookings.stream().map(booking -> {
            Client client = clients.get(booking.getClientId());
            List<BookingItem> bookingItems = items.getOrDefault(booking.getId(), List.of());
            DepositLedger ledger = ledgers.getOrDefault(booking.getId(), DepositLedger.empty());
            return BookingResponse.builder()
                    .id(booking.getId())
                    .clientId(booking.getClientId())
                    .clientCode(client == null ? null : client.getCode())
                    .clientNickname(client == null ? "客户已删除" : client.getNickname())
                    .clientDeleted(client == null || Integer.valueOf(1).equals(client.getDeleted()))
                    .startAt(booking.getStartAt())
                    .endAt(booking.getEndAt())
                    .plannedMinutes(booking.getPlannedMinutes())
                    .bufferMinutes(booking.getBufferMinutes())
                    .status(booking.getStatus())
                    .source(booking.getSource())
                    .groupId(booking.getGroupId())
                    .remark(booking.getRemark())
                    .totalPrice(bookingItems.stream()
                            .map(BookingItem::getPrice)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add))
                    .depositState(ledger.state())
                    .depositCollected(ledger.collected())
                    .depositBalance(ledger.balance())
                    .items(bookingItems.stream().map(this::toItemResponse).toList())
                    .build();
        }).toList();
    }

    private Map<Long, Client> targetClients(List<Booking> bookings) {
        if (bookings.isEmpty()) {
            return Map.of();
        }
        return clientRepository.findAllById(bookings.stream().map(Booking::getClientId).distinct().toList())
                .stream().collect(Collectors.toMap(Client::getId, Function.identity()));
    }

    private BookingResponse transit(Booking booking, String toStatus, String action, String remark) {
        String from = booking.getStatus();
        booking.setStatus(toStatus);
        bookingRepository.save(booking);
        guard.recordEvent(booking.getId(), action, from, toStatus, remark);
        return toResponses(List.of(booking)).get(0);
    }

    private void touchClientVisit(Long clientId, LocalDate date) {
        clientRepository.findById(clientId).ifPresent(client -> {
            if (client.getFirstVisitDate() == null) {
                client.setFirstVisitDate(date);
            }
            if (client.getLastVisitDate() == null || date.isAfter(client.getLastVisitDate())) {
                client.setLastVisitDate(date);
            }
            clientRepository.save(client);
        });
    }

    private void requireStatus(Booking booking, String... allowed) {
        for (String status : allowed) {
            if (status.equals(booking.getStatus())) {
                return;
            }
        }
        throw new BusinessException("当前状态是「%s」，不能执行该操作（仅支持：%s）".formatted(
                SoloBookingStatus.label(booking.getStatus()),
                String.join(" / ", java.util.Arrays.stream(allowed)
                        .map(SoloBookingStatus::label).toList())));
    }

    private String resolveCreateStatus(String requested) {
        if (!StringUtils.hasText(requested)) {
            return SoloBookingStatus.BOOKED;
        }
        String status = requested.trim().toUpperCase(Locale.ROOT);
        if (SoloBookingStatus.BOOKED.equals(status) || SoloBookingStatus.WAIT_DEPOSIT.equals(status)) {
            return status;
        }
        throw new BusinessException("建单状态只能是 BOOKED（已定档）或 WAIT_DEPOSIT（待定金）");
    }

    private String resolveSource(String requested, String fallback) {
        if (!StringUtils.hasText(requested)) {
            return fallback;
        }
        String source = requested.trim().toUpperCase(Locale.ROOT);
        return switch (source) {
            case SoloBookingSource.WECHAT, SoloBookingSource.XHS,
                 SoloBookingSource.WALKIN, SoloBookingSource.JUMP, SoloBookingSource.NORMAL -> source;
            default -> throw new BusinessException("未知的预约来源：" + requested);
        };
    }

    private int resolveBuffer(BookingCreateRequest request) {
        return request.getBufferMinutes() == null
                ? settingService.bufferMinutes()
                : request.getBufferMinutes();
    }

    private int plannedMinutes(List<ResolvedItem> items) {
        int planned = items.stream().mapToInt(ResolvedItem::durationMin).sum();
        if (planned <= 0) {
            throw new BusinessException("项目总时长必须大于 0");
        }
        return planned;
    }

    /**
     * 解析项目：有 styleId 时用款式补全名称 / 时长 / 价格，落库前做快照。
     */
    private List<ResolvedItem> resolveItems(List<BookingItemRequest> itemRequests) {
        if (itemRequests == null || itemRequests.isEmpty()) {
            throw new BusinessException("至少要有一个项目");
        }
        List<ResolvedItem> resolved = new ArrayList<>();
        for (BookingItemRequest item : itemRequests) {
            if (item.getStyleId() != null) {
                Style style = styleRepository.findById(item.getStyleId())
                        .orElseThrow(() -> new BusinessException(404, "款式不存在"));
                resolved.add(new ResolvedItem(
                        style.getId(),
                        StringUtils.hasText(item.getStyleName()) ? item.getStyleName() : style.getName(),
                        item.getDurationMin() != null ? item.getDurationMin() : style.getDurationMin(),
                        item.getPrice() != null ? item.getPrice() : style.getPrice(),
                        item.getAddon() == null ? 0 : item.getAddon()));
            } else {
                if (!StringUtils.hasText(item.getStyleName()) || item.getDurationMin() == null) {
                    throw new BusinessException("手写项目必须填名称和时长（或直接选款式）");
                }
                resolved.add(new ResolvedItem(null, item.getStyleName(), item.getDurationMin(),
                        item.getPrice() == null ? BigDecimal.ZERO : item.getPrice(),
                        item.getAddon() == null ? 0 : item.getAddon()));
            }
        }
        return resolved;
    }

    private void saveItems(Long bookingId, List<ResolvedItem> items) {
        List<BookingItem> entities = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ResolvedItem resolved = items.get(i);
            BookingItem item = new BookingItem();
            item.setBookingId(bookingId);
            item.setStyleId(resolved.styleId());
            item.setStyleName(resolved.styleName());
            item.setDurationMin(resolved.durationMin());
            item.setPrice(resolved.price());
            item.setAddon(resolved.addon());
            item.setSeq(i);
            entities.add(item);
        }
        bookingItemRepository.saveAll(entities);
    }

    private void saveChange(Long bookingId, String type, LocalDateTime oldStart, LocalDateTime newStart,
                            String initiator, int hoursBefore, String depositAction, String remark) {
        BookingChange change = new BookingChange();
        change.setBookingId(bookingId);
        change.setType(type);
        change.setOldStart(oldStart);
        change.setNewStart(newStart);
        change.setInitiator(normalizeInitiator(initiator));
        change.setHoursBefore(hoursBefore);
        change.setDepositAction(depositAction == null ? SoloChangeDepositAction.NONE : depositAction);
        change.setNotified(0);
        change.setRemark(remark);
        bookingChangeRepository.save(change);
    }

    private String normalizeInitiator(String initiator) {
        if (!StringUtils.hasText(initiator)) {
            return SoloChangeInitiator.CLIENT;
        }
        String normalized = initiator.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case SoloChangeInitiator.CLIENT, SoloChangeInitiator.OWNER -> normalized;
            default -> throw new BusinessException("发起方只能是 CLIENT 或 OWNER");
        };
    }

    private String labelOfDepositAction(String action) {
        return switch (action) {
            case SoloChangeDepositAction.REFUND -> "退还定金";
            case SoloChangeDepositAction.FORFEIT -> "没收定金";
            case SoloChangeDepositAction.FREE -> "仍免费";
            default -> "不处理定金";
        };
    }

    private BookingItemResponse toItemResponse(BookingItem item) {
        return BookingItemResponse.builder()
                .id(item.getId())
                .styleId(item.getStyleId())
                .styleName(item.getStyleName())
                .durationMin(item.getDurationMin())
                .price(item.getPrice())
                .addon(item.getAddon())
                .seq(item.getSeq())
                .build();
    }

    /** 建单解析后的项目快照。 */
    private record ResolvedItem(Long styleId, String styleName, Integer durationMin,
                                BigDecimal price, Integer addon) {
    }
}
