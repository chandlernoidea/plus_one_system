package com.nail.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffResponse {

    private Long staffId;
    private Long userId;
    private String staffNo;
    private String realName;
    private String bio;
    private String avatarUrl;
}
