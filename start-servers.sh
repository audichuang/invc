#!/bin/bash

echo "啟動 Kafka..."
docker-compose up -d zookeeper kafka

echo "等待 Kafka 啟動..."
sleep 10

echo "啟動第一個後端實例 (Pod A - 端口 9090)..."
cd back
nohup mvn spring-boot:run -Dspring-boot.run.profiles=pod1 > pod1.log 2>&1 &
POD1_PID=$!
echo "Pod A 已啟動，PID: $POD1_PID"

echo "啟動第二個後端實例 (Pod B - 端口 9091)..."
nohup mvn spring-boot:run -Dspring-boot.run.profiles=pod2 > pod2.log 2>&1 &
POD2_PID=$!
echo "Pod B 已啟動，PID: $POD2_PID"
cd ..

echo "選擇反向代理類型:"
echo "1) Java 反向代理"
echo "2) Node.js 反向代理"
read -p "請選擇 (1/2): " PROXY_TYPE

if [ "$PROXY_TYPE" = "1" ]; then
    echo "啟動 Java 反向代理..."
    cd proxy
    mvn compile exec:java -Dexec.mainClass="com.example.proxy.SimpleLoadBalancer"
    cd ..
elif [ "$PROXY_TYPE" = "2" ]; then
    echo "啟動 Node.js 反向代理..."
    cd proxy
    npm install
    npm start
    cd ..
else
    echo "無效選擇，不啟動反向代理"
fi

echo "啟動前端..."
cd frontend
npm install
npm start 