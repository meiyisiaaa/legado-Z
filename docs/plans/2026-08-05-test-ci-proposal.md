# 测试基建与 CI 门禁提案

- **日期**：2026-08-05
- **现状**：
  - `modules/web/package.json` 的 `test` 为 noop（`"test": "echo no test"`）。
  - Android 仅 `app/src/test` 下的 `ExampleUnitTest.kt` / `JsTest.kt` 桩，无业务单测。
  - 无 CI 配置（无 `.github/workflows`、无 GitLab CI）。

---

## 1. 关键路径单测（优先补）

| 关键路径 | 为何重要 | 建议测试点 |
|------|------|------|
| 文本/编码工具（MD5Utils、EncodeUtils、StringUtils） | 书源解析、缓存键、加密校验依赖，纯函数易测 | 已落地 `MD5UtilsTest.kt` 作为样本 |
| 书源规则解析（正则/JS 注入） | 阅读核心，错误会静默失效 | 选取 2-3 个代表性规则做快照测试 |
| 章节/目录解析（EpubFile、MobiReader） | 导入体验核心 | 用内置样例 epub/mobi 跑解析不抛异常 + 关键字段断言 |

> 原则：**先补纯函数/JVM 单测**（无需 Robolectric，CI 快）；UI/生命周期相关再上 instrumented 或 Robolectric。

## 2. CI 门禁（建议矩阵）

两套体系独立跑，任一失败阻断合并：

### Android（Gradle）
```
./gradlew :app:lintDebug          # Lint
./gradlew :app:detekt             # 若引入 Detekt（建议加）
./gradlew :app:testDebugUnitTest  # 单元单测
./gradlew :app:assembleDebug      # 构建（含 R8 关闭的 debug）
# 依赖漏洞：./gradlew dependencyCheckAnalyze（OWASP）或同效 SCA
```

### Web（pnpm，Node 22 + pnpm 9）
```
pnpm install --frozen-lockfile    # 必须用锁文件，拒绝漂移
pnpm type-check                   # vue-tsc
pnpm build                        # vite 产物
pnpm audit                       # 依赖漏洞（目标 0）
```

## 3. 实施建议

1. 先接**构建 + type-check/build + audit** 三件套（性价比最高，能挡住大部分回归与漏洞）。
2. Lint/Detekt 作为**警告→错误渐进**，避免一次性海量报错阻塞。
3. 单测覆盖率不追求数字，先保证「关键路径有回归护盾」。
4. 本仓未使用 GitHub Actions；若后续接入，按上述矩阵写 workflow，并将 `pnpm` 版本通过 `packageManager` 字段锁定。

## 4. 已落地部分

- `app/src/test/java/io/legado/app/utils/MD5UtilsTest.kt`：首个 JVM 单测样本（验证标准 MD5 向量）。
