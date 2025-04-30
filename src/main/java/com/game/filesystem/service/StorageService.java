package com.game.filesystem.service;

import com.game.filesystem.model.BattleRecord;
import com.game.filesystem.config.FileSystemConfig;
import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 儲存服務類
 * 負責處理戰鬥記錄的檔案讀寫操作
 * 包含儲存、讀取、批次讀取和刪除過期記錄等功能
 */
public class StorageService {
    // 日誌記錄器
    private static final Logger LOGGER = Logger.getLogger(StorageService.class.getName());
    // 儲存目錄的路徑
    private final Path storageDirectory;
    // 用於並行讀取的線程池
    private final ExecutorService executorService;
    // 配置實例
    private final FileSystemConfig config;

    /**
     * 建立儲存服務
     * @param storagePath 儲存目錄的路徑
     * @param config 配置實例
     */
    public StorageService(String storagePath, FileSystemConfig config) {
        this.storageDirectory = Paths.get(storagePath);
        this.config = config;
        
        // 使用配置的執行緒池設定
        ThreadPoolExecutor threadPool = new ThreadPoolExecutor(
            config.getMinThreads(),
            config.getMaxThreads(),
            config.getThreadKeepAliveSeconds(),
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            new ThreadFactory() {
                private int count = 1;
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r);
                    thread.setName("FileLoader-" + count++);
                    return thread;
                }
            }
        );
        
        // 允許核心執行緒超時
        threadPool.allowCoreThreadTimeOut(true);
        // 拒絕策略避免任務爆炸
        threadPool.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        this.executorService = threadPool;
        
        createStorageDirectoryIfNotExists();
    }

    /**
     * 如果儲存目錄不存在，則建立它
     * 如果建立失敗，則拋出運行時異常
     */
    private void createStorageDirectoryIfNotExists() {
        try {
            Files.createDirectories(storageDirectory);
        } catch (IOException e) {
            LOGGER.severe("建立儲存目錄失敗: " + e.getMessage());
            throw new RuntimeException("建立儲存目錄失敗", e);
        }
    }

    /**
     * 壓縮物件
     * @param obj 要壓縮的物件
     * @return 壓縮後的位元組陣列
     */
    private byte[] compressObject(Object obj) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gzos = new GZIPOutputStream(baos);
             ObjectOutputStream oos = new ObjectOutputStream(gzos)) {
            
            oos.writeObject(obj);
            oos.flush();
            gzos.finish();
            return baos.toByteArray();
        }
    }

    /**
     * 解壓縮物件
     * @param compressedData 壓縮的位元組陣列
     * @return 解壓縮後的物件
     */
    private Object decompressObject(byte[] compressedData) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(compressedData);
             GZIPInputStream gzis = new GZIPInputStream(bais);
             ObjectInputStream ois = new ObjectInputStream(gzis)) {
            
            return ois.readObject();
        }
    }

    /**
     * 儲存戰鬥記錄
     * 將記錄序列化並壓縮為二進制檔案
     * @param record 要儲存的戰鬥記錄
     * @return 戰鬥記錄ID
     */
    public String saveBattleRecord(BattleRecord record) {
        String battleRecordName = record.generateFileName();
        Path filePath = storageDirectory.resolve(battleRecordName);
        try {
            // 壓縮記錄
            byte[] compressedData = compressObject(record);
            
            // 寫入檔案
            Files.write(filePath, compressedData);
            LOGGER.fine("戰鬥記錄儲存成功: " + record.getBattleRecordName());
        } catch (IOException e) {
            LOGGER.severe("儲存戰鬥記錄失敗: " + e.getMessage());
            throw new RuntimeException("儲存戰鬥記錄失敗", e);
        }
        return battleRecordName;
    }

    /**
     * 從檔案讀取戰鬥記錄
     * @param file 檔案路徑
     * @return 戰鬥記錄，如果讀取失敗則返回null
     */
    private BattleRecord loadBattleRecordFromFile(Path file) {
        try {
            // 讀取壓縮的檔案內容
            byte[] compressedData = Files.readAllBytes(file);
            
            // 解壓縮並轉換為戰鬥記錄
            return (BattleRecord) decompressObject(compressedData);
        } catch (IOException | ClassNotFoundException e) {
            LOGGER.warning("讀取戰鬥記錄檔案失敗 " + file + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * 異步讀取單個戰鬥記錄
     * @param recordId 戰鬥記錄ID
     * @return 包含戰鬥記錄的 CompletableFuture
     */
    public CompletableFuture<BattleRecord> loadBattleRecordAsync(String recordId) {
        return CompletableFuture.supplyAsync(() -> {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(storageDirectory, recordId + "?*.dat")) {
                Path filePath = stream.iterator().next();
                BattleRecord record = loadBattleRecordFromFile(filePath);
                if (record != null) {
                    LOGGER.fine("戰鬥記錄讀取成功: " + recordId);
                    return record;
                }
                throw new CompletionException("讀取戰鬥記錄失敗", new IOException("無法讀取記錄"));
            } catch (IOException e) {
                LOGGER.severe("讀取戰鬥記錄失敗: " + recordId + ", " + e.getMessage());
                throw new CompletionException("讀取戰鬥記錄失敗", e);
            }
        }, executorService);
    }

    /**
     * 同步讀取單個戰鬥記錄（為了保持向後相容）
     * @param recordId 戰鬥記錄ID
     * @return 戰鬥記錄
     */
    public BattleRecord loadBattleRecord(String recordId) {
        try {
            return loadBattleRecordAsync(recordId).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("讀取戰鬥記錄被中斷", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("讀取戰鬥記錄失敗", e.getCause());
        } catch (TimeoutException e) {
            throw new RuntimeException("讀取戰鬥記錄超時", e);
        }
    }

    /**
     * 異步讀取所有戰鬥記錄
     * @return 包含所有戰鬥記錄的 CompletableFuture
     */
    public CompletableFuture<List<BattleRecord>> loadAllBattleRecordsAsync() {
        try {
            List<Path> files = Files.list(storageDirectory)
                .filter(path -> path.toString().endsWith(".dat"))
                .collect(Collectors.toList());

            if (files.isEmpty()) {
                return CompletableFuture.completedFuture(new ArrayList<>());
            }

            List<CompletableFuture<BattleRecord>> futures = files.stream()
                .map(file -> CompletableFuture.supplyAsync(() -> loadBattleRecordFromFile(file), executorService))
                .collect(Collectors.toList());

            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                    .map(CompletableFuture::join)
                    .filter(record -> record != null && !record.isExpired())
                    .collect(Collectors.toList()));
        } catch (IOException e) {
            LOGGER.severe("讀取儲存目錄失敗: " + e.getMessage());
            return CompletableFuture.failedFuture(new RuntimeException("讀取儲存目錄失敗", e));
        }
    }

    /**
     * 同步讀取所有戰鬥記錄（為了保持向後相容）
     * @return 戰鬥記錄列表
     */
    public List<BattleRecord> loadAllBattleRecords() {
        try {
            return loadAllBattleRecordsAsync().get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("讀取戰鬥記錄失敗", e);
        }
    }

    /**
     * 刪除所有過期的戰鬥記錄
     */
    public void deleteExpiredRecords() {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(storageDirectory, "*.dat")) {
            for (Path file : stream) {
                String fileName = file.getFileName().toString();
                if (BattleRecord.isFileExpired(fileName)) {
                    try {
                        if (Files.deleteIfExists(file)) {
                            LOGGER.fine("成功刪除過期的戰鬥記錄: " + fileName);
                        } else {
                            LOGGER.warning("檔案不存在，無法刪除: " + fileName);
                        }
                    } catch (IOException e) {
                        LOGGER.warning("刪除檔案失敗 " + fileName + ": " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.severe("讀取儲存目錄失敗: " + e.getMessage());
            throw new RuntimeException("讀取儲存目錄失敗", e);
        }
    }

    /**
     * 關閉儲存服務
     * 清理線程池資源
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(config.getThreadKeepAliveSeconds(), TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 異步讀取指定戰鬥記錄名稱的所有戰鬥記錄
     * @param battleRecordNames 戰鬥記錄名稱列表
     * @return 包含所有符合戰鬥記錄名稱的戰鬥記錄的 CompletableFuture
     */
    public CompletableFuture<List<BattleRecord>> loadBattleRecordsByBattleRecordNamesAsync(List<String> battleRecordNames) {
        try {
            List<Path> files = Files.list(storageDirectory)
                .filter(path -> {
                    String fileName = path.getFileName().toString();
                    if (!fileName.endsWith(".dat")) return false;
                    try {
                        BattleRecord record = loadBattleRecordFromFile(path);
                        return record != null && battleRecordNames.contains(record.getBattleRecordName());
                    } catch (Exception e) {
                        LOGGER.warning("讀取檔案失敗: " + fileName + ", " + e.getMessage());
                        return false;
                    }
                })
                .collect(Collectors.toList());

            List<CompletableFuture<BattleRecord>> futures = files.stream()
                .map(file -> CompletableFuture.supplyAsync(() -> loadBattleRecordFromFile(file), executorService))
                .collect(Collectors.toList());

            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                    .map(CompletableFuture::join)
                    .filter(record -> record != null)
                    .collect(Collectors.toList()));
        } catch (IOException e) {
            LOGGER.severe("讀取儲存目錄失敗: " + e.getMessage());
            return CompletableFuture.failedFuture(new RuntimeException("讀取儲存目錄失敗", e));
        }
    }

    /**
     * 同步讀取指定戰鬥記錄名稱的所有戰鬥記錄
     * @param battleRecordNames 戰鬥記錄名稱列表
     * @return 戰鬥記錄列表
     */
    public List<BattleRecord> loadBattleRecordsByBattleRecordNames(List<String> battleRecordNames) {
        try {
            return loadBattleRecordsByBattleRecordNamesAsync(battleRecordNames).get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("讀取戰鬥記錄失敗", e);
        }
    }
} 