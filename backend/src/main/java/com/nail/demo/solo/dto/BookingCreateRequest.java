package com.nail.demo.solo.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

@Data
public class BookingCreateRequest {

    @NotNull(message = "客户不能为空")
    private Long clientId;

    @NotNull(message = "开始时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startAt;

    /** 缓冲，可按单覆盖；不传用默认 20 分钟。 */
    @Min(value = 0, message = "缓冲不能为负")
    @Max(value = 120, message = "缓冲最多 120 分钟")
    private Integer bufferMinutes;

    /** 建单状态：默认 BOOKED；传 WAIT_DEPOSIT 表示先占位、待收定金后再定档。 */
    private String status;

    private String source;

    /** 连单分组，同一客户多项目或两位朋友先后到店共用。 */
    @Size(max = 32, message = "连单分组最长 32 字")
    private String groupId;

    @Size(max = 500, message = "备注最长 500 字")
    private String remark;

    /**
     * 强排：确认撞档也要建单。用于「老板明知道撞了还硬排」的场景，
     * 前端会把冲突单标红并提示冲突数，不静默覆盖。
     */
    private Boolean force;

    @NotEmpty(message = "至少要有一个项目")
    @Valid
    private List<BookingItemRequest> items = new ArrayList<>();
}
