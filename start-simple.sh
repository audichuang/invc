#!/bin/bash

echo "🚀 啟動基金系統 - 簡化版"
echo "========================="

# 檢查Redis是否運行
echo "🔍 檢查Redis狀態..."
if redis-cli ping > /dev/null 2>&1; then
    echo "✅ Redis連接正常"
else
    echo "❌ Redis未運行，請先啟動Redis"
    echo "   brew services start redis@6.2"
    echo "   或"
    echo "   redis-server"
    exit 1
fi

# 檢查Redis版本（可選）
REDIS_VERSION=$(redis-cli INFO server | grep "redis_version:" | cut -d: -f2 | tr -d '\r')
echo "📋 Redis版本: $REDIS_VERSION"

echo ""
echo "🏗️ 啟動基金系統..."

# 啟動基金系統（單體模式）
cd fund-system
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=7001" &
FUND_SYSTEM_PID=$!

echo "⏳ 等待服務啟動..."
sleep 10

# 檢查服務是否啟動成功
if curl -s http://localhost:7001/api/health > /dev/null; then
    echo "✅ 基金系統啟動成功！"
    echo ""
    echo "🌐 服務地址："
    echo "   基金系統: http://localhost:7001"
    echo "   健康檢查: http://localhost:7001/api/health"
    echo "   前端地址: http://localhost:4200"
    echo ""
    echo "📝 使用說明："
    echo "   1. 前端會自動連接到 http://localhost:7001"
    echo "   2. SSE事件推送通過Redis Pub/Sub實現"
    echo "   3. 支援Redis 4.x/5.x/6.x，包括哨兵模式"
    echo ""
    echo "⭐ 特性："
    echo "   ✅ 實時事件推送 (SSE)"
    echo "   ✅ Redis事件廣播"
    echo "   ✅ 自動故障恢復"
    echo "   ✅ 跨瀏覽器支援"
else
    echo "❌ 基金系統啟動失敗，請檢查日誌"
    kill $FUND_SYSTEM_PID 2>/dev/null
    exit 1
fi

echo ""
echo "🛑 按 Ctrl+C 停止服務"

# 設置停止信號處理
trap 'echo ""; echo "🛑 停止所有服務..."; kill $FUND_SYSTEM_PID 2>/dev/null; echo "✅ 已停止所有服務"; exit 0' INT

# 保持運行
wait $FUND_SYSTEM_PID 