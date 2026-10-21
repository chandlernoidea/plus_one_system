package com.nail.demo.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PrepUpdateRequest {

    @Size(max = 1000, message = "款式说明不能超过1000个字符")
    private String styleDescription;

    @Size(max = 500, message = "效果图地址不能超过500个字符")
    private String effectImageUrl;

    private String materialJson;

    @Size(max = 500, message = "备注不能超过500个字符")
    private String staffNote;
}
