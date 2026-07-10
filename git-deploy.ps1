# 自动提交与推送脚本

# 1. 自动检测并提示专属的 Deploy key 和 Cloudflare 部署参数
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
    write-host "GitHub 密钥配置指南:" -ForegroundColor Cyan
    write-host "1. 请复制上方以 'ssh-ed25519' 开头的整行公钥内容。" -ForegroundColor Cyan
    write-host "2. 登录您的 GitHub 仓库 -> Settings -> Deploy keys -> Add deploy key。" -ForegroundColor Cyan
    write-host "3. 粘贴公钥，并务必勾选 'Allow write access' (允许写入权限)！" -ForegroundColor Red
    
    # 输出 Cloudflare 构建提示信息
    write-host "========================================================================" -ForegroundColor Yellow
    write-host "[🌐 Cloudflare Pages 构建设置提示]" -ForegroundColor Yellow
    write-host "当您前往 Cloudflare 关联仓库创建 Pages 站点时，请按如下参数进行填写：" -ForegroundColor DarkYellow
    write-host "------------------------------------------------------------------------" -ForegroundColor DarkYellow
    write-host "1. 框架预设 (Framework preset): None" -ForegroundColor Green
    write-host "2. 构建命令 (Build command):    (静态网页请留空)" -ForegroundColor Green
    write-host "3. 输出目录 (Build output dir): ./" -ForegroundColor Green
    write-host "========================================================================" -ForegroundColor Yellow
    
    $choice = Read-Host "已在 GitHub 配置完毕并记录构建参数？(直接回车继续推送，输入 'N' 退出)"
    if ($choice.Trim().ToUpper() -eq 'N') {
        write-host "已取消推送。" -ForegroundColor Yellow
        exit
    }
}

$commit_msg = Read-Host "`n请输入本次提交的 Commit 描述 (默认: 'AI 研发更新')"
if ([string]::IsNullOrEmpty($commit_msg)) {
    $commit_msg = "AI 研发更新 - $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
}

# 2. 分支检测与本地自动化合并合并逻辑
$mainBranch = "main"
$currentBranch = git branch --show-current

write-host "----------------------------------------" -ForegroundColor Cyan
if ($currentBranch -ne $mainBranch) {
    write-host "检测到当前处于开发分支 [$currentBranch]，项目主分支为 [$mainBranch]。" -ForegroundColor Yellow
    write-host "正在自动将 [$currentBranch] 提交并一键合并到主分支 [$mainBranch]..." -ForegroundColor Yellow
    
    # 提交当前分支
    git add .
    git commit -m "$commit_msg (在开发分支 $currentBranch)"
    
    # 切换到主分支并拉取最新代码
    git checkout $mainBranch
    if ($LASTEXITCODE -ne 0) {
        git checkout -b $mainBranch
    }
    git pull origin $mainBranch 2>nul
    
    # 本地合并开发分支
    git merge $currentBranch -m "自动合并 $currentBranch 分支至主分支 $mainBranch"
    
    # 推送主分支
    write-host "正在将合并后的主分支 [$mainBranch] 推送到远程仓库..." -ForegroundColor Cyan
    git push origin $mainBranch
    
    # 切回原开发分支，保持开发环境不变
    git checkout $currentBranch
} else {
    # 如果当前就在主分支，直接提交并推送
    write-host "当前处于主分支 [$mainBranch]。正在直接拉取、提交并推送..." -ForegroundColor Yellow
    git add .
    git commit -m "$commit_msg"
    git pull origin $mainBranch 2>nul
    git push origin $mainBranch
}

if ($LASTEXITCODE -eq 0) {
    write-host "----------------------------------------" -ForegroundColor Green
    write-host "[🎉 成功] 代码已成功合并并推送到远程 $mainBranch 主分支！" -ForegroundColor Green
} else {
    write-host "----------------------------------------" -ForegroundColor Red
    write-host "[❌ 失败] 推送失败，请确认您的 GitHub 权限配置。" -ForegroundColor Red
}
pause
