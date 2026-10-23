# Legado-Z

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

使用自定义书源、本地书籍、订阅和朗读的中文读者；Web 界面用于阅读和维护书源、订阅源。

## Product Purpose

保留现有完整阅读与维护能力，通过统一的界面、状态反馈和交互提升日常使用体验。

## Capabilities and Constraints

- Android Kotlin 业务层、Room 数据、Rhino 书源、AI、MCP 和 TTS 能力继续复用。
- Android 普通页面逐屏迁移 Compose，保留 Activity 入口、Intent 和结果契约；自绘阅读、漫画、WebView、代码编辑及媒体引擎互操作复用。
- Web 保留 Vue 3、TypeScript、Element Plus、现有 hash 路由和 API 协议；各端保留自己的既有能力。
- 保留“书架、发现、订阅、我的”主导航及隐藏开关。
- 兼容 API 26、API 36、浅深主题、用户自定义主题和阅读样式、墨水屏、系统减少动画设置。
- 不覆盖已有主题、阅读进度、书源、备份、密钥或其他用户数据。

## Brand Commitments

2026-10-23 当前视觉授权：用户在三张书籍详情概念中明确选择 **A「矿绿书衣」**，要求继续落地并统一其他页面的布局、组件和动效，提供实际运行截图。该选择更新此前的视觉方向；四目的地导航、功能入口、业务与用户数据契约继续保持。详情允许采用非对称封面与整块书衣，管理页面按任务密度转译同一系统。概念与选型见 [CONCEPTS_V3.md](docs/ui/CONCEPTS_V3.md)。

保留产品名称和现有标识。历史约束要求“改回 legado 本身的页面布局和信息层级，并在此基础上进行深度优化”，参照 Android 提交 `f5cad386f` 与 Web 提交 `9f7540771`；其业务分组、入口和操作路径继续有效。2026-10-23 选择的 A 概念更新 Android 书籍详情的视觉构图，当前其余页面按同一语言适配。

本轮补充要求：[legado-with-MD3](https://github.com/HapeLee/legado-with-MD3) 只作为 UI 参考，最终界面须有原创视觉语言；现有二列、三列和紧凑模式不构成新方案。参考项目中有价值的差异功能允许适配接入，不等同于全部接入要求。三种新书架概念及已实现方向见 [UI_REFERENCES.md](UI_REFERENCES.md)，本轮证据与未完成范围见 [VISUAL_REVIEW.md](VISUAL_REVIEW.md)。

在原版结构上深入优化排版、对齐、间距、视觉密度、控件比例、主题角色、信息可读性、状态反馈、触控、动效与设备适配，保留熟悉的操作关系，不新增营销首页、续读大块、常驻续读浮条或其他改变原层级的布局。已有功能、用户主题和阅读设置、数据与接口契约继续有效；普通内容迁移至 Compose，专用引擎互操作复用的工程约束继续有效。

## Evidence on Hand

现有源码、README.md、api.md、NOTICE 与 build/porting-validation 中的历史验收材料。历史验证不代表重构后的结果。

## Product Principles

- 阅读、管理、编辑和朗读的完整功能与新界面一同交付。
- 正文排版服从读者设置；工具界面使用统一的设计规范。
- 操作有及时反馈，失败、空数据、离线及权限状态清晰可恢复。
- 系统返回、键盘、焦点和辅助功能遵循平台行为。

## Accessibility & Inclusion

Android 触控目标至少 48dp；支持字体放大与可访问名称，状态不只依赖颜色。尊重系统移除动画和墨水屏模式。

## Quality and Acceptance

- 以 Apple Design Award、Google Play Best of / Material Design Awards、Awwwards Mobile Excellence 及国际一线数字产品的完成度为品质参照，不将参考奖项表述为本产品已获认证或已达成的事实。
- 用户已拒绝第一版 Web 截图所体现的整体视觉；Android 当前按 A 矿绿书衣验收，Web 既有要求继续保留。围绕阅读、追更、内容管理和规则编辑的真实任务改善细节与完成度。
- 第一轮实现后执行全范围 Design QA / UX QA：排版、间距、视觉层级、色彩、动效、微交互、导航与可用性、设备适配、产品状态、性能、无障碍、原创性与品牌感。
- 每个确认问题均记录原因、具体优化、实际修改和复验结果；在主要页面和核心流程仍存在明显关键缺陷或高价值改进项时，不标记为最终完成。
- 最终交付 Final Design QA Report，逐维说明当前状态、发现的问题、已执行优化、剩余提升空间及最终自检结论；静态检查、生成草图和模拟响应不得代替真实运行证据。
- 用户已明确同意本轮将真实 AI/TTS Provider 效果记录为未验证；未配置状态、请求取消、错误反馈及本地服务链路仍需验证。
- 用户已明确同意本轮将真机性能与动效记录为未验证；模拟器数据单独保留，不替代真机结论。
