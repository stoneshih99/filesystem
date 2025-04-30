package com.game.filesystem;

import com.game.filesystem.model.BattleRecord;
import com.game.filesystem.service.StorageService;
import com.game.filesystem.config.FileSystemConfig;

import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * 檔案系統管理器
 * 提供高層級的檔案系統管理介面，負責協調儲存服務和定期清理任務
 */
public class FileSystemManager {
    // 日誌記錄器
    private static final Logger LOGGER = Logger.getLogger(FileSystemManager.class.getName());
    // 儲存服務實例
    private final StorageService storageService;
    // 定時任務執行器
    private final ScheduledExecutorService scheduler;
    // 配置實例
    private final FileSystemConfig config;

    /**
     * 建立檔案系統管理器
     */
    public FileSystemManager() {
        this(new FileSystemConfig());
    }

    /**
     * 建立檔案系統管理器
     * @param config 配置實例
     */
    public FileSystemManager(FileSystemConfig config) {
        this.config = config;
        this.storageService = new StorageService(config.getStoragePath(), config);
        this.scheduler = Executors.newScheduledThreadPool(1);
        if (config.isAutoCleanupEnabled()) {
            startCleanupTask();
        }
    }

    /**
     * 啟動定期清理任務
     * 根據配置的間隔時間執行清理過期的戰鬥記錄
     */
    private void startCleanupTask() {
        // 立即執行一次清理
        try {
            LOGGER.info("執行初始清理");
            storageService.deleteExpiredRecords();
        } catch (Exception e) {
            LOGGER.severe("初始清理失敗: " + e.getMessage());
        }

        // 設定定期清理任務
        scheduler.scheduleAtFixedRate(() -> {
            try {
                LOGGER.info("開始清理過期的戰鬥記錄");
                storageService.deleteExpiredRecords();
            } catch (Exception e) {
                LOGGER.severe("清理過期記錄失敗: " + e.getMessage());
            }
        }, config.getCleanupIntervalHours(), config.getCleanupIntervalHours(), TimeUnit.HOURS);
    }

    /**
     * 手動執行清理任務
     */
    public void cleanupExpiredRecords() {
        try {
            LOGGER.info("手動執行清理");
            storageService.deleteExpiredRecords();
        } catch (Exception e) {
            LOGGER.severe("手動清理失敗: " + e.getMessage());
            throw new RuntimeException("手動清理失敗", e);
        }
    }

    /**
     * 儲存戰鬥記錄
     * 使用配置中設定的過期時間
     * @param battleRecordName 戰鬥記錄名稱
     * @param battleData 戰鬥數據
     * @return 戰鬥記錄ID
     */
    public String saveBattleRecord(String battleRecordName, byte[] battleData) {
        // 將天數轉換為小時
        long expirationTimeInHours = config.getExpirationDays() * 24L;
        BattleRecord record = new BattleRecord(battleRecordName, battleData, expirationTimeInHours);
        return storageService.saveBattleRecord(record);
    }

    /**
     * 儲存戰鬥記錄（帶有指定的過期時間）
     * @param battleRecordName 戰鬥記錄名稱
     * @param battleData 戰鬥數據
     * @param expirationTimeInHours 過期時間（小時）
     * @return 戰鬥記錄ID
     */
    public String saveBattleRecord(String battleRecordName, byte[] battleData, long expirationTimeInHours) {
        BattleRecord record = new BattleRecord(battleRecordName, battleData, expirationTimeInHours);
        return storageService.saveBattleRecord(record);
    }

    /**
     * 異步讀取指定ID的戰鬥記錄
     * @param recordId 戰鬥記錄ID
     * @return 包含戰鬥記錄的 CompletableFuture
     */
    public CompletableFuture<BattleRecord> loadBattleRecordAsync(String recordId) {
        return storageService.loadBattleRecordAsync(recordId);
    }

    /**
     * 同步讀取指定ID的戰鬥記錄（為了保持向後相容）
     * @param recordId 戰鬥記錄ID
     * @return 戰鬥記錄物件
     */
    public BattleRecord loadBattleRecord(String recordId) {
        return storageService.loadBattleRecord(recordId);
    }

    /**
     * 異步讀取所有戰鬥記錄
     * @return 包含所有戰鬥記錄的 CompletableFuture
     */
    public CompletableFuture<List<BattleRecord>> loadAllBattleRecordsAsync() {
        return storageService.loadAllBattleRecordsAsync();
    }

    /**
     * 同步讀取所有戰鬥記錄（為了保持向後相容）
     * @return 戰鬥記錄列表
     */
    public List<BattleRecord> loadAllBattleRecords() {
        return storageService.loadAllBattleRecords();
    }

    /**
     * 異步讀取指定戰鬥記錄名稱的所有戰鬥記錄
     * @param battleRecordNames 戰鬥記錄名稱列表
     * @return 包含所有符合戰鬥記錄名稱的戰鬥記錄的 CompletableFuture
     */
    public CompletableFuture<List<BattleRecord>> loadBattleRecordsByBattleRecordNamesAsync(List<String> battleRecordNames) {
        return storageService.loadBattleRecordsByBattleRecordNamesAsync(battleRecordNames);
    }

    /**
     * 同步讀取指定戰鬥記錄名稱的所有戰鬥記錄
     * @param battleRecordNames 戰鬥記錄名稱列表
     * @return 戰鬥記錄列表
     */
    public List<BattleRecord> loadBattleRecordsByBattleRecordNames(List<String> battleRecordNames) {
        return storageService.loadBattleRecordsByBattleRecordNames(battleRecordNames);
    }

    /**
     * 關閉檔案系統管理器
     * 停止定時清理任務並等待其完成
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        // 關閉儲存服務
        storageService.shutdown();
    }

    /**
     * 檢查檔案系統管理器是否已關閉
     * @return true 如果已關閉，否則 false
     */
    public boolean isShutdown() {
        return scheduler.isShutdown();
    }
} 