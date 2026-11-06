package com.nail.demo.solo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ClientCreateRequest {

    @NotBlank(message = "昵称不能为空")
    @Size(max = 50, message = "昵称最长 50 字")
    private String nickname;

    /** 手机后 4 位选填；填了就必须是 4 位数字。 */
    @Pattern(regexp = "^$|^\\d{4}$", message = "手机后四位必须是 4 位数字")
    private String phoneTail;

    @Size(max = 30, message = "来源最长 30 字")
    private String source;

    @Size(max = 500, message = "备注最长 500 字")
    private String remark;

    @Size(max = 200, message = "常用色号最长 200 字")
    private String colorNotes;
}
