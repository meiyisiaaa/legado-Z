---
name: Legado
description: 以原版 Legado 页面结构与信息层级为基准，延续 Android 阅读体验并保留 Web 配套界面的独立视觉系统。
colors:
  web-light-canvas: "#e8efee"
  web-light-panel: "#f7fbfa"
  web-light-ink: "#172729"
  web-light-accent: "#007f82"
  web-dark-canvas: "#10191b"
  web-dark-panel: "#172326"
  web-dark-ink: "#e5f0ef"
  web-dark-accent: "#53d4c9"
typography:
  web-interface:
    fontFamily: "Inter, 'Noto Sans SC', 'Microsoft YaHei', system-ui, sans-serif"
rounded:
  web-button: "11px"
  web-field: "12px"
  web-editor-control: "6px"
  web-book-card: "15px"
  web-dialog: "18px"
components:
  web-source-utility-button:
    rounded: "{rounded.web-editor-control}"
    padding: "0 8px"
    height: "38px"
  web-search-field:
    rounded: "{rounded.web-field}"
  web-source-panel-tab:
    rounded: "{rounded.web-editor-control}"
    height: "40px"
  web-book-card:
    rounded: "{rounded.web-book-card}"
    padding: "15px 34px 15px 15px"
  web-book-source-tag:
    rounded: "{rounded.web-editor-control}"
---

# Design System: Legado

## Overview

**Creative North Star: "沿用原版 Legado 页面布局与信息层级，在此基础上深度优化"**

Legado 的视觉优化从熟悉的页面组织、操作位置和阅读信息关系出发。Android 主界面仍以书架、发现、RSS、我的为底部导航；书架围绕分组、书籍列表或网格和原有工具入口组织。阅读内容继续由现有阅读引擎负责，Compose 用于普通页面与配置界面，专用编辑器和阅读引擎按需保留 Android View 互操作。

Android 与 Web 各自保留当前主题来源。Android 使用 ThemeStore 和 Material 3 语义角色响应用户主题、夜间模式和墨水屏设置；Web 由 app.css 的浅色与深色 CSS 变量驱动，阅读页另有可选纸张主题和字体。两套颜色不能互相覆盖，也不构成统一的固定品牌色板。

本规范记录可从当前实现确认的视觉规则，不代表页面迁移、设备渲染或交互验收已经通过。运行与验收证据单独记录在 docs/ui-redesign-acceptance.md。

本轮书架的原创“书页边注”规格见 [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md) 与 [SCREEN_SPEC_BOOKSHELF.md](docs/ui/SCREEN_SPEC_BOOKSHELF.md)，本轮实际证据见 [VISUAL_REVIEW.md](VISUAL_REVIEW.md)。MD3 分支只作参考，现有列表、紧凑和多列选项不作为原创方案。以下全局规则继续适用；普通书架书名采用下述局部排版例外。

**The Familiar Hierarchy Rule.** 在既有页面层级和操作入口内深化排版、密度、反馈与可读性，不以新的首页概念替换 Legado 的阅读流程。

**Key Characteristics:**
- Android 主导航维持四个原有目的地的图标式底栏，并按设置隐藏发现或 RSS。
- 书架保留分组、列表或多列网格、书籍元数据、刷新和原有菜单操作。
- Android 阅读页保留专用阅读引擎，阅读设置与朗读配置使用对应的普通配置界面。
- Web 书架、章节阅读、书源编辑和订阅源编辑保持独立的 hash 路由分区。
- Android 主题服从 ThemeStore、夜间模式和墨水屏设置；Web 主题服从自身的浅色、深色和阅读主题配置。

## Colors

Legado 有两套并行的颜色来源。Android 的颜色是运行时主题值；Web 的基础界面颜色是 CSS 变量。以下前置 token 只描述 Web app.css 的浅色与深色基准，不能当作 Android 的固定颜色。

### Primary
- **Web 浅色阅读青** (web-light-accent): 用于 Web 界面的主要操作、选中状态和强调文本。
- **Web 深色阅读青** (web-dark-accent): 用于深色模式的相同语义角色。
- **Android 主色角色**: Compose 的 primary 和 onPrimary 由 ThemeStore 当前强调色及其亮度对比色解析。工具栏另使用 ThemeStore 的 primaryColor，并按亮度选择前景色。

### Neutral
- **Web 浅色背景与文字** (web-light-canvas, web-light-panel, web-light-ink): 页面背景、内容面板和主要文字。
- **Web 深色背景与文字** (web-dark-canvas, web-dark-panel, web-dark-ink): 深色模式中的对应页面、面板和文字角色。
- **Android 表面角色**: background、surface、surfaceVariant、surfaceContainerLow 至 surfaceContainerHighest 和 outline 从用户背景与文字颜色派生。primaryContainer 与 secondaryContainer 分别从背景向强调色混合 16% 与 10%；surfaceVariant 混合前景 5.5%，surfaceContainerLow、Container、High、Highest 分别混合前景 2.5%、4.5%、7%、10%；outline 与 outlineVariant 分别混合前景 48% 与 16%。onPrimary 按强调色亮度阈值 0.179 选择黑或白。夜间方案跟随 AppConfig；墨水屏停用夜间色彩混合并使用黑白强调与完整前景轮廓。图像背景仅在主 Activity 根窗口使用透明表面，独立 Dialog 保持不透明。

**The Runtime Theme Rule.** Android Compose 页面始终读取现有 ThemeStore 与 AppConfig 主题状态；Web 的 CSS 色板只服务 Web，不作为 Android 回退色板。

## Typography

**Android 字体：** Compose 常规角色使用 FontFamily.Default，并将 Material 3 的 display、headline、title、body、label 映射到 Legado 的字号与行高。普通书架书名局部使用系统 Serif 18/26sp、Medium，最多两行；API26 中文采用系统回退字形，紧凑书名仍沿用原角色。Web 使用前置 web-interface 字体栈。

### Hierarchy
- **Display**：32/40、28/36、26/34 sp，SemiBold；保留给 display 语义。
- **Headline**：24/32、22/30、20/28 sp，SemiBold；页面或分区标题使用 Material 标题角色。
- **Title**：20/28、16/22、14/20 sp，Medium；用于工具栏标题、设置组标题和列表项目名称。
- **Body**：16/24、14/22、13/18 sp，Normal；承载阅读操作说明、列表详情和表单内容。
- **Label**：14/20、12/16、11/14 sp，Medium；用于导航、辅助信息、状态和控件标签。Compose 字体间距为 0sp。
- **Web 阅读正文**：继续使用阅读配置中选择的字体、字号、行高和页面主题，不被管理界面的字体栈替换。

**The Role Scale Rule.** Android 文字优先通过 Material 3 类型角色表达；Web 阅读正文继续服从阅读设置，不将管理界面的字号层级强加给正文。

## Layout

Android 的主 Activity 保留 ViewPager 与底部导航的既有分区关系。导航顺序是书架、发现、RSS、我的；页面列表由配置过滤，发现和 RSS 可分别隐藏。导航只显示图标，目的地名称由无障碍语义提供；书架更新时在书架图标显示数量徽标。底栏高度为 56dp，并处理系统导航栏 inset。对应实现见 app/src/main/java/io/legado/app/ui/main/MainActivity.kt 和 MainNavigation.kt。

书架以分组栏和书籍内容为主。分组项可切换，长按进入分组编辑；内容沿用配置的普通列表、紧凑列表或 2 至 6 列网格。刷新、快速滚动、书籍打开和详情操作围绕原有书架条目进行。书架布局、排序、分组样式、书名、未读、更新时间、待更新数量和快速滚动由既有设置控制。对应实现见 app/src/main/java/io/legado/app/ui/main/bookshelf/BookshelfContent.kt、BookshelfGroups.kt、BookshelfOptions.kt 和 BaseBookshelfFragment.kt。

搜索、书籍编辑和阅读配置保持独立任务流。书内搜索保留查询、替换、正则、结果计数和结果定位；源规则表单继续与 CodeView 等专用编辑器协作。阅读正文和漫画阅读仍由各自引擎承载，阅读配置、HTTP TTS 与角色朗读设置使用普通界面，不重排阅读引擎的阅读区域。对应实现见 app/src/main/java/io/legado/app/ui/book/searchContent/SearchContentScreen.kt、ui/book/source/edit/SourceEditContent.kt 和 ui/book/read/config/。

Web 使用 Vue 3 的 hash 路由分区：/ 是书架，/chapter 是章节阅读，/bookSource 与 /rssSource 分别进入书源和订阅源编辑。编辑页在宽屏将规则表单、操作工具栏和源工具并排；980px 及以下切换为规则字段与源工具两个可切换面板。Web 书架网格在窄屏收为单列，540px 是当前卡片网格切换点。路由见 modules/web/src/router/bookRouter.ts、sourceRouter.ts；页面见 modules/web/src/views/BookShelf.vue、BookChapter.vue、SourceEditor.vue。

**The Existing Destination Rule.** 保持 Android 底部目的地顺序、书架分组与书籍条目关系，以及 Web 的书架、章节和源编辑路由；响应式变化只调整空间分配和面板呈现。

## Elevation & Depth

Android 使用 Material 3 语义表面层级表达内容关系。LegadoTheme 从当前背景与前景派生 container 层级；普通配色以轻微色调差区分容器，墨水屏模式收敛为黑白表面并使用清晰轮廓线。不要用任意阴影替代这些语义层。

Web 通过页面底色、内容面板、抬升面板及边线区分层级；阴影只在书卡交互态和对话框上使用。app.css 定义浅色与深色两组对话框阴影和轻层阴影；它们不应用于每张普通卡片的静止态。

**The Semantic Surface Rule.** Android 的层级来自 Material 表面角色；Web 的阴影只强化有明确浮层或交互提升语义的内容。

## Shapes

Android 的 Material 3 形状依次为 extraSmall 4dp、small 8dp、medium 12dp、large 16dp、extraLarge 24dp。控件、设置分组和浮层使用相应级别，保持圆角连续而不互相夸张。

Web 的管理界面使用较紧凑的工具按钮、输入框、书籍卡片和对话框圆角。前置 rounded token 记录 Web 当前 CSS 实际值；书籍卡片在 540px 窄屏切换为更紧凑的圆角与间距。轮廓通常使用主题边线变量，不用重边框装饰普通内容。

## Components

### Navigation
Android 使用图标式 Material NavigationBar。选中态切换原有未选中/选中图标，保留书架更新数徽标；墨水屏底栏顶部绘制 1dp 黑线。Compose TopAppBar 使用 56dp 高度，以 ThemeStore 主色为底，并按该色亮度计算标题、返回按钮和操作图标前景；墨水屏模式在工具栏底部绘制 1dp 分隔线。对应实现见 ui/main/MainNavigation.kt 和 ui/widget/compose/LegadoTopAppBar.kt。

### Bookshelf Groups and Entries
分组项是横向滚动的 48dp 选择目标；点击切换，长按编辑。书架条目按现有列表或网格布局呈现封面、书名、作者、阅读进度、最新章节、更新时间与更新状态。点击打开书籍，长按进入现有详情/操作流程；卡片布局不得遮盖来源、章节或未读信息。Compose 书架继续复用 CoverImageView 加载封面，避免改变原封面数据通路。

### Search and Editing
搜索栏提供系统键盘 Search 动作、清空和更多操作；结果高亮当前查询并允许定位。源编辑保留原字段顺序、规则选项和工具操作，代码/脚本字段可通过局部 AndroidView 承载 CodeView；整个编辑页面不以旧 RecyclerView 容器替代普通 Compose 布局。

### Reader and Speech Settings
阅读配置沿既有分区呈现字体、字号、颜色和页面行为。HTTP TTS 保留脚本编辑专用视图与保存、完整编辑、登录信息和退出未保存确认等流程；角色朗读使用角色、章节选择、生成和状态操作。普通选项使用 LegadoTheme 与 Material 控件，专用脚本编辑器继续承担代码输入。

### Web Book Card
Web 书架卡片保留封面与元数据并以整张卡片打开书籍。桌面卡片有封面、标题、作者、章节数、更新时间和章节信息；搜索结果复用相同的书籍条目并显示搜索简介和来源标签。键盘焦点与鼠标悬停也显示前进提示，交互状态使用主题强调边线和浅层阴影。

### Web Source Editor
源编辑工具栏保持规则表单、操作工具和源工具三块关系；窄屏用两个具备 tab 语义的面板切换。选中面板由强调边线和浅强调面表示，键盘方向键、Home、End 可切换并聚焦面板标签。书源与订阅源共享编辑器而保留不同路由和字段配置。

### Motion and Accessibility
Android LocalLegadoMotion 提供 press 120ms、state 240ms、container 360ms 三档时长；E-Ink 模式或 Android 系统关闭动画时三档均为 0ms。Web 状态过渡为 240ms、面板过渡为 360ms；prefers-reduced-motion: reduce 时将动画和过渡压至 0.01ms、限制为单次迭代并关闭平滑滚动。交互目标遵循至少 48dp 的 Android 触控基线，文字使用 sp；Web 控件保留可见键盘焦点状态。

## Do's and Don'ts

### Do:
- Do：沿用 Legado 原有页面层级与熟悉的操作入口，优化间距、信息密度、可读性与反馈。
- Do：保留 Android 目的地顺序、图标式底部导航、发现/RSS 可隐藏设置和书架更新徽标。
- Do：使用 Android ThemeStore 与 Material 3 语义角色，并保留夜间和墨水屏行为。
- Do：保留用户设置的阅读字体、字号、页面颜色、源规则及专用阅读或脚本引擎。
- Do：遵守 Android 系统动画设置、墨水屏动效抑制、Web 减少动态效果偏好、系统 inset 和键盘焦点状态。

### Don't:
- Don't：不要把熟悉的书架流程改成营销仪表盘、超大继续阅读主卡片或固定继续阅读条。
- Don't：不要在没有对应原有入口的情况下，把书架、编辑器或阅读操作移到新的菜单位置。
- Don't：不要用通用 Compose 内容替换原生阅读引擎或脚本引擎。
- Don't：不要把 Web 书架、章节阅读、书源编辑和订阅源编辑合并成同一页面或路由。
