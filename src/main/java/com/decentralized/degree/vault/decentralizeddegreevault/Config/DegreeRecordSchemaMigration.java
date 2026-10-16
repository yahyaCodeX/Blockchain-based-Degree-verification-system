package com.decentralized.degree.vault.decentralizeddegreevault.Config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Aligns the degree_record table with the GUCI primary key model.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(0)
public class DegreeRecordSchemaMigration implements ApplicationRunner {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();

            if (!tableExists(metaData, catalog, "degree_record")) {
                log.info("degree_record table not found; skipping schema migration");
                return;
            }

            ensureColumn(metaData, catalog, "degree_record", "global_credential_id", "VARCHAR(255)");
            ensureColumn(metaData, catalog, "degree_record", "university_code", "VARCHAR(255)");
            ensureColumn(metaData, catalog, "degree_record", "roll_no", "VARCHAR(255)");
            ensureColumn(metaData, catalog, "degree_record", "student_name", "VARCHAR(255)");
            ensureColumn(metaData, catalog, "degree_record", "cgpa", "VARCHAR(255)");
            ensureColumn(metaData, catalog, "degree_record", "data_hash", "VARCHAR(255)");
            ensureColumn(metaData, catalog, "degree_record", "ipfs_cid", "VARCHAR(255)");
            ensureColumn(metaData, catalog, "degree_record", "issuer_address", "VARCHAR(255)");
            ensureColumn(metaData, catalog, "degree_record", "issue_date", "BIGINT");

            jdbcTemplate.update("UPDATE degree_record SET university_code = COALESCE(university_code, 'LEGACY')");

            jdbcTemplate.update(
                    "UPDATE degree_record SET global_credential_id = CONCAT(university_code, '-', roll_no) " +
                    "WHERE (global_credential_id IS NULL OR global_credential_id = '') AND roll_no IS NOT NULL AND roll_no <> ''"
            );

            if (primaryKeyContains(metaData, catalog, "degree_record", "global_credential_id")) {
                log.info("degree_record already uses global_credential_id as primary key");
                return;
            }

            if (hasAnyPrimaryKey(metaData, catalog, "degree_record")) {
                jdbcTemplate.update("ALTER TABLE degree_record DROP PRIMARY KEY");
            }

            jdbcTemplate.update("UPDATE degree_record SET global_credential_id = CONCAT('LEGACY-', UUID()) WHERE global_credential_id IS NULL OR global_credential_id = ''");
            jdbcTemplate.update("ALTER TABLE degree_record MODIFY global_credential_id VARCHAR(255) NOT NULL");
            jdbcTemplate.update("ALTER TABLE degree_record ADD PRIMARY KEY (global_credential_id)");

            log.info("Migrated degree_record table to global_credential_id primary key");
        }
    }

    private void ensureColumn(DatabaseMetaData metaData, String catalog, String tableName, String columnName, String sqlType)
            throws SQLException {
        if (!columnExists(metaData, catalog, tableName, columnName)) {
            jdbcTemplate.update("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + sqlType);
        }
    }

    private boolean tableExists(DatabaseMetaData metaData, String catalog, String tableName) throws SQLException {
        try (ResultSet tables = metaData.getTables(catalog, null, tableName, null)) {
            return tables.next();
        }
    }

    private boolean columnExists(DatabaseMetaData metaData, String catalog, String tableName, String columnName)
            throws SQLException {
        try (ResultSet columns = metaData.getColumns(catalog, null, tableName, columnName)) {
            return columns.next();
        }
    }

    private boolean primaryKeyContains(DatabaseMetaData metaData, String catalog, String tableName, String columnName)
            throws SQLException {
        try (ResultSet primaryKeys = metaData.getPrimaryKeys(catalog, null, tableName)) {
            while (primaryKeys.next()) {
                if (columnName.equalsIgnoreCase(primaryKeys.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasAnyPrimaryKey(DatabaseMetaData metaData, String catalog, String tableName) throws SQLException {
        try (ResultSet primaryKeys = metaData.getPrimaryKeys(catalog, null, tableName)) {
            return primaryKeys.next();
        }
    }
}
