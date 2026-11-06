package com.nail.demo.solo.dto;

import lombok.Data;

/**
 * 客户更新。短号永不变化，所以这里没有 code 字段。
 */
@Data
public class ClientUpdateRequest {

    private String nickname;
    private String phoneTail;
    private String source;
    private String remark;
}
