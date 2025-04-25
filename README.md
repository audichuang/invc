# 基金債券處理系統

本系統包含基金處理系統和債券處理系統，並通過反向代理實現負載均衡和高可用性。

## 系統架構

### 前端

- Angular 應用，顯示基金和債券表格
- 支持多選項目並發送處理請求
- 實時進度展示彈窗

### 基金系統

- Spring Boot 應用
- 處理基金相關請求
- 通過 SSE 推送實時進度更新

### 債券系統

- Spring Boot 應用 (兩個實例，端口 9092 和 9093)
- 使用 Java 8 運行
- 處理債券相關請求
- 通過 SSE 推送實時進度更新
- 由專用反向代理提供負載均衡

### 反向代理

- Spring Cloud Gateway 應用 (端口 8081)
- 使用 Java 8 運行
- 提供債券系統的負載均衡
- 實現 SSE 連接的粘性會話
- 支持 CORS 和其他 HTTP 標頭處理

## 目錄結構

```
/
├── frontend/               # 前端Angular應用
├── back/                   # 基金系統後端
├── bond-system/            # 債券系統後端 (Java 8)
│   ├── src/                # 源代碼
│   └── pom.xml             # Maven配置
├── bond-proxy/             # 債券系統反向代理 (Java 8)
│   ├── src/                # 源代碼
│   └── pom.xml             # Maven配置
└── run-bond-system.sh      # 債券系統啟動腳本
```

## 如何啟動系統

### 啟動債券系統和反向代理

1. 確保已安裝 JDK 8 和 Maven
2. 執行啟動腳本：

```bash
chmod +x run-bond-system.sh
./run-bond-system.sh
```

這將啟動:

- 債券系統實例 1 (端口 9092)
- 債券系統實例 2 (端口 9093)
- 反向代理 (端口 8081)

### 啟動基金系統

```bash
cd back
mvn spring-boot:run
```

### 啟動前端

```bash
cd frontend
npm install
npm start
```

## 訪問系統

- 前端: http://localhost:4200
- 基金系統 API: http://localhost:8080/api
- 債券系統 API: http://localhost:8081/api (通過反向代理)
