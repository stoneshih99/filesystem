# Game Battle Record File System

這是一個用於管理遊戲戰鬥記錄的檔案系統，提供高效能的儲存、讀取和管理功能。

## 功能特點

- 支援非同步和同步操作
- 自動壓縮儲存以節省空間
- 自動過期管理
- 人性化的檔案命名格式
- 批次讀取優化
- 並行處理支援

## 檔案格式

### 檔案命名規則
檔案名稱格式為：`{recordId}_{expirationDateTime}.dat`
- `recordId`: UUID 格式的唯一識別碼
- `expirationDateTime`: 過期時間，格式為 `yyyy-MM-dd_HH-mm-ss`
- `.dat`: 檔案副檔名

例如：`123e4567-e89b-12d3-a456-426614174000_2024-04-16_12-00-00.dat`

### 檔案內容
- 使用 GZIP 壓縮
- 序列化的 BattleRecord 物件
- 包含戰鬥記錄名稱、數據和過期時間等資訊

## 主要類別

### BattleRecord
戰鬥記錄的核心類別，包含：
- 唯一識別碼 (UUID)
- 建立時間戳記
- 戰鬥記錄名稱
- 戰鬥數據
- 過期時間

### StorageService
提供檔案系統的核心服務：
- 檔案的壓縮儲存和解壓縮讀取
- 過期檔案的自動清理
- 批次讀取優化
- 並行處理支援

### FileSystemManager
檔案系統的管理介面，提供：
- 高階檔案操作 API
- 自動清理機制
- 錯誤處理和日誌記錄

## 使用方法

### 建立戰鬥記錄
```
FileSystemManager manager = new FileSystemManager();
manager.saveBattleRecord("MyBattle", battleData, 24); // 24小時後過期
```

### 讀取戰鬥記錄
```
// 非同步讀取
CompletableFuture<BattleRecord> future = manager.loadBattleRecordAsync("recordId");
BattleRecord record = future.get();

// 同步讀取
BattleRecord record = manager.loadBattleRecord("recordId");
```

### 批次讀取
```
List<String> recordNames = Arrays.asList("Battle1", "Battle2");
List<BattleRecord> records = manager.loadBattleRecordsByBattleRecordNames(recordNames);
```

### 清理過期記錄
```
manager.cleanupExpiredRecords();
```

## 錯誤處理

系統提供完整的錯誤處理機制：
- 檔案不存在處理
- 壓縮/解壓縮錯誤處理
- 序列化/反序列化錯誤處理
- 過期檢查錯誤處理

## 效能考量

- 使用 GZIP 壓縮減少儲存空間
- 非同步操作提高效能
- 批次讀取優化
- 智慧型過期檢查（從檔名判斷）
- 並行處理支援

## 相依性

- Java 8 或更高版本
- 不需要外部資料庫
- 不需要額外的函式庫

## 注意事項

1. 檔案名稱包含過期時間，請勿手動修改
2. 系統會自動處理檔案的壓縮和解壓縮
3. 過期的檔案會在下次清理時自動刪除
4. 建議定期執行清理作業以釋放空間

