package com.nail.demo.solo.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.nail.demo.exception.BusinessException;
import com.nail.demo.solo.common.SoloBookingStatus;
import com.nail.demo.solo.dto.BookingResponse;
import com.nail.demo.solo.dto.ClientCreateRequest;
import com.nail.demo.solo.dto.ClientDetailResponse;
import com.nail.demo.solo.dto.ClientResponse;
import com.nail.demo.solo.dto.ClientUpdateRequest;
import com.nail.demo.solo.entity.Booking;
import com.nail.demo.solo.entity.BookingItem;
import com.nail.demo.solo.entity.Client;
import com.nail.demo.solo.repository.BookingItemRepository;
import com.nail.demo.solo.repository.BookingRepository;
import com.nail.demo.solo.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 客户档案。短号自动生成且永不变更；删除是软删除，历史预约保留。
 */
@Service
@RequiredArgsConstructor
public class ClientService {

    private static final String CODE_PREFIX = "#C";

    private final ClientRepository clientRepository;
    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;
    private final BookingService bookingService;

    @Transactional(readOnly = true)
    public List<ClientResponse> search(String keyword) {
        String normalized = StringUtils.hasText(keyword) ? keyword.trim() : null;
        return clientRepository.search(normalized).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ClientResponse get(Long id) {
        return toResponse(requireClient(id));
    }

    /** 同名客户：新建时提示「重名了，加个区分词（如 阿岚-长甲）」。 */
    @Transactional(readOnly = true)
    public List<ClientResponse> checkName(String nickname) {
        if (!StringUtils.hasText(nickname)) {
            return List.of();
        }
        return clientRepository.findByNicknameAndDeletedOrderByIdAsc(nickname.trim(), 0)
                .stream().map(this::toResponse).toList();
    }

    /** 客户详情：档案 + 历史预约 + 做过的款式。 */
    @Transactional(readOnly = true)
    public ClientDetailResponse detail(Long id) {
        Client client = requireClient(id);
        List<Booking> bookings = bookingRepository.findByClientIdOrderByStartAtDesc(id);
        List<BookingResponse> views = bookingService.toResponses(bookings);

        Map<String, ClientDetailResponse.StyleUsage> usage = new LinkedHashMap<>();
        if (!bookings.isEmpty()) {
            List<BookingItem> items = bookingItemRepository.findByBookingIdInOrderBySeqAsc(
                    bookings.stream().map(Booking::getId).toList());
            for (BookingItem item : items) {
                String name = item.getStyleName();
                ClientDetailResponse.StyleUsage current = usage.get(name);
                if (current == null) {
                    usage.put(name, ClientDetailResponse.StyleUsage.builder()
                            .styleId(item.getStyleId())
                            .styleName(name)
                            .times(1)
                            .build());
                } else {
                    current.setTimes(current.getTimes() + 1);
                }
            }
        }

        List<ClientDetailResponse.StyleUsage> styleUsage = new ArrayList<>(usage.values());
        styleUsage.sort(Comparator.comparingInt(ClientDetailResponse.StyleUsage::getTimes).reversed());

        return ClientDetailResponse.builder()
                .client(toResponse(client))
                .bookings(views)
                .styleUsage(styleUsage)
                .totalBookings(views.size())
                .completedBookings((int) views.stream()
                        .filter(view -> SoloBookingStatus.COMPLETED.equals(view.getStatus()))
                        .count())
                .build();
    }

    @Transactional
    public ClientResponse create(ClientCreateRequest request) {
        Client client = new Client();
        client.setCode(nextCode());
        client.setNickname(request.getNickname().trim());
        client.setPhoneTail(normalizePhoneTail(request.getPhoneTail()));
        client.setSource(trimToNull(request.getSource()));
        client.setRemark(trimToNull(request.getRemark()));
        client.setColorNotes(trimToNull(request.getColorNotes()));
        client.setDeleted(0);
        return toResponse(clientRepository.save(client));
    }

    @Transactional
    public ClientResponse update(Long id, ClientUpdateRequest request) {
        Client client = requireClient(id);
        if (StringUtils.hasText(request.getNickname())) {
            client.setNickname(request.getNickname().trim());
        }
        if (request.getPhoneTail() != null) {
            client.setPhoneTail(normalizePhoneTail(request.getPhoneTail()));
        }
        if (request.getSource() != null) {
            client.setSource(trimToNull(request.getSource()));
        }
        if (request.getRemark() != null) {
            client.setRemark(trimToNull(request.getRemark()));
        }
        if (request.getColorNotes() != null) {
            client.setColorNotes(trimToNull(request.getColorNotes()));
        }
        return toResponse(clientRepository.save(client));
    }

    /** 软删除：预约、流水都还在，只是客户列表不再出现。 */
    @Transactional
    public void delete(Long id) {
        Client client = requireClient(id);
        client.setDeleted(1);
        clientRepository.save(client);
    }

    /** 下一个短号：#C001、#C002……跳过已被占用的号。 */
    private String nextCode() {
        long seq = clientRepository.findTopByOrderByIdDesc().map(Client::getId).orElse(0L) + 1;
        String code = format(seq);
        while (clientRepository.existsByCode(code)) {
            seq++;
            code = format(seq);
        }
        return code;
    }

    private String format(long seq) {
        return CODE_PREFIX + "%03d".formatted(seq);
    }

    private Client requireClient(Long id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "客户不存在"));
    }

    private String normalizePhoneTail(String phoneTail) {
        if (!StringUtils.hasText(phoneTail)) {
            return null;
        }
        String trimmed = phoneTail.trim();
        if (!trimmed.matches("\\d{4}")) {
            throw new BusinessException("手机后四位必须是 4 位数字");
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private ClientResponse toResponse(Client client) {
        return ClientResponse.builder()
                .id(client.getId())
                .code(client.getCode())
                .nickname(client.getNickname())
                .phoneTail(client.getPhoneTail())
                .source(client.getSource())
                .remark(client.getRemark())
                .colorNotes(client.getColorNotes())
                .firstVisitDate(client.getFirstVisitDate())
                .lastVisitDate(client.getLastVisitDate())
                .deleted(client.getDeleted())
                .build();
    }
}
