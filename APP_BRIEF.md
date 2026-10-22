# Legado-Z 产品与设计简报

本项目以现有 Legado 阅读产品为基础，保留已有书籍数据、功能和 Android 技术框架，并参考 [HapeLee/legado-with-MD3（3.26.18）](https://github.com/HapeLee/legado-with-MD3/tree/3.26.18) 的视觉改绘作为灵感来源；最终界面须原创，不照搬其页面或素材。首阶段 Android 书架核心 UI 已实现；其他页面仍是后续规格，尚未据此完成改绘或验收。`PRODUCT.md`、`DESIGN.md` 和源码用于了解现状，不构成禁止原创视觉探索或有价值功能接入的额外限制。

## 目标用户与场景

**有材料支持的目标用户：**使用自定义书源、本地书籍、订阅和朗读的中文读者。App 不预置书源或书籍内容，读者需要导入自己的书源或本地文件。依据：`PRODUCT.md:11`、`README.md:3-5`。

主要场景：

- 初次使用：导入自有书源，或打开 TXT / EPUB 文件，开始阅读。依据：`README.md:30-33`。
- 日常阅读：从书架分组中找到书，检查阅读进度、最新章节和更新状态，再打开书籍。
- 书籍维护：刷新目录、管理书架、导入或导出书架、按个人习惯调整列表或网格。依据：`DESIGN.md:94`、`BaseBookshelfFragment.kt:87-119,161-197`。
- 扩展使用：通过发现页搜索或维护自定义书源，阅读 RSS 订阅，并使用朗读、AI 阅读辅助等能力。依据：`README.md:16-22,38-54`。

## 核心任务与页面优先级

以下为基于产品用途和当前入口的设计优先级建议，不是用户研究结论。

| 优先级 | 页面 / 流程 | 设计重点 |
| --- | --- | --- |
| P0 | **书架** | 让读者快速找到书、理解进度和更新状态，并继续阅读或进入书籍详情。默认主页面，也是首阶段视觉验证页。 |
| P0 流程承接 | **阅读器与目录** | 书架打开书籍后的核心阅读流程；保留原阅读引擎与现有阅读设置，按书架完成后的页面评审继续推进。 |
| P1 | **发现与书籍搜索** | 帮助读者使用自己导入的书源发现和搜索内容；现有发现页也承担书源分类、搜索和维护入口。 |
| P2 | **RSS / 订阅** | 阅读与整理订阅内容，保持独立导航入口。 |
| P3 | **我的 / 设置与维护** | 承载配置、备份和其他工具；后续按页面规格评审入口组织与优先级。 |

Android 主导航顺序为书架、发现、RSS、我的；发现和 RSS 可由设置隐藏。`MainActivity.kt:370-412`、`MainNavigation.kt:32-65`。默认首页为书架，但用户设置可选择发现、RSS 或我的；见 `AppConfig.kt:571`、`MainActivity.kt:400-412`。

## 首个核心页面与现有调用链

首阶段以**书架页**为核心验证对象，默认 `bookGroupStyle = 0` 的分组标签样式作为主要验收路径，同时保留 `style2` 书架入口。现有主导航、分组关系、书籍数据与打开/管理行为是接入设计的基线；书架呈现可原创重构，不能把旧文档中的视觉约束解释为禁止新设计或新功能。`AppConfig.kt:233-242` 显示分组样式默认值为 `0`；`MainActivity.kt:415-420` 中 `1` 选择 `BookshelfFragment2`，其他值选择 `BookshelfFragment1`。

### 书架视觉方向与本轮实现

概念页比较了同一普通列表的三种原创视觉语言；本轮选择并实现**书页边注**：书名使用系统 Serif 18/26sp，作者使用 `bodySmall`，有数据时当前阅读位置与最新章节以“读至”“更新”短标签和细竖线组织。标签按自然文本宽度布局，最小 28dp，后接 8dp 间隔。普通列表封面宽 66dp、内容边界 16dp，书名最多两行；紧凑列表仍为一行，网格保留用户偏好，并按可用宽度和字体缩放调整实际列数。三种方案见 `docs/ui/CONCEPTS.md`，具体实现约束见 `docs/ui/SCREEN_SPEC_BOOKSHELF.md`。

**书目索引**与**章节对照**是已比较但未实现的视觉方案，不是当前可选模式。书架现有列表/紧凑/网格选项仍保留。本轮实现了局部书名排版与章节边注；不代表参考项目的其他功能已经接入。功能差异须另行核对，确认适配性后再考虑纳入后续阶段。

当前主要路径：

1. `app/src/main/res/layout/activity_main.xml:9-20` 用 `ViewPager` 承载主页面、用 `ComposeView` 承载底部导航。
2. `app/src/main/java/io/legado/app/ui/main/MainActivity.kt:182-197,431-462` 初始化 `TabFragmentPageAdapter`，按可见目的地创建主页面 Fragment，并在底栏与页面间切换。
3. 默认分组标签布局由 `app/src/main/res/layout/fragment_bookshelf1.xml:8-19` 提供标题栏和书架分组 `ViewPager`。`app/src/main/java/io/legado/app/ui/main/bookshelf/style1/BookshelfFragment1.kt:77-100,142-190` 以 Compose 绘制分组标签，再由内层 `TabFragmentPageAdapter` 为每组创建 `BooksFragment`。
4. `app/src/main/java/io/legado/app/ui/main/bookshelf/style1/books/BooksFragment.kt:67-104` 通过 `LegadoComposeView` 调用共享的 `BookshelfContent`，并从 `bookDao.flowByGroup(groupId)` 获取和排序书籍。
5. 另一种布局由 `app/src/main/res/layout/fragment_bookshelf2.xml:6-10` 提供标题栏和 `ComposeView`；`app/src/main/java/io/legado/app/ui/main/bookshelf/style2/BookshelfFragment2.kt:62-77,95-112,127-142` 调用同一 `BookshelfContent`，读取书架数据并处理分组、打开书籍与详情。
6. `app/src/main/java/io/legado/app/ui/main/bookshelf/BookshelfContent.kt:59-64,76-168` 处理加载、失败、空书架、下拉刷新、列表和网格；`BookshelfContent.kt:201-213` 定义书籍条目的点击与长按。书籍条目使用 Compose `LazyColumn` / `LazyVerticalGrid`，当前调用路径中没有独立的 RecyclerView 书籍 Adapter；ViewPager Adapter 负责页面或分组切换。
7. `app/src/main/java/io/legado/app/ui/main/bookshelf/BaseBookshelfFragment.kt:87-119,161-197` 集中承接搜索、添加本地书、书架管理、导入导出、下载和书架显示设置等既有菜单行为。

书架实现继续承接分组、列表/紧凑列表/网格偏好、封面、书名、作者、阅读进度、最新章节、更新时间与更新状态，并复用现有封面数据通路。分组点击切换、长按编辑；书籍点击打开、长按进入现有详情操作。依据：`DESIGN.md:94,122`、`BookshelfGroups.kt:47-75`、`BookshelfContent.kt:118-168,201-265`。网格保持保存的 2–6 列偏好；窄屏或放大字体时实际列数可减少。

## 情绪目标

以下是待视觉验证的设计假设，不是已确认的用户偏好：界面应让读者感到熟悉、安静、可控、可信。书架应优先帮助读者辨认自己的书、进度和更新情况；加载、错误与空书架状态应让下一步行动清楚。设计情绪仅作待验证假设。

## 平台与实现约束

- 本轮平台为 Android。项目最低支持 Android 8.0 / API 26；产品约束列出 API 26 与 API 36。依据：`README.md:8`、`PRODUCT.md:23`。
- Android 普通页面逐屏使用 Compose；保留现有 Activity、Fragment、Intent 和结果契约。阅读、漫画、WebView、代码编辑及媒体引擎按现有专用实现互操作。依据：`PRODUCT.md:20`。
- 当前书架是 XML / View / Fragment 外壳与 Compose 内容并存；Compose 使用 Material 3，数据通过 Room，异步数据流使用 Kotlin Coroutines。依据：`app/build.gradle:72-75,170-179,244-248`、`PRODUCT.md:20`。
- 沿用 ThemeStore 与用户主题、夜间模式、墨水屏和系统减少动画设置；Android 触控目标至少 48dp，并保留可访问名称。依据：`DESIGN.md:45-47,74,119,139-145`、`PRODUCT.md:23,45`。
- 保留四个主导航目的地及发现/RSS 隐藏开关、书架现有分组与菜单位置；不覆盖书源、阅读进度、主题、备份或密钥。依据：`PRODUCT.md:22-24`。

## 本轮范围与验收

**首阶段实现：**默认分组标签宿主与共享 `BookshelfContent` 的 Android 书架核心 UI 已完成，`style2` 继续调用同一内容组件。保留既有阅读、搜索、分组、导入和管理调用契约；本状态描述实现范围，不代表最终视觉/测试验收通过。

**后续扩展：**首阶段验收不代表产品仅限书架页。结合方案评审和功能差异对照，可继续处理其他高优先级页面或接入适配的功能；扩展时仍保留已有功能、数据和框架，逐项核实实现状态。

**验收要求：**

- 本轮书架保留分组与书籍内容关系、现有工具入口、显示偏好及用户主题；后续页面结构与功能可按各自规格和评审扩展。
- 书籍打开、详情、分组选择与编辑、刷新、快速滚动等已有操作仍可到达原流程。
- 加载中、失败、空数据及重试状态清楚；书籍进度、最新章节和更新状态在当前布局中可读。
- 尊重主题、墨水屏、减少动画、触控与无障碍要求；沿用封面数据通路。
- 验收应基于实际渲染与交互结果；静态源码检查不能代替运行界面检查。全范围 Design QA / UX QA 和最终报告要求见 `PRODUCT.md:50-55`。

**当前范围边界：**本阶段实际 UI 改动集中在书架。发现、搜索、阅读器、订阅、我的与设置已整理为后续页面规格，但尚未据此重绘或验收；不宣称全 App 已改版。全 App 实施和功能差异仍按评审结果分阶段处理。

## 视觉参考与验证边界

视觉灵感参考：[HapeLee/legado-with-MD3（3.26.18）](https://github.com/HapeLee/legado-with-MD3/tree/3.26.18)。真实参考截图包括 `docs/ui/assets/reference-md3-bookshelf.png` 和 `docs/ui/assets/reference-md3-my.png`；书架图是官方独立包的空态，不代表本项目目标布局。实现前比较的三个原创书架方案见 `docs/ui/CONCEPTS.md`。当前运行截图保存在 `docs/ui/assets/runtime/`；书架的实际结果、截图及未覆盖范围见 [VISUAL_REVIEW.md](VISUAL_REVIEW.md)，不代表全 App 已完成。
