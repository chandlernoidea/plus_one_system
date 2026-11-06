package com.nail.demo.solo.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CSV 导入客户：先预览（同名标黄），再确认导入。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientImportResponse {

    private List<Row> rows;

    private int total;

    /** 与现有客户或文件内前面行重名的条数。 */
    private int duplicates;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Row {
        private int line;
        private String nickname;
        private String phoneTail;
        private String source;
        private String remark;
        private boolean duplicate;
        private boolean valid;
        private String note;
    }

    /** 确认导入的结果。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Result {
        private int created;
        private int skipped;
        private List<String> codes;
    }
}
