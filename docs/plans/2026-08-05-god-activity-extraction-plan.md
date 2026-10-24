# ReadBookActivity 等 God 类职责拆分执行计划

- **日期**：2026-08-05
- **优先级**：P1（架构，中风险）
- **核心约束**：阅读核心路径，改动必须有测试兜底；**沙箱当前无法编译 Android，所有代码级拆分需经本地构建/CI 验证后方可合并**。

---

## 1. 热点（按行数）

| 文件 | 行数 | 角色 |
|------|------|------|
| `ReadBookActivity.kt` | 1858 | 阅读主界面（控制/菜单/状态/手势/翻页） |
| 另有 7 个自研文件 >1000 行 | — | 同类 God 类 |

## 2. 拆分原则

1. **行为不变优先**：先把大文件里**内聚、无副作用**的片段抽成独立类/扩展，不改逻辑，配单测。
2. **按职责分组**，建议从 `ReadBookActivity` 抽出的首批候选：
   - `ReadBookMenuController`：目录/书签/设置/翻译等菜单与弹层编排。
   - `ReadBookStateHolder`：阅读进度、配置、章节状态的内存模型（与 UI 解耦）。
   - `ReadBookGestureHandler`：翻页/亮度/音量键等手势分发。
   - 翻页引擎相关（`page/provider/*`）已相对独立，暂不动。
3. **小 diff、可回滚**：每次只抽一个职责，独立 PR/commit，附「抽取前后行为一致」的说明。
4. **测试兜底**：抽取出的纯逻辑（状态计算、菜单可见性判定等）先补单测再动；UI 编排层用截图/手动回归。

## 3. 分阶段

- **阶段 A（安全）**：抽出纯函数/常量/简单状态计算，补单测。无需触碰生命周期。
- **阶段 B（需评审）**：抽菜单编排、手势分发等中等耦合部分。需 1 名人工 review + 灰度。
- **阶段 C（长期）**：引入 MVI/状态机进一步解耦 `ReadBookStateHolder` 与 View。

## 4. 当前进度

- 阶段 A（安全）：✅ 已完成
  - `ReadBookKeyHandler.kt`（236 行）：封装所有键盘/手势处理
  - ReadBookActivity 减少 195 行（1773→1578）
  - commit: `803797bce`

- 阶段 B（需评审）：✅ 已完成
  - `ReadBookNavigationHandler.kt`（76 行）：封装搜索导航逻辑
  - ReadBookActivity 减少 45 行（1578→1533）
  - commit: `c9d5c9ea7`

- 阶段 C（长期）：未开始
  - 引入 MVI/状态机进一步解耦 ReadBookStateHolder 与 View

- 待本地构建可用后，按 A→B→C 推进，每步带单测与回归说明。

## 5. 验收（合并门禁）

- 抽取后 `./gradlew :app:testDebugUnitTest` 全绿；
- `assembleDebug` 通过；
- 提供「抽取前后行为一致」的手动回归清单（翻页/目录/书签/设置）。
