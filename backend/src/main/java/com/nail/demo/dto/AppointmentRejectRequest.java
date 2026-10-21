package com.nail.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AppointmentRejectRequest {

    @NotBlank(message = "拒绝原因不能为空")
    @Size(max = 255, message = "拒绝原因不能超过255个字符")
    private String reason;
}
