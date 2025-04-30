package com.game.filesystem.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.ZoneId;
import java.util.UUID;

/**
 * 戰鬥記錄實體類
 * 用於儲存戰鬥相關的資料，包含戰鬥ID、時間戳記、玩家名稱、戰鬥數據和過期時間
 * 實現 Serializable 介面以支援二進制序列化
 */
public class BattleRecord implements Serializable {
    /**
     * 序列化版本ID，用於確保序列化的相容性
     */
    private static final long serialVersionUID = 1L;
    
    /**
     * 日期時間格式
     */
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    
    /**
     * 戰鬥記錄的唯一識別碼
     */
    private final String id;
    /**
     * 戰鬥記錄的建立時間
     */
    private final LocalDateTime timestamp;
    /**
     * 戰鬥記錄名稱
     */
    private final String battleRecordName;
    /**
     * 戰鬥數據
     */
    private final byte[] battleData;
    /**
     * 記錄的過期時間（以毫秒為單位的時間戳記）
     */
    private final long expirationTime;

    /**
     * 建立新的戰鬥記錄
     * @param battleRecordName 戰鬥記錄名稱
     * @param battleData 戰鬥數據
     * @param expirationTimeInHours 過期時間（小時）
     */
    public BattleRecord(String battleRecordName, byte[] battleData, long expirationTimeInHours) {
        this.id = UUID.randomUUID().toString();
        this.timestamp = LocalDateTime.now();
        this.battleRecordName = battleRecordName;
        this.battleData = battleData;
        this.expirationTime = expirationTimeInHours <= 0 ?
            System.currentTimeMillis() - 1 : // 確保立即過期
            System.currentTimeMillis() + (expirationTimeInHours * 3600000L);
    }

    /**
     * 獲取戰鬥記錄ID
     * @return 戰鬥記錄的唯一識別碼
     */
    public String getId() {
        return id;
    }

    /**
     * 獲取戰鬥記錄的建立時間
     * @return 時間戳記
     */
    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    /**
     * 獲取戰鬥記錄名稱
     * @return 戰鬥記錄名稱
     */
    public String getBattleRecordName() {
        return battleRecordName;
    }

    /**
     * 獲取戰鬥數據
     * @return 戰鬥數據字串
     */
    public byte[] getBattleData() {
        return battleData;
    }

    /**
     * 獲取過期時間
     * @return 過期時間的時間戳記
     */
    public long getExpirationTime() {
        return expirationTime;
    }

    /**
     * 檢查記錄是否已過期
     * @return 如果記錄已過期返回true，否則返回false
     */
    public boolean isExpired() {
        return System.currentTimeMillis() >= expirationTime;
    }

    /**
     * 生成檔案名稱
     * 格式：id_expirationDateTime.dat
     * 其中 expirationDateTime 格式為 yyyy-MM-dd_HH-mm-ss
     * @return 檔案名稱
     */
    public String generateFileName() {
        LocalDateTime expirationDateTime = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(expirationTime), 
            ZoneId.systemDefault()
        );
        return String.format("%s?%s.dat", id, expirationDateTime.format(DATE_TIME_FORMATTER));
    }

    /**
     * 從檔案名稱解析過期時間
     * @param fileName 檔案名稱
     * @return 過期時間（毫秒）
     */
    public static long parseExpirationTimeFromFileName(String fileName) {
        try {
            String timeStr = fileName.substring(fileName.lastIndexOf('&') + 1, fileName.lastIndexOf('.'));
            try {
                // 嘗試補齊時間格式，例如 12-1 -> 12-01
                String[] timeParts = timeStr.split("-");
                if (timeParts.length == 3) {
                    if (timeParts[1].length() == 1) timeParts[1] = "0" + timeParts[1]; // 補齊分鐘
                    if (timeParts[2].length() == 1) timeParts[2] = "0" + timeParts[2]; // 補齊秒數
                    timeStr = String.join("-", timeParts);
                }
                // 嘗試解析為日期時間格式
                LocalDateTime expirationDateTime = LocalDateTime.parse(timeStr, DATE_TIME_FORMATTER);
                return expirationDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            } catch (DateTimeParseException e) {
                // 如果解析失敗，可能是舊格式的時間戳
                return Long.parseLong(timeStr);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("無效的檔案名稱格式: " + fileName + ", 錯誤: " + e.getMessage());
        }
    }

    /**
     * 從檔案名稱解析戰鬥記錄ID
     * @param fileName 檔案名稱
     * @return 戰鬥記錄ID
     */
    public static String parseBattleRecordIdFromFileName(String fileName) {
        try {
            return fileName.substring(0, fileName.lastIndexOf('_'));
        } catch (Exception e) {
            throw new IllegalArgumentException("無效的檔案名稱格式: " + fileName + ", 錯誤: " + e.getMessage());
        }
    }

    /**
     * 檢查檔案是否已過期
     * @param fileName 檔案名稱
     * @return 如果檔案已過期返回true，否則返回false
     */
    public static boolean isFileExpired(String fileName) {
        try {
            long expirationTime = parseExpirationTimeFromFileName(fileName);
            return System.currentTimeMillis() >= expirationTime;
        } catch (Exception e) {
            // 如果解析失敗，記錄錯誤並當作未過期處理
            System.err.println("檢查檔案過期時發生錯誤: " + fileName + ", 錯誤: " + e.getMessage());
            return false;
        }
    }
} 