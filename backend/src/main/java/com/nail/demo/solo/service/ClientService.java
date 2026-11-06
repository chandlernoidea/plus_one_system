package com.nail.demo.solo.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.nail.demo.exception.BusinessException;
import com.nail.demo.solo.common.SoloBookingStatus;
import com.nail.demo.solo.dto.BookingResponse;
import com.nail.demo.solo.dto.ClientCreateRequest;
import com.nail.demo.solo.dto.ClientDetailResponse;
import com.nail.demo.solo.dto.ClientImportResponse;
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

    /**
     * CSV 导入预览：逐行解析 + 标出同名（和库里重、或文件内前面行重）。
     *
     * <p>列顺序：昵称, 手机后四位, 来源, 备注。首行是表头会自动跳过。
     */
    @Transactional(readOnly = true)
    public ClientImportResponse previewImport(String csv) {
        List<ClientImportResponse.Row> rows = parseCsv(csv);
        int duplicates = (int) rows.stream().filter(ClientImportResponse.Row::isDuplicate).count();
        return ClientImportResponse.builder()
                .rows(rows)
                .total(rows.size())
                .duplicates(duplicates)
                .build();
    }

    /** 确认导入：只建合法的行，短号接着往后发，重名的照建（名字是老板的事）。 */
    @Transactional
    public ClientImportResponse.Result importClients(String csv) {
        List<ClientImportResponse.Row> rows = parseCsv(csv);
        List<String> codes = new ArrayList<>();
        int skipped = 0;
        Set<String> usedCodes = new HashSet<>();

        long seq = clientRepository.findTopByOrderByIdDesc().map(Client::getId).orElse(0L);
        for (ClientImportResponse.Row row : rows) {
            if (!row.isValid()) {
                skipped++;
                continue;
            }
            String code;
            do {
                seq++;
                code = format(seq);
            } while (usedCodes.contains(code) || clientRepository.existsByCode(code));
            usedCodes.add(code);

            Client client = new Client();
            client.setCode(code);
            client.setNickname(row.getNickname());
            client.setPhoneTail(StringUtils.hasText(row.getPhoneTail()) ? row.getPhoneTail() : null);
            client.setSource(StringUtils.hasText(row.getSource()) ? row.getSource() : null);
            client.setRemark(StringUtils.hasText(row.getRemark()) ? row.getRemark() : null);
            client.setDeleted(0);
            clientRepository.save(client);
            codes.add(code);
        }
        return ClientImportResponse.Result.builder()
                .created(codes.size())
                .skipped(skipped)
                .codes(codes)
                .build();
    }

    private List<ClientImportResponse.Row> parseCsv(String csv) {
        List<ClientImportResponse.Row> rows = new ArrayList<>();
        if (!StringUtils.hasText(csv)) {
            return rows;
        }
        Set<String> seen = new HashSet<>();
        clientRepository.findByDeletedOrderByLastVisitDateDescIdDesc(0)
                .forEach(client -> seen.add(client.getNickname()));

        String[] lines = csv.replace("\uFEFF", "").split("\r?\n");
        int lineNo = 0;
        for (String line : lines) {
            if (!StringUtils.hasText(line)) {
                continue;
            }
            lineNo++;
            List<String> cells = splitCsvLine(line);
            String nickname = cells.isEmpty() ? "" : cells.get(0);
            if (lineNo == 1 && isHeader(nickname)) {
                continue;
            }
            String phoneTail = cells.size() > 1 ? cells.get(1) : "";
            String source = cells.size() > 2 ? cells.get(2) : "";
            String remark = cells.size() > 3 ? cells.get(3) : "";

            boolean valid = StringUtils.hasText(nickname)
                    && (!StringUtils.hasText(phoneTail) || phoneTail.matches("\\d{4}"));
            boolean duplicate = StringUtils.hasText(nickname) && seen.contains(nickname);

            String note;
            if (!StringUtils.hasText(nickname)) {
                note = "昵称空着，跳过";
            } else if (!valid) {
                note = "手机后四位要 4 位数字";
            } else if (duplicate) {
                note = "同名，建议加区分词";
            } else {
                note = "";
            }

            rows.add(ClientImportResponse.Row.builder()
                    .line(lineNo)
                    .nickname(nickname)
                    .phoneTail(phoneTail)
                    .source(source)
                    .remark(remark)
                    .duplicate(duplicate)
                    .valid(valid)
                    .note(note)
                    .build());

            if (StringUtils.hasText(nickname)) {
                seen.add(nickname);
            }
        }
        return rows;
    }

    private boolean isHeader(String first) {
        return "昵称".equals(first) || "nickname".equalsIgnoreCase(first) || "name".equalsIgnoreCase(first);
    }

    /** 极简 CSV 切分：支持双引号包住的逗号，够导入名片用。 */
    private List<String> splitCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                quoted = !quoted;
                continue;
            }
            if (c == ',' && !quoted) {
                cells.add(current.toString().trim());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        cells.add(current.toString().trim());
        return cells;
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
