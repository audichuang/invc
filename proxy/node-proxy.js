const http = require('http');
const httpProxy = require('http-proxy');

// 定義後端服務器
const backends = [
    { url: 'http://localhost:9090' },
    { url: 'http://localhost:9091' }
];

// 創建代理伺服器
const proxy = httpProxy.createProxyServer({});

// 處理代理錯誤
proxy.on('error', (err, req, res) => {
    console.error('代理錯誤:', err);
    res.writeHead(500, { 'Content-Type': 'text/plain' });
    res.end('代理錯誤: ' + err.message);
});

// 請求計數器用於輪詢
let counter = 0;

// 創建 HTTP 伺服器
const server = http.createServer((req, res) => {
    // 輪詢選擇目標伺服器
    const target = backends[counter % backends.length];
    counter++;

    // 檢查是否為 SSE 請求
    const isSSE = req.url.includes('/events/');
    
    if (isSSE) {
        console.log(`檢測到 SSE 請求: ${req.method} ${req.url} -> ${target.url}`);
    } else {
        console.log(`代理請求: ${req.method} ${req.url} -> ${target.url}`);
    }

    // 轉發請求
    proxy.web(req, res, { target: target.url });
});

// 啟動伺服器
const PORT = 8080;
server.listen(PORT, () => {
    console.log(`反向代理服務器在 http://localhost:${PORT} 上運行`);
    console.log(`後端服務器: ${backends.map(b => b.url).join(', ')}`);
}); 