# 视觉验收 · A 矿绿书衣（2026-10-23）

## 追加验收：漫画分类与 TXT 规则滚动

本轮沿用 A 方案，增加“漫画”系统分组，复用现有图片书籍类型与阅读器；空分组默认可见，分组管理可隐藏、改名和排序，升级初始化保留已有设置。TXT 规则为全量数据库观察，列表按需渲染；修复长按后的事件接管、边缘自动滚动、连续勾选、同帧排序与同 ID 编辑状态更新。规格见 [书架](docs/ui/SCREEN_SPEC_BOOKSHELF.md)、[TXT 规则](docs/ui/SCREEN_SPEC_TXT_RULE.md)，根因及完整构建问题/有界结果见 [修复记录](docs/ui/TXT_RULE_SCROLL_FIX.md)。

本轮三个主要问题及复验：

| 位置与影响 | 修改措施 | 实际验证 |
| --- | --- | --- |
| 书架只有全部、音频、视频，缺少独立漫画入口 | 接入系统分组与共享 DAO 分类路径，沿用 Tab 组件和空态 | 3 项 Room 回归、Tab 交互；411dp 与 320dp 实际窗口截图 |
| TXT 长按后的移动被内层滚动消费，排序/连选中断；边缘缺少滚动驱动 | 长按确认后在 Initial pass 接管移动/抬手，按帧驱动边缘滚动 | 6 项失败拖动回归修复后通过；管理/picker 120 条 fixture 跨屏，实际 App 连选 8/26 |
| 同 ID 编辑/启停刷新被相等判断吞掉，快速拖动可能使用旧顺序/回调 | 引用相等快照、最新回调及手势内同步 ID 顺序 | 实际编辑保存、启停及 Activity 重建；同帧重复/不同目标与视口保持断言 |

API36 常规屏 16/16 通过（46.901s），320dp 规则交互 10/10 复验通过（34.395s）；共 16 个不同用例。实际常规窗口为 1080×2400 / 420dpi，小屏为 840×1680 / 420dpi，字体 1×；两者来自同一模拟器。真实 App 使用已有 26 条规则，长按连选只改变临时选择；重进恢复 0/26，普通滑动完整到达末项。实际 Activity 测试的独立 fixture 已清理。

原始 ADB screencap，已逐张检查：

- [漫画·常规屏](assets/runtime/comic-txt/manga-ready-final.png)：最终安装版本，四个标签、选中指示与空态。
- [漫画·320dp](assets/runtime/comic-txt/manga-compact.png)：四个标签与搜索/菜单均完整，空态指引自然换行；该组件在后续手势修正中未变。
- [TXT·320dp 连选](assets/runtime/comic-txt/txt-compact-drag-final.png)：跨过首屏，底部 8/26，状态与操作区完整。
- [TXT·320dp 末项](assets/runtime/comic-txt/txt-compact-bottom-verified.png)：选择清空，末条完整位于固定操作栏上方。

取消/多指接续只核对源码，真机帧率、TalkBack、Provider 和夜间主题未在本轮重测。截图证明对应静态界面；长按、滑动、状态保存由本轮设备测试和实际原生事件验证。模拟器最终恢复常规尺寸并保留可见，停在“漫画”标签。

---

用户已从三种原创构图中选择 A。本轮以当前 Android 架构落地书籍详情，并统一书源与分组、文章与收藏、所有书签、阅读记录；书架、发现、订阅、目录和共享设置沿用原功能并接入同一配色、状态与动效。候选与批准记录见 [CONCEPTS_V3.md](docs/ui/CONCEPTS_V3.md)。

## 实现范围与数据约束

- 书衣采用真实布纹 bitmap、非对称封面与轻衬线书名，纸面承载章节、简介与备注；底部保留独立书架/阅读操作。
- 管理页面使用连续列表、细分隔、明确的名称/状态/工具层级。文章原有五种样式及二列、三列、瀑布流继续保留。
- 共享按压使用0.985缩放、可中断的临界阻尼spring；系统移除动画和墨水屏立即反馈。展开区由原AnimatedVisibility负责，去掉重复尺寸动画。
- 新安装默认矿绿色；已有安装保留已存主题。可在“主题设置 → 主题列表”选择“矿绿书衣”或“矿绿书衣·夜”。真实升级截图保留原棕红主题，证明未覆盖旧配置；验收设备随后手动选择A。
- 修复真实拖动落点错误：未在拖动中重排的源列表，结束时直接使用目标项，避免上下相邻拖动提交自身。
- 书签增加真实加载/错误/手动重试；已有快照在失败和重试中仍可打开与长按，避免局部错误移除有效内容。

## 证据来源

使用保留数据的既有API36模拟器，1080×2400、420dpi（约411dp）；实际小屏840×1680（320dp）、1×/2×字体。API26虚拟机沿用此前已清理状态，本轮未新建。模拟器与Gradle顺序运行，降低宿主内存竞争。

`host-r1/`与`host-final/`由实际Activity经ADB screencap直接输出；`all-r1/`、`all-final/`由生产Compose组件在原生instrumentation宿主渲染。fixture内容为内存合成样本，点击、长按、状态、真实触摸拖动由测试执行；其截图只证明对应内容与状态。部分320dp内容fixture位于411dp外窗内，侧边留白属于fixture框架；真实320dp窗口另外截图。图片保留原始像素。

概念图由imagegen生成，属于方向探索；布纹是实际加入APK的生成资产。现有书封与元数据保持应用数据。截图、测试与生成概念分别记录。

## 核心页面第一轮：三个主要问题

| 位置与影响 | 修改 | 验证路径 |
| --- | --- | --- |
| 详情来源/分组前缀与章节标签重复，削弱书名层级 | raw字段直接显示；当前/最新按区分组 | 核对Activity各更新路径及内容交互测试，重拍真实详情 |
| 详情工具栏与书衣有阴影接缝，次按钮描边偏重 | 宿主工具栏elevation归零，次按钮用tonal表面 | 真实详情首屏；既有自定义主题升级保留 |
| 简介内层8dp重复缩进，破坏左侧基线 | AndroidView内部start padding归零，复用外层20dp | 详情与长备注截图、原编辑/目录回调测试 |

## 全页面第二轮：三个主要问题与集中修正

| 位置与影响 | 修改 | 复验 |
| --- | --- | --- |
| 320dp详情大封面纵排吞没首屏；常规书签停在绿色区，进度区域分散 | compact1×使用104×150封面/标题横排，元数据下移；大字缩小封面后纵排。常规书签跨接缝，章节收进1dp细框，标题Normal，按钮恢复原生图标 | 新compact首屏断言、真实320dp1×/2×图与常规详情重拍 |
| RSS2/4固定画框内出现大段空白 | 明确区分固定画框centerCrop与瀑布流自然高度fitCenter；load key包含模式 | 五种样式、收藏、分页/重试测试和重拍 |
| 阅读记录行清除控件实际40dp | 同一TextButton显式heightIn(min=48.dp) | 保留48dp断言，打开/独立清除/总时长回归 |

独立fresh finish review第一轮结论为 `disposition: fix`，提出上述接缝、进度、字重、图标、图片和小屏证据六项。修复后的verdict见下方最终记录；本轮detector仅执行一次，结果`[]`。

## 第三轮：主题证据与修复后的构图确认

| 位置与影响 | 修改 | 验证方法 |
| --- | --- | --- |
| 夜间library/RSS/source fixture沿用application浅主题，文字attr与持久背景不一致 | fixture显式配置当前night mask及AppTheme_Dark/Light，正文/表面对比度断言至少4.5 | 真实ReadRecordActivity夜间白字对照；修正后fixture重跑。旧图仅保留两个样本到before/fixture-*-night-context.png，已排除出通过证据 |
| 书衣修复接缝时底部对齐使左上留白增大 | 恢复封面与身份垂直居中，Canvas按真实封面底沿计算书签起点 | 常规日夜真实Activity重拍；分类、长来源仍保留 |
| 点击断言后立即拍正常态，ripple斑驳遮挡内容 | fixture截图前推进Compose时钟400ms，再等待空闲 | 部分硬件ripple仍按真实时间消退，图中保留按压反馈；稳定列表使用真实ReadRecordActivity对照，press独立用例覆盖反向/取消/减少动画 |

## 本轮设备与构建记录

本轮覆盖56个不同设备用例。首批55项中54通过，唯一失败是记录清除40dp；修复后22项定向用例全部通过（153.618s），新增compact用例包含在内。截图Context修正后，夜间25项全部通过（131.734s），包括书源9项、书签/记录8项、文章5项及详情3项。最终compact分类收尾后详情3/3通过（17.441s，320dp×536dp含分类的完整章节边界断言）；RSS五样式loop与书签/记录2项为3/3通过（44.554s）。56项中曾失败的控件已修复复验通过；不同尺寸复跑按同一用例计。

所有Gradle调用经`gradle-run`包装器（工作流`38f9d9ec7f5c315ed2330b147743051e`），使用离线限定任务`assembleAppDebug`、`assembleAppDebugAndroidTest`与`--max-workers=2`。

| 验证问题（原文） | 有界答案 |
| --- | --- |
| “Does the mineral book detail build with the existing Android host and UI tests?” | 初次测试编译时新增helper晚于主源码快照，未解析引用；稳定源文件后重新构建 |
| “Does the mineral book detail and shared press feedback build with its UI tests?” | 0002通过，145s |
| “Do all mineral UI screens and their Android tests compile?” | 先定位height import及无效test import并修复；0006应用与测试包通过，45s |
| “Do the screenshot-driven mineral refinements and accessibility fixes compile?” | 0008通过，235.188s；仅保留既有测试Locale构造器deprecated警告 |
| “Do the centered cover ribbon and theme-faithful screenshot fixtures compile?” | 0010通过，161.609s，无warning/failure fingerprint |
| “Do compact chapter visibility and consistent RSS placeholder framing compile?” | 0012通过，176.797s；既有Locale deprecated警告 |
| “Are Gradle daemons stopped before device validation?” | 0003、0007、0009、0011、0013成功；每次设备验收前释放daemon；工作流finish成功，仅清理包装器拥有的日志 |

## 实际运行截图索引

| 页面 | 原生Activity / 常规 | 受控内容与状态 |
| --- | --- | --- |
| 详情 | [日间常规](docs/ui/assets/iteration3/host-final/book-info-day.png) · [夜间](docs/ui/assets/iteration3/host-final/book-info-night-confirm.png) · [墨水屏模式](docs/ui/assets/iteration3/host-final/book-info-eink.png) | [真实320dp](docs/ui/assets/iteration3/host-final/book-info-320-day.png) · [2×首屏](docs/ui/assets/iteration3/host-final/book-info-320-2x-day.png) · [2×滚动](docs/ui/assets/iteration3/host-final/book-info-320-2x-scrolled.png) |
| 书源 | [实际列表](docs/ui/assets/iteration3/host-r1/book-sources.png) | [夜间选择/错误](docs/ui/assets/iteration3/all-final/sources/a-confirm-night-normal.png) · [分组弹窗](docs/ui/assets/iteration3/all-final/sources/a-confirm-night-group-edit.png) |
| 阅读记录 | [实际夜间](docs/ui/assets/iteration3/host-final/records-night.png) | [2×长记录](docs/ui/assets/iteration3/all-final/library/a-confirm-night-records-narrow-large-type.png) |
| 书签 | 生产AllBookmarkScreen内容fixture | [夜间内容与按压反馈](docs/ui/assets/iteration3/all-final/library/a-confirm-night-bookmarks-normal.png) · [错误保留内容](docs/ui/assets/iteration3/all-final/library/a-confirm-night-bookmarks-content-error.png) |
| 文章 | 生产文章组件与原图片加载器，固定本地图片 | [最终日间二列](docs/ui/assets/iteration3/all-final/articles/a-delivery-day-style-2.png) · [最终三列](docs/ui/assets/iteration3/all-final/articles/a-delivery-day-style-4.png) · [分页错误](docs/ui/assets/iteration3/all-final/articles/a-confirm-night-pagination-error.png) |
| 共享页面 | [实际书架](docs/ui/assets/iteration3/host-r1/main.png) | [书架](docs/ui/assets/iteration3/all-r1/bookshelf/a-r1-normal.png) · [发现](docs/ui/assets/iteration3/all-r1/explore/a-r1-explore-normal.png) · [目录](docs/ui/assets/iteration3/all-r1/toc/a-r1-normal.png) |

## 最终视觉结论

**通过本轮Android模拟器内验收。** Fresh finish review复核六项原问题：接缝书签、进度分区、轻衬线字重、双CTA图标、RSS图片密度和真实小屏证据全部 `resolved`；最终结论 `disposition: ship`，`remaining: clear`。该结论仅覆盖本报告提供的渲染与测试证据。

| 内部审美维度 | 分值 | 实际依据与限制 |
| --- | ---: | --- |
| 构图与层级 | 18/20 | 常规非对称书衣、纸面进度分区；320dp恢复当前章节首屏可见 |
| 字体与内容 | 14/15 | 轻衬线书名、工具sans、正文层级；2×文案按原生方式换行 |
| 色彩与材质 | 14/15 | 矿绿/纸面、真实布纹、日夜及模拟墨水屏实图 |
| 组件与细节 | 14/15 | 共享形状/边距/图标、48dp操作、源状态分层 |
| 识别性 | 8/10 | 整块书衣与接缝书签构成记忆点，工具页保持任务密度 |
| 交互与动态 | 8/10 | 实际点击、长按、拖动、reverse/cancel/reduced-motion用例；真机帧时未测 |
| 适配 | 9/10 | 常规、真实320dp、2×首屏/滚动、日夜和模拟墨水屏 |
| 状态完整度 | 5/5 | 空/加载/错误/手动重试、有效快照保留、长内容 |
| 合计 | 90/100 | 内部主观评审参考，依据本轮图与操作；逐项未验范围继续保留 |

本轮所检页面未见仍影响主要阅读、操作或结构的P0/P1问题。后续P2空间包括2×字体下CTA文案的行平衡、真实墨水屏残影与封面阴影的设备调校；这些未作为本轮已验证结果。设备已恢复常规尺寸、1×字体和矿绿日间模式，headless模拟器已停止并保留数据。

## 当前交付物与验证范围

[APP_BRIEF.md](APP_BRIEF.md)、[UI_REFERENCES.md](UI_REFERENCES.md)、[DESIGN_SYSTEM.md](DESIGN_SYSTEM.md)、[DESIGN.md](DESIGN.md)。规格：[详情](docs/ui/SCREEN_SPEC_BOOK_INFO_V3.md)、[书源/分组](docs/ui/SCREEN_SPEC_SOURCES_V3.md)、[文章/收藏](docs/ui/SCREEN_SPEC_RSS_ARTICLES_V3.md)、[书签/记录](docs/ui/SCREEN_SPEC_LIBRARY_TOOLS_V3.md)；既有核心页规格见[索引](docs/ui/SCREEN_SPECS_CORE.md)。

调试APK已安装执行，交付副本为 `build/ui-redesign-validation/delivery/legado-mineral-ui-20261023.apk`，与受测源APK SHA-256一致。真机帧率、长时间性能、TalkBack实际播报、真实墨水屏设备及真实AI/TTS Provider效果未在本轮验证。现有专用阅读/媒体引擎、全部设置子页和Web本轮保留原实现，随共享主题更新；页面重构范围以上列文件和截图为准。

---

# 历史阶段记录：A选型前的核心页面与差异功能

以下为此前提交`0a6aa4451`阶段材料，保留用于追溯；其中85项设备/7项单元及截图属于历史结果，本轮结果见上方。

# 视觉验收 · 核心页面与差异功能

本轮将原创“书页边注”延伸至发现、搜索、订阅、我的/共享设置分组和目录，接入独立书目备注与阅读进度筛选。沿用当前 Android 架构、主题和原有功能。MD3 项目用于参考；三种原创探索见 [UI_REFERENCES.md](UI_REFERENCES.md)，列表、紧凑与列数偏好继续沿用原能力。

本轮分批通过 **85 个不同设备用例、7 个单元测试**；同一用例在其他尺寸的复跑单独统计。备注内容fixture安全区补验通过；真实编辑Activity亦在320dp、2×字体与IME下通过。此前书架证据见 [历史验收](docs/ui/VISUAL_REVIEW_BOOKSHELF.md)，对应 `ff18ea38b`。

## 环境、API26 清理与数据

- 使用既有 `LegadoUI_API_36`，Google APIs x86_64，420dpi；常规 1080×2400 约411dp，小屏覆盖840×1680为320dp。字体检查1×、2×；部分窄屏 fixture 额外指定1.3×/1.5×。
- API26注册、私有数据、配置、锁及运行进程已核查：项目原 `build/ui-reference` 已不存在；当前/默认/项目AVD注册目录均无API26实例。另确认并删除12个API26专属旧运行日志（214,699字节），逐文件检查剩余0。保留API36数据与共享SDK镜像。
- 应用升级前数据库为90、书籍7条；升级后为91，原7条记录的全部33个旧字段逐项一致，新增备注为null。Room schema对比：仅增加nullable TEXT `userNote`，原字段、索引及其余20张表保持一致。
- 截图均由Android UiAutomation或screencap直接输出，保留原像素；图中内容是固定测试数据。内容fixture复用生产Compose组件：发现分类使用slot、RSS使用固定本地图标；原生宿主另行检查系统栏、主题与IME。真实网络Provider列入后续验证。

## 第一轮：三个主要视觉问题

| 位置 / 问题 | 影响 | 修改措施 | 验证 |
| --- | --- | --- | --- |
| 发现：源名、组名与展开分类层级接近 | 长名难扫描，展开关系弱 | 源名两行，组名独立，细分隔和展开边线 | 展开/长按菜单、长错误滚动重试；常规/320dp/2×截图 |
| 搜索：长书名层级弱，长历史词挤占删除入口 | 识别与删除效率下降 | 共享Serif18/26sp书名、来源数与入架勾选语义，删除目标48dp | 长结果、历史词、暂停/继续及空态测试 |
| 我的：首组条目连续，分区标题不一致 | 工具入口难扫视；[改前](docs/ui/assets/iteration2/before-my.png) | 四组任务结构、Serif20/28sp标题与细线 | 实际宿主与共享设置fixture；原15个key节点属性、顺序、类型、默认值一致 |

## 第二轮：真实窗口与触控

| 位置 / 问题 | 影响 | 修改措施 | 验证 |
| --- | --- | --- | --- |
| 搜索fixture状态栏安全区遗漏；[改前](docs/ui/assets/iteration2/before-search-insets.png) | 输入框与系统时钟重叠 | 在主题Surface内消费statusBars inset | 8项搜索fixture复跑通过；[实际SearchActivity与IME](docs/ui/assets/iteration2/app-search-ime-r5.png)另验，Back隐藏IME |
| 书目编辑IME整体上移；[改前](docs/ui/assets/iteration2/before-editor-ime.png) | 保存栏离屏，原相对坐标断言覆盖不足 | Manifest显式adjustResize，内容独立消费IME，改为保存按钮物理屏幕边界断言 | [最终原生IME](docs/ui/assets/iteration2/book-note-visual/r3-ime-final-ime.png)；按钮位于状态栏与键盘之间，保存/取消/重建3项通过 |
| 目录首尾定位按钮实际测得40dp | 触控边界偏小 | 两个IconButton显式48dp | 目录8项复验通过，窄屏标题与定位操作通过 |

## 第三轮：截图复拍与两倍字体

| 位置 / 问题 | 影响 | 修改措施 | 验证 |
| --- | --- | --- | --- |
| 目录2×字体把“3200字”拆出孤立单位；[改前](docs/ui/assets/iteration2/before-toc-metadata.png) | 次级信息扫读变慢 | tag、字数使用FlowRow独立单元，字数保持单行 | 实际TextLayoutResult断言单行/无省略/行盒边界；[修正版](docs/ui/assets/iteration2/toc-visual/r5-large-final-normal.png) |
| 目录正常截图受到System UI无响应弹窗遮挡 | 正常构图与颜色证据失效 | 保留环境记录，排除遮挡图并使用新路径复拍 | [常规最终图](docs/ui/assets/iteration2/toc-visual/r5-regular-final-normal.png)及常规13项、2×3项复验通过 |
| 筛选与备注的裸内容测试宿主未消费系统栏 | 内容顶到系统时钟/手势区 | 仅在fixture主题Surface内增加状态栏、导航栏留白 | 筛选10项及2×选择/无结果通过；备注常规与2×各2项通过；正式Main/编辑宿主另验 |

## 设备用例与最终复验

| 测试类 / 范围 | 不同用例数 | 分批结果 |
| --- | ---: | --- |
| ComposeMainUiTest / BookshelfVisualTest | 10 / 7 | 全部通过，导航、书架原布局与异常状态 |
| ReadingFilterContentTest | 10 | 全部通过；最终宿主inset修改后10项重新通过 |
| ExploreContentSemanticsTest / SearchScreenInstrumentedTest | 4 / 5 | 全部通过 |
| ExploreSearchVisualTest / RssSubscriptionVisualTest | 8 / 4 | 全部通过，含菜单/暂停继续/长文本/空态 |
| ComposePreferenceScreenTest | 5 | 全部通过，分类heading、回调、禁用与原key默认值 |
| ChapterListVisualTest / ChapterAccessibilityTest | 8 / 5 | 全部通过；metadata修改后正常/窄屏/首尾定位3项复验通过 |
| BookNoteUiTest / BookNoteEditActivityTest / BookInfoScreenInstrumentedTest | 2 / 3 / 2 | 全部通过；备注内容fixture最终inset复验通过 |
| MigrationTest / BookNotePersistenceTest / BookNoteConsumerTest | 3 / 5 / 4 | 全部通过，含真实换源、转本地及最新/已清空备注保护 |
| ReadingStatusTest（JVM） | 7 | 全部通过，0.049s；零位置、单章、未知目录、目录增长及状态互斥 |

- **320dp、1×：**19个不同用例分批通过；搜索长标题断言修正后单项复验通过，12.851s。
- **320dp、2×：**18个不同用例分批通过。初轮15通过、3个懒加载定位失败；修正为先由LazyList组合目标节点，原断言保留，三项复验通过。
- **最后数据/大字复验：**3个大字用例加5项备注持久化全部通过，37.925s；新增单本分享测试比较除备注外的完整JSON、构造器外字段、原实体及完整备份。
- **最后目录/筛选复验：**常规13/13，49.962s；320dp、2×3/3，14.582s。
- **备注最终复拍：**常规2/2（15.429s）、320dp与2×2/2（21.356s）；额外真实编辑Activity的2×IME用例1/1（9.265s）。保存按钮物理边界、IME出现/隐藏及取消保留已存备注均通过。

备注编辑的裸内容fixture曾采集到部分输入内容缺失，[相关图](docs/ui/assets/iteration2/environment/note-edit-content-capture.png)已排除出视觉通过证据；编辑交互的语义/数据断言通过，最终编辑画面使用真实Activity截图。

本轮初次设备检查包含已修复的测试问题：错误测试类路径、从横向筛选条开始的下拉手势、未挂载PreferenceManager的分类、标签资源context差异，以及把maxLines截断当成行盒裁切。长文本改用末行底边/省略检查；针对实际40dp触控和IME窗口上移的生产问题分别修复并复验。中间失败保留原因，结果按最终有效复跑统计。

环境曾出现 [System UI弹窗](docs/ui/assets/iteration2/environment/system-ui-anr.png)和 [应用启动ANR](docs/ui/assets/iteration2/environment/app-startup-anr.png)；后者exit-info记录为failed startup，发生时宿主可用内存约550MB。停止Gradle daemon、独立重启既有AVD后同一组8项通过；记录现象，具体性能根因待真机/追踪验证。构建与设备验证此后顺序执行。

## 可查看的截图

| 区域 | 常规 | 小屏 / 两倍字体 / 异常 |
| --- | --- | --- |
| 发现 | [展开源](docs/ui/assets/iteration2/explore-search-visual/r2-final-regular-explore-normal.png) | [320dp](docs/ui/assets/iteration2/explore-search-visual/r3-small-explore-normal.png) · [2×长错误与重试](docs/ui/assets/iteration2/explore-search-visual/r3-large-explore-error.png) |
| 搜索 | [结果](docs/ui/assets/iteration2/explore-search-visual/r2-final-regular-search-results.png) · [实际IME](docs/ui/assets/iteration2/app-search-ime-r5.png) | [320dp长书名](docs/ui/assets/iteration2/explore-search-visual/r3-small-final-search-results.png) · [2×暂停继续](docs/ui/assets/iteration2/explore-search-visual/r4-large-final-search-paused-results.png) |
| RSS | [源网格](docs/ui/assets/iteration2/rss-visual/r2-regular-normal.png) | [320dp](docs/ui/assets/iteration2/rss-visual/r3-small-normal.png) · [2×](docs/ui/assets/iteration2/rss-visual/r3-large-normal.png) · [空态](docs/ui/assets/iteration2/rss-visual/r3-large-empty.png) |
| 我的/设置（实际宿主） | [我的](docs/ui/assets/iteration2/app-my-r2.png) · [深色设置](docs/ui/assets/iteration2/app-settings-dark-r5.png) | [320dp、2×](docs/ui/assets/iteration2/app-my-small-large-r5.png) · [深色](docs/ui/assets/iteration2/app-my-dark-r5.png) · [墨水屏](docs/ui/assets/iteration2/app-my-eink-r5.png) |
| 目录 | [最终正常](docs/ui/assets/iteration2/toc-visual/r5-regular-final-normal.png) · [尾部定位](docs/ui/assets/iteration2/toc-visual/r5-regular-final-last.png) | [窄屏1.5×](docs/ui/assets/iteration2/toc-visual/r5-regular-final-narrow-large-type.png) · [320dp、2×](docs/ui/assets/iteration2/toc-visual/r5-large-final-normal.png) · [错误重试](docs/ui/assets/iteration2/toc-visual/r3-small-error.png) |
| 书目备注 | [实际编辑IME](docs/ui/assets/iteration2/book-note-visual/r3-ime-final-ime.png) · [详情内容](docs/ui/assets/iteration2/book-note-visual/r6-regular-final-detail.png) | [2×详情长文](docs/ui/assets/iteration2/book-note-visual/r6-large-final-detail.png) · [320dp、2×真实编辑IME](docs/ui/assets/iteration2/book-note-visual/r6-native-large-ime.png) |
| 阅读筛选 | [在读](docs/ui/assets/iteration2/reading-filter-visual/r5-regular-final-reading.png) · [实际Main常规](docs/ui/assets/iteration2/app-bookshelf-regular.png) | [2×无结果与清除](docs/ui/assets/iteration2/reading-filter-visual/r5-large-final-empty-filter.png) · [实际Main宿主320dp、2×](docs/ui/assets/iteration2/app-bookshelf-small-large-r5.png) |

已直接查看表中关键正常、长文本、2×及异常图片；RSS/目录另有独立只读视觉复核。内容fixture用于内容层级和状态，完整宿主证据覆盖Main、My、其它设置、SearchActivity与书籍编辑Activity。图中可见的视口边缘条目允许继续滚动，交互可达性另由测试验证。

## 构建核验记录

所有Gradle调用经`gradle-run`包装器，以离线限定任务执行；从0003起限定2个worker。每行保留原始验证问题及有界答案，完整日志由包装器管理。

| Run | Verification question（原文） | 有界答案 |
| --- | --- | --- |
| 0001 / 0002 | “Do the redesigned pages, note migration and reading filters build with their focused tests?” | 先后定位ViewModel回调与测试API编译错误；后续0003/0005通过 |
| 0003 | “Do the focused page fixtures and reading status unit tests pass compilation after correcting the test API calls?” | 测试包与定向单元测试通过，163.235s；7项单元通过 |
| 0004/0006/0009/0011/0013/0015/0017/0019 | “Can idle Gradle daemons be stopped to release memory for device validation?” | 每次`--stop`成功停止1个本轮空闲daemon |
| 0005 | “Do the refined book editor and corrected visual fixtures build for device revalidation?” | 应用与测试包通过，205.047s |
| 0007 | “Does the catalog build with explicit 48dp position controls?” | 应用通过，309.328s |
| 0008 | “Do explicit IME resize and the physical save-button visibility test build?” | 应用与测试包通过，85.860s；测试旧resize常量有deprecated警告 |
| 0010 | “Does the corrected narrow search title assertion compile?” | 测试包通过，118.985s |
| 0012 | “Do lazy-list-aware visual assertions compile for two-times font validation?” | 测试包通过，242.109s |
| 0014 | “Do private-note-free single-book sharing and its preservation test build?” | 应用与测试包通过，282.406s |
| 0016 | “Do atomic chapter metadata and inset-correct reading-filter fixtures build for final screenshot validation?” | 应用与测试包通过，229.922s；先前工具会话中断后确认进程退出，重跑同一限定任务取得结果 |
| 0018 | “Do inset-correct book-note content fixtures compile for the final retake?” | 测试包通过，48.531s |

## 交付与验证范围

[APP_BRIEF.md](APP_BRIEF.md)、[UI_REFERENCES.md](UI_REFERENCES.md)、[DESIGN_SYSTEM.md](DESIGN_SYSTEM.md)；规格：[发现/搜索](docs/ui/SCREEN_SPEC_EXPLORE_SEARCH.md)、[订阅](docs/ui/SCREEN_SPEC_RSS.md)、[我的](docs/ui/SCREEN_SPEC_MY.md)、[目录](docs/ui/SCREEN_SPEC_TOC.md)、[备注](docs/ui/SCREEN_SPEC_BOOK_NOTE.md)、[阅读筛选](docs/ui/SCREEN_SPEC_READING_FILTER.md)。

备注独立于简介和脚本变量，完整备份保留；普通书架分享沿用字段白名单，单本分享移除备注并保留其他原字段。已启用Web服务的读客户端沿用整书访问协议，返回Book含备注。完整恢复保留原覆盖语义，旧备份的缺省备注会覆盖为null；本轮验证了Gson与DAO通路，整包导出/恢复UI流程列入后续验证。

阅读筛选以已保存位置和本地真实目录派生；“已到末章”表达位置，阅读完成及书源实际最新目录另行确认。零位置统一归为未开始。分组ID、布局偏好、整组刷新/导出/管理继续沿用原行为。

待验范围：真实硬件性能与墨水屏刷新/残影、TalkBack实际朗读、真实书源/RSS网络Provider、任意自定义主题、iOS、全阅读引擎及全部设置页面。实际深色/墨水屏截图覆盖My和代表设置页，其他页本轮主要验证默认主题的内容和交互。最后截图与通过断言只支持各自覆盖范围。

最终复核：所有设备检查结束后再次对比升级前数据库，7本书的33个旧字段完全一致，测试备注记录已清理。`themeMode=0`（跟随系统）、fontScale=1.0和1080×2400已读回确认。API36验收进程已关闭，原AVD数据保留；本轮临时数据库备份与重复截图已清理。Gradle workflow已finish，仅清理包装器拥有的日志。

本轮调试包：[legado_app_3.26.102223.apk](app/build/outputs/apk/app/debug/legado_app_3.26.102223.apk)。

# 订阅媒体下载与相册保存（2026-10-23）

本节为本轮新增功能的独立记录。沿用 A「矿绿书衣」、现有 Compose/XML 混合界面、Media3 1.8.0 与 API36 AVD。下载由用户选择并提交；API26虚拟机保持删除。规格见 [SCREEN_SPEC_RSS_MEDIA_DOWNLOAD.md](docs/ui/SCREEN_SPEC_RSS_MEDIA_DOWNLOAD.md)。

## 实际运行发现与修改

| 位置 / 问题 | 影响 | 修改措施 | 验证方法 |
| --- | --- | --- | --- |
| 选择面板的裸内容截图宿主遗漏底部安全区 | 手势指示条覆盖下载按钮 | fixture 与正式 Dialog 同样消费 navigationBars inset | 常规与320dp重新截图，按钮物理边界及点击检查 |
| 已识别文章重复显示「打开并播放」 | 次级操作打断媒体扫描，扩大组间空白 | 按未识别/需手动播放的状态显示入口 | 正常状态无重复入口；空态恢复回调检查 |
| 空态长说明和文章恢复入口相互分离 | 信息重复，恢复动作难定位 | 文章标题下短提示和单一「打开并识别」 | 常规、小屏及大字体空态重新截图 |

首轮设备测试还暴露两个生产问题：未挂载的嗅探 WebView 无法返回动态播放器资源；快速暂停、停止服务再继续触发前台服务启动超时。分别修复 WebView 挂载生命周期和服务命令/空闲停止竞争，保留原失败路径复测。原生 Dialog 首次打开超时同步修正事务与主线程观测，结果以重新运行记录为准。

## 验证状态

本轮实现及API36范围内的验收已完成。最终截图与通过用例见下表；既有章节的通过结果只覆盖其原改动。

已取得的本轮证据：MediaStore/离线导出12项通过（14.355s）；附件解析、真实91→92迁移、选择/快照/请求上下文和三种视觉状态13项通过（63.771s）；320dp视觉/列表多选5项通过（33.589s）。视频检查包含6秒时长、音轨、实际帧解码和拖动读取，导出时关闭样例原站并断言无新网络请求。两项取消竞争断言分别验证发布后拒绝绑定的清理，以及完成绑定后caller取消仍保留成品。

实际进程恢复使用独立宿主样例服务与`recovery-r3`：prepare通过（14.928s）→ADB `am force-stop io.legado.app.debug`→resume单独运行通过（20.300s）。断言PID改变、PAUSED与非零缓存进度保留、非零Range续传，成品可读取音视频轨。受控断连接后持续503、恢复后用户retry，以及302下载两项通过（58.195s）。此处为实际目标进程结束后的验证；空间不足测试使用受控ENOSPC写入故障。

恢复用例的后续日志显示 `Bringing down service while still waiting for start foreground`，发生于测试在启动回调送达前调用 `stopService`。测试改为观测实际服务/cache启动后再停止，并增加全程保留服务的立即暂停/继续回归。恢复两阶段与其他case曾合批运行，JUnit重排及过宽的测试清理破坏了阶段标记；已将清理限制到该case自有任务，正式结果以分阶段重跑为准。动态iframe测试曾在扩展名预候选发出后过早断言服务端请求，现要求收到实际 `video/mp4` 响应后再判定，保留20秒上限。

## 第二轮：正式窗口与截图证据

| 位置 / 问题 | 影响 | 修改措施 | 验证方法 |
| --- | --- | --- | --- |
| 正式Dialog继承浮层默认inset、阴影与遮罩 | 实际可用宽度低于内容fixture，背景页面外露 | 使用Dialog专属非floating/noTitle主题、满幅窗口及系统栏安全区 | 原生view屏幕x=0且宽度等于可用屏宽断言，常规/小屏复拍 |
| 选择后立即截图仍带按压涟漪 | 截图混入短暂交互状态，影响静态构图评估 | 记录器等待按压动画和窗口空闲 | 重新查看稳定选择截图，减少动态效果配置另验 |
| 崩溃中断留下上一例的测试任务 | 完成画面混入失效样例任务 | 清理严格限定fixture身份和当前case的DB/index/cache/相册条目 | 原生完成/失败面板复拍，确认仅显示本例任务 |

## 本轮构建验证问题与有界答案

Gradle由同一`gradle-run` workflow管理；嵌套任务为`:app:testAppDebugUnitTest --tests io.legado.app.model.rss.media.*`、`:app:assembleAppDebug`、`:app:assembleAppDebugAndroidTest`。日志由包装器管理，以下保留每次非空问题及实际答案。

| Run | Verification question（原文） | 有界答案 |
| --- | --- | --- |
| 0001 | “Do RSS media unit tests and both debug APK builds pass after the download implementation?” | 失败，88.531s；PowerShell拆分未加引号的Kotlin参数，随后修正 |
| 0002 | “Do RSS media unit tests and both debug APK builds pass with correctly quoted Kotlin compiler arguments?” | 失败，379.296s；定位图片回调和Activity访问编译错误 |
| 0003 | “Do RSS media unit tests and both debug APK builds pass after fixing the two UI compiler errors and refreshing retry credentials?” | 失败，195.547s；按VMBaseActivity的protected契约增加内部文章访问方法 |
| 0004 | “Do all RSS media unit tests and both debug APK builds pass after the final compiler, MIME, credential and cancellation fixes?” | 通过，498.765s；当时14项单元及两个APK通过，随后继续实际设备验收 |
| 0005 | “Do the RSS media credential, cancellation and service-race fixes pass focused unit tests and build both APKs for real recovery and screenshot revalidation?” | 21项单元通过；测试包编译失败，363.391s；删除成员方法assertDoesNotExist的无效顶层import |
| 0006 | “Do committed-file cancellation, target-domain request reuse and corrected media UI fixtures pass focused tests and build both APKs for device acceptance?” | 通过，510.031s；21项单元及两个APK通过 |
| 0007 | “Do the final redirect credential fix, full-screen media window and lifecycle-correct recovery fixtures pass focused unit tests and build both acceptance APKs?” | 通过，480.140s；21项单元及两个APK通过，进入最终设备复验 |
| 0008 | “Does the Android test APK build after making the redirect regression method explicitly return Unit for JUnit?” | 通过，449.359s；仅修改测试方法返回类型，生产代码沿用0007安装包 |

## 最终设备结果与截图

| 验证范围 | 不同用例数 | 实际结果 |
| --- | ---: | --- |
| JVM解析/内容签名/凭据边界/服务命令 | 21 | 全部通过 |
| Media3离线导出与MediaStore | 12 | 全部通过；PNG、MP4、TS/fMP4 HLS、分离音视频、AES-128、DASH、鉴权分片、文件名和取消清理 |
| 持久下载队列 | 9 | 全部通过；暂停/服务重启、立即暂停继续、重复文件、真实跨域302私密头清理、HTML伪装、过期403重试、无后缀HLS、取消、直播拒绝 |
| 真实Dialog及下载流程 | 3 | 全部通过；与队列合批12/12，66.286s；实际页面宽度、HLS勾选到相册MP4、直播原因/重试/原文入口 |
| 目标进程重启/断网恢复/重定向 | 4 | 分阶段及独立case全部通过，见上文r3记录 |
| 动态WebView识别 | 2 | iframe/blob底层请求与无后缀fetch均收到实际video/mp4响应；保留20秒上限 |
| 媒体选择/快照/失败动作 | 6 | 全部通过；默认未选、全选取消、选择提交、压缩状态、实际请求头和双恢复动作 |
| 文章列表多选 | 2 | 全部通过；五种原布局、长按、多选退出及失效选择清理，320dp复验通过 |
| 视觉状态 | 3 | 正常/空/错误全部通过；常规、小屏、2倍字体、深色及关闭动画配置分批检查 |
| RSS附件/收藏与Room91→92迁移 | 2 | 全部通过；旧文章/收藏保留，附件与任务记录可持久化 |

共21项JVM与43项不同设备用例取得通过证据；同一用例在尺寸/主题下复跑不重复计数。未受后续独立跨域分支或Dialog窗口修改影响的有效结果直接复用。中间失败包含已修复的生产问题及测试生命周期、JUnit返回类型、前置标记和重复节点问题，按最终有效复跑统计。

- **常规：**1080×2400、420dpi、1倍字体。原生选择/完成/失败页面来自生产Dialog与实际Media3任务。
- **小屏：**840×1680、420dpi，320dp。1倍的视觉/列表5项通过；2倍字体的视觉/原生流程6/6通过（33.687s）。
- **深色与减少动态效果：**常规屏、1倍字体、系统night=yes，三个动画scale=0；视觉/原生流程6/6通过（27.767s）。
- **真实首页入口：**从Main点击「订阅→更多选项→下载任务」，实际打开空任务页；测试自有任务、缓存索引与相册成品已清理。原书架和漫画仍可见。

| 截图 | 来源 |
| --- | --- |
| [媒体选择](docs/ui/assets/rss-media/selection-native.png) · [相册完成](docs/ui/assets/rss-media/task-completed.png) · [直播拒绝](docs/ui/assets/rss-media/task-live-rejected.png) | 真实Dialog、受控网页与实际下载 |
| [320dp、2倍字体选择](docs/ui/assets/rss-media/selection-small-large.png) · [大字失败操作](docs/ui/assets/rss-media/task-live-small-large.png) | 真实Dialog，原生物理屏宽断言通过 |
| [深色选择](docs/ui/assets/rss-media/selection-dark.png) · [深色失败](docs/ui/assets/rss-media/task-live-dark.png) | 真实Dialog，关闭动画配置 |
| [长标题常规](docs/ui/assets/rss-media/selection-long-regular.png) · [长标题320dp](docs/ui/assets/rss-media/selection-long-small.png) | 同一生产选择组件，固定本地图像fixture |
| [错误](docs/ui/assets/rss-media/selection-error.png) · [空态](docs/ui/assets/rss-media/selection-empty.png) · [大字空态](docs/ui/assets/rss-media/empty-small-large.png) | 同一生产选择组件与真实点击回调 |
| [订阅首页实际打开任务页](docs/ui/assets/rss-media/tasks-entry.png) | ADB点击真实导航与菜单，无测试内容替换 |

已查看常规、小屏、大字、深色及关键异常原图；独立视觉复核未发现验收阻断。固定fixture使用既有本地占位素材，真实下载样例使用项目内生成的公开测试图像和6秒音视频；没有私有站点凭据或远程素材混入交付。

待验范围：API26–28实机权限与扫描入库、物理设备声音/编码性能、TalkBack实际朗读、私有站点登录与签名轮换、真实DRM/SAMPLE-AES来源、非H.264/AAC输入的设备转码兼容性。空间不足已验证受控ENOSPC清理，磁盘填满场景未执行。清晰度沿用来源可获取的标签，未知大小/清晰度省略。

设备已恢复1080×2400、font_scale=1.0、night=no及原动画设置（animator_duration_scale缺省，另两项1.0）；保留API36模拟器在实际「下载任务」页。宿主样例服务已结束且8765无监听。调试包：[legado_app_3.26.102317.apk](app/build/outputs/apk/app/debug/legado_app_3.26.102317.apk)。

Gradle workflow已finish，仅清理包装器拥有的日志；本轮重复截图目录和临时UI层级文件已删除，最终原图保留在`docs/ui/assets/rss-media/`。AVD运行日志供当前模拟器使用，未清理AVD或共享SDK。

## 公开媒体订阅源实测 · 2026-10-23

本轮仅追加可导入的订阅规则及验收截图，沿用已安装的调试包。通过现有 `legado://import/rssSource` 预览确认流程加入「公开媒体测试」，原书架及其他订阅保留。

- 规则文件：[rss-public-media.json](docs/samples/rss-public-media.json)。数据来自 [Google Cast 官方样例目录](https://storage.googleapis.com/cpe-sample-media/content.json)，[官方教程](https://developers.google.com/cast/codelabs/cast-receiver)的 Sample API 节引用该目录。
- 实际解析出 5 篇短片，每篇有 PNG 封面和 MP4。4 篇 15 秒，For Bigger Fun 为 60 秒；本轮十个媒体 URL 的 HEAD 均返回 200，总大小 24,644,687 字节。前两段视频另验证 Range 206 和 MP4 文件头。
- 在 API36、1080×2400、420dpi、1 倍字体下，从文章标题长按进入多选，勾选前两篇；下载面板初始为 0 项，提供两个视频与两张图片，点击全选后提交 4 项。
- 四项均显示「已保存到相册」。系统 MediaStore 的 `is_pending` 均为 0；图片位于 `Pictures/Legado/`，视频位于 `Movies/Legado/`，文件字节数与公开来源一致。
- Joyrides 的任务「打开」实际进入系统相册播放器，看到播放画面及 0:04 / 0:15 控件。未执行本轮音频听感或拖动播放检查。

| 条目 | 图片字节数 | 视频字节数 | 相册视频元数据 |
| --- | ---: | ---: | --- |
| For Bigger Blazes | 492,470 | 2,498,125 | 15,022ms，1280×720 |
| For Bigger Joyrides | 509,147 | 2,372,820 | 15,045ms，1280×720 |

实际截图：[订阅列表](docs/ui/assets/rss-media/public-source-list.png)、[两篇文章的媒体选择](docs/ui/assets/rss-media/public-source-panel.png)、[四项相册保存完成](docs/ui/assets/rss-media/public-source-completed.png)、[系统相册播放](docs/ui/assets/rss-media/public-source-playback.png)。已查看原图，列表图片正常加载，选择、提交和打开操作实际执行。

观察到一项既有显示问题：完成任务仍展示最后一次下载中的字节进度，例如 97 kB / 509 kB；实际相册文件为完整 509,147 字节。该问题留待任务进度显示修复，本轮追加订阅规则未修改下载实现。

保留两篇的四个完成任务及相册成品供验收，其余三篇尚未完整下载，媒体链接已验证。模拟器返回「公开媒体测试」文章列表。公开源后续可用性受提供方和网络影响；本轮未重跑其他来源或旧版设备检查。

## 完成大小、正文过滤与原画质 · 2026-10-23

本轮修复上一节记录的字节显示问题。完成状态与相册输出大小原子写入，旧轮询仅能更新等待/下载状态；历史完成任务按相册文件校准。HLS/DASH 下载阶段统计缓存媒体字节，完成阶段显示重封装文件的真实大小。

正文图片依据 DOM 语义和明确原图线索选择：剔除网站图标、头像、导航及站点栏装饰、追踪像素；保留正文小图、漫画及文章 hero。`data-original`、WordPress 原图字段、图片链接与最大声明尺寸 `srcset` 优先，原始签名参数保留。网络层只直接捕获视频，图片候选来自正文 DOM；解析结果回传排除地址，避免摘要预览及图标在异步合并中残留。

下载专用音视频能力列表用于最高分辨率选轨，保留默认音轨；兼容编码优先原样封装，必要转码关闭自动缩小分辨率的格式回退。实现依据、公开素材清单及 4K fixture 说明见 [原画质说明](docs/rss-media-quality.md)。

本轮 Gradle workflow：`c7930f5df68be7dfa405616c982cd78d`，通过 `gradle-run` 管理完整日志。嵌套任务为 `:app:testAppDebugUnitTest --tests io.legado.app.model.rss.media.*`、`:app:assembleAppDebug`、`:app:assembleAppDebugAndroidTest`。

| Run | Verification question（原文） | 实际结果 |
| --- | --- | --- |
| 0001 | “Do RSS media progress, content filtering and original-quality changes pass focused unit tests and build both acceptance APKs?” | 通过，616.578s；29项 JVM、两个 APK；随后补齐正文排除地址回传与非封面预览回归输入 |
| 0002 | “正文解析回传排除集合后，媒体单元测试和两个验收 APK 是否通过？” | 通过，646.875s；29项 JVM、两个 APK |
| 0003 | “设备验收前 Gradle 守护进程是否已停止？” | 通过，4.921s；1个守护进程停止 |
| 0004 | “修正已有任务下的测试定位后，Android 验收测试包是否构建通过？” | 失败，147.203s；测试代码 `onFirst` 缺少可解析引用；核对本地 Compose API 后改用集合索引 `[0]` |
| 0005 | “修正已有任务下的测试定位后，Android 验收测试包是否构建通过？” | 通过，112.250s；仅测试定位修改，生产包沿用0002 |
| 0006 | “等待原图识别结束再提交的两个安装包是否构建通过？” | 通过，424.141s；新增提交等待解析/嗅探结束的条件及对应状态测试 |

常规屏设备组执行 41 项、193.151s：40 项通过；一项旧原生 HLS 测试在保存成功后，因页面保留用户的4条完成记录，定位「打开」匹配到5个节点而失败。已将测试改为定位最新任务行的第一个操作，并在随后13/13复验中通过。本轮5项进度、1项4K、12项导出、9项队列、8项选择、2项嗅探和新增批量原图场景均已取得通过证据。

新增批量原图场景从两篇文章解析4项媒体：站点栏图标、头像、追踪像素与预览均未进入最终选择；1920×1280 PNG 和直链 MP4 保存后与 HTTP 原始字节逐个一致，MediaStore 已发布，完成进度与真实文件大小相同。第二篇专门覆盖摘要预览先出现、正文后返回原图的异步合并。

4K 用例实际选择3840×2160轨道及默认音轨；关闭来源后从下载缓存导出，逐压缩视频样本 SHA-256 与输入一致。该证据覆盖原编码重封装，未扩展为设备所有编码转码兼容性结论。

### 公开来源实际批量下载

通过既有导入预览更新「公开媒体测试」，加入 Chromecast Ultra Ad 的 HLS 主清单；原有5篇直链短片及历史记录保留。从真实文章列表长按标题、勾选 Ultra 和 Escapes 两篇，面板默认未选且仅显示两个视频和两张图片；全选提交后4项均保存成功。

| 文件 | 相册大小 | 实际尺寸 / 内容 | 来源比对 |
| --- | ---: | --- | --- |
| Ultra PNG | 35,326字节 | 852×479 | SHA-256与公开PNG一致 |
| Escapes PNG | 219,326字节 | 852×479 | SHA-256与公开PNG一致 |
| Escapes MP4 | 2,299,653字节 | 1280×720，约15秒 | SHA-256与公开MP4一致 |
| Ultra HLS导出MP4 | 17,286,970字节 | 1920×1080 H.264，视频37.233333秒；双声道44100Hz AAC，音频37.306042秒 | ffprobe确认最高轨及音轨；ffmpeg解码音频无错误，平均音量-17.8dB、峰值-0.7dB |

四个MediaStore条目均 `is_pending=0`，目录分别为 `Pictures/Legado/`、`Movies/Legado/`。任务页分别显示219kB/219kB、2.3MB/2.3MB、35kB/35kB、17MB/17MB。历史四个完成任务也已纠正为完整字节。通过任务「打开」进入系统相册，实际播放1080p文件，观察到0:22/0:37进度及画面。

公开来源提供的这两张原始封面为852×479，保持该尺寸；1920×1280原图能力由受控真实HTTP场景验证。本轮没有放大素材来替代原图。

实际截图：[历史大小纠正](docs/ui/assets/rss-media/progress-history-corrected.png)、[原图批量选择](docs/ui/assets/rss-media/original-batch-selected.png)、[原图批量完成](docs/ui/assets/rss-media/original-batch-completed.png)、[公开源选择](docs/ui/assets/rss-media/public-hd-selected.png)、[公开源相册完成](docs/ui/assets/rss-media/public-hd-completed.png)、[系统相册1080p播放](docs/ui/assets/rss-media/public-hd-playback.png)。已查看原图。

本轮生产安装包：`app/build/outputs/apk/app/debug/legado_app_3.26.102320.apk`；SHA-256 `E1ED72CB62891431B2B00DB996CF524BE241D823F57234F55FA425D76AF7D897`。初次安装早于模拟器package服务就绪，等待系统完成启动后，两次安装均实际返回Success。

收尾进一步补齐提交时序：解析/嗅探仍在运行时，允许勾选但暂缓提交；识别结束或用户停止嗅探后启用「下载所选」，防止快速操作下载尚待替换的预览。新增Compose状态测试验证两种进行中状态及结束后启用。

| 位置 / 问题 | 影响 | 修改措施 | 验证方法 |
| --- | --- | --- | --- |
| 完成任务行仍显示旧字节数 | 相册文件已完整，页面仍像未下载完成 | 完成状态原子记录输出大小；旧轮询状态守卫；历史记录按文件校准 | 快速/未知长度/HLS设备测试、历史与新公开任务截图、MediaStore大小核对 |
| 媒体选择混入网站图标、头像和预览版本 | 信息噪声增加，可能批量下载低清或无关图片 | DOM语义过滤；原图及最大srcset优先；异步排除集合合并；识别结束后允许提交 | 8项JVM图片规则、两篇实际HTTP原图批量保存、截图及提交时序UI测试 |
| 最高画质选择受设备解码能力及编码回退限制 | 存档可能选择较低分辨率 | 下载专用轨道能力；默认音轨；原编码重封装；关闭转码尺寸回退 | 真实4K低码率优先与逐样本一致、公开1080p相册输出及ffprobe |

最终包的选择/原生Dialog组13/13通过（51.315s），含新增提交时序测试和修复后的原HLS测试；320dp小屏实际批量场景1/1通过（18.390s）。累计29项JVM及42项不同设备用例取得有效通过证据；重跑和尺寸复验不重复计数。已通过且未受最后提交按钮条件影响的进度、选轨、导出、队列和嗅探结果直接复用。

最终包 SHA-256：`128A3677C588C85634983007246F4DBF382D229413E0A5996C67734CB3E95BD8`，文件仍为 `app/build/outputs/apk/app/debug/legado_app_3.26.102320.apk`，已安装。公开源下载逻辑和文件结果保持；0006补充识别期间暂缓提交，并完成实际原生批量复跑。

新增小屏原图：[选择](docs/ui/assets/rss-media/original-batch-small-selected.png)、[完成](docs/ui/assets/rss-media/original-batch-small-completed.png)。已查看常规、小屏实际截图：类型、选择数量、提交操作和最终大小清晰；小屏列表可滚动，底部提交与系统导航区域保持分离。本轮未增加动效或改变主题排版；此前共享组件的大字体、深色及减少动态效果证据继续按未变化范围复用。

公开1080p成品进一步完整解码1117帧、30fps，无解码错误；18秒位置定点解码成功。系统相册实际播放与离线解码分别记录，物理设备声音未实测。

设备已恢复1080×2400、420dpi；保留公开源8个完成任务及对应相册文件，For Bigger Fun和For Bigger Meltdowns仍供用户自行下载。受控测试任务、相册成品由测试按自身ID清理；电脑端本轮拉取的媒体副本及临时API jar已删除，正式截图保留。私有站点、旧版设备和必要转码的设备兼容性继续列为待验。

Gradle workflow已finish，仅清理包装器拥有的日志；本轮临时设备层级、截图和电脑端instrumentation原始日志已清理，AVD运行日志继续供当前模拟器使用。清理时 `rm /sdcard/media-quality-play.png` 返回 `No such file or directory`；复查 `/sdcard` 已无本轮 `media-quality-` 文件，未留下待清理目标。模拟器停在实际下载任务页，截图已更新为最终安装包的当前画面。

## PR #1 合并前评审与回归 · 2026-10-24

目标为 `meiyisiaaa/legado-Z:main`；上游 `Luoyacheng/legado-E` 保持原状。本轮在提交 `0e7eaced` 的独立工作树中完成评审修复，原工作区同时进行的知乎功能未纳入本 PR。

三组独立代码评审及交叉复核发现并修复以下问题。TLS 测试服务器改用与现有 OkHttp 同版本的 MockWebServer 5.3.2，仅加入 `testImplementation`；其 [HTTPS 接口](https://github.com/lysine-dev/okhttp/blob/parent-5.3.2/mockwebserver/src/main/kotlin/mockwebserver3/MockWebServer.kt)与 [Apache-2.0 许可](https://central.sonatype.com/artifact/com.squareup.okhttp3/mockwebserver3/5.3.2)已核对。运行结果单独记录。

| 位置 / 问题 | 用户影响与修改 | 验证 |
| --- | --- | --- |
| RSS WebView 媒体代理重定向 | 跨域跳转可能携带来源的私有 Header；逐跳隔离凭据，按响应域保存 Cookie，嗅探专用客户端使用原生逐跳拦截 | API36 真实 A→A→B 重定向用例，验证源域凭据保留、目标域凭据隔离与 Cookie 归属 |
| AI 请求复用书源网络策略 | AI 密钥及正文需要正常证书、主机名校验；四个调用方改用独立客户端，并返回重定向供用户配置最终地址 | 真实本地 HTTPS 正例、证书与主机名拒绝、302 保持原响应；运行结果见下表 |
| 系统角色朗读默认音色 | 角色段落后旁白可能沿用角色音色；每次合成恢复已初始化引擎的默认音色，更新音频缓存指纹 | Robolectric 实际调用 provider，覆盖角色音色后 null / 空默认绑定 |
| 搜索草稿与已提交条件 | 宿主重建可能丢失已提交词及临时单源范围；分别保存恢复，帮助页隐藏继续搜索按钮 | API36 6 项搜索组件、2 项真实宿主重建测试 |

### 本轮实际设备结果

2026-10-23，独立 API36 实例，zh-CN、1080×2400、420dpi、1 倍字体，同一冻结 Debug APK。四组共 12 项不同用例取得有效通过证据，复跑按同一用例计数。

| 分组 | 实际结果 | 执行时间 |
| --- | ---: | ---: |
| `SearchScreenInstrumentedTest` | 6/6 | 41.293s |
| `SearchActivityStateInstrumentedTest` | 2/2 | 21.173s |
| `RssMediaSnifferInstrumentedTest` | 3/3 | 17.733s |
| `RssMediaNativeFlowInstrumentedTest#batchDownloadsOnlyOriginalContentImagesAndVideosWithFinalSizes` | 1/1 | 20.721s |

批量用例实际从两篇文章选择两张原图和两个 MP4：初始未选、提交禁用、预览图片被排除、全选计数为 4；四项均完成。相册读取字节逐个等于 HTTP 来源，`bytesDownloaded=totalBytes=成品大小`，`MediaStore.IS_PENDING=0`，两张图片均为1920×1280。本轮未复验视频播放或视频尺寸，相关旧结果见前节。

首次搜索启动与最后重拍在用例开始前遇到模拟器系统 startup ANR；正常启动后，上述四组均取得通过结果。两次批量截图分别被 Messages / System UI 的系统 ANR 弹窗遮挡，已排除并删除，本节未新增视觉验收通过结论。此前未变化的页面截图及小屏、大字体、深色证据按覆盖范围复用。

临时 API36 实例已正常关闭；核实所属目录无联接点且无相关进程后，已删除本次临时 AVD、日志及被遮挡截图。原有 AVD 及数据未改动，未创建 API26 虚拟机。真实 Provider、私有站点、物理设备及旧版系统继续列为待验。

### 本轮构建检查

Gradle 由 `gradle-run` workflow `76b55cab7926be3e54e800abf467879e` 管理。初次 3GB 堆配置在高 GC 下中断，随后采用单 worker、4GB 堆。原工作区的并行知乎改动触发一次编译错误，后续检查全部切换至隔离工作树；该功能未纳入本轮修复。

| Verification question（原文） | 实际结果 |
| --- | --- |
| “PR审查修复后，全部JVM单测、Debug设备验收包与Release构建是否通过？” | 0008 最终通过，1371.813s：全量 JVM、Debug、androidTest 和启用 R8 的 Release 构建全部成功。初次堆压力中断及0006的 JDK 测试服务器编译失败已通过后续修正解决 |
| “PR审查修复后，全部JVM单测和Debug设备验收包是否通过？” | 原工作区先发现 TLS 夹具问题，后一次受并行代码影响；未记为通过 |
| “独立PR工作区的全部JVM单测和Debug设备验收包是否通过？” | 0008 最终通过：39个测试类、243/243通过，失败、错误、跳过均为0；两个 Debug 验收 APK 构建成功。首次夹具失败结果由本轮全量结果替代 |
| “独立PR工作区的Debug设备验收包是否构建通过？” | 0004 通过，698.172s，两个 APK 实际安装并用于上述12项设备验收；0008 重新构建两个 APK 成功 |
| “TLS夹具修正后，TLS与Robolectric同进程定向回归是否通过？” | 0007 通过，832.062s；5 项 TLS、4 项 AI 配置及 1 项默认音色测试共10/10，失败、错误、跳过均为0。此前手写协议与 JDK 方案均已被替换 |

GitHub 当前未注册 Actions 工作流、必需检查或分支规则；云端 CI 没有执行记录。本轮以本地构建、JVM、设备检查和独立代码评审记录作为合并验证，合并前再次核对远端条件。

0008 产物为 `3.26.102409`。Debug APK 为61,099,189字节，SHA-256 `A8C4A28D12753E28ED92D21154FD9E0C045E1034646AEE2A78C5AC0385CF5DFF`；Release APK 为31,162,533字节，SHA-256 `097F2E4AFA5B2E78EC210D5CA8BE2232C68DA5E2296DF62C8AD2C367A98E349F`。R8 mapping 已生成，构建产物保留于隔离工作树的 `app/build/outputs/`。

设备验收使用0004的 `3.26.102322` 冻结 Debug 包，生产源码与0008保持一致；后续修改限于 JVM 测试服务器、测试依赖、版本元数据和验证记录。0008 包未覆盖用户已有实例，Release 运行及签名分发未验证。构建保留既有弃用与平台空值警告，本轮未扩展修改这些无关代码。

Gradle workflow 已 finish，仅清理包装器拥有的日志。评审代码提交为 `7d7a54f67`；最终记录更新仅涉及本文件，复用上述已通过验证。
