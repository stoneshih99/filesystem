package com.game.filesystem.service;

import com.game.filesystem.model.BattleRecord;
import com.game.filesystem.config.FileSystemConfig;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * StorageService 的單元測試類
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class StorageServiceTest {
    private StorageService storageService;
    private Path testStoragePath;
    private FileSystemConfig mockConfig;

    @BeforeAll
    void setUp() throws IOException {
        // 建立測試目錄路徑
        testStoragePath = Paths.get("test-storage/battles");
        // 確保測試目錄存在
        Files.createDirectories(testStoragePath);

        // 建立模擬配置
        mockConfig = Mockito.mock(FileSystemConfig.class);
        when(mockConfig.getStoragePath()).thenReturn(testStoragePath.toString());
        when(mockConfig.getMinThreads()).thenReturn(2);
        when(mockConfig.getMaxThreads()).thenReturn(4);
        when(mockConfig.getThreadKeepAliveSeconds()).thenReturn(60);

        // 使用模擬配置建立 StorageService
        storageService = new StorageService(testStoragePath.toString(), mockConfig);
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
    void testSaveAndLoadBattleRecord() throws IOException, ClassNotFoundException {
        // 建立測試記錄
        String battleRecordName = "TestBattleRecord";
        String battleData = "TestBattleData";
        BattleRecord record = new BattleRecord(battleRecordName, battleData.getBytes(), 24);

        // 儲存記錄
        storageService.saveBattleRecord(record);

        // 讀取記錄
        BattleRecord loadedRecord = storageService.loadBattleRecord(record.getId());

        // 驗證
        assertNotNull(loadedRecord);
        assertEquals(battleRecordName, loadedRecord.getBattleRecordName());
        assertArrayEquals(battleData.getBytes(), loadedRecord.getBattleData()); // 修正為 assertArrayEquals
    }

    @Test
    void testLoadAllBattleRecords() throws IOException, ClassNotFoundException {
        // 儲存多個記錄
        int recordCount = 5;
        for (int i = 0; i < recordCount; i++) {
            BattleRecord record = new BattleRecord(
                "BattleRecord" + i,
                    ("BattleData" + i).getBytes(),
                24
            );
            storageService.saveBattleRecord(record);
        }

        // 讀取所有記錄
        List<BattleRecord> records = storageService.loadAllBattleRecords();

        // 驗證
        assertEquals(recordCount, records.size());
        assertTrue(records.stream().anyMatch(r -> r.getBattleRecordName().equals("BattleRecord0")));
        assertTrue(records.stream().anyMatch(r -> r.getBattleRecordName().equals("BattleRecord4")));
    }

    @Test
    void testDeleteExpiredRecords() throws IOException, InterruptedException {
        // 儲存一個即將過期的記錄
        BattleRecord expiredRecord = new BattleRecord("ExpiredRecord", "ExpiredData".getBytes(), 0);
        storageService.saveBattleRecord(expiredRecord);

        // 等待一小段時間確保記錄過期
        Thread.sleep(100);

        // 儲存一個有效的記錄
        BattleRecord validRecord = new BattleRecord("ValidRecord", "ValidData".getBytes(), 24);
        storageService.saveBattleRecord(validRecord);

        // 執行清理
        storageService.deleteExpiredRecords();

        // 驗證
        List<BattleRecord> records = storageService.loadAllBattleRecords();
        assertEquals(1, records.size());
        assertEquals("ValidRecord", records.get(0).getBattleRecordName());
    }

    @Test
    void testLoadBattleRecordsByBattleRecordNames() throws IOException {
        // 儲存多個記錄
        String[] battleRecords = {"Record1", "Record2", "Record3"};
        for (String record : battleRecords) {
            storageService.saveBattleRecord(new BattleRecord(record, ("Data-" + record).getBytes(), 24));
        }

        // 讀取指定記錄
        List<String> targetRecords = Arrays.asList("Record1", "Record3");
        List<BattleRecord> records = storageService.loadBattleRecordsByBattleRecordNames(targetRecords);

        // 驗證
        assertEquals(2, records.size());
        assertTrue(records.stream().allMatch(r -> targetRecords.contains(r.getBattleRecordName())));
    }

    @Test
    void testConcurrentLoading() throws ExecutionException, InterruptedException {
        // 儲存多個記錄
        int recordCount = 10;
        for (int i = 0; i < recordCount; i++) {
            BattleRecord record = new BattleRecord(
                "ConcurrentPlayer" + i,
                    ("ConcurrentData" + i).getBytes(),
                24
            );
            storageService.saveBattleRecord(record);
        }

        // 同時讀取所有記錄
        CompletableFuture<List<BattleRecord>> futureRecords = storageService.loadAllBattleRecordsAsync();
        List<BattleRecord> records = futureRecords.get();

        // 驗證
        assertEquals(recordCount, records.size());
        assertEquals(recordCount, records.stream().distinct().count());
    }

    @AfterAll
    void tearDown() {
        storageService.shutdown();
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
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
} 