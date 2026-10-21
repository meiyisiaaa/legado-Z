# Final Design QA Report（当前验收候选，非最终完成）

本报告评估 Android 与 Web 的产品体验和视觉交互，基线是 Legado 原有布局、信息层级、操作位置及用户主题。每项结论只覆盖引用的实际页面与路径；源码改动、截图、模拟器操作和自动化测试分别按其证据范围描述。

当前 Android 验收证据来自 API 36 模拟器；视觉与交互以 Legado 原有布局、信息层级、操作位置和用户主题为基线，结论只适用于明确列出的路径与证据。R65/R70 构建、安装和烟测仅作历史记录，详细证据保留在下方维度和[双端 UI 重构验收记录](ui-redesign-acceptance.md)；API 26 测试 AVD 已按用户要求删除，`minSdk=26` 未变。用户接受实体设备性能与动效、真实 AI/TTS Provider 效果本轮未验证，本报告不将其计为通过。

**本轮工作树状态（2026-10-21）：** 本轮已确认未修复的源码缺陷为无；R74 release R8 已构建、签名、安装，21/21 XML/路径/版本/对比断言通过，最新 Android 自动化按唯一 class.test 合并为 70/70 PASS（30 classes）。R8 实测覆盖四导航、320dp RSS 搜索/IME/清空恢复、书源过滤空态恢复、Discovery 日夜与字体 2；Web fixture 为 137/137（18 screens，320/390/1280），模拟器已关闭且 delivery 仅保留 R74 APK 与 idsig。报告仍为 final=false：以上仅覆盖列明路径，全页全状态人工读屏、真实 TalkBack 语音/物理手势、实体设备性能/动效与真实 AI/TTS Provider 未验证；旧轮次及包详情见[验收记录](ui-redesign-acceptance.md)。

本轮证据：[Android 去重用例](../build/ui-redesign-validation/native/api36-r74-state-a11y-final.json)、[R8 包校验](../build/ui-redesign-validation/native/api36-r74-package.json)、[R8 安装与烟测](../build/ui-redesign-validation/native/api36-r74-runtime-summary.json)、[Web 状态与键盘检查](../build/ui-redesign-validation/web-a11y/report.json)。

## 1. 排版 Typography

- **当前状态：** R65 签名 R8 包在 API 36 模拟器上实际打开 HttpTTS 表单，标签不重复、hint/underline/cursor 为红色；Gboard 两个可见状态标记均为 true，Save 可达。截图见 `build/ui-redesign-validation/native/api36-r65-tts-focus.png`，R63 同一表单像素检查仍是先前证据。
- **发现的问题：** Compose 外层文本与 TextInputLayout hint 重复；全屏 CodeEdit 以 hint 作为标题，直接删 hint 会丢失编辑器标题。
- **已执行优化：** 删除重复的外层文本，保留 hint，并将间距从 12dp 收至 8dp。R63 模拟器截图确认实际渲染符合预期。
- **剩余可提升空间：** 本轮未提交 HttpTTS 表单，也未验证真实 TTS Provider 的输出；其他表单的排版未由该路径覆盖。

## 2. 间距 Spacing

- **当前状态：** R65 历史导入弹窗在 10 条列表、字体 1→2 重建后保留唯一项与页脚可见；R74 release R8 烟测复核 320dp RSS 输入 51.05×48dp、清除 48×48dp，Discovery 字体 2 内容未被裁切。R63 的 30 行 debug 重建不代表 R8 覆盖。
- **发现的问题：** ThemeList 少量内容曾占用近整屏；导入弹窗把可用空间错误留给内容区，字体放大后挤压操作页脚；Source/RSS 底部操作区靠近系统手势区域。
- **已执行优化：** ThemeList 按内容自然收高；导入列表依工具栏和页脚实际尺寸使用剩余空间，并在重建后保留列表状态；Source/RSS 底部安全区及 FileManager/QR 点击区按系统 Insets 和可触达尺寸调整。R63 字体缩放重建路径通过。
- **剩余可提升空间：** 已验证的 Insets、字体比例和窗口尺寸仅代表对应 API 36 模拟器页面；其他屏幕和真实设备布局仍需按实际范围核对。

## 3. 视觉层级 Visual Hierarchy

- **当前状态：** 书籍详情主要操作在 320dp、字体比例 2 下保持等高；R65 模拟器中长按书籍可打开 BookInfo，实际页面显示默认封面及原创 QA 内容（`build/ui-redesign-validation/native/api36-r65-book-detail.xml/png`，非 mockup）。Video 404 错误画面不再叠加原生播放圆标。书架、书源字段和原有操作入口仍遵循 Legado 组织方式。其他证据见 `build/ui-redesign-validation/native/api36-r45-runtime-summary.json`、`api36-r41-video-error.png` 和 `api36-r41-video-recovered.png`。
- **发现的问题：** 狭窄窗口和较大字体会放大按钮高度差；视频错误提示曾与原生播放控件重叠，遮挡状态信息；重构若替换首页和字段组织会削弱用户熟悉的层级。
- **已执行优化：** 统一详情操作按钮尺寸，隐藏错误态重复播放图标并修正播放器初始化顺序；保留书架、书源及阅读器既有组织和位置，放弃此前的新首页概念。
- **剩余可提升空间：** 当前证据是若干重点页面与播放器路径，不是全 App 逐页的视觉层级评审；Vietnamese 字符测试及上述尺寸覆盖不代表所有语言和字号组合。

## 4. 色彩 Color

- **当前状态：** R71 Theme ROI 和 R73 SearchView 包记录为历史证据；R74 release R8 实际像素对比度为 Light #FFFFFF/#715043=7.16563:1、Dark #FFFFFF/#5F7782=4.72159:1，Discovery 日夜显示与字体 2 页面已查看，输入与清除目标均为 48dp。
- **发现的问题：** 部分控件曾绕过 ThemeStore 语义色，导致深色主题标题发黑、系统栏与浅色内容对比不足，输入焦点的旧 tint 也没有实际改变像素颜色。R72 SearchView 的固定灰色 ImageSpan 与提示文字对比不足，且触控区域仅 30dp；R72 因此保留为历史候选。
- **已执行优化：** 颜色改用主题语义角色，修正深色标题、FileManager 系统栏对比和 HttpTTS 支持 tint。共享 SearchView 的 hint 沿用输入框 ColorStateList，ImageSpan 按 paint.color 着色，命中区修正为 48dp 并留 4dp 边距；R74 定向测试 2/2 PASS。R63 截图是焦点颜色的渲染证据，R59 洋红下划线只作为修复前基线。
- **剩余可提升空间：** R74 release R8 已复核列明的 SearchView 对比度、尺寸和主题路径；其他主题、系统栏组合与显示模式未逐一核对，也不由该路径证明全页状态。

## 5. 动效 Motion

- **当前状态：** 源码保留既有交互反馈规则并处理媒体初始化和连接收尾；现有截图只证明静态画面。R36 模拟器录像有缺帧记录，本轮实体设备动效按用户接受边界未验证。
- **发现的问题：** 尚无可证明页面转场连续性、快速操作可中断性或减少动态效果下表现的本轮设备记录；R36 录像缺帧也使历史动效观察不完整。
- **已执行优化：** 媒体初始化顺序和连接生命周期已有源码调整；报告将源码规则与实际连续播放/转场验证分开，未把截图视为动效通过。
- **剩余可提升空间：** 动效质量仍未验收。用户已接受本轮实体设备动效未验证；模拟器录像缺帧保留，不能据此宣称流畅或把现象单独归因于模拟器。

## 6. 微交互 Micro-interaction

- **当前状态：** Web 搜索结束后可返回书架，已入架内容会被过滤，零新增结果有解释；WebSocket 正常结束、取消和无 ACK 超时释放连接有对应 R50 检查。Web 检查记录为 commit `f5cad386f` 的 12 项新增检查。
- **发现的问题：** 搜索结束仍显示“取消搜索”，用户难以判断当前结果；已入架内容会让空结果看起来像搜索失败；网络关闭时等待可能没有明确完成反馈。
- **已执行优化：** 结果态文案改为返回书架，过滤书架已有项并解释零新增；连接关闭采用 ACK、5 秒期限、幂等清理，并先关闭输出以解除等待。R50 的四项 WebSocket 检查覆盖正常码、取消和无 ACK 释放路径。本轮 Web 修复删除确认期防重入、入架并发请求防重、弹层 Escape 关闭与嵌套焦点归还；Node socket tests 3/3 通过，fixture 浏览器相应状态路径已通过。
- **剩余可提升空间：** 本轮 Web fixture 浏览器检查覆盖报告所列的书架、搜索保存、阅读配置/目录/正文和书源删除失败/恢复路径，API 故障由 deterministic route fixtures 注入；真实 API、不同服务端与网络故障的提示、恢复及 Provider 效果仍未验证。

## 7. 导航与操作 Navigation & Usability

- **当前状态：** R65 签名 R8 包中四个主导航正常；FileManager 从根目录进缓存目录时面包屑更新为 root/cache，`..` 返回后同步回 root。搜索结果出现时 IME 收起；源编辑器保存/未保存返回另有 API 36 模拟器证据。相关 R65 记录见 `build/ui-redesign-validation/native/api36-r65-*`，此前证据见 R48、`api36-r45-runtime-summary.json` 和 R44 SourceEditor 记录。
- **发现的问题：** 进入子目录后路径提示曾滞后；搜索结果与软键盘争夺可视空间；源编辑器离开时需要区分保存和放弃修改。
- **已执行优化：** 目录导航与源编辑器维持原有路径修复。R73 历史包曾发现 clearButton 只有 40×48dp；提交 1b1122377 将其修正为至少 48dp，并在 Discovery/RSS 即时过滤时隐藏无副作用的 SubmitButton。R74 release R8 的 320dp RSS 输入/清除分别为 51.05×48dp、48×48dp；查询 999777 后按 Gboard 搜索键收起 IME，清空恢复 5 项；书源筛选无匹配后清除恢复 3 项并返回“我的”。
- **剩余可提升空间：** 所列结果不证明全应用返回栈、所有授权路径或全页焦点顺序。R74 release R8 指定搜索、IME 和筛选路径已通过；TalkBack bridge 不验证真实朗读或物理手势，Chrome 原生软键盘、外接键盘和权限拒绝/恢复仍未覆盖。

## 8. 适配 Adaptive Layout

- **当前状态：** R65 签名 R8 包在 API 36 模拟器上通过导入 10 条列表的字体 1→2 重建与页脚可见性检查；此前还覆盖 320dp/字体比例 2 下的书籍详情、RoleTTS/MCP 横向布局，以及 Web 五种宽度的浅色和深色界面，这些均为历史证据。当前 Web fixture 浏览器结果覆盖 320/390/1280 三宽、18 屏和错误/键盘路径，不外推到 Android 或真实服务。R63 30 行重建为 debug 测试证据，不扩张为本次 R8 覆盖。
- **发现的问题：** 窄屏及大字号可能压缩详情按钮与导入页脚；横屏内容需按原有导航关系重新排布；Web 搜索和详情曾有文字、标题对比与裁切问题。
- **已执行优化：** 调整详情按钮等高、导入列表与页脚的空间关系，并修正 Web 自然高度、标题对比和布局细节。历史 Web `f5cad386f` 的 10 屏矩阵对应 5 种宽度 × 2 种主题；本轮 Vite build 和 Android asset sync 通过，fixture 浏览器报告覆盖 320/390/1280 三宽下 18 屏。浏览器中的工具热区至少 44px 并在视口安全区内。
- **剩余可提升空间：** 适配结论限于所列尺寸、页面和主题。R74 release R8 已检查 320dp RSS 操作区、Discovery 字体 2 输入和内容；其他窗口、显示缩放、外接键盘与 Chrome 软键盘仍待核验。

## 9. 产品状态 Product States

- **当前状态：** R65 签名 R8 包中的 Replace 可新增、保存、重开回读字段并确认删除后回到空列表；R70 烟测以规则名 7070、pattern 123、replacement 456 新增 Replace，保存后重开字段一致并确认删除回空；还实际核对发现空态、RSS 收藏空态、书源 Checkbox 0→1→0、书源编辑首次帮助关闭后表单可打开、Reader/界面配置保持 25% 进度，以及阅读记录三条数据/查询空态/排序保留 query/清空后恢复和排序还原。R70 缓存空组下载仍可点是修复前发现，R71 tap/long-press 确认无菜单/确认框、开始下载 disabled，切回 all 后恢复；R71 ChapterList 搜索 999777 显示“没有匹配的章节”，定位在父节点 disabled，清除后恢复 2 章；视频目录快查最终映射 index=3/volume=1/chapterInVolume=0。QR 图库导入弹窗中的取消选择、字体重建和取消返回路径通过。较早 API 36 模拟器证据还显示音频 404 暂停/上一章恢复、视频错误重试/有效章节画面；这些均为历史基线。当前 Android 静态核对新增书架本地读取失败 E 与 Retry，E 不等同于网络离线 O；无 I/O 的纯偏好页才适用错误态 N/A。Web fixture 浏览器已验证书架加载失败不伪装成空态、重试恢复，搜索保存和书源删除的业务/网络失败反馈，以及阅读配置/目录/正文失败重试；共 137/137 checks、18 screens。R74 release R8 的 21/21 runtime assertions 另核对导航选中、RSS/书源过滤与恢复、Discovery 日夜/字体 2，以及烟测后最终书架。R65 运行证据见 `build/ui-redesign-validation/native/api36-r65-*`。
- **发现的问题：** 音频错误后 MediaSession 曾继续呈现错误播放状态；视频 404 提示与原生播放控件重叠；图库解码对非方形拉伸图不稳定；空 Replace 列表缺少明确下一步；R70 曾发现缓存空组下载入口未禁用，可能让用户进入没有有效任务的确认流程，该缺陷已在 R71 修复路径复验。
- **已执行优化：** 音频错误后更新为暂停状态及失败章节 metadata，视频错误态隐藏重复控件并保留重试与章节恢复；QR 图片按比例缩放后解码；Replace 空态可进入创建流程。R65 签名 R8 实际核对 Replace 保存/回读/删除和 QR 字体重建取消路径；较早证据见 `build/ui-redesign-validation/native/api36-r45-runtime-summary.json`、`api36-r41-video-error.png`、`api36-r50-gallery-selected.png` 和 `api36-r48-replace-cleaned.png`。
- **剩余可提升空间：** 这些记录覆盖明确的成功、空态、拒绝或错误分支，不代表所有页面的加载、空、失败和恢复状态均已检查。R71 定向测试及 R72 API 36 烟测验证缓存空组开始/导出禁用、触控无确认框及切组恢复；真实 AI/TTS Provider 效果按用户接受边界未验证。

## 10. 性能 Performance

- **当前状态：** R36 API 36 模拟器混合会话记录 548 帧、470 帧卡顿（85.77%），p50 53ms、p90 150ms、p95 300ms；该数据为负向历史证据，性能尚未通过。R70、R72 与 R74 smoke 的 crash/anr 事件文件均无新增记录，仅支持各自实际路径，不是性能通过证据。本轮实体设备性能按用户接受边界未验证。
- **发现的问题：** R36 采样混合了首次使用、录屏与导航，不是隔离稳态测量；录像缺帧仍保留，不能单独归因为产品或模拟器。
- **已执行优化：** 列表按需加载和连接生命周期有源码层调整，但现有记录没有可对照的隔离 FPS/帧时数据，不能据此声称量化性能改善。
- **剩余可提升空间：** 性能结论仍为未通过/未完成。用户接受本轮实体设备性能不测；R36 高 jank 与录像缺帧继续保留，后续需以可复现、隔离的测量确认效果。

## 11. 无障碍 Accessibility

- **当前状态：** QR 手电筒控件为 48dp，开启/关闭时名称会更新；ReaderMenu 的“自动亮度”语义值显示启用状态。证据来自 API 36 模拟器 R48 控件范围与 R50 XML/语义检查。R71 `api36-r71-state-a11y-final.json` 按唯一 class.test 合并 68/68 PASS、29 classes；affected run 14/14 PASS（74.614 秒：Cache 2、Chapter 5、TocQuery 1、Theme 6）。R68/R70 的测试配置与 selected tab 定位失败均为历史；R71 TalkBack navigation bridge 1/1 PASS（15.542 秒，0 ignored），仅验证 framework focus/action/selection，不是语音内容、物理手势或所有页面状态的人工读屏通过。服务 Bound/touchExploration=true 的前后证据见 R71 service 文件，随后恢复关闭。R71 实际目录查询显示“没有匹配的章节”并可清除恢复；对比度样本见第 4 维度及 matrix。
- **发现的问题：** 开关如果只暴露控件名称或格式化数值，屏幕阅读器用户无法得知当前开关状态；较小热区也会增加触控难度。
- **已执行优化：** QR 开关增加明确状态名称并达到 48dp；ReaderMenu 标签改为本地化的启用/禁用状态。本轮源码增加加载/错误 live region、Retry、RadioButton/Checkbox 角色与 selected/toggle 状态、目录空态和禁用态；新增 Compose 语义测试源及一个要求真实 TalkBack service 的导航测试源。共享 SearchView 的 hint/color-state、ImageSpan tint 与 48dp/4dp target 修正已写入源码；新增 `SearchViewContrastInstrumentedTest` 两项经纠正后 2/2 PASS（2.444 秒），最新合并结果 70/70、30 classes、0 notPassed。Web `scripts/state-a11y.cjs` fixture 浏览器验收 137/137 通过，检查书源 checkbox/selected、Tab 焦点路径、reader Enter/Escape 与焦点归还、失败提示和重试。
- **剩余可提升空间：** R74 release R8 的 SearchView 对比度、48dp 输入/清除目标、Discovery 字体 2 和所列搜索过滤路径已通过；全页人工读屏、真实语音、物理手势、权限边界、真机性能/动效和真实 Provider 未由此验收。

## 12. 原创性与品牌 Originality & Brand

- **当前状态：** Android 与 Web 回到 Legado 原版布局、信息层级和操作位置；保留书架、来源、阅读和用户主题的熟悉组织。Web 以 5 种宽度的浅/深主题覆盖代表页面。
- **发现的问题：** 此前的新首页概念偏离用户要求的原版结构；只看单个组件容易误把局部改造当成全产品视觉定稿。
- **已执行优化：** 放弃新首页方向，围绕 Legado 原有信息关系优化排版、状态反馈、Insets、键盘和错误提示；不改变熟悉的业务入口和字段组织。
- **剩余可提升空间：** 当前范围展示的是有证据的局部改进，不是所有页面均完成最终美术评审；不据此宣称获奖或整体视觉已最终定稿。

## 最终自检结论

**当前已确认仍未修复的源码缺陷：无。** R74 release R8 已构建、签名、安装，70/70 去重自动化和 21/21 指定 XML/路径/版本/对比断言通过；包 SHA-256 为 ed145c24f19745706f3da9ff097d5c2057ddee6232a792e532517c659541a78c。实际覆盖与环境记录见验收记录。本报告仍为 final=false：本轮仅验证明确路径，不代表全 App 每页每状态人工评审；真实 TalkBack 语音/物理手势、实体设备性能/动效及真实 Provider 仍未验证。
