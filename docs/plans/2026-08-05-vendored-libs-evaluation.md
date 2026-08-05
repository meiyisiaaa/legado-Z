# 阅读Sigma vendored 第三方库评估

- **日期**：2026-08-05
- **范围**：`app/src/main/java/io/legado/app/lib/` 及 `app/build.gradle` 中以源码形式引入、未走 Maven 依赖的第三方代码。
- **目标**：标注上游版本号，评估是否改为依赖引入（去 vendored）。

---

## 1. 现状盘点

| 目录 / 依赖 | 性质 | 上游 | 当前形态 | 评估 |
|------|------|------|------|------|
| `lib/icu4j/`（CharsetDetector 等） | 第三方 charset 检测 | ICU4J（IBM） | 裁剪后源码 vendored | **保留 vendored**：上游为巨型库，仅少量类被裁剪使用，整体依赖不划算；建议补 `// upstream: icu4j x.y.z` 注释 + 记录裁剪了哪些类 |
| `lib/mobi/`（MobiReader / KF8Book） | 第三方 Kindle 解析 | 开源 mobi 解析（fbreader 系） | 源码 vendored | **保留 vendored**：小众格式，上游无稳定 Maven 包；补上游版本/出处注释 |
| `lib/cronet/`（BodyUploadProvider 等） | Google Cronet 网络栈封装 | Chromium Cronet | 封装 + 少量 vendored 接口 | 封装层可保留；Cronet 本体应走 `libs.cronet` 依赖而非源码 |
| `lib/aliyun` `lib/webdav` `lib/dialogs` `lib/permission` `lib/prefs` `lib/theme` | 多为**本仓自有**封装/工具，非第三方 | — | 自研 | **非 vendored**：勿误判；保持现状 |
| `gsyVideoPlayer`（build.gradle `libs.gsyVideoPlayer.*`） | 第三方视频播放 | GSYVideoPlayer | Maven 依赖（正确） | 已依赖化，无需改动 |
| `umdlib`（早期 grep 命中） | — | — | **当前主工程已无此目录** | 已清理/迁移，无需处理 |

> 说明：审计早期「全仓 81 处 printStackTrace、77 处 TODO」中大量来自 vendored 树（icu4j / umdlib 等），这些**不应由本仓修复**，应随上游升级解决。本仓自研真实负担：TODO 22→（现 62，含 icu4j 等 vendored 误计）、printStackTrace 自研 33（其中 28 已在本轮统一为 LogUtils）。

## 2. 推荐动作（按性价比）

1. **立即（低成本）**：为每个 vendored 目录顶部补一行 `// upstream: <项目名> <版本/commit>`，固化溯源信息。不改动代码、零风险。
2. **中期（需评估）**：仅当某个 vendored 库出现**稳定且体积更小的 Maven 包**时，才考虑替换为依赖（如 icu4j 的 `com.ibm.icu:icu4j` 若只需 charset 检测则过重，维持 vendored 更优）。
3. **长期（架构）**：建立 `third_party/` 目录集中放置 vendored 源码，与自研 `lib/` 包分离，便于审计与升级。

## 3. 不在本次范围

- 实际替换/升级 vendored 库（涉及行为验证与回归测试，需单独排期）。
- 删除 vendored 源码（会丢失裁剪后的功能）。
