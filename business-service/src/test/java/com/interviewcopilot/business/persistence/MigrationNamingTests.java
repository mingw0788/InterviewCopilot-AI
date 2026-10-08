package com.interviewcopilot.business.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationNamingTests {

    private static final Pattern VERSIONED_MIGRATION =
            Pattern.compile("^V([1-9][0-9]*)__[a-z][a-z0-9_]*\\.sql$");

    @Test
    void migrationsHaveValidNamesAndUniqueVersions() throws IOException {
        Resource[] migrations = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:db/migration/*.sql");

        assertTrue(migrations.length > 0, "At least one migration is required");
        Set<String> versions = new HashSet<>();
        for (Resource migration : migrations) {
            String filename = migration.getFilename();
            assertNotNull(filename);
            Matcher matcher = VERSIONED_MIGRATION.matcher(filename);
            assertTrue(matcher.matches(), "Invalid migration filename: " + filename);
            assertTrue(versions.add(matcher.group(1)), "Duplicate migration version: " + filename);
        }
        assertTrue(versions.contains("1"), "The initial schema must be version 1");
    }
}
