# 阅读Sigma（legado-Z）Android 端 P2 技术债清单

- **生成日期**：2026-08-04
- **依据**：2026-08-03 健康体检报告（静态启发式扫描，非编译级验证）+ 本次实时复核
- **用途**：作为「修复当前项目所有问题」中 Android 端的待办清单。**本清单仅列出，待确认后再动手**——统一原则：不改行为、低风险优先、小 diff、可回滚。
- **结论前置**：仓库无 P0/P1 缺陷，以下均为可维护性 / 技术债层面的 P2 观察项，**不影响构建与运行**。

---

## 一、实时复核数据（2026-08-04）

| 指标 | 自研代码数值 | 备注 |
|------|------|------|
| 自研 `TODO/FIXME/待优化/HACK` | **22 行** | 全仓 77 处含 vendored 库（icu4j、epublib 等），自研仅占少数 |
| `printStackTrace` | **33 行** | 全仓 81 处，其中 25 处在 vendored `umdlib`，已排除 |
| `!!` 非空断言 | **12 处（仅 3 文件）** | 极低，远优于同规模项目 |
| `ReadBookActivity.kt` | **1858 行** | God Activity 标杆；另有 7 个自研文件 >1000 行 |

> 注：计数已排除 `androidTest` 与 vendored 第三方树，反映自研真实负担。

---

## 二、优先级清单

| 优先级 | 项 | 证据 | 建议动作 | 风险 | 工作量 |
|------|------|------|------|------|------|
| **P1** | God Activity 拆分（`ReadBookActivity` 1858 行等 8 个大文件） | 复杂度热点表 | 按职责拆（阅读控制 / 菜单 / 状态），分步、小 diff、配测试 | 中（回归风险） | 中 |
| **P2** | `JsEncodeUtils.kt` 18 处 TODO | 自研 TODO 最大热点 | 迭代中逐条消化，每条给验收 | 低 | 小 |
| **P2** | 33 处 `printStackTrace` → `LogUtils` | 计数 33（自研） | 统一日志门面，便于线上排查 | 低 | 小–中 |
| **P2** | `build.gradle` 4 处 `//implementation(libs.xxx)` 死注释 | 清理时已定位（app/build.gradle 175/212/213/217） | 顺手删除注释 | 零 | 极小 |
| **P2** | 双构建体系（Gradle + pnpm）「分裂大脑」 | 架构观察 | 写 ADR/README 明确 `modules/web` 构建、发布、与上游同步策略 | 低 | 文档 |
| **P2** | vendored 库体积膨胀（icu4j/epublib/umdlib/mobi…） | 重复与死代码节 | 标注上游版本号，评估是否改为依赖引入 | 中 | 大 |
| **P2** | 测试基建缺失 | `package.json` test 为 noop；Android 仅 `androidTest` 桩 | 补关键路径单测 + 接入 CI 跑 Lint/Detekt | 中 | 大 |

---

## 三、建议执行顺序（低风险优先）

1. **立即可做（低风险清理）**
   - 删 `build.gradle` 4 处死注释
   - 消化 `JsEncodeUtils.kt` 的 18 处 TODO
   - `printStackTrace` 统一替换为 `LogUtils`
2. **短期（1–2 迭代）**：明确双构建体系同步策略（出 ADR）
3. **中期（架构）**：`ReadBookActivity` 等 God 类职责拆分（需充分测试兜底）
4. **长期（质量基建）**：补齐自动化测试 + CI 门禁

---

## 四、不在本清单内（已处理 / 无问题）

- **Version Catalog 8 个零引用条目**：已清理（2026-08-03/04，commit `aad5e0114`）
- **Web 端 vite `^6.4.3` 安全修复**：已落地并验证 `pnpm build` / `pnpm type-check` 通过
- **安全**：无硬编码密钥，无 P0/P1 安全缺陷（见体检报告第 6 节）

---

## 五、待确认

- 是否按上述顺序**逐项实施**？还是只先推进「立即可做」那档（零/低风险）？
- God Activity 拆分涉及阅读核心，是否需要单独评审 + 灰度？

---

## 六、执行状态（2026-08-05）

按「全部按优先级执行」推进。沙箱**无法编译 Android**（gradle wrapper jar 异常 + 完整构建需联网拉 Gradle 8.14.4 分发版与数百 AAR），故代码级改动均标注需本地构建/CI 验证。

| 项 | 状态 | 交付 |
|------|------|------|
| build.gradle 4 处死注释 | ✅ 已落地 | commit `41d3c7b69`（删 7 行死注释 + 附属 noinspection/描述） |
| JsEncodeUtils 18 处 TODO | ✅ 已核实无 | 当前文件 0 个 TODO（清单计数为过时），无需处理 |
| 33 处 printStackTrace → LogUtils | ✅ 已落地 | commit `55e097661`（20 文件 / 28 处替换；保留 LogUtils·CrashHandler 本职 4 处） |
| 双构建体系 ADR | ✅ 已落地 | `docs/adr/2026-08-05-dual-build-system.md` |
| vendored 库评估 | ✅ 已落地 | `docs/plans/2026-08-05-vendored-libs-evaluation.md`（标注+评估，未改源码） |
| 测试/CI 提案 | ✅ 已落地 | `docs/plans/2026-08-05-test-ci-proposal.md` + `app/src/test/.../MD5UtilsTest.kt` 样本 |
| God Activity 拆分 | 🟡 已出计划，代码待构建 | `docs/plans/2026-08-05-god-activity-extraction-plan.md`（分阶段 A/B/C；沙箱无法编译，未做代码级拆分） |

**计数偏差更正**：原清单「自研 TODO 22 / printStackTrace 33」已过时——实测自研 TODO 现 62（含 vendored icu4j 误计），printStackTrace 自研 33 中 5 处为 LogUtils·CrashHandler 本职，本轮替换 28 处。
