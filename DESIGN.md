---
name: Legado
description: Android 以矿绿书衣与连续纸面统一阅读和维护界面，保留用户主题及 Web 配套界面的独立视觉系统。
colors:
  android-cloth: "#183F38"
  android-accent: "#24675B"
  android-paper: "#F7F7F2"
  android-bottom: "#EBEEE7"
  android-night-cloth: "#172E2A"
  android-night-accent: "#A3D4C1"
  android-night-paper: "#151D1B"
  android-night-bottom: "#1C2824"
  web-light-canvas: "#e8efee"
  web-light-panel: "#f7fbfa"
  web-light-ink: "#172729"
  web-light-accent: "#007f82"
  web-dark-canvas: "#10191b"
  web-dark-panel: "#172326"
  web-dark-ink: "#e5f0ef"
  web-dark-accent: "#53d4c9"
typography:
  android-display:
    fontFamily: "sans-serif"
    fontSize: "32sp"
    fontWeight: 600
    lineHeight: "40sp"
    letterSpacing: "0sp"
  android-headline:
    fontFamily: "sans-serif"
    fontSize: "24sp"
    fontWeight: 600
    lineHeight: "32sp"
    letterSpacing: "0sp"
  android-title:
    fontFamily: "sans-serif"
    fontSize: "16sp"
    fontWeight: 500
    lineHeight: "22sp"
    letterSpacing: "0sp"
  android-body:
    fontFamily: "sans-serif"
    fontSize: "14sp"
    fontWeight: 400
    lineHeight: "22sp"
    letterSpacing: "0sp"
  android-label:
    fontFamily: "sans-serif"
    fontSize: "12sp"
    fontWeight: 500
    lineHeight: "16sp"
    letterSpacing: "0sp"
  android-book-title:
    fontFamily: "serif"
    fontSize: "18sp"
    fontWeight: 500
    lineHeight: "26sp"
    letterSpacing: "0sp"
  android-book-identity:
    fontFamily: "serif"
    fontSize: "28sp"
    fontWeight: 400
    lineHeight: "36sp"
    letterSpacing: "0sp"
  web-interface:
    fontFamily: "Inter, 'Noto Sans SC', 'Microsoft YaHei', system-ui, sans-serif"
rounded:
  android-extra-small: "4dp"
  android-small: "8dp"
  android-medium: "12dp"
  android-large: "16dp"
  android-extra-large: "24dp"
  web-button: "11px"
  web-field: "12px"
  web-editor-control: "6px"
  web-book-card: "15px"
  web-dialog: "18px"
spacing:
  android-tight: "4dp"
  android-related: "8dp"
  android-inner: "12dp"
  android-content: "16dp"
  android-reading-edge: "20dp"
  android-section: "24dp"
  android-state: "32dp"
components:
  android-book-read-button:
    backgroundColor: "{colors.android-accent}"
    rounded: "{rounded.android-medium}"
    padding: "8dp 16dp"
  android-book-shelf-button:
    rounded: "{rounded.android-medium}"
    padding: "8dp 16dp"
  android-source-search:
    rounded: "{rounded.android-medium}"
  android-tool-bar:
    backgroundColor: "{colors.android-cloth}"
    height: "56dp"
  android-article-image:
    rounded: "{rounded.android-small}"
  android-article-waterfall:
    rounded: "{rounded.android-medium}"
    padding: "12dp"
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

**Creative North Star: "矿绿书衣，连续纸面"**

Android 以用户选择的 A「矿绿书衣」为现行视觉方向：书籍身份在完整的书衣色场中被识别，阅读与维护落在清楚的纸面层。详情以非对称封面、局部衬线书名和书签细节形成识别；书源、文章、书签与记录将同一语言转译为矿绿导航、连续列表、细线和清楚的文字层级。概念种子 `d57b1583` 作为方向来源记录，用户选择 A 决定最终方向；五块方向契约见 [CONCEPTS_V3.md](docs/ui/CONCEPTS_V3.md)。

熟悉的四目的地导航、页面层级、操作入口和完整阅读能力继续保留。Compose 承担普通内容，专用阅读、代码编辑与媒体引擎保持互操作。Android 使用 ThemeStore 与 Material 3 语义角色响应用户主题、夜间和墨水屏；Web 由 app.css 的浅深 CSS 变量驱动，阅读正文另服从用户选择的字体与纸张主题。

本文件以当前源码提取长期视觉规则；[DESIGN_SYSTEM.md](DESIGN_SYSTEM.md) 补充 Design DNA、语义映射、组件模式与平台适配；页面构图见 `docs/ui/SCREEN_SPEC_*_V3.md`，实际设备与交互证据见 [VISUAL_REVIEW.md](VISUAL_REVIEW.md)。原书架“书页边注”继续作为列表模式使用。

**The Familiar Hierarchy Rule.** 在既有页面层级和操作入口内深化排版、密度、反馈与可读性，不以新的首页概念替换 Legado 的阅读流程。

**Key Characteristics:**
- Android 主导航维持四个原有目的地的图标式底栏，并按设置隐藏发现或 RSS。
- 书架保留分组、列表或多列网格、书籍元数据、刷新和原有菜单操作。
- Android 阅读页保留专用阅读引擎，阅读设置与朗读配置使用对应的普通配置界面。
- Web 书架、章节阅读、书源编辑和订阅源编辑保持独立的 hash 路由分区。
- Android 主题服从 ThemeStore、夜间模式和墨水屏设置；Web 主题服从自身的浅色、深色和阅读主题配置。
- 详情使用书衣与纸面的鲜明主次，管理页用连续内容和细分隔保持扫描效率。

## Colors

前置 Android 颜色 token 记录 `mineral_colors.xml` 的默认主题值，用户已保存的主题仍由 ThemeStore 解析；组件中的颜色引用展示默认映射。Web token 保留 app.css 的现行值。页面代码通过语义角色取色。

sidecar 的八阶 OKLCH 色带用于色板浏览；运行时颜色继续由源码中的语义混合与可读性算法生成。Android HTML/CSS 组件预览是原生结构的展示转译，原生材质、输入状态和物理动效以实际 Android 渲染为准。

### Primary
- **Web 浅色阅读青** (web-light-accent): 用于 Web 界面的主要操作、选中状态和强调文本。
- **Web 深色阅读青** (web-dark-accent): 用于深色模式的相同语义角色。
- **Android 矿绿书衣** (android-cloth, android-night-cloth): 默认导航与详情身份区；`tertiaryContainer` 读取 ThemeStore `primaryColor`，`onTertiaryContainer` 选择与其对比更高的黑或白。
- **Android 阅读绿** (android-accent, android-night-accent): `primary`、`secondary`、`tertiary` 共用可读性调整后的用户强调色，用于主要操作、选中与个人备注。`contrastSafeAccent()` 在组合后的七种表面上搜索至少 4.5:1 的邻近 HSL 明度；极端自定义配色无共同可行值时选择最低对比度较高的黑或白。`onPrimary` 等取对比更高的黑或白。

### Neutral
- **Web 浅色背景与文字** (web-light-canvas, web-light-panel, web-light-ink): 页面背景、内容面板和主要文字。
- **Web 深色背景与文字** (web-dark-canvas, web-dark-panel, web-dark-ink): 深色模式中的对应页面、面板和文字角色。
- **Android 纸面与底栏** (android-paper, android-bottom, android-night-paper, android-night-bottom): 默认正文与导航底面。普通 `background` / `surface` 使用用户背景；`surfaceVariant` 混合前景 5.5%，container 的 Lowest / Low / Container / High / Highest 分别混合前景 0% / 2.5% / 4.5% / 7% / 10%；`outline` / `outlineVariant` 分别混合前景 48% / 16%。`onSurface` / `onSurfaceVariant` 取 ThemeStore 主/次文字色。
- **Android 选择表面**: `primaryContainer` / `secondaryContainer` 分别从用户背景向原始强调色混合 16% / 10%，配主题主文字色。`inversePrimary` 将浅橄榄书签基色与强调色混合 12%，只承担书签细节。
- **Android 状态色**: `error` 浅色为深红、深色为浅珊瑚，成对前景和容器由 `LegadoTheme.kt` 显式提供；错误同时具有文案和语义。墨水屏收敛为白底、黑色强调、完整前景轮廓，纹理与运动关闭。图像背景只在 Activity 根窗口使用透明表面，独立 Dialog 保持不透明。

**The Runtime Theme Rule.** Android Compose 页面始终读取现有 ThemeStore 与 AppConfig 主题状态；Web 的 CSS 色板只服务 Web，不作为 Android 回退色板。

**The Cloth and Paper Rule.** 书衣承担身份与导航，纸面承担阅读和维护；工具列表以细分隔组织，色块只服务真实层级与状态。

## Typography

**Android 字体：** Compose 常规角色使用 FontFamily.Default，并将 Material 3 的 display、headline、title、body、label 映射到 Legado 的字号与行高。普通书架书名局部使用系统 Serif 18/26sp、Medium，最多两行；API26 中文采用系统回退字形，紧凑书名仍沿用原角色。Web 使用前置 web-interface 字体栈。

原生系统字体与字形回退是有意的平台适配：管理界面优先保持中文覆盖、系统缩放与熟悉的控件度量。详情身份书名使用系统 Serif、28/36sp、Normal，设置分组局部采用 Serif 20/28sp、Medium；衬线只建立书籍与分区气质。前置 typography 为代表角色，完整大小层级如下。

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

Android 固定间距以 4 / 8 / 12 / 16 / 20 / 24 / 32dp 建立关系：工具列表以 16dp 为水平基线，详情纸面、书签与阅读记录以 20dp 为阅读基线，详情书衣使用 24dp 内距。详情常规封面为 136×196dp，图文相隔 24dp并垂直居中；可用宽度小于 360dp 时封面缩为 104×150dp、与书名相隔 16dp横排，分类位于右侧书名/作者之下、与小封面同行，来源/分组进入下方；fontScale ≥1.5 时封面与身份纵向排列，仍使用较小封面。compact 常规字体的书衣顶部/底部内距为 16/12dp，常规为 24/32dp，大字为 24/20dp；这些属于详情局部约束。底部两项操作平分宽度、间隔 12dp，最小高度 52dp并随文案增高。书签与记录内容最大宽度为 840dp、居中排列。

书架以分组栏和书籍内容为主。分组项可切换，长按进入分组编辑；内容沿用配置的普通列表、紧凑列表或 2 至 6 列网格。刷新、快速滚动、书籍打开和详情操作围绕原有书架条目进行。书架布局、排序、分组样式、书名、未读、更新时间、待更新数量和快速滚动由既有设置控制。对应实现见 app/src/main/java/io/legado/app/ui/main/bookshelf/BookshelfContent.kt、BookshelfGroups.kt、BookshelfOptions.kt 和 BaseBookshelfFragment.kt。

搜索、书籍编辑和阅读配置保持独立任务流。书内搜索保留查询、替换、正则、结果计数和结果定位；源规则表单继续与 CodeView 等专用编辑器协作。阅读正文和漫画阅读仍由各自引擎承载，阅读配置、HTTP TTS 与角色朗读设置使用普通界面，不重排阅读引擎的阅读区域。对应实现见 app/src/main/java/io/legado/app/ui/book/searchContent/SearchContentScreen.kt、ui/book/source/edit/SourceEditContent.kt 和 ui/book/read/config/。

Web 使用 Vue 3 的 hash 路由分区：/ 是书架，/chapter 是章节阅读，/bookSource 与 /rssSource 分别进入书源和订阅源编辑。编辑页在宽屏将规则表单、操作工具栏和源工具并排；980px 及以下切换为规则字段与源工具两个可切换面板。Web 书架网格在窄屏收为单列，540px 是当前卡片网格切换点。路由见 modules/web/src/router/bookRouter.ts、sourceRouter.ts；页面见 modules/web/src/views/BookShelf.vue、BookChapter.vue、SourceEditor.vue。

**The Existing Destination Rule.** 保持 Android 底部目的地顺序、书架分组与书籍条目关系，以及 Web 的书架、章节和源编辑路由；响应式变化只调整空间分配和面板呈现。

## Elevation & Depth

Android 以连续纸面、细分隔与 Material 3 语义表面层级表达内容关系，`surfaceTint` 为透明。普通列表保持平面；书籍详情的实体封面单独使用 6dp 阴影和 4dp 裁切圆角，表达放置在书衣上的物体。详情书衣叠加静态 `mineral_cloth` 资源（alpha 0.35、Modulate），墨水屏省略纹理。浮层按自身语义使用平台层级。纹理与封面阴影是书籍身份区的材质语言，延展时保留其用途。

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

### Mineral Book Identity and Actions
详情书衣使用 `tertiaryContainer/onTertiaryContainer`，封面、书名、作者、来源与分组在同一身份区内排列。常规书衣末端的 16dp 纸面带与随实际封面底沿计算起点的 20×30dp Canvas 书签跨接缝衔接；来源/分组的原操作邻接真实字段，分类横向滚动。当前/最新章节组合在 1dp 细框、small 圆角和 16dp 内距的阅读区域，简介与非空备注接续纸面，备注保留文本选择。底部阅读用实色 `primary`，书架操作用柔和 `secondaryContainer`，两者使用已有图标、medium 圆角、最小 52dp 高度和 16dp 水平/8dp 垂直内容内距。

### Source Management
搜索框为 12dp 圆角，名称与辅助域名/分组先行，启用开关、编辑与更多在第二行；名称最多两行。选中以 `primaryContainer` 与 Checkbox 同时表达，启停由 Switch 和状态文字独立表达。编辑/更多为 48dp 目标，批量工具拆为计数/全选与动作两层。域名吸顶头部和分组 Dialog 延续相同纸面与细线；嵌套输入 Dialog 继续使用原生焦点、IME 和返回行为。

### Articles and Favorites
五种文章模式保留图文行、大图、二列、瀑布流、三列。图文行采用右侧缩略图与 12dp 图文间隔；fontScale ≥1.5 时图片宽度从 96dp 收为 64dp，高度保持 76dp。大图区域高 220dp；二/三列图片宽高比 0.75；固定画框图片使用 CENTER_CROP，缺图占位使用 ContentScale.Crop。瀑布流使用 168dp 自适应单元、medium 圆角、细轮廓和低层表面，图片自然高度配 FIT_CENTER。已读标题使用次级前景与 Normal，未读使用主要前景与 SemiBold，同时表达状态。收藏复用图文行并保留长按删除。

### Bookmarks and Reading Records
书签以书名/作者组成不透明吸顶头，真实数量作为辅助信息；章节、原文与个人备注分别使用标题、次级正文与主色正文。章节最多两行、摘录及备注各最多三行预览。阅读记录以紧凑总时长行承接连续记录，书名、时长和日期逐层排列，单项清除具有书名专属可访问名称。保留搜索、排序、确认、导出和原数据责任。

### Reader and Speech Settings
阅读配置沿既有分区呈现字体、字号、颜色和页面行为。HTTP TTS 保留脚本编辑专用视图与保存、完整编辑、登录信息和退出未保存确认等流程；角色朗读使用角色、章节选择、生成和状态操作。普通选项使用 LegadoTheme 与 Material 控件，专用脚本编辑器继续承担代码输入。

### Web Book Card
Web 书架卡片保留封面与元数据并以整张卡片打开书籍。桌面卡片有封面、标题、作者、章节数、更新时间和章节信息；搜索结果复用相同的书籍条目并显示搜索简介和来源标签。键盘焦点与鼠标悬停也显示前进提示，交互状态使用主题强调边线和浅层阴影。

### Web Source Editor
源编辑工具栏保持规则表单、操作工具和源工具三块关系；窄屏用两个具备 tab 语义的面板切换。选中面板由强调边线和浅强调面表示，键盘方向键、Home、End 可切换并聚焦面板标签。书源与订阅源共享编辑器而保留不同路由和字段配置。

### Motion and Accessibility
Android LocalLegadoMotion 提供 press 120ms、state 240ms、container 360ms 三档时长。共享 `legadoPressFeedback` 实际使用可中断 spring（dampingRatio=1、stiffness=700），按压绘制缩放至 0.985，测量与命中区域保持稳定。书源选择颜色使用 state tween、内容大小使用 container tween；书签和记录增删/排序使用 state tween 的淡入、淡出与位置插值。E-Ink 或系统关闭动画时 token 为 0ms、spring 替换为 snap，Ripple 关闭；文字与图形状态继续表达结果。

Web 状态过渡为 240ms、面板过渡为 360ms；prefers-reduced-motion: reduce 时将动画和过渡压至 0.01ms、限制为单次迭代并关闭平滑滚动。Android 触控基线为至少 48dp、文字为 sp；Web 保留可见键盘焦点。Android 当前已实现，iOS 的 Safe Area、Dynamic Type、VoiceOver、Reduce Motion 与至少 44pt 命中区属于未来建议，详见 DESIGN_SYSTEM.md 的 Platform Adaptation。

## Do's and Don'ts

### Do:
- Do：沿用 Legado 原有页面层级与熟悉的操作入口，优化间距、信息密度、可读性与反馈。
- Do：保留 Android 目的地顺序、图标式底部导航、发现/RSS 可隐藏设置和书架更新徽标。
- Do：使用 Android ThemeStore 与 Material 3 语义角色，并保留夜间和墨水屏行为。
- Do：保留用户设置的阅读字体、字号、页面颜色、源规则及专用阅读或脚本引擎。
- Do：遵守 Android 系统动画设置、墨水屏动效抑制、Web 减少动态效果偏好、系统 inset 和键盘焦点状态。
- Do：按任务转译书衣与纸面；长文本与大字通过换行、纵向重排和滚动保持操作可达。

### Don't:
- Don't：不要把熟悉的书架流程改成营销仪表盘、超大继续阅读主卡片或固定继续阅读条。
- Don't：不要在没有对应原有入口的情况下，把书架、编辑器或阅读操作移到新的菜单位置。
- Don't：不要用通用 Compose 内容替换原生阅读引擎或脚本引擎。
- Don't：不要把 Web 书架、章节阅读、书源编辑和订阅源编辑合并成同一页面或路由。
- Don't：不要将概念图示例内容、装饰性编号、无真实用途的统计或逐条入场序列写入产品界面。
- Don't：不要将发现的触控尺寸缺陷固化为紧凑组件标准；触控基线继续为 48dp。
