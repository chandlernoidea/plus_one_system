package com.nail.demo.dto;

import java.util.List;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AppointmentCompleteRequest {

    @Size(max = 6, message = "完工图最多上传6张")
    private List<String> finishImageUrls;
}
