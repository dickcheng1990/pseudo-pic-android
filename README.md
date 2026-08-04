# 图片深度伪原创工具 (PseudoPic)

一个纯本地离线的 Android 应用，用于批量处理图片以绕过平台的查重机制。

## 功能特性

### 核心处理流程
1. **文件层处理**：修改文件二进制结构和元数据，破坏 MD5/SHA-1 等文件级哈希
2. **像素层处理**：1-2% 边缘裁剪、±5% 色彩/亮度微调、DCT 系数修改
3. **隐形干扰线**：极低透明度、单像素宽度的微高频干扰线，人眼不可见
4. **隐形水印**：在频域嵌入人眼不可见的水印信息
5. **局部像素重排**：打乱局部像素顺序，破坏像素行/列连续性

### 技术约束
- **平台**：Android 原生 (Kotlin)
- **运行模式**：纯本地离线，无需联网
- **处理能力**：支持 10-50 张/批，多线程并发
- **格式支持**：JPEG、PNG、WEBP
- **最低兼容**：Android 7.0 (API 24)
- **性能**：默认模式单张 <1 秒，10 张批量 <10 秒

## 项目结构

```
picturesApp/
├── app/
│   ├── build.gradle.kts          # 模块构建配置
│   └── src/main/
│       ├── AndroidManifest.xml   # 应用清单
│       ├── java/com/example/pseudo/
│       │   ├── PseudoApp.kt      # 应用入口
│       │   ├── models/
│       │   │   └── ImageModels.kt    # 数据模型
│       │   ├── database/
│       │   │   └── AppDatabase.kt    # Room 数据库
│       │   ├── processors/
│       │   │   ├── ImageProcessor.kt   # 核心处理引擎
│       │   │   └── ImageHashUtils.kt   # 哈希工具
│       │   └── ui/
│       │       ├── MainActivity.kt     # 主活动
│       │       ├── MainViewModel.kt    # 视图模型
│       │       ├── ImagePickerFragment.kt   # 图片选择界面
│       │       ├── ProcessingFragment.kt  # 处理参数界面
│       │       ├── HistoryFragment.kt     # 历史记录界面
│       │       └── Adapters.kt           # RecyclerView 适配器
│       └── res/
│           ├── layout/             # 布局文件
│           ├── values/             # 字符串、颜色、主题
│           ├── drawable/           # 图标资源
│           ├── menu/               # 菜单资源
│           └── mipmap-*/           # 应用图标
├── build.gradle.kts                # 项目级构建配置
├── settings.gradle.kts             # 项目设置
└── gradle.properties               # Gradle 配置
```

## 使用方法

### 1. 导入项目
1. 打开 Android Studio
2. 选择 "Open an Existing Project"
3. 选择 `E:\picturesApp` 目录

### 2. 配置 SDK 路径
编辑 `local.properties` 文件：
```
sdk.dir=C:\\Users\\YourUsername\\AppData\\Local\\Android\\Sdk
```

### 3. 构建运行
- 点击 "Build" -> "Make Project" 编译项目
- 连接 Android 设备或启动模拟器
- 点击 "Run" 运行应用

## 用户操作流程

1. **选择图片**：点击"选择图片"按钮，从系统相册多选图片（支持 JPEG/PNG/WEBP）
2. **设置参数**：在参数预览页面调整处理参数：
   - 边缘裁剪比例 (1-2%)
   - 色彩偏移幅度 (±5%)
   - 亮度调整幅度 (±5%)
   - 噪点强度
   - 干扰线密度
   - 隐形水印内容
   - 深度 AI 模式（可选）
3. **开始处理**：点击"一键处理"，多线程并发执行
4. **查看结果**：处理完成后查看结果列表，图片保存为 `原名_pseudo.后缀`

## 技术实现细节

### ImageProcessor.kt - 核心处理引擎
- `processFileLevel()`: 修改 JPEG/PNG/WEBP 文件结构
- `processPixelLevel()`: 像素级色彩/亮度微调
- `addInterferenceLines()`: 添加隐形干扰线
- `modifyDctCoefficients()`: DCT 频域扰动
- `rearrangeLocalPixels()`: 局部像素重排
- `addInvisibleWatermark()`: 频域水印嵌入

### 防查重机制
| 检测类型 | 防护方式 |
|---------|---------|
| 文件哈希 (MD5/SHA) | 修改文件二进制结构 |
| 感知哈希 (pHash/aHash) | 像素微调 + 干扰线 + DCT 扰动 |
| AI 特征提取 | 频域水印 + 局部像素重排 |

## 注意事项

- 本项目为个人工具，仅供学习研究使用
- 请遵守相关法律法规，不要用于违规行为
- 处理后的图片质量与原图视觉差异极小，但哈希值完全不同

## 依赖库

- AndroidX Core KTX 1.12.0
- Material Components 1.11.0
- ConstraintLayout 2.1.4
- Lifecycle Runtime KTX 2.7.0
- Room Database 2.6.1
- Coroutines 1.7.3
- Coil 2.5.0 (图片加载)
