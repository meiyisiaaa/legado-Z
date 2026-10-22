# Legado-Z Design System

本文件定义 Android 的语义 token、组件状态与跨平台建议。首阶段书架核心 UI 已实现，其他页面仍是后续规格，未据此重绘或验收。规则分为 **现行**（源码已有）、**本轮实现**（书架代码已有）、**计划**（尚未实施）和 **待验证**（尚无实际证据）；书架运行范围与未覆盖项见 [VISUAL_REVIEW.md](VISUAL_REVIEW.md)。

运行时主题以 `app/src/main/java/io/legado/app/ui/widget/compose/LegadoTheme.kt` 和 `ThemeStore` 为准。`DESIGN.md`、`PRODUCT.md` 用于了解既有信息结构、功能和平台边界，不限制原创呈现或经差异对照后接入有价值功能。强调色和前景当前采用对比度安全选择，旧文档中基于亮度阈值的描述已过时。现有主题、全局字体设置和动效系统保持不变；普通书架书名的 Serif 样式是本轮已实现的局部排版，不代表全局字体修改。

## Design DNA

Legado 是以读者自己的书籍和书源为中心的阅读工具。设计围绕找书、识别进度、发现更新和继续阅读展开，尊重现有数据、核心行为和 Android 技术框架，同时为界面呈现保留原创探索空间。视觉参考 [HapeLee/legado-with-MD3（3.26.18）](https://github.com/HapeLee/legado-with-MD3/tree/3.26.18) 仅提供灵感，不复制其页面、素材或具体设计。功能差异另行对照，确认价值和适配性后再纳入方案；未核实的功能不写成现状。

视觉语言围绕阅读内容和书目组织：稳定的基线与边缘、清楚的文字层级、克制的表面差异、明确而不喧宾夺主的强调色。颜色随用户运行时主题，不另造与其冲突的固定品牌色板。新图标优先沿用现有资源；新增图标使用许可适用的开源 UI 图标。

**设计情绪假设：**熟悉、安静、可控、可信。该假设用于视觉决策，未代表用户研究结论。

## Token architecture

沿用三层语义组织：**Primitive → Semantic → Component**。这是文档中的命名和映射方式，不要求新增 token 生成器、CSS 层、依赖或代码框架。

- **Primitive**：运行时主题值、间距刻度、字号与行高、圆角和动效时长等基础值。
- **Semantic**：将基础值映射为背景、正文、强调、分隔、选中、危险状态、页面留白等用途。
- **Component**：组件只引用对应语义值，例如书架分组标签使用选中强调角色、条目正文使用 `onSurface`、辅助信息使用 `onSurfaceVariant`。

组件需要特殊值时，先使用现有语义角色；仅为本轮明确计划的规格记录组件级约束，不为未来场景预留扩展层。

## Tokens

### Color

**现行来源：**Android Compose 读取 `ThemeStore` 当前背景、前景、次级前景及强调色。浅色/深色由 `AppConfig.isNightTheme` 控制，墨水屏模式使用黑白方案。颜色 token 是语义角色，不是静态十六进制色板。

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
| `error`, `onError`, `errorContainer`, `onErrorContainer` | 使用当前 Material 3 浅色或深色方案的错误角色；组件不直接指定红色常量。 |

强调色的 4.5:1 搜索适用于当前背景、surface 与 container 组合后的颜色；对比度安全算法的当前实现见 `LegadoTheme.kt`。若与 `DESIGN.md` 的旧阈值文字冲突，以运行时代码为准。

### Typography

**现行来源：**`FontFamily.Default`、Android `sp`、Material 3 类型角色、0sp 字距。尊重系统字体缩放；全局主题字体和常规角色保持现状。普通书架书名对 Material 3 `titleMedium` 作局部 Serif 覆盖，详见下表。

| Role | Size / line height | Weight | 主要用途 |
| --- | --- | --- | --- |
| Display Large / Medium / Small | 32/40, 28/36, 26/34 sp | SemiBold | 仅用于对应 display 层级。 |
| Headline Large / Medium / Small | 24/32, 22/30, 20/28 sp | SemiBold | 页面或分区标题。 |
| Title Large / Medium / Small | 20/28, 16/22, 14/20 sp | Medium | 工具栏、书名、设置组和列表标题。 |
| Body Large / Medium / Small | 16/24, 14/22, 13/18 sp | Normal | 主要说明、书籍元数据、辅助正文。 |
| Label Large / Medium / Small | 14/20, 12/16, 11/14 sp | Medium | 控件标签、短状态和徽标。 |
| 书架书名（普通列表） | Serif 18/26sp | Medium | **本轮实现：**系统衬线字体，仅用于普通列表书名；API26 中文采用系统回退，API36 呈衬线字形，详见视觉报告。 |
| 书架作者 | `bodySmall` | Normal | **本轮实现：**普通列表作者信息。 |

阅读正文仍服从用户的阅读设置；本表用于普通管理界面，不覆盖阅读器字体、字号和行距。

### Spacing and layout

**本轮间距刻度：**4、8、12、16、24、32 dp。语义映射为：图标与文字间距 4dp、紧邻控件间距 8dp、条目内部次级间距 12dp、内容边距 16dp、分组间距 24dp、主要区域间距 32dp。布局使用这些值建立一致节奏，不将空间用于装饰性留白。

书架的 `bookshelfMargin` 是现有用户设置，继续生效；刻度用于固定布局关系，不能覆盖用户间距设置。主导航、书架分组关系、既有菜单行为与列表/紧凑/网格能力是现有产品基础；普通列表的视觉层级与条目布局可通过原创方案重新组织。

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

**现行 Shape token：**Material 3 `extraSmall=4dp`、`small=8dp`、`medium=12dp`、`large=16dp`、`extraLarge=24dp`。组件沿用对应语义形状，不新增任意圆角值。

Android 内容层级来自 `surface` 和 `surfaceContainer*` 角色，不给静态书目逐项添加任意阴影。菜单与 Dialog 按浮层语义区分层级；墨水屏使用清晰轮廓和黑白层次。书籍封面保持既有裁切和加载路径。

### Motion

**现行动效 token：**`LocalLegadoMotion` 提供 `press=120ms`、`state=240ms`、`container=360ms`。动画受 Android 系统动画开关控制；墨水屏或系统关闭动画时三档均为 0ms，并关闭 Ripple。沿用现有时长，不新增曲线、弹簧参数或第二套动效系统。

动效用于按压、选中和容器变化的即时反馈；保持可中断，不遮挡阅读状态，不与用户滚动争夺控制。减少动态效果模式下保留必要的选中、加载、成功或失败信息，确保状态不依赖动画或颜色单独表达。本轮书架视觉实现沿用现行动效 token，没有新增一套动效系统；这里不记录设备动效验收结论。

## Component specifications

所有组件使用上述 Material 3 语义色、排版和形状。仅为相应设备和任务定义适用状态：触屏不依赖 Hover；有键盘或指针输入时提供可见焦点与悬停反馈。Pressed 使用当前交互反馈，Selected 使用清晰的强调色/容器变化，Disabled 采用组件默认的禁用语义；状态不能只靠颜色区分。

| State | 规范 |
| --- | --- |
| Default | 以组件语义表面和前景角色呈现，可操作区域清楚。 |
| Hover | 仅在支持指针的设备上显示轻微反馈；不作为发现或完成操作的前提。 |
| Focus / Focus Visible | 对键盘与辅助输入提供可见焦点；保留平台焦点语义，不能只变色。 |
| Pressed | 采用当前 `LocalLegadoMotion` 按压反馈；组件状态反馈不得造成布局抖动。 |
| Selected / Expanded | 使用选中语义、标签或指示器；仅在内容确有选中/展开状态时呈现。 |
| Loading | 对实际异步任务显示局部等待状态并尽量保持内容边界稳定；不伪造进度。 |
| Success / Error | 只在操作结果真实存在时显示对应语义与必要说明；错误可恢复时沿用现有重试路径。 |
| Disabled / Read-only | 以 Material 3 语义表示不可操作或只读，仍保证文字与当前表面的可读性。 |

| Component | Layout and behavior | States |
| --- | --- | --- |
| Main navigation | 保留书架、发现、RSS、我的顺序；底栏图标式，目的地名称作为无障碍名称；书架可显示更新数量。 | 现行：未选中、选中、更新徽标；支持 Pressed 与键盘焦点。发现和 RSS 仍可按设置隐藏。 |
| Top bar | 56dp 高，沿用标题、返回及已有操作位置。工具栏底色继续读取 `ThemeStore.primaryColor`，依当前工具栏对比逻辑选择前景；不把 Compose `primary` 强加到原生工具栏。 | 现行：普通、操作按压、墨水屏分隔线；可见焦点随输入设备适配。 |
| Bookshelf group tab | 48dp 选择高度，横向滚动。点击切换分组，长按编辑；选中项使用文字层级与指示线表达。 | 现行：默认、选中、按压；fixture 覆盖点击和选中，键盘焦点与快速切换的设备体验未据此断言通过。 |
| Book row — regular | Serif 18/26sp 书名、`bodySmall` 作者；“读至”“更新”短标签与两行章节由细竖边线组织。标签自然宽度、最小 28dp，后接 8dp 间隔。 | 本轮实现：普通列表书名最多两行；保留 66dp 封面和 16dp 内容边界。 |
| Book row — compact | 保留紧凑密度与既有封面、作者/章节关系。 | 本轮保留：书名一行省略；不继承普通列表的两行标题规则。 |
| Book grid item | 保留 2–6 列偏好；可用宽度或字体放大时减少实际列数，保存偏好不变；最小单元格为 `48dp × max(1, fontScale)`。 | 默认、按压、更新中、未读徽标；点击打开、长按详情。书名模式继续遵循设置。 |
| Update badge | 无未读时隐藏；更新中使用既有进度/静态图标；完整状态有可访问描述。 | 本轮实现：最小 20dp、`labelMedium`、数字不换行；极端数字省略但保留完整语义。 |
| Loading | 骨架沿用书目结构，不添加闪烁。 | 本轮实现：与列表共用 16dp 边界和 66×88dp 封面；状态有文本，骨架不重复读出。 |
| Empty | 保留原有添加入口。 | 本轮实现：短标题与辅助正文居中、内容可滚动。 |
| Error / retry | 保留具体原因与原重试回调。 | 本轮实现：原因可滚动，重试随内容可达；长文本与大字号实际结果见视觉报告。 |

### 书架视觉候选与选型

同一普通书架比较了三种原创语言，方案图见 `docs/ui/CONCEPTS.md`。本轮采用**书页边注**；其余两种用于比较扫描效率、信息层次和阅读气质。列数、网格与紧凑模式属于既有功能，不作为设计创新。

| 方向 | 视觉组织 | 状态 |
| --- | --- | --- |
| **书页边注** | 衬线书名与作者分级，当前和最新章节以两行边注组织。 | 本轮采用并实现。 |
| **书目索引** | 无衬线标题、固定字段关系和水平分隔辅助扫视书目。 | 比较方案，未实现。 |
| **章节对照** | 将“读至”的当前位置与“更新”的最新章节并列或分组对照，突出差异。 | 比较方案，未实现。 |

三个方向均沿用既有书籍、进度和更新数据。只有书页边注进入本轮书架实现；其他方案不是现有功能。运行截图位于 `docs/ui/assets/runtime/`，本轮书架的实际验收见 [VISUAL_REVIEW.md](VISUAL_REVIEW.md)，不能据此推断全 App 已完成。

## Platform guidance

### Android — current implementation

本项目当前实现平台为 Android，没有 iOS 工程。Android 采用 Kotlin、XML/View/Fragment 宿主与 Jetpack Compose Material 3 内容共存的迁移结构；书架列表使用 Compose Lazy 列表/网格。保留 Activity、Fragment、Intent、数据库和阅读引擎边界，不增加 UI 依赖或生成框架。

使用 dp 表示布局和触控尺寸、sp 表示文字。处理系统状态栏/导航栏 inset、软键盘、系统返回、字体缩放、夜间主题、用户主题、墨水屏和系统减少动画。触控目标至少 48dp，图标按钮保留无障碍名称；颜色之外还使用文字、图形或结构表达状态。

### iOS — cross-platform recommendation only

iOS 部分是未来跨平台设计建议，不表示当前存在 iOS 代码、组件或验收结果。沿用相同的语义颜色、排版层级、4/8/12/16/24/32 间距和组件状态定义；实现时将 dp 映射到 iOS pt，并使用平台原生安全区、导航、返回手势和控件习惯，保留一致的信息层级而不强行复刻 Android 组件外形。

未来 iOS 实现应适配 Dynamic Type、VoiceOver、系统浅/深色、Reduce Motion、硬件键盘和指针焦点；可见标签省略时仍保留平台可访问名称。新增图标沿用许可适用的开源 UI 图标来源。以上内容均为建议，需在 iOS 工程存在后单独设计与验证。

| 平台差异 | Android（当前） | iOS（未来建议） |
| --- | --- | --- |
| 导航与返回 | 既有四目的地底栏、系统返回与应用返回栈 | Tab Bar 表达目的地，Navigation Stack 和边缘返回手势表达层级 |
| 安全区 | WindowInsets 处理状态栏、手势导航栏和 IME | Safe Area 处理顶部设备区域、底部 Home Indicator 与键盘 |
| 触控 | 本项目按至少 48×48dp 设计可操作目标 | 按至少 44×44pt 设计按钮命中区域 |
| 字体与辅助功能 | sp 与字体缩放，TalkBack 语义/动作 | Dynamic Type 与 VoiceOver；不机械固定 Android 字号或导航外形 |

触控基线来源：[Android 可访问性指南](https://developer.android.com/guide/topics/ui/accessibility/views/apps-views)、[Apple 按钮指南](https://developer.apple.com/design/human-interface-guidelines/buttons)。这些是本规范采用的设计基线，不能代替设备和辅助功能实测。

## Verification boundary

本文件记录设计规范与书架实现状态，不替代真实渲染验收。现行和本轮实现状态描述源码中可确认的行为；是否通过视觉/交互验收须结合目标设备截图和关键交互检查。静态源码、Preview 和计划不构成验收。已执行检查和未验证边界见 [VISUAL_REVIEW.md](VISUAL_REVIEW.md)。

参考说明：`docs/ui/assets/reference-md3-bookshelf.png` 与 `docs/ui/assets/reference-md3-my.png` 是真实参考截图；书架图来自官方包的空态，不是本项目目标布局或方案图。参考仓库仅用于设计灵感；界面和图标保持原创并遵守开源许可。功能差异单独核对，尚未核实或接入的参考项目功能不作为当前实现事实。
