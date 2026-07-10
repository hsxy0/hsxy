# 自动提交与推送脚本

# 1. 自动检测并提示专属的 Deploy key
$publicKeyFile = "deploy_key.pub"
if (Test-Path $publicKeyFile) {
    $pubKey = Get-Content -Raw -Path $publicKeyFile
    $pubKey = $pubKey.Trim()
    
    write-host "========================================================================" -ForegroundColor Yellow
    write-host "[🔑 提示] 检测到当前项目使用了自动生成的专属 Deploy Key。" -ForegroundColor Yellow
    write-host "如果您还没有在 GitHub 仓库配置此公钥，请务必先完成配置，否则推送将失败！" -ForegroundColor Yellow
    write-host "------------------------------------------------------------------------" -ForegroundColor DarkYellow
    write-host "$pubKey" -ForegroundColor Green
    write-host "------------------------------------------------------------------------" -ForegroundColor DarkYellow
    write-host "操作指南:" -ForegroundColor Cyan
    write-host "1. 请复制上方以 'ssh-ed25519' 开头的整行公钥内容。" -ForegroundColor Cyan
    write-host "2. 登录您的 GitHub 仓库 -> Settings -> Deploy keys -> Add deploy key。" -ForegroundColor Cyan
    write-host "3. 粘贴公钥，并务必勾选 'Allow write access' (允许写入权限)！" -ForegroundColor Red
    write-host "========================================================================" -ForegroundColor Yellow
    
    $choice = Read-Host "已在 GitHub 配置完毕？(直接回车继续推送，输入 'N' 退出脚本)"
    if ($choice.Trim().ToUpper() -eq 'N') {
        write-host "已取消推送。" -ForegroundColor Yellow
        exit
    }
}

$commit_msg = Read-Host "`n请输入本次提交的 Commit 描述 (默认: 'AI 研发更新')"
if ([string]::IsNullOrEmpty($commit_msg)) {
    $commit_msg = "AI 研发更新 - $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
}

write-host "----------------------------------------" -ForegroundColor Cyan
write-host "1. 正在将修改添加到暂存区..." -ForegroundColor Cyan
git add .

write-host "2. 正在进行本地 Commit..." -ForegroundColor Cyan
git commit -m "$commit_msg"

write-host "3. 正在推送到远程仓库..." -ForegroundColor Cyan
$branch = git branch --show-current
git push origin $branch

if ($LASTEXITCODE -eq 0) {
    write-host "----------------------------------------" -ForegroundColor Green
    write-host "[🎉 成功] 代码已成功推送到远程仓库！" -ForegroundColor Green
} else {
    write-host "----------------------------------------" -ForegroundColor Red
    write-host "[❌ 失败] 推送失败，请确认您的 GitHub Deploy keys 已添加并勾选了 'Allow write access' 权限。" -ForegroundColor Red
}
pause
