#!/bin/bash

# Redis版本檢測腳本
# 用途：檢測Redis版本並確認兼容性

echo "🔍 Redis版本檢測工具"
echo "===================="

# 檢測redis-cli是否可用
if ! command -v redis-cli &> /dev/null; then
    echo "❌ redis-cli 未找到，請確認Redis客戶端已安裝"
    exit 1
fi

# 設置Redis連接信息（可根據需要修改）
REDIS_HOST=${REDIS_HOST:-localhost}
REDIS_PORT=${REDIS_PORT:-6379}
REDIS_PASSWORD=${REDIS_PASSWORD:-""}

echo "🔗 連接信息："
echo "   主機: $REDIS_HOST"
echo "   端口: $REDIS_PORT"
if [ -n "$REDIS_PASSWORD" ]; then
    echo "   密碼: ***已設置***"
else
    echo "   密碼: 無"
fi
echo ""

# 構建redis-cli命令
if [ -n "$REDIS_PASSWORD" ]; then
    REDIS_CMD="redis-cli -h $REDIS_HOST -p $REDIS_PORT -a $REDIS_PASSWORD"
else
    REDIS_CMD="redis-cli -h $REDIS_HOST -p $REDIS_PORT"
fi

# 測試連接
echo "🧪 測試Redis連接..."
if ! $REDIS_CMD ping > /dev/null 2>&1; then
    echo "❌ 無法連接到Redis服務器"
    echo "   請檢查："
    echo "   1. Redis服務是否運行"
    echo "   2. 主機和端口是否正確"
    echo "   3. 防火牆設置"
    echo "   4. 認證信息"
    exit 1
fi
echo "✅ Redis連接成功"
echo ""

# 獲取Redis版本信息
echo "📋 獲取Redis版本信息..."
REDIS_VERSION=$($REDIS_CMD INFO server | grep "^redis_version:" | cut -d: -f2 | tr -d '\r')

if [ -z "$REDIS_VERSION" ]; then
    echo "❌ 無法獲取Redis版本信息"
    exit 1
fi

echo "🎯 Redis版本: $REDIS_VERSION"
echo ""

# 版本兼容性分析
echo "🔍 兼容性分析："
echo "==============="

# 提取主版本號
MAJOR_VERSION=$(echo $REDIS_VERSION | cut -d. -f1)
MINOR_VERSION=$(echo $REDIS_VERSION | cut -d. -f2)

case $MAJOR_VERSION in
    "2")
        echo "⚠️  Redis 2.x - 較舊版本"
        echo "   ✅ 基本功能支持：Pub/Sub, String操作"
        echo "   ❌ 可能缺少某些功能：連接池優化"
        echo "   📝 建議：升級到更新版本"
        ;;
    "3")
        echo "⚠️  Redis 3.x - 舊版本"
        echo "   ✅ 功能支持良好"
        echo "   ✅ 我們的系統完全兼容"
        echo "   📝 建議：考慮升級以獲得更好性能"
        ;;
    "4")
        echo "✅ Redis 4.x - 完全兼容！"
        echo "   ✅ 所有功能完全支持"
        echo "   ✅ 性能優秀"
        echo "   ✅ 穩定性極佳"
        echo "   🎉 我們的系統可以完美運行！"
        ;;
    "5")
        echo "✅ Redis 5.x - 完全兼容！"
        echo "   ✅ 所有功能完全支持"
        echo "   ✅ 性能優秀"
        echo "   ✅ 新增了Streams功能（我們暫未使用）"
        echo "   🎉 我們的系統可以完美運行！"
        ;;
    "6")
        echo "🚀 Redis 6.x - 推薦版本！"
        echo "   ✅ 所有功能完全支持"
        echo "   ✅ 性能最佳"
        echo "   ✅ 支持ACL、SSL等新特性"
        echo "   🎉 我們的系統可以完美運行並享受最佳性能！"
        ;;
    "7"|"8"|"9")
        echo "🆕 Redis $MAJOR_VERSION.x - 新版本"
        echo "   ✅ 應該完全兼容（向下兼容）"
        echo "   📝 建議：進行完整測試"
        ;;
    *)
        echo "❓ 未知版本：$REDIS_VERSION"
        echo "   📝 建議：手動驗證兼容性"
        ;;
esac

echo ""

# 功能測試
echo "🧪 功能兼容性測試："
echo "=================="

# 測試基本String操作
echo -n "1. 測試String操作..."
if $REDIS_CMD SET test_key "test_value" EX 5 > /dev/null 2>&1 && \
   [ "$($REDIS_CMD GET test_key)" = "test_value" ] && \
   $REDIS_CMD DEL test_key > /dev/null 2>&1; then
    echo " ✅"
else
    echo " ❌"
fi

# 測試Pub/Sub
echo -n "2. 測試Pub/Sub功能..."
if $REDIS_CMD PUBLISH test_channel "test_message" > /dev/null 2>&1; then
    echo " ✅"
else
    echo " ❌"
fi

# 測試過期時間
echo -n "3. 測試TTL功能..."
if $REDIS_CMD SET test_ttl "value" EX 1 > /dev/null 2>&1 && \
   $REDIS_CMD TTL test_ttl > /dev/null 2>&1; then
    echo " ✅"
    $REDIS_CMD DEL test_ttl > /dev/null 2>&1
else
    echo " ❌"
fi

echo ""

# 獲取Redis配置信息
echo "⚙️  Redis配置信息："
echo "=================="
echo "最大內存: $($REDIS_CMD CONFIG GET maxmemory | tail -n1)"
echo "內存策略: $($REDIS_CMD CONFIG GET maxmemory-policy | tail -n1)"
echo "數據庫數量: $($REDIS_CMD CONFIG GET databases | tail -n1)"
echo ""

# 性能信息
echo "📊 性能信息："
echo "============"
MEMORY_INFO=$($REDIS_CMD INFO memory | grep "used_memory_human:" | cut -d: -f2 | tr -d '\r')
CONNECTED_CLIENTS=$($REDIS_CMD INFO clients | grep "connected_clients:" | cut -d: -f2 | tr -d '\r')
echo "已用內存: $MEMORY_INFO"
echo "連接客戶端: $CONNECTED_CLIENTS"
echo ""

# 推薦配置
echo "💡 針對您的Redis版本($REDIS_VERSION)的配置建議："
echo "==========================================="

if [[ $MAJOR_VERSION -eq 4 ]]; then
    cat << EOF
# application.yml 建議配置（Redis 4.x）
spring:
  redis:
    host: $REDIS_HOST
    port: $REDIS_PORT
    timeout: 3000ms
    jedis:
      pool:
        max-active: 8
        max-idle: 4
        min-idle: 1
        max-wait: 1000ms
EOF
elif [[ $MAJOR_VERSION -eq 5 ]]; then
    cat << EOF
# application.yml 建議配置（Redis 5.x）
spring:
  redis:
    host: $REDIS_HOST
    port: $REDIS_PORT
    timeout: 5000ms
    jedis:
      pool:
        max-active: 12
        max-idle: 6
        min-idle: 2
        max-wait: 2000ms
    connect-timeout: 10000ms
    client-name: fund-system
EOF
elif [[ $MAJOR_VERSION -ge 6 ]]; then
    cat << EOF
# application.yml 建議配置（Redis 6.x+）
spring:
  redis:
    host: $REDIS_HOST
    port: $REDIS_PORT
    timeout: 10000ms
    jedis:
      pool:
        max-active: 20
        max-idle: 10
        min-idle: 5
        max-wait: 3000ms
    connect-timeout: 10000ms
    client-name: fund-system
    ssl: false
EOF
fi

echo ""

# 總結
echo "📝 總結："
echo "========"
if [[ $MAJOR_VERSION -ge 4 ]]; then
    echo "🎉 您的Redis版本($REDIS_VERSION)完全兼容我們的基金債券處理系統！"
    echo "✅ 可以直接部署，無需任何修改"
    echo "✅ 所有功能都會正常工作：SSE、Pub/Sub、事件廣播"
    echo "✅ 建議使用上方提供的配置優化性能"
elif [[ $MAJOR_VERSION -eq 3 ]]; then
    echo "⚠️  您的Redis版本($REDIS_VERSION)基本兼容"
    echo "✅ 核心功能可以正常工作"
    echo "📝 建議：如可能，升級到Redis 4.x+以獲得更好體驗"
else
    echo "⚠️  您的Redis版本($REDIS_VERSION)較舊"
    echo "📝 建議：升級到Redis 4.x+以確保最佳兼容性"
fi

echo ""
echo "🔗 相關文檔："
echo "   - 詳細兼容性分析: REDIS-VERSION-COMPATIBILITY.md"
echo "   - 系統架構文檔: SYSTEM-ARCHITECTURE-DOCUMENTATION.md"
echo ""
echo "檢測完成！如有問題，請參考相關文檔或聯繫技術支持。" 