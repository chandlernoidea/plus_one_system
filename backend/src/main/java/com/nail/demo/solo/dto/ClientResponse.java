package com.nail.demo.solo.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientResponse {

    private Long id;
    private String code;
    private String nickname;
    private String phoneTail;
    private String source;
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate firstVisitDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate lastVisitDate;

    /** 删除后历史预约保留，展示时标「客户已删除」。 */
    private Integer deleted;
}
