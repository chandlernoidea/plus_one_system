package com.nail.demo.solo.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.nail.demo.solo.common.SoloBookingSource;
import com.nail.demo.solo.dto.BookingCreateRequest;
import com.nail.demo.solo.dto.BookingItemRequest;
import com.nail.demo.solo.dto.DemoSeedResponse;
import com.nail.demo.solo.entity.Booking;
import com.nail.demo.solo.entity.Client;
import com.nail.demo.solo.entity.Style;
import com.nail.demo.solo.repository.BookingRepository;
import com.nail.demo.solo.repository.ClientRepository;
import com.nail.demo.solo.repository.StyleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 一键演示数据：3 客户、5 款式、今天 4 单（含 1 单连做，留出空档）。
 *
 * <p>已经建过（存在「演示-」开头的客户）就不重复建，直接报数。
 */
@Service
@RequiredArgsConstructor
public class DemoDataService {

    private static final String PREFIX = "演示-";

    private final ClientRepository clientRepository;
    private final StyleRepository styleRepository;
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;

    @Transactional
    public DemoSeedResponse seed() {
        long existing = clientRepository.search(PREFIX).stream()
                .filter(client -> client.getNickname() != null && client.getNickname().startsWith(PREFIX))
                .count();
        if (existing > 0) {
            return DemoSeedResponse.builder()
                    .created(false)
                    .clients((int) clientRepository.search(null).stream()
                            .filter(client -> client.getNickname() != null
                                    && client.getNickname().startsWith(PREFIX)).count())
                    .styles((int) styleRepository.findAllByOrderByIdDesc().stream()
                            .filter(style -> style.getName().startsWith(PREFIX)).count())
                    .bookings(0)
                    .notes(List.of("库里已经有演示数据了，没有重复创建。"))
                    .build();
        }

        List<Client> clients = new ArrayList<>();
        clients.add(createClient("阿岚", "8821", "小红书", "#E03 裸粉，怕深色"));
        clients.add(createClient("小满", "3090", "朋友介绍", "喜欢猫眼"));
        clients.add(createClient("橙子", null, "路过", "#F21 正红"));

        List<Style> styles = new ArrayList<>();
        styles.add(createStyle("本甲单色", 60, 128, "单色,本甲", "[{\"name\":\"底胶\",\"qty\":1},{\"name\":\"甲油胶\",\"qty\":1}]"));
        styles.add(createStyle("法式裸粉", 90, 168, "法式,裸色", "[{\"name\":\"底胶\",\"qty\":1},{\"name\":\"法式白\",\"qty\":1}]"));
        styles.add(createStyle("猫眼奶茶", 60, 138, "猫眼", "[{\"name\":\"猫眼胶\",\"qty\":1}]"));
        styles.add(createStyle("延长甲", 120, 258, "延长", "[{\"name\":\"延长胶\",\"qty\":1},{\"name\":\"甲模\",\"qty\":2}]"));
        styles.add(createStyle("手绘小花", 120, 198, "手绘,节日", "[{\"name\":\"彩绘胶\",\"qty\":1}]"));

        LocalDate today = LocalDate.now();
        // 10:00 法式（90+20） / 13:00 单色（60+20） / 15:00 连做猫眼+手绘（60+120+20） / 19:00 延长（120+20）
        createBooking(clients.get(0), today.atTime(10, 0), List.of(styles.get(1)), null, "带了参考图");
        createBooking(clients.get(1), today.atTime(13, 0), List.of(styles.get(0)), null, null);
        createBooking(clients.get(2), today.atTime(15, 0), List.of(styles.get(2), styles.get(4)),
                "DEMO-" + today, "连做：猫眼 + 手绘");
        createBooking(clients.get(0), today.atTime(19, 0), List.of(styles.get(3)), null, null);

        return DemoSeedResponse.builder()
                .created(true)
                .clients(clients.size())
                .styles(styles.size())
                .bookings(4)
                .notes(List.of(
                        "10:00 法式裸粉（单）",
                        "13:00 本甲单色（单）",
                        "15:00 猫眼奶茶 + 手绘小花（连做，同一单两个项目）",
                        "19:00 延长甲（单）",
                        "11:50–13:00、14:20–15:00、18:20–19:00 留作空档"))
                .build();
    }

    private Client createClient(String nickname, String tail, String source, String colorNotes) {
        Client client = new Client();
        client.setCode(nextCode());
        client.setNickname(PREFIX + nickname);
        client.setPhoneTail(tail);
        client.setSource(source);
        client.setColorNotes(colorNotes);
        client.setDeleted(0);
        return clientRepository.save(client);
    }

    private Style createStyle(String name, int duration, int price, String tags, String materialJson) {
        Style style = new Style();
        style.setName(PREFIX + name);
        style.setDurationMin(duration);
        style.setPrice(java.math.BigDecimal.valueOf(price));
        style.setTags(tags);
        style.setMaterialJson(materialJson);
        style.setEnabled(1);
        return styleRepository.save(style);
    }

    private void createBooking(Client client, LocalDateTime start, List<Style> styles,
                               String groupId, String remark) {
        BookingCreateRequest request = new BookingCreateRequest();
        request.setClientId(client.getId());
        request.setStartAt(start);
        request.setSource(SoloBookingSource.NORMAL);
        request.setGroupId(groupId);
        request.setRemark(remark);
        List<BookingItemRequest> items = new ArrayList<>();
        for (Style style : styles) {
            BookingItemRequest item = new BookingItemRequest();
            item.setStyleId(style.getId());
            items.add(item);
        }
        request.setItems(items);
        bookingService.create(request);
    }

    private String nextCode() {
        long seq = clientRepository.findTopByOrderByIdDesc().map(Client::getId).orElse(0L) + 1;
        String code = "#C" + "%03d".formatted(seq);
        while (clientRepository.existsByCode(code)) {
            seq++;
            code = "#C" + "%03d".formatted(seq);
        }
        return code;
    }
}
