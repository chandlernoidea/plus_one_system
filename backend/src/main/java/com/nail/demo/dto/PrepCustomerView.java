package com.nail.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrepCustomerView {

    private Long appointmentId;
    private String styleDescription;
    private String effectImageUrl;
}
