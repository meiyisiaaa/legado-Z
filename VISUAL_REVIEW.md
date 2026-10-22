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
