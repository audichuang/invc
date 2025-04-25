#!/bin/bash

# 啟動Kafka（如果需要）
echo "啟動Kafka..."
# 這裡添加啟動Kafka的命令，如果已有獨立Kafka，可以省略

# 啟動債券系統實例1
echo "啟動債券系統實例1 (端口 9092)..."
cd bond-system
mvn spring-boot:run &
BOND_INSTANCE1=$!

# 等待第一個實例啟動
sleep 10

# 啟動債券系統實例2
echo "啟動債券系統實例2 (端口 9093)..."
mvn spring-boot:run -Dspring-boot.run.profiles=instance2 &
BOND_INSTANCE2=$!

# 等待第二個實例啟動
sleep 10

# 啟動反向代理
echo "啟動反向代理 (端口 8081)..."
cd ../bond-proxy
mvn spring-boot:run &
PROXY=$!

echo "所有服務已啟動!"
echo "債券系統實例1 PID: $BOND_INSTANCE1 (端口 9092)"
echo "債券系統實例2 PID: $BOND_INSTANCE2 (端口 9093)"
echo "反向代理 PID: $PROXY (端口 8081)"
echo "前端可以通過 http://localhost:8081/api 訪問債券系統"

# 捕獲CTRL+C
trap "echo '停止所有服務...'; kill $BOND_INSTANCE1 $BOND_INSTANCE2 $PROXY; exit 0" INT

# 保持腳本運行
wait 