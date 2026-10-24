# Legado-Z Design System

本文件记录 A「矿绿书衣」在当前 Android 源码中的 Design DNA、Tokens、Component Patterns 与 Platform Adaptation。根 [DESIGN.md](DESIGN.md) 的 frontmatter 持有基础值，本文解释运行时语义和组件约束；`.impeccable/design.json` 补充动效、深度、断点与组件预览。设备、状态和操作的实际验收结果统一见 [VISUAL_REVIEW.md](VISUAL_REVIEW.md)。

运行时主题以 `app/src/main/java/io/legado/app/ui/widget/compose/LegadoTheme.kt` 和 `ThemeStore` 为准。`DESIGN.md`、`PRODUCT.md` 用于了解既有信息结构、功能和平台边界，原创呈现与差异功能按用户授权推进。强调色和前景当前采用对比度安全选择。现有主题、全局字体设置和动效系统保持；书架普通列表、搜索结果和编辑预览通过 `BookTypography.kt` 共享局部 `bookTitle` 角色，设置分组另用局部衬线标题，阅读正文继续服从阅读设置。

## Design DNA

**现行方向：矿绿书衣，连续纸面。** 用户在三张详情概念中选择 A，并授权统一其他页面；方向契约和候选图见 [CONCEPTS_V3.md](docs/ui/CONCEPTS_V3.md)。种子 `d57b1583` 保留为概念来源，明确的 A 选择决定实现。详情以书衣色场、左封面/右书名、局部衬线和书签细节识别书籍；书源、文章、书签与记录以导航色场、连续纸面、细线和文字层次转译相同语言。

Legado 是以读者自己的书籍和书源为中心的阅读工具。找书、辨识进度、发现更新和继续阅读决定信息关系；原有四目的地、菜单与数据契约继续有效。[HapeLee/legado-with-MD3（3.26.18）](https://github.com/HapeLee/legado-with-MD3/tree/3.26.18) 保留为历史视觉参考，现行页面由当前组件原创实现。

视觉语言围绕阅读内容和书目组织：稳定的基线与边缘、清楚的文字层级、克制的表面差异和适度的强调色。颜色随用户运行时主题。新图标优先沿用现有资源；新增图标使用许可适用的开源 UI 图标。

**设计性格：**熟悉、安静、可控、可信。书籍详情允许更鲜明的物体感，维护页面优先扫描效率；低对比静态布纹只用于详情书衣，纸面与细分隔承担主体信息。

## Token architecture

沿用三层语义组织：**Primitive → Semantic → Component**，通过现有主题与组件实现命名和映射。

- **Primitive**：运行时主题值、间距刻度、字号与行高、圆角和动效时长等基础值。
- **Semantic**：将基础值映射为背景、正文、强调、分隔、选中、危险状态、页面留白等用途。
- **Component**：组件只引用对应语义值，例如书架分组标签使用选中强调角色、条目正文使用 `onSurface`、辅助信息使用 `onSurfaceVariant`。

组件需要特殊值时，先使用现有语义角色，并为已确定的规格记录组件级约束。

## Tokens

### Color

**现行来源：**Android Compose 读取 `ThemeStore` 当前背景、前景、次级前景及强调色。浅色/深色由 `AppConfig.isNightTheme` 控制，墨水屏模式使用黑白方案。颜色 token 按这些运行时值承担语义角色。

默认浅/深矿绿主题基础值见 DESIGN.md 的 `android-*` 颜色 token，实际资源为 `res/values/mineral_colors.xml`。`ThemeConfig` 将其作为缺省值，已保存的颜色、图片背景和阅读配置继续生效。页面只引用语义角色。

| Semantic token | 用途与当前映射 |
| --- | --- |
| `background` / `onBackground` | 页面底色与主要文字，来自当前主题背景、前景。 |
| `surface` / `onSurface` | 内容表面与主要文字。普通页面的 surface 跟随主题背景；仅主 Activity 的图像背景根窗口可透明，独立 Dialog 保持不透明。 |
| `surfaceVariant` / `onSurfaceVariant` | 次级内容面与辅助文字。普通模式的 surfaceVariant 为背景向前景混合 5.5%；次级前景来自 ThemeStore。 |
| `surfaceContainerLowest`, `Low`, `Container`, `High`, `Highest` | 普通模式依次使用背景向前景混合 0%、2.5%、4.5%、7%、10% 的表面层次；墨水屏模式不混色。 |
| `primary` / `onPrimary`, `secondary` / `onSecondary` | 强调操作、选中状态及其前景。强调色来自 ThemeStore，并由 `contrastSafeAccent()` 尝试满足相对全部组合表面至少 4.5:1；必要时向 HSL 明度较近的可行色调整。若不存在可同时满足的色值，回退为黑/白中对所有表面最低对比度较高者。强调色前景取与该色对比度较高的黑或白。 |
| `primaryContainer` / `onPrimaryContainer` | 当前背景与原始强调色按 16% 混合；前景使用主题主文字色。墨水屏模式停用混色。 |
| `secondaryContainer` / `onSecondaryContainer` | 当前背景与原始强调色按 10% 混合；前景使用主题主文字色。墨水屏模式停用混色。 |
| `tertiaryContainer` / `onTertiaryContainer` | 书衣身份区使用 ThemeStore `primaryColor`；前景取黑/白中对比更高者。墨水屏使用白底黑字。 |
| `inversePrimary` | 浅橄榄书签基色向用户强调色混合 12%，用于详情书签细节；墨水屏使用黑。 |
| `outline` / `outlineVariant` | 普通模式分别为背景向前景混合 48% / 16%；墨水屏模式使用完整前景色。 |
| `error`, `onError`, `errorContainer`, `onErrorContainer` | `LegadoTheme.kt` 显式提供浅色深红/深色浅珊瑚及成对前景、容器；墨水屏以黑白和真实错误文案表达。 |

强调色的 4.5:1 搜索适用于当前背景、surface 与 container 组合后的七种颜色；对比度安全算法的实现见 `LegadoTheme.kt`。`tertiary` 与 `primary/secondary` 共用安全强调色，书衣使用 `tertiaryContainer` 独立承载身份色场。

### Typography

**现行来源：**`FontFamily.Default`、Android `sp`、Material 3 类型角色、0sp 字距。尊重系统字体缩放；全局主题字体和常规角色保持现状。`Typography.bookTitle` 对 `titleMedium` 作局部 Serif 覆盖，由普通书架、搜索书名和编辑预览使用；紧凑列表和目录章节继续使用各自常规角色。

系统字体和中文回退属于有意的原生平台适配，保持字形覆盖、系统字体缩放和控件度量。局部 Serif 提供书籍气质，常规工具标题继续服从 Material 类型角色。

| Role | Size / line height | Weight | 主要用途 |
| --- | --- | --- | --- |
| Display Large / Medium / Small | 32/40, 28/36, 26/34 sp | SemiBold | 仅用于对应 display 层级。 |
| Headline Large / Medium / Small | 24/32, 22/30, 20/28 sp | SemiBold | 页面或分区标题。 |
| Title Large / Medium / Small | 20/28, 16/22, 14/20 sp | Medium | 工具栏、书名、设置组和列表标题。 |
| Body Large / Medium / Small | 16/24, 14/22, 13/18 sp | Normal | 主要说明、书籍元数据、辅助正文。 |
| Label Large / Medium / Small | 14/20, 12/16, 11/14 sp | Medium | 控件标签、短状态和徽标。 |
| `bookTitle` | Serif 18/26sp | Medium | **本轮实现：**普通书架、搜索结果和编辑预览共享；使用系统字体及回退，实际字形与窗口检查见视觉报告。 |
| 详情身份书名 | Serif 28/36sp | Normal | A 书衣主焦点，完整换行与heading语义；窄屏调整图文比例，大字纵向重排。 |
| 书架作者 | `bodySmall` | Normal | **本轮实现：**普通列表作者信息。 |
| Preference 分组标题 | Serif 20/28sp | Medium | **本轮实现：**`titleLarge` 局部衬线覆盖，0sp 字距，标题可换行并有 heading 语义。 |
| 目录章名 | `bodyLarge` | Normal；当前章加强 | **本轮实现：**最多两行；卷名使用紧凑标题层级，保留完整语义和长按通路。 |

阅读正文的字体、字号和行距服从用户阅读设置；本表用于普通管理界面。

### Spacing and layout

**当前复用间距：**4、8、12、16、20、24、32 dp。紧邻信息以 4/8dp 组织、条目内部以 12dp 分层；工具列表以 16dp 为边缘，详情纸面、书签和记录以 20dp 为阅读边缘，书衣与分区以 24dp 形成呼吸空间，状态区域使用 32dp 留白。6/10/14dp 等局部光学值留在对应组件源码。

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
| `bookInfo.cover` | 常规136×196dp；窄屏/大字104×150dp | 详情身份区，纸面列表继续使用原封面比例。 |
| `bookInfo.compact` | width <360dp | 小封面与书名横排、16dp间隔；分类在右侧书名/作者之下，来源/分组在下方；书衣顶部/底部内距16/12dp。 |
| `bookInfo.stack` | fontScale ≥1.5 | 小封面与身份纵向重排，保留信息顺序与操作。 |
| `bookInfo.primaryActions` | 最小 52dp；12dp 间隔 | 两操作平分宽度、高度随文案增加。 |
| `libraryTools.contentMaximum` | 840dp | 书签与阅读记录内容居中，20dp 水平边缘。 |

### Shape and depth

**现行 Shape token：**Material 3 `extraSmall=4dp`、`small=8dp`、`medium=12dp`、`large=16dp`、`extraLarge=24dp`。组件沿用对应语义形状。

Android 内容层级来自 `surface` 和 `surfaceContainer*` 角色，`surfaceTint` 为透明，静态书目使用连续平面。详情封面单独使用 6dp 阴影、4dp 圆角，表达书衣上的实体书；书衣的 `mineral_cloth` 静态资源以 alpha 0.35、Modulate 叠加。墨水屏省略纹理，以清晰轮廓和黑白层次保持结构。菜单与 Dialog 按浮层语义区分层级，封面保持既有加载路径。

### Motion

**现行动效 token：**`LocalLegadoMotion` 提供 `press=120ms`、`state=240ms`、`container=360ms`。动画受 Android 系统动画开关控制；墨水屏或系统关闭动画时三档均为 0ms，并关闭 Ripple。各页复用这套配置。

共享按压实际由 `pressSpec()` 的临界阻尼 spring（dampingRatio=1、stiffness=700）驱动；按下绘制缩放至 0.985、释放回到 1，测量与命中区域稳定。`press=120ms` 为时长 token，spring 按物理参数结束。书源选中颜色为 `tween(state)`，内容增高为 `tween(container)`；书签和记录使用 `animateItem` 的 state 淡入/淡出/位置过渡。减少动画时 spring 改为 snap，tween 时长为零。

动效用于按压、选中和容器变化的即时反馈，保持可中断并服从用户滚动。减少动态效果模式下通过文字、图形和语义表达选中、加载、成功或失败。本轮设备操作与截图的实际覆盖见根验收报告，真机动态表现作为后续验证范围。

## Component Patterns

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
| 矿绿书衣详情 | 常规书衣顶部/底部内距24/32dp、大字24/20dp；常规封面与右侧身份垂直居中；Canvas书签随实际封面底沿计算起点，跨接书衣/纸面；阅读区域1dp细框、8dp圆角和16dp内距；底部阅读实色、书架柔和次按钮，配原图标。 | 已实现：常规横排；<360dp缩小封面，分类接右侧书名/作者，来源/分组下移；≥1.5×纵排；长文本换行、分类滚动、原点击/长按/选择文本。 |
| 书源与分组管理 | 16dp边缘、12dp搜索圆角；名称/域名/分组与启停/编辑/更多分为两段；底部全选计数与批量动作分层。 | 已实现：选中表面+Checkbox、Switch+状态文字、检测消息live region、错误色、原拖动与菜单、48dp图标操作。 |
| 文章与收藏 | 五模式保留：图文行、大图、二列、瀑布流、三列；图片小圆角，固定画框CENTER_CROP、缺图占位ContentScale.Crop；瀑布流自然高度FIT_CENTER、低层表面+细轮廓。 | 已实现：已读次级前景+Normal、未读主前景+SemiBold；真实刷新/分页/重试；收藏长按删除。 |
| 全书签 | 20dp边缘、840dp内容上限；不透明书名/作者吸顶头与真实数量，章节/原文/备注依次排列、细线分隔。 | 已实现：空/加载/错误/手动重试；已有内容保留可操作快照，局部状态反馈；原点击/长按和导出宿主。 |
| 阅读记录 | 紧凑总时长摘要、连续记录；书名/时长/日期分层，独立清除可访问名称包含书名。 | 已实现：原搜索、排序、开关、确认和查询取消；加载/错误/空态；稳定key与位置动效。 |

各页行为、状态和数据边界见 [核心页面规格索引](docs/ui/SCREEN_SPECS_CORE.md) 及独立 `SCREEN_SPEC`；截图、操作、主题和辅助功能的实际覆盖见 [VISUAL_REVIEW.md](VISUAL_REVIEW.md)。

### 书架视觉候选与选型

同一普通书架比较了三种原创语言，方案图见 `docs/ui/CONCEPTS.md`。既有普通列表采用**书页边注**；其余两种作为概念方案比较扫描效率、信息层次和阅读气质。列数、网格与紧凑模式归于既有功能，原创设计聚焦书目组织与文字层级。

| 方向 | 视觉组织 | 状态 |
| --- | --- | --- |
| **书页边注** | 衬线书名与作者分级，当前和最新章节以两行边注组织。 | 既有列表实现，随矿绿书衣系统继续保留。 |
| **书目索引** | 无衬线标题、固定字段关系和水平分隔辅助扫视书目。 | 概念比较方案。 |
| **章节对照** | 将“读至”的当前位置与“更新”的最新章节并列或分组对照，突出差异。 | 概念比较方案。 |

三个方向均沿用既有书籍、进度和更新数据。书页边注已用于书架及本轮相邻页面，另外两种保留为概念比较方案。列表、紧凑模式和网格列数继续按原显示能力提供。此前运行截图位于 `docs/ui/assets/runtime/`，对应 [书架历史验收](docs/ui/VISUAL_REVIEW_BOOKSHELF.md)；本轮页面与能力的实际覆盖见 [VISUAL_REVIEW.md](VISUAL_REVIEW.md)。

### 书目编辑

编辑页以66×88dp封面、共享`bookTitle`和作者形成平面预览；表单对齐16dp边缘，形状复用`shapes.small`。简介与备注各自使用4–10行可伸展输入框，保存期间禁用编辑操作，保存反馈保留原有入口。键盘 inset 由内容层消费，根窗口只处理系统栏。

### 反模式

- 管理页反复复制详情大封面构图，或把连续工具列表堆为大圆角卡片。
- 使用概念图示例书目、装饰序号、无真实用途的统计和重复标签替代真实内容。
- 用单一颜色表达已读、选中或失败，或去除纯图标操作的可访问名称。
- 将自定义主题覆盖成默认矿绿，或将工具字体强加给阅读正文。
- 将触控尺寸缺陷记录为紧凑标准；项目基线继续为至少48dp。
- 在滚动内容中加入无业务意义的整列交错入场或持续运动。

## Platform Adaptation

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

本文件记录设计规范与源码事实；[VISUAL_REVIEW.md](VISUAL_REVIEW.md) 汇总书籍详情、书源/分组、文章/收藏、全书签、阅读记录及既有核心页面的实际构建、状态、操作和截图证据。API36、320dp、常规屏、字体缩放与主题的具体覆盖、修复后复验和未验证项以该报告为准。文档维护仅执行格式、JSON结构和源码值核对；本文件自身承载设计规则，运行结论由验收报告提供。iOS内容属于未来建议，项目当前平台为Android；真机性能与动效、TalkBack实际朗读和真实Provider效果按本轮授权与报告记录。

主要源码依据：`ui/widget/compose/LegadoTheme.kt`、`LegadoPressFeedback.kt`、`help/config/ThemeConfig.kt`、`res/values/mineral_colors.xml`；组件依据为 `BookInfoScreen.kt`、`BookSourceManageContent.kt`、`GroupManageDialog.kt`、`RssArticleContent.kt`、`AllBookmarkScreen.kt` 和 `ReadRecordActivity.kt`。Kotlin路径均位于 `app/src/main/java/io/legado/app/` 下。页面契约见 `docs/ui/SCREEN_SPEC_*_V3.md`。

参考说明：`docs/ui/assets/reference-md3-bookshelf.png` 与 `docs/ui/assets/reference-md3-my.png` 记录官方参考包的空书架与我的首屏。参考仓库提供灵感，界面在当前组件上原创实现；书目备注和只读阅读筛选已接入，专门手柄适配、逐章时间轴和平板双栏列入后续独立评估范围。
