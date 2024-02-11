package com.renomad.inmra.migrations;

import com.renomad.inmra.utils.IFileUtils;
import com.renomad.inmra.utils.MemoriaContext;
import com.renomad.minum.Constants;
import com.renomad.minum.Context;
import com.renomad.minum.logging.ILogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class DatabaseMigration {
    private final ILogger logger;
    private final Constants constants;
    private final List<String> migrations;
    final Path migrationsRecords;
    private final IFileUtils fileUtils;
    private final Migration1 migration1;
    private final Migration2 migration2;
    private final Migration3 migration3;
    private final Migration4 migration4;
    private final Migration5 migration5;
    private final Migration6 migration6;
    private final Migration7 migration7;
    private final Migration8 migration8;
    private final Migration9 migration9;
    private final Migration10 migration10;
    private final Migration11 migration11;
    private final Migration12 migration12;
    private final Migration13 migration13;
    private final Migration14 migration14;

    public DatabaseMigration(Context context, MemoriaContext memoriaContext) {
        this.logger = context.getLogger();
        this.fileUtils = memoriaContext.fileUtils();
        this.constants = context.getConstants();
        Path dbDirectory = Path.of(constants.DB_DIRECTORY);
        migrationsRecords = dbDirectory.resolve("migrations.ddps");
        migrations = determineFinishedMigrations();
        this.migration1 = new Migration1(dbDirectory, logger);
        this.migration2 = new Migration2(dbDirectory, logger);
        this.migration3 = new Migration3(dbDirectory, logger);
        this.migration4 = new Migration4(dbDirectory, logger);
        this.migration5 = new Migration5(dbDirectory, logger);
        this.migration6 = new Migration6(dbDirectory, logger);
        this.migration7 = new Migration7(dbDirectory, logger, context);
        this.migration8 = new Migration8(dbDirectory, logger);
        this.migration9 = new Migration9(dbDirectory, logger);
        this.migration10 = new Migration10(dbDirectory, logger);
        this.migration11 = new Migration11(dbDirectory, logger);
        this.migration12 = new Migration12(dbDirectory, logger);
        this.migration13 = new Migration13(dbDirectory, logger);
        this.migration14 = new Migration14(dbDirectory, logger, context);
    }

    /**
     * Here is where we add new migrations to be run.
     */
    public void migrate() throws IOException {
        if (needsToRun("migration1")) {
            migration1.run();
            Files.writeString(migrationsRecords, "migration1", StandardOpenOption.APPEND);
        }

        if (needsToRun("migration2")) {
            migration2.run();
            Files.writeString(migrationsRecords, "migration1\nmigration2\n", StandardOpenOption.TRUNCATE_EXISTING);
        }

        if (needsToRun("migration3")) {
            migration3.run();
            Files.writeString(migrationsRecords, "migration3\n", StandardOpenOption.APPEND);
        }

        if (needsToRun("migration4")) {
            migration4.run();
            Files.writeString(migrationsRecords, "migration4\n", StandardOpenOption.APPEND);
        }
        if (needsToRun("migration5")) {
            migration5.run();
            Files.writeString(migrationsRecords, "migration5\n", StandardOpenOption.APPEND);
        }
        if (needsToRun("migration6")) {
            migration6.run();
            Files.writeString(migrationsRecords, "migration6\n", StandardOpenOption.APPEND);
        }
        if (needsToRun("migration7")) {
            migration7.run();
            Files.writeString(migrationsRecords, "migration7\n", StandardOpenOption.APPEND);
        }
        if (needsToRun("migration8")) {
            migration8.run();
            Files.writeString(migrationsRecords, "migration8\n", StandardOpenOption.APPEND);
        }
        if (needsToRun("migration9")) {
            migration9.run();
            Files.writeString(migrationsRecords, "migration9\n", StandardOpenOption.APPEND);
        }
        if (needsToRun("migration10")) {
            migration10.run();
            Files.writeString(migrationsRecords, "migration10\n", StandardOpenOption.APPEND);
        }
        if (needsToRun("migration11")) {
            migration11.run();
            Files.writeString(migrationsRecords, "migration11\n", StandardOpenOption.APPEND);
        }
        if (needsToRun("migration12")) {
            migration12.run();
            Files.writeString(migrationsRecords, "migration12\n", StandardOpenOption.APPEND);
        }
        if (needsToRun("migration13")) {
            migration13.run();
            Files.writeString(migrationsRecords, "migration13\n", StandardOpenOption.APPEND);
        }
        if (needsToRun("migration14")) {
            migration14.run();
            Files.writeString(migrationsRecords, "migration14\n", StandardOpenOption.APPEND);
        }
    }

    /**
     * Determine which migrations have already run
     */
    private List<String> determineFinishedMigrations() {
        final List<String> migrations;
        try {
            if (!Files.exists(migrationsRecords)) {
                fileUtils.makeDirectory(Path.of(constants.DB_DIRECTORY));
                Files.createFile(migrationsRecords);
                migrations = new ArrayList<>();
            } else {
                migrations = Files.readAllLines(migrationsRecords);
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        return migrations;
    }

    private boolean needsToRun(String migrationName) {
        var shouldRun = ! migrations.contains(migrationName);
        if (!shouldRun) {
            logger.logDebug(() -> "already ran " + migrationName + ". skipping migration");
        }
        return shouldRun;
    }

}
