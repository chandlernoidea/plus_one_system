package com.nail.demo.solo.service;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.nail.demo.exception.BusinessException;
import com.nail.demo.solo.dto.SettingsRequest;
import com.nail.demo.solo.dto.SettingsResponse;
import com.nail.demo.solo.entity.SoloSetting;
import com.nail.demo.solo.repository.SoloSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 单人版设置。库里只存改过的项，没存过就用这里的默认值。
 *
 * <p>这些值直接决定三处行为：营业时间模板（排期兜底时间轴）、缓冲默认值、
 * 定金金额、免费改期阈值。
 */
@Service
@RequiredArgsConstructor
public class SoloSettingService {

    public static final String OPEN_TIME = "open_time";
    public static final String CLOSE_TIME = "close_time";
    public static final String BUFFER_MINUTES = "buffer_minutes";
    public static final String DEPOSIT_AMOUNT = "deposit_amount";
    public static final String FREE_RESCHEDULE_HOURS = "free_reschedule_hours";

    /** 默认时间轴（营业时间模板）：实际营业常落在这个区间内。 */
    private static final String DEFAULT_OPEN_TIME = "08:30";
    private static final String DEFAULT_CLOSE_TIME = "22:30";
    private static final int DEFAULT_BUFFER_MINUTES = 20;
    private static final BigDecimal DEFAULT_DEPOSIT_AMOUNT = new BigDecimal("30");
    private static final int DEFAULT_FREE_RESCHEDULE_HOURS = 48;

    private final SoloSettingRepository settingRepository;

    /** 全部设置（默认值 + 库里的覆盖），顺序固定，前端直接铺表单。 */
    public Map<String, String> raw() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(OPEN_TIME, DEFAULT_OPEN_TIME);
        values.put(CLOSE_TIME, DEFAULT_CLOSE_TIME);
        values.put(BUFFER_MINUTES, String.valueOf(DEFAULT_BUFFER_MINUTES));
        values.put(DEPOSIT_AMOUNT, DEFAULT_DEPOSIT_AMOUNT.toPlainString());
        values.put(FREE_RESCHEDULE_HOURS, String.valueOf(DEFAULT_FREE_RESCHEDULE_HOURS));

        settingRepository.findAll().forEach(setting -> {
            if (values.containsKey(setting.getSettingKey()) && StringUtils.hasText(setting.getSettingValue())) {
                values.put(setting.getSettingKey(), setting.getSettingValue());
            }
        });
        return values;
    }

    public LocalTime openTime() {
        return parseTime(raw().get(OPEN_TIME), LocalTime.of(8, 30));
    }

    public LocalTime closeTime() {
        return parseTime(raw().get(CLOSE_TIME), LocalTime.of(22, 30));
    }

    public int bufferMinutes() {
        return parseInt(raw().get(BUFFER_MINUTES), DEFAULT_BUFFER_MINUTES);
    }

    public BigDecimal depositAmount() {
        return new BigDecimal(raw().get(DEPOSIT_AMOUNT));
    }

    public int freeRescheduleHours() {
        return parseInt(raw().get(FREE_RESCHEDULE_HOURS), DEFAULT_FREE_RESCHEDULE_HOURS);
    }

    public SettingsResponse view() {
        Map<String, String> values = raw();
        return SettingsResponse.builder()
                .openTime(values.get(OPEN_TIME))
                .closeTime(values.get(CLOSE_TIME))
                .bufferMinutes(Integer.parseInt(values.get(BUFFER_MINUTES)))
                .depositAmount(new BigDecimal(values.get(DEPOSIT_AMOUNT)))
                .freeRescheduleHours(Integer.parseInt(values.get(FREE_RESCHEDULE_HOURS)))
                .build();
    }

    @Transactional
    public SettingsResponse update(SettingsRequest request) {
        LocalTime open = parseTime(request.getOpenTime(), null);
        LocalTime close = parseTime(request.getCloseTime(), null);
        if (open == null || close == null) {
            throw new BusinessException("营业时间格式应为 HH:mm");
        }
        if (!close.isAfter(open)) {
            throw new BusinessException("打烊时间要晚于开门时间");
        }
        if (request.getBufferMinutes() == null || request.getBufferMinutes() < 0 || request.getBufferMinutes() > 120) {
            throw new BusinessException("默认缓冲应在 0–120 分钟之间");
        }
        if (request.getDepositAmount() == null
                || request.getDepositAmount().compareTo(BigDecimal.ZERO) < 0
                || request.getDepositAmount().compareTo(new BigDecimal("1000")) > 0) {
            throw new BusinessException("定金应在 0–1000 元之间");
        }
        if (request.getFreeRescheduleHours() == null
                || request.getFreeRescheduleHours() < 1
                || request.getFreeRescheduleHours() > 168) {
            throw new BusinessException("免费改期阈值应在 1–168 小时之间");
        }

        save(OPEN_TIME, open.toString().substring(0, 5));
        save(CLOSE_TIME, close.toString().substring(0, 5));
        save(BUFFER_MINUTES, String.valueOf(request.getBufferMinutes()));
        save(DEPOSIT_AMOUNT, request.getDepositAmount().stripTrailingZeros().toPlainString());
        save(FREE_RESCHEDULE_HOURS, String.valueOf(request.getFreeRescheduleHours()));
        return view();
    }

    private void save(String key, String value) {
        SoloSetting setting = settingRepository.findBySettingKey(key).orElseGet(SoloSetting::new);
        setting.setSettingKey(key);
        setting.setSettingValue(value);
        settingRepository.save(setting);
    }

    private LocalTime parseTime(String text, LocalTime fallback) {
        if (!StringUtils.hasText(text)) {
            return fallback;
        }
        try {
            return LocalTime.parse(text.trim().length() == 5 ? text.trim() : text.trim().substring(0, 5));
        } catch (DateTimeParseException | StringIndexOutOfBoundsException e) {
            if (fallback != null) {
                return fallback;
            }
            throw new BusinessException("时间格式应为 HH:mm");
        }
    }

    private int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
