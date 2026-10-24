# 全书签与阅读记录 · 矿绿书衣

本轮执行状态：源码与测试包已构建，相关常规/长文本/错误恢复/触控用例已实际运行；具体通过项、主题fixture修正、最新截图和未验证范围以根目录 [VISUAL_REVIEW.md](../../VISUAL_REVIEW.md) 当前A阶段记录为准。下文验收清单描述目标范围，分项执行证据以该报告为准。


状态：主线已在 API36 实际启动检查书籍详情并确认 A 方向，本轮两页内容 UI 已按本规格实施。构建与设备验收已执行，分项结果见根验收报告。

## 目标与视觉依据

面向回查摘录、定位章节、整理阅读轨迹的读者；两页采用 Operate 模式，以可扫描的内容索引延续书籍详情的纸面语言。视觉基准为 `CONCEPTS_V3.md` 中的 A 概念与 `SCREEN_SPEC_BOOK_INFO_V3.md`：矿绿导航、纸面正文、精确对齐、清晰字号差、细分隔、小圆角。工具页面以内容为重心，保留既有导航、菜单位置和业务流程。

默认主题参考矿绿 `#183F38`、纸面 `#F7F7F2`、主操作 `#24675B`；页面读取 `MaterialTheme.colorScheme` 与 `MaterialTheme.typography`，由共享主题解析默认、深色、自定义主题及墨水屏颜色。页面内使用 `background` / `surface`、`onSurface` / `onSurfaceVariant`、`primary`、`outlineVariant`，颜色角色按语义取用。

原生系统字体与 Material 字体角色保持统一。书名使用 `titleMedium`，章节使用 `titleSmall`，摘录使用 `bodyMedium`，作者、日期和计数使用 `labelMedium` 或 `bodySmall`。字号差与留白承担层级，阅读工具保持连续版面；8–12dp 圆角仅用于必要的局部表面或按压裁切。

## 全书签

### 信息与版式

- 宿主标题栏保留「所有书签」、返回与导出菜单。内容区从纸面开始，左右基准 20dp，底部保留系统导航栏 inset 与 24dp 滚动余量。
- 书名与作者组成吸顶分组头：书名 `titleMedium`、作者 `labelMedium`，右侧是该书真实书签数。分组头使用不透明主题表面，细线定义上下文；长书名最多两行，作者自然换行，计数保持可读宽度。
- 移除现有分组装饰性序号胶囊。书名与作者本身提供明确索引；头部计数表达真实内容规模。
- 每条书签是同一纸面上的可点击内容行：章节标题在前，原文摘录与个人备注依次位于下方，内容基线统一。行间用细分隔与 16dp 左右的垂直节奏区分，组间留白高于条目内部留白。
- 章节最多两行；原文摘录与备注各保留最多三行预览，使用省略提示。备注通过主色和独立间隔表达个人内容，原文使用次级前景。空摘录或空备注省略相应区域；编辑页保留完整原文和备注。
- 加载、读取失败和空列表使用清楚的状态区域。加载提供原 `R.string.loading`；错误与空态复用 `LegadoPageState` 的书签图标、标题和留白，错误保留重试。删除现有装饰破折号与大圆角空态卡片。

### 保留契约

| 契约 | 现有责任与保留方式 |
| --- | --- |
| 数据与顺序 | `BookmarkDao.flowAll()` 提供按书名、作者、章节、章节位置排序的数据；`AllBookmarkScreen` 继续按 `(bookName, bookAuthor)` 分组，保留输入顺序 |
| 吸顶 | 每书一组 `stickyHeader`，抬头完整覆盖滚入其下的摘录内容 |
| 单击 | 调用 `onBookmarkClick(bookmark)`；宿主以书名+作者查找书籍并传递 `index` / `chapterPos` 定位；缺书时打开 `BookmarkDialog` |
| 长按 | 调用 `onBookmarkLongClick(bookmark, index)`，保留当前组内 index 参数及编辑/删除可用行为 |
| 编辑和删除 | `BookmarkDialog` / `BookmarkEditScreen` 继续承担保存、删除、取消与持久化 |
| 导出 | 宿主原菜单保留 JSON 和 Markdown 导出、目录选择、结果反馈；页面外的数据与文件流程沿用 |
| 状态所有权 | Activity 持有数据、加载/错误和事件；内容函数保持 `List<Bookmark>` 与事件回调 API，并追加可选 `loading` / `loadError` / `onRetry`，列表滚动状态由 Compose 所有 |

书签 Flow 由 Activity 的 `lifecycleScope` 和单个 `Job` 收集。首次与手动重试先取消旧订阅、显示加载并清除旧错误；成功发射列表后结束加载，失败写入原 `AppLog` 并显示可重试错误。无内容时使用整页状态；已有成功快照时持续展示可操作的书签列表，并在列表顶部局部显示错误与重试或重试中的加载文字。`flowOn(IO)` 位于 `catch` 前，Compose 状态在主线程更新。重试由用户触发，沿用相同 DAO Flow，导出和编辑数据路径保持原行为。

## 阅读记录

### 信息与版式

- 宿主保留返回、搜索输入、搜索提交/收起键盘、排序与「开启记录」菜单。内容函数继续只呈现总时长、内容状态和记录。
- 顶部总时长是紧凑的纸面摘要行：`all_read_time` 和总时长位于同一区域，右侧保留原「清空」文字按钮。总时长允许换行，摘要行通过底部分隔进入列表，避免大数字英雄区和独立卡片。
- 列表每条记录以书名 `titleMedium` 为主，阅读时长 `bodyMedium` 在其下，最后阅读日期使用 `labelMedium`。时长与日期顺序保持一致；日期缺失时省略相应行。
- 内容左右基准 20dp，单项删除仍位于行尾，与可点击书名区分。删除沿用原 `R.string.clear` 可见文案及原确认逻辑，可访问名称组合真实书名，避免多个相同「清空」按钮难以区分。
- 行内间距紧凑，条目之间由细分隔与 14–16dp 上下空间区分。书名最多两行，时长和日期可自然换行；小屏和大字下删除按钮保持 48dp 目标，正文使用剩余宽度。

### 保留契约

| 契约 | 现有责任与保留方式 |
| --- | --- |
| 总时长 | `ReadRecordDao.allTime` 表达全局阅读时长，搜索过滤只影响列表；刷新、删除或清空后按原逻辑更新 |
| 搜索 | 原 SearchView 输入实时查询，提交清焦点；`readRecordJob` 取消旧请求并只接收有效请求结果 |
| 聚合 | DAO 按书名聚合 `sum(readTime)` 与 `max(lastRead)`，保持每个书名单条记录 |
| 排序 | 0 为中文书名、1 为阅读时长降序、2 为最后阅读降序；`LocalConfig.readRecordSort` 持久化，排序保留当前查询 |
| 开关 | `AppConfig.enableReadRecord` 与原菜单勾选行为保留 |
| 打开 | 找到同名书籍后打开；缺书时转到 `SearchActivity` 搜索书名 |
| 全部清空 | 原摘要行入口、确认框、DAO 清空和总时长刷新保留；加载或当前列表为空时继续禁用 |
| 单项删除 | 书名专属确认框、`deleteByName`、当前搜索与总时长刷新保留 |
| 错误恢复 | `loading → loadError → empty/content` 优先级保持；错误文字和重试使用现有 string，重试刷新总时长 |
| 状态所有权 | Activity 保留请求、取消、持久配置和所有数据；`ReadRecordContent` 保持不可变输入与回调，预览与现有错误状态测试可直接调用 |

### 页面状态

加载时摘要区域与列表内容区域保持稳定；保留带 `R.string.loading` 可访问名称的加载指示。读取失败呈现 `error_get_data` 与 `retry`，错误采用 `LiveRegionMode.Polite`。无结果与初始空数据继续使用 `read_record_empty`；界面提供原搜索与菜单，可直接恢复查询。清空成功后呈现更新后的总时长与空态；确认取消继续停留当前上下文。

## 交互、动效与可访问性

- 使用原生 Material 触摸、键盘焦点与 ripple 反馈；整行打开和行尾删除分别保留可访问操作，触控目标至少 48dp。
- 可点击行使用 `Role.Button`；分组标题使用 heading 语义，书签长按提供清晰的操作名称。日期、时长、计数保留真实文本，状态同时通过文案与结构传达。
- 列表使用业务稳定 key：书签保留 `bookmark.time`，记录保留 `bookName`；数据刷新复用条目身份。排序和删项位置变化复用 `LocalLegadoMotion`，列表操作保持可中断；系统关闭动画和墨水屏时直接更新位置。
- Material 按钮复用现有反馈；可点击内容行复用 `Modifier.legadoPressFeedback(interactions: InteractionSource)`，使用同一交互源将按压缩放限制在视觉层，并遵守 `LocalLegadoMotion`。无新装饰动画、长序列入场或持续运动。
- 320dp、常规手机、平板/分屏以及 2× 字体下保留同一信息顺序。高度随文本增长；页面保持可滚动，宽屏限制合理正文宽度并居中，保留宿主系统 inset 与搜索键盘行为。
- 浅色、深色、自定义主题与墨水屏始终从语义颜色取值；分隔通过 `outlineVariant`，必要前景对比遵循共享主题保证。

## 实施与验收范围

实施允许修改：`AllBookmarkScreen.kt`、`AllBookmarkActivity.kt`、`ReadRecordActivity.kt`、本页视觉/行为 fixture 与本规格。共享主题、字符串资源、Activity XML、DAO、ViewModel、编辑页和导出逻辑由各自现有责任维持；跨文件问题先向主线报告。

静态检查覆盖两页全部调用方、类型/API、分组与 key、菜单事件、空/加载/错误状态以及本次 diff。运行验收由主线统一执行，优先使用真实 Activity 和现有 instrumentation：

1. 书签多书同名不同作者、长章节/长摘录/长备注、空态、stickyHeader、打开/缺书/长按编辑、两种导出。
2. 记录总时长、查询与快速输入、三种排序、开启开关、错误重试、空搜索、打开/缺书、删除确认取消与确认、全部清空。
3. 默认/深色/自定义/墨水屏、320dp 与大字、系统减少动画；实际触发滚动、按压、排序、删除和返回。

现有 `ReadRecordErrorStateTest` 覆盖错误文字与重试回调，`ReadRecordQueryInstrumentedTest` 覆盖快速查询与排序后的查询一致性。两页已构建，8项内容视觉/交互用例在夜间通过，原错误和快速查询/排序数据库用例通过；真实ReadRecordActivity夜间已截图对照。宿主所有确认框、书签两种导出及整包恢复路径未在本轮端到端执行。

### 已实现的视觉 fixture

本轮已在现有 Compose instrumentation 中增加两组纯内容 fixture，沿用 `LegadoTheme`，直接调用现有内容函数并传入可记录的事件回调；测试数据驻留内存。

| 内容函数 | 同一 fixture 内覆盖的真实内容形态 | 少量高价值断言 |
| --- | --- | --- |
| `AllBookmarkScreen` | 3 组书籍，其中同名不同作者；每组数条书签；覆盖超长章节、长原文、有/无备注 | 同名作者仍分组、章节和备注可见、单击与长按返回原 bookmark、滚动后吸顶抬头保留 |
| `ReadRecordContent` | 5 条不同时长/日期记录，含超长书名、超过一天时长、无日期；另外传入 loading/error/empty 状态 | 原记录打开和删除回调、总时长独立于列表、重试、清空禁用状态、长文本与末项可达 |

渲染采样以常规默认主题为基础；两页各追加 320dp/2× 字体、深色与墨水屏的代表样本。现有 Activity 查询测试继续承担数据库/搜索/排序契约，纯内容 fixture 只补足本轮版式和操作反馈证据。

已新增 `app/src/androidTest/java/io/legado/app/ui/about/LibraryToolsVisualTest.kt`，8 个测试覆盖书签分组/单击/长按、空态、加载/错误/手动重试恢复为空或内容、非空快照在错误与重试加载中保持点击和长按、320dp/2× 字体末项可达，以及记录单击/独立删除/清空、加载/错误重试/空态。截图写入设备 `externalFilesDir/library-tools-visual/`，文件名为 `${visualPrefix ?: "current"}-<state>.png`；支持 `visualFontScale` 参数，窄屏专项固定为 320dp/2× 字体。fixture 使用内存固定数据并直接调用内容函数，宿主搜索框、排序菜单、确认框与真实导航交由 Activity 集成验收。
