# Legado-Z Design System

本文件定义 Android 的语义 token、组件状态与跨平台建议。书架核心 UI 已有实现，本轮继续接入发现、搜索、RSS、我的与共享设置分组、目录内容、书目备注和阅读进度筛选。**现行 / 本轮实现**描述源码，**计划**描述后续设计；API36 的约 411dp 常规屏、320dp 小屏及 1×/2× 字体已执行状态、操作和截图检查，分项结果及复验进展见 [VISUAL_REVIEW.md](VISUAL_REVIEW.md)。

运行时主题以 `app/src/main/java/io/legado/app/ui/widget/compose/LegadoTheme.kt` 和 `ThemeStore` 为准。`DESIGN.md`、`PRODUCT.md` 用于了解既有信息结构、功能和平台边界，原创呈现与差异功能按用户授权推进。强调色和前景当前采用对比度安全选择。现有主题、全局字体设置和动效系统保持；书架普通列表、搜索结果和编辑预览通过 `BookTypography.kt` 共享局部 `bookTitle` 角色，设置分组另用局部衬线标题，阅读正文继续服从阅读设置。

## Design DNA

Legado 是以读者自己的书籍和书源为中心的阅读工具。设计围绕找书、识别进度、发现更新和继续阅读展开，尊重现有数据、核心行为和 Android 技术框架，同时为界面呈现保留原创探索空间。[HapeLee/legado-with-MD3（3.26.18）](https://github.com/HapeLee/legado-with-MD3/tree/3.26.18) 提供视觉灵感与功能对照，当前页面在项目既有组件上原创实现。功能差异经行为和数据核实后纳入对应规格。

视觉语言围绕阅读内容和书目组织：稳定的基线与边缘、清楚的文字层级、克制的表面差异和适度的强调色。颜色随用户运行时主题。新图标优先沿用现有资源；新增图标使用许可适用的开源 UI 图标。

**设计情绪假设：**熟悉、安静、可控、可信，作为视觉决策的设计假设保留。

## Token architecture

沿用三层语义组织：**Primitive → Semantic → Component**，通过现有主题与组件实现命名和映射。

- **Primitive**：运行时主题值、间距刻度、字号与行高、圆角和动效时长等基础值。
- **Semantic**：将基础值映射为背景、正文、强调、分隔、选中、危险状态、页面留白等用途。
- **Component**：组件只引用对应语义值，例如书架分组标签使用选中强调角色、条目正文使用 `onSurface`、辅助信息使用 `onSurfaceVariant`。

组件需要特殊值时，先使用现有语义角色，并为已确定的规格记录组件级约束。

## Tokens

### Color

**现行来源：**Android Compose 读取 `ThemeStore` 当前背景、前景、次级前景及强调色。浅色/深色由 `AppConfig.isNightTheme` 控制，墨水屏模式使用黑白方案。颜色 token 按这些运行时值承担语义角色。

| Semantic token | 用途与当前映射 |
| --- | --- |
| `background` / `onBackground` | 页面底色与主要文字，来自当前主题背景、前景。 |
| `surface` / `onSurface` | 内容表面与主要文字。普通页面的 surface 跟随主题背景；仅主 Activity 的图像背景根窗口可透明，独立 Dialog 保持不透明。 |
| `surfaceVariant` / `onSurfaceVariant` | 次级内容面与辅助文字。普通模式的 surfaceVariant 为背景向前景混合 5.5%；次级前景来自 ThemeStore。 |
| `surfaceContainerLowest`, `Low`, `Container`, `High`, `Highest` | 普通模式依次使用背景向前景混合 0%、2.5%、4.5%、7%、10% 的表面层次；墨水屏模式不混色。 |
| `primary` / `onPrimary`, `secondary` / `onSecondary` | 强调操作、选中状态及其前景。强调色来自 ThemeStore，并由 `contrastSafeAccent()` 尝试满足相对全部组合表面至少 4.5:1；必要时向 HSL 明度较近的可行色调整。若不存在可同时满足的色值，回退为黑/白中对所有表面最低对比度较高者。强调色前景取与该色对比度较高的黑或白。 |
| `primaryContainer` / `onPrimaryContainer` | 当前背景与原始强调色按 16% 混合；前景使用主题主文字色。墨水屏模式停用混色。 |
| `secondaryContainer` / `onSecondaryContainer` | 当前背景与原始强调色按 10% 混合；前景使用主题主文字色。墨水屏模式停用混色。 |
| `outline` / `outlineVariant` | 普通模式分别为背景向前景混合 48% / 16%；墨水屏模式使用完整前景色。 |
| `error`, `onError`, `errorContainer`, `onErrorContainer` | 使用当前 Material 3 浅色或深色方案的错误角色。 |

强调色的 4.5:1 搜索适用于当前背景、surface 与 container 组合后的颜色；对比度安全算法的当前实现见 `LegadoTheme.kt`。若与 `DESIGN.md` 的旧阈值文字冲突，以运行时代码为准。

### Typography

**现行来源：**`FontFamily.Default`、Android `sp`、Material 3 类型角色、0sp 字距。尊重系统字体缩放；全局主题字体和常规角色保持现状。`Typography.bookTitle` 对 `titleMedium` 作局部 Serif 覆盖，由普通书架、搜索书名和编辑预览使用；紧凑列表和目录章节继续使用各自常规角色。

| Role | Size / line height | Weight | 主要用途 |
| --- | --- | --- | --- |
| Display Large / Medium / Small | 32/40, 28/36, 26/34 sp | SemiBold | 仅用于对应 display 层级。 |
| Headline Large / Medium / Small | 24/32, 22/30, 20/28 sp | SemiBold | 页面或分区标题。 |
| Title Large / Medium / Small | 20/28, 16/22, 14/20 sp | Medium | 工具栏、书名、设置组和列表标题。 |
| Body Large / Medium / Small | 16/24, 14/22, 13/18 sp | Normal | 主要说明、书籍元数据、辅助正文。 |
| Label Large / Medium / Small | 14/20, 12/16, 11/14 sp | Medium | 控件标签、短状态和徽标。 |
| `bookTitle` | Serif 18/26sp | Medium | **本轮实现：**普通书架、搜索结果和编辑预览共享；使用系统字体及回退，实际字形与窗口检查见视觉报告。 |
| 书架作者 | `bodySmall` | Normal | **本轮实现：**普通列表作者信息。 |
| Preference 分组标题 | Serif 20/28sp | Medium | **本轮实现：**`titleLarge` 局部衬线覆盖，0sp 字距，标题可换行并有 heading 语义。 |
| 目录章名 | `bodyLarge` | Normal；当前章加强 | **本轮实现：**最多两行；卷名使用紧凑标题层级，保留完整语义和长按通路。 |

阅读正文的字体、字号和行距服从用户阅读设置；本表用于普通管理界面。

### Spacing and layout

**本轮间距刻度：**4、8、12、16、24、32 dp。语义映射为：图标与文字间距 4dp、紧邻控件间距 8dp、条目内部次级间距 12dp、内容边距 16dp、分组间距 24dp、主要区域间距 32dp。布局以这些值建立一致节奏与内容关系。

书架的 `bookshelfMargin` 继续按用户设置生效，间距刻度用于条目内部的固定关系。主导航、书架分组、既有菜单与列表/紧凑/网格能力是现有产品基础；普通列表的视觉层级与条目布局通过原创方案重新组织。

| Layout token / constraint | Value | Status |
| --- | ---: | --- |
| `topBar.height` | 56dp | 现行 |
| `bottomNavigation.height` | 56dp | 现行 |
| `bookshelf.groupTarget.height` | 48dp | 现行 |
| `touchTarget.minimum` | 48dp | 现行基线 |
| `bookshelf.contentHorizontalInset` | 16dp | 本轮实现；加载骨架与真实列表共用 |
| `bookshelf.cover.width` | 66dp 普通列表、48dp 紧凑列表 | 现行 |
| `bookshelf.cover.aspectRatio` | 0.75（宽/高） | 现行 |
| `bookshelf.grid.columns` | 用户设置的 2–6 列 | 现行 |
| `bookshelf.badge.minimumHeight` | 20dp | 本轮实现；单行数字与完整语义描述，实际截图和断言结果见视觉报告。 |

### Shape and depth

**现行 Shape token：**Material 3 `extraSmall=4dp`、`small=8dp`、`medium=12dp`、`large=16dp`、`extraLarge=24dp`。组件沿用对应语义形状。

Android 内容层级来自 `surface` 和 `surfaceContainer*` 角色，静态书目使用连续平面。菜单与 Dialog 按浮层语义区分层级；墨水屏使用清晰轮廓和黑白层次。书籍封面保持既有裁切和加载路径。

### Motion

**现行动效 token：**`LocalLegadoMotion` 提供 `press=120ms`、`state=240ms`、`container=360ms`。动画受 Android 系统动画开关控制；墨水屏或系统关闭动画时三档均为 0ms，并关闭 Ripple。各页复用这套配置。

动效用于按压、选中和容器变化的即时反馈，保持可中断并服从用户滚动。减少动态效果模式下通过文字、图形和语义表达选中、加载、成功或失败。本轮设备操作与截图的实际覆盖见根验收报告，真机动态表现作为后续验证范围。

## Component specifications

所有组件使用上述 Material 3 语义色、排版和形状。触屏保留直接操作入口；键盘和指针输入提供可见焦点与悬停反馈。Pressed 使用当前交互反馈，Selected 使用强调色、标记和语义，Disabled 采用组件默认的禁用语义。

| State | 规范 |
| --- | --- |
| Default | 以组件语义表面和前景角色呈现，可操作区域清楚。 |
| Hover | 在支持指针的设备上显示轻微反馈，触屏保留直接操作入口。 |
| Focus / Focus Visible | 对键盘与辅助输入提供可见焦点和平台焦点语义。 |
| Pressed | 采用当前 `LocalLegadoMotion` 按压反馈，保持布局稳定。 |
| Selected / Expanded | 使用选中语义、标签或指示器；仅在内容确有选中/展开状态时呈现。 |
| Loading | 对实际异步任务显示局部等待状态并保持内容边界稳定。 |
| Success / Error | 只在操作结果真实存在时显示对应语义与必要说明；错误可恢复时沿用现有重试路径。 |
| Disabled / Read-only | 以 Material 3 语义表示不可操作或只读，仍保证文字与当前表面的可读性。 |

| Component | Layout and behavior | States |
| --- | --- | --- |
| Main navigation | 保留书架、发现、RSS、我的顺序；底栏图标式，目的地名称作为无障碍名称；书架可显示更新数量。 | 现行：未选中、选中、更新徽标；支持 Pressed 与键盘焦点。发现和 RSS 仍可按设置隐藏。 |
| Top bar | 56dp 高，沿用标题、返回及已有操作位置。工具栏底色读取 `ThemeStore.primaryColor`，依当前对比逻辑选择前景。 | 现行：普通、操作按压、墨水屏分隔线；可见焦点随输入设备适配。 |
| Bookshelf group tab | 48dp 选择高度，横向滚动。点击切换分组，长按编辑；选中项使用文字层级与指示线表达。 | 现行：默认、选中、按压；本轮已执行分组及相关操作 fixture，详细覆盖见根报告。 |
| Book row — regular | Serif 18/26sp 书名、`bodySmall` 作者；“读至”“更新”短标签与两行章节由细竖边线组织。标签自然宽度、最小 28dp，后接 8dp 间隔。 | 本轮实现：普通列表书名最多两行；保留 66dp 封面和 16dp 内容边界。 |
| Book row — compact | 保留紧凑密度与既有封面、作者/章节关系。 | 本轮保留：书名使用原常规角色并一行省略。 |
| Book grid item | 保留 2–6 列偏好；可用宽度或字体放大时减少实际列数，保存偏好不变；最小单元格为 `48dp × max(1, fontScale)`。 | 默认、按压、更新中、未读徽标；点击打开、长按详情。书名模式继续遵循设置。 |
| Update badge | 无未读时隐藏；更新中使用既有进度/静态图标；完整状态有可访问描述。 | 本轮实现：最小 20dp、`labelMedium`、数字不换行；极端数字省略但保留完整语义。 |
| Loading | 骨架沿用稳定的书目结构。 | 本轮实现：与列表共用 16dp 边界和 66×88dp 封面；状态文本承担读屏信息。 |
| Empty | 保留原有添加入口。 | 本轮实现：短标题与辅助正文居中、内容可滚动。 |
| Error / retry | 保留具体原因与原重试回调。 | 本轮实现：原因可滚动，重试随内容可达；长文本与大字号实际结果见视觉报告。 |
| 发现书源 / 分类 | 16dp 边缘、至少 48dp 展开目标、细分隔和展开区边线；长书源名最多两行。 | 本轮实现：原分类 renderer、书源菜单及真实分组/分类加载与错误状态保持。 |
| 搜索结果 | 66×88dp 封面、12dp 图文间隔、16dp 边缘；共享 `bookTitle`，书架状态与来源数有文字语义。 | 本轮实现：历史、本地匹配、扫描/停止/继续/分页；空结果区分暂停与无匹配，沿用现有搜索状态模型。 |
| RSS 规则入口 / 源网格 | 规则入口全宽且至少 56dp；源网格最小宽度 `104dp × max(1, fontScale)`，8dp 列距、16dp 行距；50dp 图标、两行源名。 | 本轮实现：原图片加载与条件登录菜单保持；真实空源保留规则入口，状态由现有 DAO 数据流提供。 |
| 我的 / Preference 分组 | 16dp 横向边界、24dp 顶部与 8dp 底部间距，衬线标题与细线；原条目保留至少 60dp 高度和已有控件。 | 本轮实现：我的四组与共享分组标题；其他设置页仅在存在可见非空分组标题时受影响。 |
| 目录条目 / 定位栏 | 16dp 边缘、至少 48dp 目标、两行章名、卷分隔与底部定位栏；当前章使用侧线与勾选。 | 本轮实现：selected 语义及缓存/VIP/标签/字数；原首尾/当前定位、搜索、空/加载/错误/重试保留。 |
| 书目备注 | 详情在简介后显示非空备注；编辑页使用独立多行输入，至少四行，原工具栏保存。 | 本轮实现：独立字段与草稿，空白保存为 null；已执行迁移、持久化和编辑相关设备检查，IME/重建/保存的分项与修复结果见根报告。 |
| 阅读进度筛选 | 当前纯书籍组上方横向单选条，至少 48dp 目标；全部 / 未开始 / 在读 / 已到末章。 | 本轮实现：分组内保存页面状态；根文件夹页保留全量入口，无结果保留筛选与清除，刷新/管理/导出覆盖原整组。 |
| 共享页面状态 | `LegadoPageState` 使用 48dp 图标、短标题、可选原因与真实操作，内容可滚动。 | 本轮实现：发现与搜索复用，内容由宿主真实状态驱动。 |

各页行为、状态和数据边界见 [核心页面规格索引](docs/ui/SCREEN_SPECS_CORE.md) 及独立 `SCREEN_SPEC`；截图、操作、主题和辅助功能的实际覆盖见 [VISUAL_REVIEW.md](VISUAL_REVIEW.md)。

### 书架视觉候选与选型

同一普通书架比较了三种原创语言，方案图见 `docs/ui/CONCEPTS.md`。本轮采用**书页边注**；其余两种作为概念方案比较扫描效率、信息层次和阅读气质。列数、网格与紧凑模式归于既有功能，原创设计聚焦书目组织与文字层级。

| 方向 | 视觉组织 | 状态 |
| --- | --- | --- |
| **书页边注** | 衬线书名与作者分级，当前和最新章节以两行边注组织。 | 本轮采用并实现。 |
| **书目索引** | 无衬线标题、固定字段关系和水平分隔辅助扫视书目。 | 概念比较方案。 |
| **章节对照** | 将“读至”的当前位置与“更新”的最新章节并列或分组对照，突出差异。 | 概念比较方案。 |

三个方向均沿用既有书籍、进度和更新数据。书页边注已用于书架及本轮相邻页面，另外两种保留为概念比较方案。列表、紧凑模式和网格列数继续按原显示能力提供。此前运行截图位于 `docs/ui/assets/runtime/`，对应 [书架历史验收](docs/ui/VISUAL_REVIEW_BOOKSHELF.md)；本轮页面与能力的实际覆盖见 [VISUAL_REVIEW.md](VISUAL_REVIEW.md)。

### 书目编辑

编辑页以66×88dp封面、共享`bookTitle`和作者形成平面预览；表单对齐16dp边缘，形状复用`shapes.small`。简介与备注各自使用4–10行可伸展输入框，保存期间禁用编辑操作，保存反馈保留原有入口。键盘 inset 由内容层消费，根窗口只处理系统栏。

## Platform guidance

### Android — current implementation

本项目当前实现平台为 Android，最低支持 API26，本轮设备检查使用 API36。Android 采用 Kotlin、XML/View/Fragment 宿主与 Jetpack Compose Material 3 内容共存的迁移结构；书架列表使用 Compose Lazy 列表/网格，Activity、Fragment、Intent、数据库和阅读引擎继续承接现有职责。

使用 dp 表示布局和触控尺寸、sp 表示文字。处理系统状态栏/导航栏 inset、软键盘、系统返回、字体缩放、夜间主题、用户主题、墨水屏和系统减少动画。触控目标至少 48dp，图标按钮保留无障碍名称；颜色之外还使用文字、图形或结构表达状态。

### iOS — cross-platform recommendation only

iOS 部分作为未来跨平台设计建议保留：沿用相同的语义颜色、排版层级、4/8/12/16/24/32 间距和组件状态定义，将 dp 映射到 iOS pt，并使用平台原生安全区、导航、返回手势和控件习惯，保持一致的信息层级。

未来 iOS 实现应适配 Dynamic Type、VoiceOver、系统浅/深色、Reduce Motion、硬件键盘和指针焦点；可见标签省略时仍保留平台可访问名称。新增图标沿用许可适用的开源 UI 图标来源。以上内容均为建议，需在 iOS 工程存在后单独设计与验证。

| 平台差异 | Android（当前） | iOS（未来建议） |
| --- | --- | --- |
| 导航与返回 | 既有四目的地底栏、系统返回与应用返回栈 | Tab Bar 表达目的地，Navigation Stack 和边缘返回手势表达层级 |
| 安全区 | WindowInsets 处理状态栏、手势导航栏和 IME | Safe Area 处理顶部设备区域、底部 Home Indicator 与键盘 |
| 触控 | 本项目按至少 48×48dp 设计可操作目标 | 按至少 44×44pt 设计按钮命中区域 |
| 字体与辅助功能 | sp 与字体缩放，TalkBack 语义/动作 | Dynamic Type 与 VoiceOver，遵循平台字号与导航习惯 |

触控基线来源：[Android 可访问性指南](https://developer.android.com/guide/topics/ui/accessibility/views/apps-views)、[Apple 按钮指南](https://developer.apple.com/design/human-interface-guidelines/buttons)。具体设备和辅助功能结果按实际执行记录判断。

## Verification boundary

本文件记录设计规范与源码范围；[VISUAL_REVIEW.md](VISUAL_REVIEW.md) 汇总 API36 上发现、搜索、RSS、我的与共享设置分组、目录、备注和筛选的实际构建、状态、操作与截图证据。常规与 320dp 小屏检查、1×/2× 字体检查已运行，修复后的 fixture 复验按根报告更新。真机、TalkBack 实际朗读、真实网络 Provider、iOS、全阅读引擎和全部设置页属于后续验证范围。

参考说明：`docs/ui/assets/reference-md3-bookshelf.png` 与 `docs/ui/assets/reference-md3-my.png` 记录官方参考包的空书架与我的首屏。参考仓库提供灵感，界面在当前组件上原创实现；书目备注和只读阅读筛选已接入，专门手柄适配、逐章时间轴和平板双栏列入后续独立评估范围。
