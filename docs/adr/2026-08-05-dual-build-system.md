# ADR-001：双构建体系（Gradle + pnpm）边界与同步策略

- **状态**：采纳（Accepted）
- **日期**：2026-08-05
- **背景**：本仓库存在两套相互独立的构建体系，长期缺乏书面约定，容易在「哪套负责什么」「如何与上游同步」上产生分歧（即「分裂大脑」）。

---

## 1. 两套体系各自职责

| 体系 | 入口 | 产物 | 负责范围 |
|------|------|------|----------|
| **Gradle（Android）** | `gradlew` / `app/build.gradle` | APK / AAB | 主应用、所有 `app/**` 自研与 vendored 代码 |
| **pnpm（Web 前端）** | `modules/web/`（`package.json` / `pnpm-lock.yaml` / `vite`） | `modules/web/dist/**` 静态资源 | 阅读器 WebView 内嵌的前端 UI |

`modules/web` **不进入 Gradle 构建图**（`settings.gradle` 未将其声明为含 `com.android` 插件的模块），是仓库内的独立前端子项目。

## 2. 决策

1. **职责隔离**：Gradle 永远不构建 `modules/web`；前端资源由 pnpm 单独产出，再以约定方式（assets 拷贝 / 远程加载）交付给 Android 侧。
2. **锁文件必须提交**：`modules/web/pnpm-lock.yaml` 被 git 跟踪，**禁止无锁文件提交 package.json 改版本**。改依赖后必须本机 `pnpm install` 重新生成锁文件并验证 `pnpm build` / `pnpm type-check` 通过再提交。
3. **Node 版本**：前端约定 Node 22 + pnpm 9（与已验证的 vite `^6.4.3` 安全修复环境一致）。CI 中需 `corepack enable` 并锁定 pnpm 版本。
4. **与上游同步**：
   - Android 主工程跟随 `gedoor/legado` 上游，merge 时重点审查 `libs.versions.toml`（Version Catalog 单一事实源）与 `app/build.gradle` 的依赖块。
   - `modules/web` 视为本仓自研扩展，不强制跟随上游；其上游同步策略在各自 PR 中说明。
5. **CI 门禁**：两套体系各自独立跑门禁（见 `2026-08-05-test-ci-proposal.md`），互不阻塞；任一体系失败即阻断合并。

## 3. 后果

- **正面**：职责清晰，新人无需猜测；依赖升级可独立验证、独立回滚。
- **负面 / 成本**：需要维护两份工具链与两份 CI 配置；前端资源交付到 Android 的衔接点需显式约定（避免「本地能跑、打包后白屏」）。
- **待办**：在 `README.md` 增加「本地开发 / 构建 / 发布」一节，引用本 ADR。
