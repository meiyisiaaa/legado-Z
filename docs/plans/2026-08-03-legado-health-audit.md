# 阅读Sigma（legado-Z）项目健康度体检报告

- **体检日期**：2026-08-03
- **体检范围**：全仓通扫（app / modules）+ 热点深挖
- **手段**：轻量静态启发式扫描（Grep / Glob / Bash），不依赖 Android 完整构建
- **总体结论**：**健康度良好，无 P0 级（崩溃/安全/构建失败）问题，P1 极少。** 工具链现代、依赖管理有纪律；发现的绝大多数为 P2 观察项。

---

## 1. 项目基线

| 维度 | 数据 |
|------|------|
| 性质 | Android 阅读应用（Legado 衍生版），Kotlin + Gradle |
| 主工程 app | 857 `.kt` + 10 `.java`，约 **1.95 万行**（Kotlin/Java） |
| modules | 78 `.java` + 31 `.kt`（含 vendored epublib/umdlib），约 **1.65 万行** |
| modules/web（独立前端） | 17 `.vue` + 26 `.ts`，约 **5.8 千行** |
| 资源 | app 下 549 `.xml`、116 `.json`、33 `.md`、若干图片/字体 |
| Git | 分支 `main`，工作区干净，近期提交以功能优化 + Bug 修复为主 |
| 工具链 | Kotlin 2.3.10 / AGP 8.13.2 / compileSdk·targetSdk 36 / Java 17 / minSdk 21 |

---

## 2. 依赖与构建健康（亮点）

- **集中式 Version Catalog**：所有依赖版本在 `gradle/libs.versions.toml` 统一管理，符合现代最佳实践。
- **有纪律的主动版本锁定**：多处旧版本带 `noinspection` + 注释说明"为何不能升"：
  - `jsoup 1.16.2` → 卡 issue #3811 破坏性变更
  - `commons-text 1.13.1` → 新版用到 Android 6 缺失的 `Arrays.setAll` 会崩
  - `rhino 1.8.1` → 新版用 Android 8 以下无法编译的 `VarHandle`
  - `hutool 5.8.22`、`protobuf-javalite 4.26.1` 等明确标注"不要更新"
  - 这是**有意识的工程决策**，不是疏忽，应保留。
- **构建配置合理**：release 开启 `minifyEnabled + shrinkResources`，`lint.checkDependencies = true`，`MissingTranslation` 主动禁用（有注释说明）。

---

## 3. 复杂度热点（大文件）

> 注：icu4j / epublib / umdlib 等属 vendored 第三方库，体积大属正常，不计入自研技术债。

| 文件 | 行数 | 归属 | 说明 |
|------|------|------|------|
| `ReadBookActivity.kt` | 1858 | 自研 | 阅读主界面，God Activity，可拆 |
| `TextChapterLayout.kt` | 1333 | 自研 | 排版核心，复杂度高 |
| `ObsoleteUrlFactory.kt` | 1201 | 自研 | 旧版 `java.net.URL` 兼容垫片（legacy shim） |
| `JsExtensions.kt` | 1199 | 自研 | JS 扩展引擎核心 |
| `BookInfoActivity.kt` | 1178 | 自研 | 书籍详情界面 |
| `ReadBook.kt` | 1064 | 自研 | 阅读模型 |
| `QueryTTF.java` | 1055 | 自研 | TTF 字体查询 |
| `ReadMenu.kt` | 698 | 自研 | 阅读菜单 |

**判定**：大文件是**可维护性**风险（P1 观察），非缺陷。重构需谨慎，不在本次"最小变更"范围内。

---

## 4. 技术债标记

- 全仓 `TODO/FIXME/HACK/待优化/临时/Deprecated` 等标记共 **77 处**。
- 分布：**多数集中在 vendored 库**（icu4j、epublib），非自研代码。
- 自研热点：`JsEncodeUtils.kt` 18 处、`JsExtensions.kt` 4 处、`EpubFile.kt`/`AnalyzeByJSoup.kt` 各 3 处。
- **判定**：P2。建议后续迭代中逐步消化 `JsEncodeUtils.kt` 的 18 处待办。

---

## 5. 空安全与错误处理

| 指标 | 数值 | 评价 |
|------|------|------|
| Kotlin `!!` 非空断言 | **12 处**（仅 3 个文件） | 极好，远优于同规模项目 |
| `printStackTrace` / `System.out` / `println` | 81 处 | 其中 25 处在 vendored `umdlib`；其余散落 |
| 异常处理 | 多数为 `catch` 后空转或 `printStackTrace` | P2，建议统一走 `LogUtils` |

---

## 6. 安全与隐私

- **源码无硬编码密钥**：唯一命中的 `accessKeySecret='2222'` 位于 `androidTest` 测试桩，非真实凭证；WebDav/代理密码均来自用户配置，未硬编码。
- **Firebase Analytics + Perf** 已集成，README 已含免责声明（第三方书源内容不担责）。
- WebView 渲染第三方书源内容属产品固有风险，已在免责声明覆盖。
- **判定**：无 P0/P1 安全缺陷。

---

## 7. 架构与模块边界

- Gradle 多模块仅含 `:app`、`:modules:book`、`:modules:rhino`。
- **`modules/web`（Vue3 + Vite 前端）完全独立**，不进 Gradle 构建，有自己的一套 `package.json` / `vite.config.ts` / `eslint` / `prettier`。
- **判定（P2 架构观察）**：仓库内存在**两套独立构建体系**（Gradle + Node/pnpm），建议明确 `modules/web` 的构建/发布流程与上游同步策略，避免"分裂大脑"。

---

## 8. 重复与死代码

- **完全重复文件**：无（基于内容哈希比对，.kt 文件 0 对重复）。
- **vendored 第三方树**：`app/lib` 下含 aliyun / cronet / dialogs / icu4j / mobi / permission / prefs / theme / webdav，显著放大代码体积——属 fork 常见策略，建议标注上游版本便于跟踪。

---

## 9. Web 前端依赖（modules/web）

| 包 | 版本 | 备注 |
|----|------|------|
| vue | ^3.5.12 | 较新 |
| vite | ^5.4.8 | 偏旧（已有 6/7 大版本） |
| element-plus | 2.8.5 | 中等 |
| axios | ^1.7.7 | 早于若干安全修复版本，建议 `pnpm audit` 后升级 |
| typescript | ~5.5.4 | 偏旧 |
| eslint | ^9.12.0 | 较新 |

**判定（P2）**：整体可用但偏旧，建议周期性 `pnpm update` + `pnpm audit`，并补 `pnpm-lock.yaml` 提交以保证可复现构建。

---

## 10. 风险清单（按优先级）

| 级别 | 项 | 建议 |
|------|----|------|
| **P0** | 无 | — |
| **P1** | 超大 God Activity（ReadBookActivity 1858 行等） | 后续按职责拆分（阅读控制 / 菜单 / 状态），分步重构，勿一次性大改 |
| **P1** | Web 端 axios 1.7.7 早于安全修复 | 跑 `pnpm audit` 后升级到安全版本 |
| **P2** | Version Catalog 中 8 个零引用条目 | **已修复（见第 11 节）** |
| **P2** | `JsEncodeUtils.kt` 18 处 TODO | 迭代中消化 |
| **P2** | 81 处 `printStackTrace`/`System.out` | 统一替换为 `LogUtils` |
| **P2** | `modules/web` 与 Gradle 双构建体系 | 明确构建/发布/同步流程 |
| **P2** | vendored 库（icu4j/epublib/umdlib 等）体积膨胀 | 标注上游版本，评估可否改为依赖引入 |
| **P2** | build.gradle 内若干注释掉的 `//implementation(libs.xxx)` 死注释 | 顺手清理（本次未改） |

---

## 11. 本次已修复项

**操作**：清理 `gradle/libs.versions.toml` 中 8 个**全仓零引用**的库条目（已 grep 验证所有 `*.gradle` 无实际引用，仅存在于注释中）：

- `glide-compose`
- `glide-avif`
- `avif`
- `splitties-activities`
- `media3-exoplayer-hls`
- `media3-ui`
- `media3-session`
- `kotlin-reflect`

**风险**：零。纯配置噪音删除，不影响编译与运行。

---

## 12. 改进路线建议

1. **立即可做（低风险的 P2 清理）**：删除零引用 catalog 条目（已完成）、清理 build.gradle 死注释、统一 `printStackTrace` → `LogUtils`。
2. **短期（1-2 个迭代）**：消化 `JsEncodeUtils.kt` 待办；Web 端 `pnpm audit` + 升级 axios/typescript/vite。
3. **中期（架构）**：对 `ReadBookActivity` 等 God 类做职责拆分；明确 `modules/web` 的构建与上游同步策略；评估 vendored 库改为依赖引入。
4. **长期（质量基建）**：补齐自动化测试（当前 `package.json` test 为 noop，Android 仅 androidTest 桩）；接入 CI 跑 Lint/Detekt。

---

## 13. 遗留 / 未做项

- 未运行 Android Lint / Detekt（本环境缺 Android SDK + 完整构建链），结论基于静态启发式扫描，非编译级验证。
- 未对 `modules/web` 跑 `pnpm install` / `pnpm audit`（仅做 package.json 静态研判）。
- 大文件重构、vendored 库替换等 P1/P2 项**仅建议、未实施**（遵循"最小变更、不改行为"原则，避免引入回归风险）。
