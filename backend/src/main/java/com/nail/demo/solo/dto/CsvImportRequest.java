package com.nail.demo.solo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * CSV 原文。导入走 JSON 而不是文件上传：手机上从微信复制一段就能导，
 * 不必先存成文件。
 */
@Data
public class CsvImportRequest {

    @NotBlank(message = "CSV 内容不能为空")
    private String csv;
}
