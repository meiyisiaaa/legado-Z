# RSS 文章与收藏 · 矿绿书衣

本轮执行状态：源码与测试包已构建，相关常规/长文本/错误恢复/触控用例已实际运行；具体通过项、主题fixture修正、最新截图和未验证范围以根目录 [VISUAL_REVIEW.md](../../VISUAL_REVIEW.md) 当前A阶段记录为准。下文验收清单描述目标范围，分项执行证据以该报告为准。


状态：用户已选择 A「矿绿书衣」；核心书籍详情实际渲染确认后，文章列表与收藏已按此规格完成源码实现，构建与设备检查已执行，分项结果见根验收报告。

## 任务与视觉依据

读者进入订阅源后，以标题、图片和发布日期识别文章并打开阅读；收藏沿用同一文章语言，点击阅读、长按删除。页面模式为 Read / Operate，以连续扫描和快速识别内容为优先。

继承已选 A 的「书衣与纸面」关系：宿主工具栏承担矿绿色书衣，文章落在安静的纸面上，标题、日期、细分隔构成章节索引节奏。参考概念中的矿绿 `#183F38`、纸面 `#F7F7F2` 只描述默认主题方向；页面实现从 `MaterialTheme.colorScheme` 取色。`tertiaryContainer/onTertiaryContainer` 由共享主题承接原生工具栏配色，`primary` 继续跟随用户强调色，`surface` 跟随用户背景。

`PRODUCT.md`、`DESIGN.md`、`DESIGN_SYSTEM.md` 的现有导航、数据与平台契约继续适用；本轮原创外观以用户选择的 A 概念为准。文章页保持原工具栏、订阅分类/分页宿主、菜单及阅读路径，收藏保持原分组与删除确认流程。

## 布局方案

共用 16dp 纸面边界，标题、日期及分隔线保持同一文字起点；内距采用 4/8/12/16/24dp 节奏。标题是首要视觉层，日期是辅助层；保留真实标题与发布日期，缺失日期自然收拢，缺失图片按各模式既有规则呈现。内容保持平面，小圆角仅用于图片和瀑布流的独立单元，细线替代厚分区色条。

| `articleStyle` | 布局与细节 | 保留差异 |
| --- | --- | --- |
| `0` 图文行 | 左侧标题/日期，右侧约 96×76dp 图片；12dp 图文间隔，行内上下约 16dp，图片 `shapes.small`。标题 `titleMedium`，最多两行；日期 `labelSmall`，与标题间隔 8dp。底部以 `outlineVariant` 细线组织连续索引。无图时文字使用整行。 | 单列图文、右侧缩略图、整行打开；收藏复用此模式。 |
| `1` 大图 | 单列图片先行，保持既有大图语义与约 220dp 稳定图片区域；图片 8–12dp 圆角。图下 12dp 是两行 `titleMedium` 标题，后接日期；条目间 24dp 呼吸空间和细分隔。 | 有图大幅展示，无图收拢图片区域；整条可打开。 |
| `2` 二列 | 保留两列与缺图占位；图像维持大幅竖向比例，使用约 0.75 宽高比适应单元宽度。图下 12dp 排两行 `bodyMedium` 标题和日期，单元间距 12dp。单元落在纸面，不再用 8dp 厚条重复切割。 | 两列、完整大图占位、标题两行。 |
| `3` 瀑布流 | 保留 `StaggeredGridCells.Adaptive(168.dp)` 和自然内容高度；图片高度仍受现有 90–340dp 边界约束。单元使用 `surfaceContainerLow`、12dp 圆角和一层细轮廓；文字内距 12dp，标题 `bodyMedium`、较强字重，日期维持原可换行能力。 | 瀑布流高度差、长标题最多九行、现有日期行数和无图收拢。 |
| `4` 三列 | 保留三列；图片按紧凑竖向比例稳定占位，8dp 圆角。标题 `bodySmall` 两行，日期 `labelSmall`，图文间隔 8dp；密度服从既有模式。 | 三列、无图时自然收拢、紧凑展示。 |

五种模式共享颜色、文字层级与按压品质，保留用户选择的布局差异。原始 `articleStyle` 值、默认分支及宿主传参保持。

收藏使用同一图文索引行，保留 `origin + link` 稳定身份和 `RssStar.toRssArticle()`。分组标签、工具栏、批量删除菜单及确认弹窗归宿主负责，本次实现范围为收藏条目与其空态。

## 状态、交互与可访问性

- 未读标题使用 `onSurface` 和较强字重；已读标题继续使用 `onSurfaceVariant`，配合较轻字重形成第二种可见差异。日期始终使用 `onSurfaceVariant`；错误文字使用 `error`。避免整条降低透明度而损害文字对比。
- 每条保留一个 `Role.Button` 点击目标，最小高度 48dp；收藏继续使用 `combinedClickable`、`onLongClickLabel = delete`，整条长按交给原删除回调。图片是标题的视觉补充，保留空 `contentDescription`，防止重复朗读。
- 按压接入现有 `legadoPressFeedback` 和同一 `MutableInteractionSource`，缩放绘制层、保持测量与命中区域稳定；点击、长按及反馈共用该交互源。运动配置读取 `LocalLegadoMotion`，系统关闭动画或墨水屏时保持静态反馈。
- 稳定 Lazy key、列表状态、滚动位置与原有回调继续生效；本轮以按压作为主要动效，刷新和分页沿用现有机制，避免逐条入场干扰阅读和滚动。
- 图片区域在请求前确定几何尺寸，继续通过 `AndroidView/ImageView`、现有 `ImageLoader` 和 placeholder/error 图加载，保留 `origin` 请求选项及 `origin + url` 标签去重。
- 首次加载仍呈现现有真实加载状态；已有文章时刷新保留正文与下拉指示。无文章、加载失败、收藏为空分别使用现有资源，保留原刷新、重试和收藏说明；错误与长说明在小屏/大字下可滚动到真实操作。
- 页尾保留加载、分页错误/重试、手动加载更多、无更多四类状态；沿用 live region、原加载语义与错误节选策略。按钮至少 48dp，错误说明与操作按纵向顺序布局。
- 320dp、常规手机、横屏和 2× 字体下文字自然增高、图片区宽度受可用空间约束；小屏图文行允许缩小缩略图或上下排列以优先保证标题可读。标题仍按各模式约定省略，完整标题保留在语义树。两列/三列偏好和瀑布流模式继续保留。

## 实现与保留契约清单

源码写入范围：`app/src/main/java/io/legado/app/ui/rss/article/RssArticleContent.kt`；本页视觉 fixture 位于 `app/src/androidTest/java/io/legado/app/ui/rss/article/RssArticleVisualTest.kt`。复用现有共享主题、排版与反馈能力；共享主题、组件及 strings 由主任务管理。

| 契约 | 实现时必须保持 |
| --- | --- |
| 文章输入与宿主 | `RssArticleListContent` 的全部参数、默认 `modifier`、`onRead(article)` 和 Fragment 接入保持。 |
| 五种模式 | `0/1/2/3/4`、二列/三列 `GridCells.Fixed`、瀑布流 `Adaptive(168.dp)` 及默认单列分支保持。 |
| 刷新/分页 | `PullToRefreshBox`、`onRefresh`、自动 `onLoadMore(false)`、手动/重试 `onLoadMore(true)` 保持。 |
| 预加载 | 网格/瀑布流原阈值及 `preload` 控制、单列原触底加载语义保持；视觉调整沿用现有状态读取。 |
| 加载保护 | `hasMore` 与 `isLoadingMore` 条件、现有 `LaunchedEffect` 键、页尾分支优先级保持。 |
| 稳定身份 | 文章 `origin + sort + link`，收藏 `origin + link`；数据对象直接传回原回调。 |
| 阅读状态 | `article.read` 控制已读灰色呈现；加载/刷新后仍由宿主真实数据驱动。 |
| 收藏 | 点击 `onRead(favorite)`，长按 `onDelete(favorite)`；空态资源及 `announceEmpty=false` 保持。 |
| 图片 | `ImageView.CENTER_CROP`、`adjustViewBounds`、原 placeholder/error drawable、`sourceOriginOption` 和 tag 去重保持。 |
| 文案 | 复用原 strings；继续显示真实标题、日期及错误，不添加装饰标签或虚构统计。 |
| 无障碍 | Button 角色、删除动作名称、加载说明及现有 live region 保持；焦点/按压与大字号布局一起检查。 |

## 验证计划与当前证据

已读取实现与两个直接消费者，核对共享卡片调用关系；已检查现有 `RssArticleContentInstrumentedTest`。它覆盖普通条目点击、空态刷新、首次错误重试，可直接复用。

实现后的检查由主任务统一执行：编译目标源码，运行上述现有测试；实际查看五种样式和收藏，触发刷新、分页/重试、收藏长按删除；批量检查长标题、无图、缺日期、已读、空/加载/错误、320dp、常规屏、2× 字体、深色/自定义/墨水屏主题与减少动画。共享图片链路通过真实宿主核验。

源码已完成五模式与收藏的纸面排版、语义色、小圆角、共享按压、已读字重和空/错误态滚动可达性；分页、预加载、稳定身份与图片加载函数保持原逻辑。大字图文行使用 64dp 缩略图，常规尺寸使用 96×76dp 缩略图。

新增 `RssArticleVisualTest` 提供五种模式逐一截图/点击、320dp / 2× 字体、收藏阅读/长按删除、收藏空态和分页错误强制重试；图片使用 bundled drawable 的 `android.resource` 地址，测试数据与回调独立于真实网络和数据库写入。原生截图写入应用 external files 下 `rss-articles-visual/<visualPrefix>-<state>.png`，支持 `visualPrefix` 与 `visualFontScale` 参数。

已构建并执行原3项文章交互及新增5项视觉用例，夜间5项复跑通过；最终日间五样式循环重拍通过。图片来自受控本地资源、由原加载器真实渲染；真实RSS网络服务及Provider内容未在本轮核验。
