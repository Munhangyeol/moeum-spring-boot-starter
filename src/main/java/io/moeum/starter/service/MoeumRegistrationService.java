package io.moeum.starter.service;

import io.moeum.starter.client.MoeumClient;
import io.moeum.starter.client.RegisterPayload;
import io.moeum.starter.properties.MoeumProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;

@Slf4j
@RequiredArgsConstructor
public class MoeumRegistrationService {

    private final MoeumProperties properties;
    private final MoeumClient moeumClient;
    private final DataSource dataSource;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!properties.isEnabled()) {
            log.debug("[Moeum] 스타터가 비활성화 상태입니다.");
            return;
        }
        if (properties.getServerUrl() == null || properties.getApiKey() == null) {
            log.warn("[Moeum] serverUrl 또는 apiKey가 설정되지 않았습니다. 등록을 건너뜁니다.");
            return;
        }

        try {
            log.info("[Moeum] DB 메타데이터 수집 시작...");
            List<RegisterPayload.TableInfo> tables = collectTableMetadata();
            List<RegisterPayload.RelationInfo> relations = collectRelations(tables);

            RegisterPayload payload = RegisterPayload.builder()
                    .projectKey(properties.getProjectKey())
                    .applicationName(properties.getApplicationName())
                    .databaseType(detectDatabaseType())
                    .jdbcUrl(detectJdbcUrl())
                    .username(detectUsername())
                    .tables(tables)
                    .relations(relations)
                    .build();

            moeumClient.register(payload);
            log.info("[Moeum] DTMS 등록 완료. 테이블 {}개, 관계 {}개", tables.size(), relations.size());
        } catch (Exception e) {
            log.warn("[Moeum] DTMS 등록 실패 (앱 구동에는 영향 없음): {}", e.getMessage());
        }
    }

    private List<RegisterPayload.TableInfo> collectTableMetadata() throws Exception {
        List<RegisterPayload.TableInfo> tables = new ArrayList<>();

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String catalog = conn.getCatalog();
            String schema = conn.getSchema();

            try (ResultSet tableRs = meta.getTables(catalog, schema, "%", new String[]{"TABLE"})) {
                while (tableRs.next()) {
                    String tableName = tableRs.getString("TABLE_NAME");

                    // 시스템 테이블 제외
                    if (isSystemTable(tableName)) {
                        continue;
                    }

                    Set<String> primaryKeys = getPrimaryKeys(meta, catalog, schema, tableName);
                    List<RegisterPayload.ColumnInfo> columns = getColumns(meta, catalog, schema, tableName, primaryKeys);

                    tables.add(RegisterPayload.TableInfo.builder()
                            .name(tableName)
                            .columns(columns)
                            .build());
                }
            }
        }
        return tables;
    }

    private List<RegisterPayload.RelationInfo> collectRelations(List<RegisterPayload.TableInfo> tables) {
        List<RegisterPayload.RelationInfo> relations = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String catalog = conn.getCatalog();
            String schema = conn.getSchema();

            for (RegisterPayload.TableInfo table : tables) {
                try (ResultSet fkRs = meta.getImportedKeys(catalog, schema, table.getName())) {
                    while (fkRs.next()) {
                        String fromTable = fkRs.getString("FKTABLE_NAME");
                        String toTable = fkRs.getString("PKTABLE_NAME");
                        String key = fromTable + "->" + toTable;
                        if (seen.add(key)) {
                            relations.add(RegisterPayload.RelationInfo.builder()
                                    .fromTable(fromTable)
                                    .toTable(toTable)
                                    .build());
                        }
                    }
                } catch (Exception e) {
                    log.debug("[Moeum] FK 조회 실패: table={}, error={}", table.getName(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("[Moeum] 관계 정보 수집 실패: {}", e.getMessage());
        }
        return relations;
    }

    private Set<String> getPrimaryKeys(DatabaseMetaData meta, String catalog, String schema, String tableName) {
        Set<String> pks = new HashSet<>();
        try (ResultSet pkRs = meta.getPrimaryKeys(catalog, schema, tableName)) {
            while (pkRs.next()) {
                pks.add(pkRs.getString("COLUMN_NAME"));
            }
        } catch (Exception e) {
            log.debug("[Moeum] PK 조회 실패: table={}, error={}", tableName, e.getMessage());
        }
        return pks;
    }

    private List<RegisterPayload.ColumnInfo> getColumns(DatabaseMetaData meta, String catalog,
                                                         String schema, String tableName,
                                                         Set<String> primaryKeys) {
        List<RegisterPayload.ColumnInfo> columns = new ArrayList<>();
        try (ResultSet colRs = meta.getColumns(catalog, schema, tableName, "%")) {
            while (colRs.next()) {
                String colName = colRs.getString("COLUMN_NAME");
                String typeName = colRs.getString("TYPE_NAME");
                int columnSize = colRs.getInt("COLUMN_SIZE");
                boolean nullable = colRs.getInt("NULLABLE") == DatabaseMetaData.columnNullable;

                Integer length = isLengthRelevant(typeName) ? columnSize : null;

                columns.add(RegisterPayload.ColumnInfo.builder()
                        .name(colName)
                        .type(normalizeType(typeName))
                        .length(length)
                        .nullable(nullable)
                        .primaryKey(primaryKeys.contains(colName))
                        .build());
            }
        } catch (Exception e) {
            log.debug("[Moeum] 컬럼 조회 실패: table={}, error={}", tableName, e.getMessage());
        }
        return columns;
    }

    private boolean isLengthRelevant(String typeName) {
        if (typeName == null) return false;
        String upper = typeName.toUpperCase();
        return upper.contains("VARCHAR") || upper.contains("CHAR") || upper.contains("NVARCHAR");
    }

    private String normalizeType(String typeName) {
        if (typeName == null) return "VARCHAR";
        String upper = typeName.toUpperCase();
        if (upper.contains("VARCHAR")) return "VARCHAR";
        if (upper.contains("CHAR")) return "VARCHAR";
        if (upper.contains("TEXT") || upper.contains("CLOB")) return "TEXT";
        if (upper.contains("BIGINT")) return "BIGINT";
        if (upper.contains("INT")) return "INT";
        if (upper.contains("BOOL") || upper.contains("BIT")) return "BOOLEAN";
        if (upper.contains("TIMESTAMP") || upper.contains("DATETIME")) return "TIMESTAMP";
        if (upper.contains("DATE")) return "DATE";
        if (upper.contains("FLOAT") || upper.contains("DOUBLE") || upper.contains("DECIMAL") || upper.contains("NUMERIC")) return "DECIMAL";
        return upper;
    }

    private boolean isSystemTable(String tableName) {
        if (tableName == null) return true;
        String lower = tableName.toLowerCase();
        // H2, MySQL 시스템 테이블 패턴 제외
        return lower.startsWith("sys_") || lower.startsWith("information_schema")
                || lower.equals("flyway_schema_history") || lower.equals("schemata");
    }

    private String detectDatabaseType() {
        try (Connection conn = dataSource.getConnection()) {
            String productName = conn.getMetaData().getDatabaseProductName().toUpperCase();
            if (productName.contains("MYSQL")) return "MYSQL";
            if (productName.contains("POSTGRESQL") || productName.contains("POSTGRES")) return "POSTGRES";
            if (productName.contains("H2")) return "H2";
            return productName;
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    private String detectJdbcUrl() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.getMetaData().getURL();
        } catch (Exception e) {
            return null;
        }
    }

    private String detectUsername() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.getMetaData().getUserName();
        } catch (Exception e) {
            return null;
        }
    }
}
