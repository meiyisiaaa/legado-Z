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
