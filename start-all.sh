#!/bin/bash

echo "🚀 啟動新架構的所有服務..."
echo "使用端口號自動生成POD身份，集群間不同配置"

# 檢查Redis是否運行
echo "🔍 檢查Redis狀態..."
if redis-cli ping > /dev/null 2>&1; then
    echo "✅ Redis連接正常"
else
    echo "❌ Redis未運行，請先啟動Redis"
    echo "   brew services start redis@6.2"
    exit 1
fi

# 啟動主反向代理
echo "啟動主反向代理 (端口 8000)..."
cd main-proxy
mvn spring-boot:run &
MAIN_PROXY_PID=$!
cd ..

sleep 5

# 啟動基金代理1
echo "啟動基金代理1 (端口 8081)..."
cd fund-proxy
mvn spring-boot:run -Dspring-boot.run.arguments=--spring.config.additional-location=classpath:application-proxy1.yml &
FUND_PROXY1_PID=$!
cd ..

sleep 3

# 啟動基金代理2
echo "啟動基金代理2 (端口 8082)..."
cd fund-proxy
mvn spring-boot:run -Dspring-boot.run.arguments=--spring.config.additional-location=classpath:application-proxy2.yml &
FUND_PROXY2_PID=$!
cd ..

sleep 3

# 啟動集群1 - POD1（端口7001 → 自動生成pod-1）
echo "啟動集群1 POD1 (端口 7001 → pod-1)..."
cd fund-system
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.config.additional-location=classpath:application-cluster1.properties --server.port=7001" &
FUND_POD1_PID=$!
cd ..

sleep 3

# 啟動集群1 - POD2（端口7002 → 自動生成pod-2）
echo "啟動集群1 POD2 (端口 7002 → pod-2)..."
cd fund-system
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.config.additional-location=classpath:application-cluster1.properties --server.port=7002" &
FUND_POD2_PID=$!
cd ..

sleep 3

# 啟動集群2 - POD3（端口7003 → 自動生成pod-3）
echo "啟動集群2 POD3 (端口 7003 → pod-3)..."
cd fund-system
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.config.additional-location=classpath:application-cluster2.properties --server.port=7003" &
FUND_POD3_PID=$!
cd ..

sleep 3

# 啟動集群2 - POD4（端口7004 → 自動生成pod-4）
echo "啟動集群2 POD4 (端口 7004 → pod-4)..."
cd fund-system
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.config.additional-location=classpath:application-cluster2.properties --server.port=7004" &
FUND_POD4_PID=$!
cd ..

echo ""
echo "✅ 所有服務已啟動！"
echo "================================="
echo "🌐 服務地址："
echo "   主反向代理: http://localhost:8000"
echo "   基金代理1: http://localhost:8081"
echo "   基金代理2: http://localhost:8082"
echo ""
echo "🏗️ POD實例："
echo "   集群1 POD1 (7001→pod-1): http://localhost:7001"
echo "   集群1 POD2 (7002→pod-2): http://localhost:7002"
echo "   集群2 POD3 (7003→pod-3): http://localhost:7003"
echo "   集群2 POD4 (7004→pod-4): http://localhost:7004"
echo ""
echo "🔍 健康檢查："
echo "   curl http://localhost:7001/api/health  # 應該顯示 pod-1, cluster-1"
echo "   curl http://localhost:7002/api/health  # 應該顯示 pod-2, cluster-1"
echo "   curl http://localhost:7003/api/health  # 應該顯示 pod-3, cluster-2"
echo "   curl http://localhost:7004/api/health  # 應該顯示 pod-4, cluster-2"
echo ""
echo "⭐ 特點："
echo "   ✅ 端口號自動生成POD ID (7001→pod-1, 7002→pod-2...)"
echo "   ✅ 集群間不同配置文件 (cluster1.properties, cluster2.properties)"
echo "   ✅ 集群內相同配置，不同端口自動區分POD身份"
echo "   ✅ 無需環境變量，適合CICD部署"

echo ""
echo "🛑 按 Ctrl+C 停止所有服務"

# 設置停止信號處理
trap 'echo ""; echo "🛑 停止所有服務..."; kill $MAIN_PROXY_PID $FUND_PROXY1_PID $FUND_PROXY2_PID $FUND_POD1_PID $FUND_POD2_PID $FUND_POD3_PID $FUND_POD4_PID 2>/dev/null; echo "✅ 已停止所有服務"; exit 0' INT

# 保持運行
wait 