@echo off
title 本地 Web 服务启动器 - hsxy
echo ==========================================
echo       正在启动项目 [hsxy] 本地预览...
echo ==========================================

where node >nul 2>nul
if %ERRORLEVEL% equ 0 (
    echo [检测成功] 已安装 Node.js，正在启动零依赖极速 HTTP 服务...
    start http://localhost:8080
    node -e "const http = require('http'), fs = require('fs'); http.createServer((req, res) => { let p = '.' + (req.url === '/' ? '/index.html' : req.url); fs.readFile(p, (err, data) => { if (err) { res.writeHead(404); res.end('Not Found'); } else { res.writeHead(200); res.end(data); } }); }).listen(8080); console.log('本地服务已运行在 http://localhost:8080');"
    goto end
)

where python >nul 2>nul
if %ERRORLEVEL% equ 0 (
    echo [检测成功] 已安装 Python，正在启动 8080 端口 HTTP 服务...
    start http://localhost:8080
    python -m http.server 8080
    goto end
)

echo [错误] 本地未检测到 Node.js 或 Python 环境！
pause

:end
