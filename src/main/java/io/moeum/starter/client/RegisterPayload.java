package io.moeum.starter.client;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class RegisterPayload {

    private String projectKey;
    private String applicationName;
    private String databaseType;
    private String jdbcUrl;
    private String username;
    private List<TableInfo> tables;
    private List<RelationInfo> relations;

    @Getter
    @Builder
    public static class RelationInfo {
        private String fromTable;
        private String toTable;
    }

    @Getter
    @Builder
    public static class TableInfo {
        private String name;
        private List<ColumnInfo> columns;
    }

    @Getter
    @Builder
    public static class ColumnInfo {
        private String name;
        private String type;
        private Integer length;
        private boolean nullable;
        private boolean primaryKey;
    }
}
