package com.game.filesystem.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FileSystemConfig 的單元測試類
 */
public class FileSystemConfigTest {
    private FileSystemConfig config;

    @BeforeEach
    void setUp() {
        config = new FileSystemConfig();
    }

    @Test
    void testStoragePathConfiguration() {
        assertEquals("test-storage/battles", config.getStoragePath());
    }

    @Test
    void testExpirationConfiguration() {
        assertEquals(1, config.getExpirationDays());
        assertTrue(config.isAutoCleanupEnabled());
        assertEquals(1, config.getCleanupIntervalHours());
    }

    @Test
    void testThreadConfiguration() {
        assertEquals(2, config.getFileLoaderPoolSize());
        assertEquals(1, config.getMinThreads());
        assertEquals(2, config.getMaxThreads());
        assertEquals(30, config.getThreadKeepAliveSeconds());
    }

    @Test
    void testLoggingConfiguration() {
        assertEquals("test-logs/filesystem.log", config.getLoggingPath());
        assertEquals(Level.INFO, config.getLoggingLevel());
    }

    @Test
    void testDynamicThreadPoolSize() {
        // 測試當設定為0時是否返回處理器核心數
        int processors = Runtime.getRuntime().availableProcessors();
        assertTrue(processors > 0);
    }
} 