# GitHub Actions APK 构建流程

## 快速开始

### 方法一：使用 Python 脚本（推荐）

```powershell
# 1. 获取 GitHub Token
# 访问 https://github.com/settings/tokens 生成新 Token
# 勾选 repo 权限

# 2. 运行构建脚本
python build_apk.py --token ghp_xxxxxxxxxx --username your_username
```

### 方法二：手动推送代码

```powershell
# 1. 添加 GitHub 远程仓库
git remote add origin https://your_token@github.com/your_username/pseudo-pic-android.git

# 2. 推送代码
git push -u origin master

# 3. 访问 GitHub Actions 页面
# https://github.com/your_username/pseudo-pic-android/actions
```

### 方法三：使用 GitHub CLI（如果已安装）

```powershell
# 1. 登录 GitHub
gh auth login

# 2. 创建仓库
gh repo create pseudo-pic-android --public

# 3. 推送代码
git remote add origin https://github.com/your_username/pseudo-pic-android.git
git push -u origin master
```

## 构建流程说明

1. GitHub Actions 会自动检测到 `.github/workflows/build.yml`
2. 使用 Ubuntu 最新的 GitHub Actions runner
3. 配置 JDK 17 (Temurin)
4. 执行 Gradle 构建命令
5. 上传 APK 作为构建产物

## 构建产物

- **APK 文件**: `app/build/outputs/apk/debug/app-debug.apk`
- **下载位置**: GitHub Actions → Runs → Download artifact
- **本地保存**: 脚本会自动下载并解压到项目目录

## 常见问题

### Q: Token 获取失败
A: 确保 Token 有 `repo` 权限，且未过期

### Q: 构建超时
A: 首次构建需要下载依赖，可能需要 5-10 分钟

### Q: APK 文件损坏
A: 重新触发一次 Workflow，或检查构建日志

## 项目结构

```
picturesApp/
├── .github/workflows/
│   └── build.yml          # GitHub Actions 配置
├── app/
│   ├── build.gradle.kts   # Android 模块配置
│   └── src/main/          # 源代码
├── build_apk.py           # 自动化构建脚本
└── README.md              # 项目说明
```
