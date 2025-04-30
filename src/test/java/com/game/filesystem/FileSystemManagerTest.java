package com.game.filesystem;

import com.game.filesystem.model.BattleRecord;
import com.game.filesystem.config.FileSystemConfig;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * FileSystemManager 的單元測試類
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class FileSystemManagerTest {
    private FileSystemManager fileSystemManager;
    private Path testStoragePath;
    private FileSystemConfig mockConfig;

    @BeforeAll
    void setUp() throws IOException {
        // 建立測試目錄路徑
        testStoragePath = Paths.get("test-storage", "battles").toAbsolutePath();
        // 確保測試目錄存在
        Files.createDirectories(testStoragePath);

        // 建立模擬配置
        mockConfig = Mockito.mock(FileSystemConfig.class);
        when(mockConfig.getStoragePath()).thenReturn(testStoragePath.toString());
        when(mockConfig.getExpirationDays()).thenReturn(7);
        when(mockConfig.isAutoCleanupEnabled()).thenReturn(false);
        when(mockConfig.getCleanupIntervalHours()).thenReturn(1);
        when(mockConfig.getMinThreads()).thenReturn(2);
        when(mockConfig.getMaxThreads()).thenReturn(4);
        when(mockConfig.getThreadKeepAliveSeconds()).thenReturn(60);

        // 使用模擬配置建立 FileSystemManager
        fileSystemManager = new FileSystemManager(mockConfig);
    }

    @BeforeEach
    void clearStorage() throws IOException {
        // 清理測試目錄
        if (Files.exists(testStoragePath)) {
            Files.walk(testStoragePath)
                .sorted((a, b) -> -a.compareTo(b))
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
        }
        // 重新創建測試目錄
        Files.createDirectories(testStoragePath);
    }

    @Test
    void testSaveAndLoadBattleRecord() throws ExecutionException, InterruptedException {
        // 儲存戰鬥記錄
        String battleRecordName = "TestBattleRecord";
        byte[] battleData = "TestBattleData".getBytes();
        fileSystemManager.saveBattleRecord(battleRecordName, battleData);

        // 讀取所有記錄
        CompletableFuture<List<BattleRecord>> futureRecords = fileSystemManager.loadAllBattleRecordsAsync();
        List<BattleRecord> records = futureRecords.get();

        // 驗證
        assertEquals(1, records.size());
        BattleRecord record = records.get(0);
        assertEquals(battleRecordName, record.getBattleRecordName());
        assertArrayEquals(battleData, record.getBattleData());

        // 使用ID讀取記錄
        CompletableFuture<BattleRecord> futureRecord = fileSystemManager.loadBattleRecordAsync(record.getId());
        BattleRecord loadedRecord = futureRecord.get();
        assertEquals(record.getId(), loadedRecord.getId());
    }

    @Test
    void testLoadMultipleRecords() throws ExecutionException, InterruptedException {
        // 儲存多個記錄
        int recordCount = 5;
        for (int i = 0; i < recordCount; i++) {
            final int index = i;
            fileSystemManager.saveBattleRecord(
                    "BattleRecord" + index,
                    ("BattleData" + index).getBytes()
            );
        }

        // 異步讀取所有記錄
        CompletableFuture<List<BattleRecord>> futureRecords = fileSystemManager.loadAllBattleRecordsAsync();
        List<BattleRecord> records = futureRecords.get();

        // 驗證
        assertEquals(recordCount, records.size(), "Record count mismatch");
        for (int i = 0; i < recordCount; i++) {
            final int index = i;
            assertTrue(records.stream()
                            .anyMatch(r -> r.getBattleRecordName().equals("BattleRecord" + index) &&
                                    new String(r.getBattleData()).equals("BattleData" + index)),
                    "Record not found: BattleRecord" + index);
        }
    }

    @Test
    void testRecordExpiration() throws InterruptedException {
        // 儲存一個即將過期的記錄（過期時間為0小時）
        fileSystemManager.saveBattleRecord("ExpiredRecord", "ExpiredData".getBytes(), 0);

        // 等待一小段時間確保記錄過期
        Thread.sleep(200);

        // 儲存一個有效的記錄（24小時過期）
        fileSystemManager.saveBattleRecord("ValidRecord", "ValidData".getBytes(), 24);

        // 手動執行清理並等待完成
        fileSystemManager.cleanupExpiredRecords();
        Thread.sleep(200);

        // 驗證：應該只有一個有效記錄
        List<BattleRecord> records = fileSystemManager.loadAllBattleRecords();
        assertEquals(1, records.size(), "應該只有一個有效記錄");
        assertEquals("ValidRecord", records.get(0).getBattleRecordName(), "有效記錄應該是 ValidRecord");
    }

    @Test
    void testConcurrentOperations() throws ExecutionException, InterruptedException {
        // 創建多個並行的儲存操作
        CompletableFuture<Void>[] saveFutures = new CompletableFuture[5];
        for (int i = 0; i < 5; i++) {
            final int index = i;
            saveFutures[i] = CompletableFuture.runAsync(() ->
                fileSystemManager.saveBattleRecord("ConcurrentRecord" + index, ("ConcurrentData" + index).getBytes())
            );
        }

        // 等待所有儲存操作完成
        CompletableFuture.allOf(saveFutures).get();

        // 讀取所有記錄
        List<BattleRecord> records = fileSystemManager.loadAllBattleRecords();

        // 驗證
        assertEquals(5, records.size());
        assertEquals(5, records.stream().distinct().count());
    }

    @Test
    void testLoadBattleRecordsByBattleRecordNames() throws ExecutionException, InterruptedException {
        // 儲存多個不同戰鬥記錄
        String[] battleRecords = {"Record1", "Record2", "Record3", "Record4"};
        for (String record : battleRecords) {
            fileSystemManager.saveBattleRecord(record, ("BattleData-" + record).getBytes());
        }

        // 指定要讀取的戰鬥記錄名稱
        List<String> targetRecords = List.of("Record1", "Record3");

        // 異步讀取指定戰鬥記錄
        CompletableFuture<List<BattleRecord>> futureRecords = 
            fileSystemManager.loadBattleRecordsByBattleRecordNamesAsync(targetRecords);
        List<BattleRecord> records = futureRecords.get();

        // 驗證
        assertEquals(2, records.size());
        assertTrue(records.stream().allMatch(r -> targetRecords.contains(r.getBattleRecordName())));
        assertTrue(records.stream().anyMatch(r -> r.getBattleRecordName().equals("Record1")));
        assertTrue(records.stream().anyMatch(r -> r.getBattleRecordName().equals("Record3")));
        assertFalse(records.stream().anyMatch(r -> r.getBattleRecordName().equals("Record2")));
        assertFalse(records.stream().anyMatch(r -> r.getBattleRecordName().equals("Record4")));

        // 測試同步方法
        List<BattleRecord> syncRecords = fileSystemManager.loadBattleRecordsByBattleRecordNames(targetRecords);
        assertEquals(2, syncRecords.size());
        assertTrue(syncRecords.stream().allMatch(r -> targetRecords.contains(r.getBattleRecordName())));
    }

    @AfterAll
    void tearDown() {
        fileSystemManager.shutdown();
        try {
            // 清理測試目錄
            if (Files.exists(testStoragePath)) {
                Files.walk(testStoragePath)
                    .sorted((a, b) -> -a.compareTo(b))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
            }
            // 刪除測試目錄的父目錄
            Files.deleteIfExists(testStoragePath.getParent());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
} 