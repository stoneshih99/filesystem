package com.game.filesystem.config;

import org.yaml.snakeyaml.Yaml;
import java.io.InputStream;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 檔案系統配置類
 * 負責讀取和管理配置文件
 */
public class FileSystemConfig {
    private static final Logger LOGGER = Logger.getLogger(FileSystemConfig.class.getName());
    private final Map<String, Object> config;

    /**
     * 建立配置類實例並讀取配置文件
     */
    public FileSystemConfig() {
        this.config = loadConfig();
    }

    /**
     * 讀取配置文件
     * @return 配置映射
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> loadConfig() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.yml")) {
            if (input == null) {
                LOGGER.severe("找不到配置文件 config.yml");
                throw new RuntimeException("找不到配置文件 config.yml");
            }
            return new Yaml().load(input);
        } catch (Exception e) {
            LOGGER.severe("讀取配置文件失敗: " + e.getMessage());
            throw new RuntimeException("讀取配置文件失敗", e);
        }
    }

    /**
     * 獲取儲存路徑
     * @return 儲存路徑
     */
    @SuppressWarnings("unchecked")
    public String getStoragePath() {
        Map<String, Object> storage = (Map<String, Object>) config.get("storage");
        return (String) storage.get("path");
    }

    /**
     * 獲取檔案過期天數
     * @return 過期天數
     */
    @SuppressWarnings("unchecked")
    public int getExpirationDays() {
        Map<String, Object> storage = (Map<String, Object>) config.get("storage");
        Map<String, Object> expiration = (Map<String, Object>) storage.get("expiration");
        return (Integer) expiration.get("days");
    }

    /**
     * 檢查是否啟用自動清理
     * @return 是否啟用自動清理
     */
    @SuppressWarnings("unchecked")
    public boolean isAutoCleanupEnabled() {
        Map<String, Object> storage = (Map<String, Object>) config.get("storage");
        Map<String, Object> expiration = (Map<String, Object>) storage.get("expiration");
        return (Boolean) expiration.get("enableAutoCleanup");
    }

    /**
     * 獲取清理間隔（小時）
     * @return 清理間隔
     */
    @SuppressWarnings("unchecked")
    public int getCleanupIntervalHours() {
        Map<String, Object> storage = (Map<String, Object>) config.get("storage");
        Map<String, Object> expiration = (Map<String, Object>) storage.get("expiration");
        return (Integer) expiration.get("cleanupIntervalHours");
    }

    /**
     * 獲取檔案讀取執行緒池大小
     * 如果配置值小於等於0，則返回處理器核心數
     * @return 執行緒池大小
     */
    @SuppressWarnings("unchecked")
    public int getFileLoaderPoolSize() {
        Map<String, Object> thread = (Map<String, Object>) config.get("thread");
        int configuredSize = (Integer) thread.get("fileLoaderPoolSize");
        return configuredSize <= 0 ? Runtime.getRuntime().availableProcessors() : configuredSize;
    }

    /**
     * 獲取最小執行緒數
     * @return 最小執行緒數
     */
    @SuppressWarnings("unchecked")
    public int getMinThreads() {
        Map<String, Object> thread = (Map<String, Object>) config.get("thread");
        return (Integer) thread.get("minThreads");
    }

    /**
     * 獲取最大執行緒數
     * 如果配置值小於等於0，則返回處理器核心數
     * @return 最大執行緒數
     */
    @SuppressWarnings("unchecked")
    public int getMaxThreads() {
        Map<String, Object> thread = (Map<String, Object>) config.get("thread");
        int configuredSize = (Integer) thread.get("maxThreads");
        return configuredSize <= 0 ? Runtime.getRuntime().availableProcessors() : configuredSize;
    }

    /**
     * 獲取執行緒閒置超時時間（秒）
     * @return 超時時間
     */
    @SuppressWarnings("unchecked")
    public int getThreadKeepAliveSeconds() {
        Map<String, Object> thread = (Map<String, Object>) config.get("thread");
        return (Integer) thread.get("keepAliveSeconds");
    }

    /**
     * 獲取日誌等級
     * @return 日誌等級
     */
    @SuppressWarnings("unchecked")
    public Level getLoggingLevel() {
        Map<String, Object> logging = (Map<String, Object>) config.get("logging");
        String level = (String) logging.get("level");
        return Level.parse(level);
    }

    /**
     * 獲取日誌檔案路徑
     * @return 日誌檔案路徑
     */
    @SuppressWarnings("unchecked")
    public String getLoggingPath() {
        Map<String, Object> logging = (Map<String, Object>) config.get("logging");
        return (String) logging.get("path");
    }
} 