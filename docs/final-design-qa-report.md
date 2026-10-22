# Final Design QA Report（当前验收候选，非最终完成）

本报告评估 Android 与 Web 的产品体验和视觉交互，基线是 Legado 原有布局、信息层级、操作位置及用户主题。每项结论只覆盖引用的实际页面与路径；源码改动、截图、模拟器操作和自动化测试分别按其证据范围描述。

新增 API35 证据来自 PFZM10（1080×2412、density 480、fontScale 1.15），仅使用隔离 QA 包 `io.legado.app.debug`，未覆盖 `io.legado.app.release`。用户要求后续只在既有 API36 模拟器验收；本报告以下新增运行证据来自该模拟器，不再操作真机。R75 当前 R8 QA 包已构建并完成指定 smoke，包标识、签名、资产校验与已测路径见[验收记录](ui-redesign-acceptance.md)。API 26 测试 AVD 已删除，`minSdk=26` 未变；R65–R74 包及运行结果仍仅是各自历史证据。

**本轮状态（2026-10-22）：** R75 API36 模拟器的分批 debug instrumentation 结果合并为 17 个不同测试均有通过记录（About 3、RSS 2、ReaderMenu 10、正文 1、Main 1），并非单次 17/17 运行，也不是 R8 测试；首次 ANR/夹具失败保留为历史诊断，最终正文和 Main 复验为 2/2 PASS。R75 R8 QA 包另行完成 Main→My→About、RSS 与普通触控 smoke：About 帮助、无日志、许可证及免责声明入口可见；Meow云 tile 打开真实 WebView 登录页后返回，阅读记录/收藏空态、规则弹窗取消、源管理与新增源表单的 Basic/List/WebView 标签和 CookieJar 横向可达已查看；Backup 进入系统文件夹选择器后取消返回，Main Log 的 No logs 空态返回正常，QR 相机权限拒绝后回到源管理。未登录 Meow云、未保存新源、未授权 Backup 目录或执行备份/恢复、未测试 QR 权限允许/恢复和扫码成功。报告保持 `final=false`，不推断全应用无缺陷。切换到 API36 模拟器后的复验没有启用 TalkBack，当前保持关闭且不再启用；全页语音/完整手势、真机性能与动效、真实 AI/TTS Provider 均未验收。

本轮证据：[分批定向测试汇总](../build/ui-redesign-validation/native/api36-r75-state-a11y-final.json)、[R8 普通触控与 21 项标签/路径断言](../build/ui-redesign-validation/native/api36-r75-runtime-summary.json)、[R75 API36 定向回归与最终正文复验](../build/ui-redesign-validation/native/api36-r75-reader-navigation-final.txt)、[R75 分批 debug instrumentation 测试/诊断](../build/ui-redesign-validation/native/api36-r75-focused-tests.txt)、[R75 包元数据](../build/ui-redesign-validation/native/api36-r75-package.json)、[R75 ANR 事件](../build/ui-redesign-validation/native/api36-r75-anr-events.txt)、[R75 About 首屏](../build/ui-redesign-validation/native/api36-r75-about.png)、[许可证页](../build/ui-redesign-validation/native/api36-r75-about-license.png)、R75 RSS 实测证据 `../build/ui-redesign-validation/native/api36-r75-rss-source-settled`、`api36-r75-rss-history`、`api36-r75-rss-favorites`、`api36-r75-rule-subscription`、`api36-r75-rule-subscription-new`、`api36-r75-rss-manage`、`api36-r75-rss-new`、`api36-r75-rss-new-help-dismissed`、`api36-r75-rss-edit-start`、`api36-r75-rss-options-horizontal`、`api36-r75-rss-edit-list`、`api36-r75-rss-edit-webview`、`api36-r75-rss-edit-cancel`、Backup `api36-r75-backup*` / `api36-r75-system-folder-picker*` / `api36-r75-backup-picker-cancel` / `api36-r75-backup-return`、Log `api36-r75-app-log` / `api36-r75-log-return`、QR 权限 `api36-r75-qr-permission` / `api36-r75-qr-permission-denied`、[API35 focused 回归](../build/ui-redesign-validation/physical-talkback/focused-regressions.txt)、[API35 普通回归](../build/ui-redesign-validation/physical-talkback/ordinary-regressions.txt)、[API35 导航点击结果](../build/ui-redesign-validation/physical-talkback/ordinary-navigation.json)、[人工试听确认范围](../build/ui-redesign-validation/physical-talkback/user-listening-confirmation.json)、[Web 状态与键盘检查](../build/ui-redesign-validation/web-a11y/report.json)。

## 1. 排版 Typography

- **当前状态：** R65 历史签名 R8 包在 API 36 模拟器上实际打开 HttpTTS 表单，标签不重复、hint/underline/cursor 为红色；Gboard 两个可见状态标记均为 true，Save 可达。截图见 `build/ui-redesign-validation/native/api36-r65-tts-focus.png`，R63 同一表单像素检查仍是先前证据。
- **发现的问题：** Compose 外层文本与 TextInputLayout hint 重复；全屏 CodeEdit 以 hint 作为标题，直接删 hint 会丢失编辑器标题。
- **已执行优化：** 删除重复的外层文本，保留 hint，并将间距从 12dp 收至 8dp。R63 模拟器截图确认实际渲染符合预期。
- **剩余可提升空间：** 本轮未提交 HttpTTS 表单，也未验证真实 TTS Provider 的输出；其他表单的排版未由该路径覆盖。

## 2. 间距 Spacing

- **当前状态：** R65 历史导入弹窗在 10 条列表、字体 1→2 重建后保留唯一项与页脚可见；R74 历史 release R8 烟测复核 320dp RSS 输入 51.05×48dp、清除 48×48dp，Discovery 字体 2 内容未被裁切。R63 的 30 行 debug 重建不代表 R8 覆盖。
- **发现的问题：** ThemeList 少量内容曾占用近整屏；导入弹窗把可用空间错误留给内容区，字体放大后挤压操作页脚；Source/RSS 底部操作区靠近系统手势区域。
- **已执行优化：** ThemeList 按内容自然收高；导入列表依工具栏和页脚实际尺寸使用剩余空间，并在重建后保留列表状态；Source/RSS 底部安全区及 FileManager/QR 点击区按系统 Insets 和可触达尺寸调整。R63 字体缩放重建路径通过。
- **剩余可提升空间：** 已验证的 Insets、字体比例和窗口尺寸仅代表对应 API 36 模拟器页面；其他屏幕和真实设备布局仍需按实际范围核对。

## 3. 视觉层级 Visual Hierarchy

- **当前状态：** 书籍详情主要操作在 320dp、字体比例 2 下保持等高；R65 历史模拟器中长按书籍可打开 BookInfo，实际页面显示默认封面及原创 QA 内容（`build/ui-redesign-validation/native/api36-r65-book-detail.xml/png`，非 mockup）。Video 404 错误画面不再叠加原生播放圆标。书架、书源字段和原有操作入口仍遵循 Legado 组织方式。其他证据见 `build/ui-redesign-validation/native/api36-r45-runtime-summary.json`、`api36-r41-video-error.png` 和 `api36-r41-video-recovered.png`。
- **发现的问题：** 狭窄窗口和较大字体会放大按钮高度差；视频错误提示曾与原生播放控件重叠，遮挡状态信息；重构若替换首页和字段组织会削弱用户熟悉的层级。
- **已执行优化：** 统一详情操作按钮尺寸，隐藏错误态重复播放图标并修正播放器初始化顺序；保留书架、书源及阅读器既有组织和位置，放弃此前的新首页概念。
- **剩余可提升空间：** 当前证据是若干重点页面与播放器路径，不是全 App 逐页的视觉层级评审；Vietnamese 字符测试及上述尺寸覆盖不代表所有语言和字号组合。

## 4. 色彩 Color

- **当前状态：** R71 Theme ROI、R73 SearchView 包与 R74 release R8 均为历史证据；R74 实际像素对比度为 Light #FFFFFF/#715043=7.16563:1、Dark #FFFFFF/#5F7782=4.72159:1，Discovery 日夜显示与字体 2 页面已查看，输入与清除目标均为 48dp。
- **发现的问题：** 部分控件曾绕过 ThemeStore 语义色，导致深色主题标题发黑、系统栏与浅色内容对比不足，输入焦点的旧 tint 也没有实际改变像素颜色。R72 SearchView 的固定灰色 ImageSpan 与提示文字对比不足，且触控区域仅 30dp；R72 因此保留为历史候选。
- **已执行优化：** 颜色改用主题语义角色，修正深色标题、FileManager 系统栏对比和 HttpTTS 支持 tint。共享 SearchView 的 hint 沿用输入框 ColorStateList，ImageSpan 按 paint.color 着色，命中区修正为 48dp 并留 4dp 边距；R74 定向测试 2/2 PASS。R63 截图是焦点颜色的渲染证据，R59 洋红下划线只作为修复前基线。
- **剩余可提升空间：** R74 历史 release R8 已复核列明的 SearchView 对比度、尺寸和主题路径；其他主题、系统栏组合与显示模式未逐一核对，也不由该路径证明全页状态。

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

- **当前状态：** R75 API36 模拟器正常 Launcher 进入 Main 后，经普通触控到 My、About；Main Log 可打开 No logs 空态并返回。Backup 可从首次帮助进入 Backup to 与 Select folder，系统 DocumentsUI 正常打开，Back 取消后回 Backup 再回 Main；未选目录或执行备份。RSS 订阅首页的 Meow云 tile 进入真实 WebView 登录页，系统返回后回到订阅首页。RSS 源管理 Add 帮助关闭后进入 Basic 表单；Start、List、WebView 标签均可切换，横向滚动后 CookieJar 完整可达；系统 Back 回管理页，四条默认源仍是 0/4 选中。Import from QR code 显示系统相机权限提示，Don't allow 后回到源管理且默认项不变。About 和上述路径证据详见验收记录。API35 四个主导航普通点击均选中目标页、`ordinary-navigation.json` 无 `SETTLING`，仅为该 API35 路径证据。RSS 本地 HTML 重建/配置与 native 宿主两项定向测试也通过。
- **发现的问题：** RSS 共享宿主曾把 `ComposeView` 强转为 `TitleBar`，导致 ClassCastException；主导航变更曾偏离原生直接切页行为。普通导航状态测试不测连续动画或帧率。
- **已执行优化：** `d0707cc6b` 修复 BaseActivity/Fragment 的宿主强转；`MainActivity` 恢复原生 `setCurrentItem(false)` 直接切页。API35 普通导航四入口结果见 `ordinary-navigation.json`；RSS 的两项回归及普通回归见 `focused-regressions.txt` 与 `ordinary-regressions.txt`。
- **剩余可提升空间：** R75 支持所列 API36 普通触控路径，不证明完整返回栈、全页焦点顺序或流畅度改善；Meow云仅打开登录页并返回，未登录、未读账号内容；新 RSS 源表单未保存，未验证实际列表/WebView 数据或网络提交。QR 相机权限的拒绝后返回已验证，但允许/恢复及扫码成功未验；Backup picker 未选择目录，备份与恢复未验。其他 RSS 服务状态、Chrome 软键盘和外接键盘仍未覆盖。

## 8. 适配 Adaptive Layout

- **当前状态：** R65 历史签名 R8 包在 API 36 模拟器上通过导入 10 条列表的字体 1→2 重建与页脚可见性检查；此前还覆盖 320dp/字体比例 2 下的书籍详情、RoleTTS/MCP 横向布局，以及 Web 五种宽度的浅色和深色界面，这些均为历史证据。当前 Web fixture 浏览器结果覆盖 320/390/1280 三宽、18 屏和错误/键盘路径，不外推到 Android 或真实服务。R63 30 行重建为 debug 测试证据，不扩张为 R8 覆盖。
- **发现的问题：** 窄屏及大字号可能压缩详情按钮与导入页脚；横屏内容需按原有导航关系重新排布；Web 搜索和详情曾有文字、标题对比与裁切问题。
- **已执行优化：** 调整详情按钮等高、导入列表与页脚的空间关系，并修正 Web 自然高度、标题对比和布局细节。历史 Web `f5cad386f` 的 10 屏矩阵对应 5 种宽度 × 2 种主题；本轮 Vite build 和 Android asset sync 通过，fixture 浏览器报告覆盖 320/390/1280 三宽下 18 屏。浏览器中的工具热区至少 44px 并在视口安全区内。
- **剩余可提升空间：** 适配结论限于所列尺寸、页面和主题。R74 历史 release R8 已检查 320dp RSS 操作区、Discovery 字体 2 输入和内容；其他窗口、显示缩放、外接键盘与 Chrome 软键盘仍待核验。

## 9. 产品状态 Product States

- **当前状态：** R75 API36 最终正文/Main 定向 debug instrumentation 复验 2/2 PASS（20.605 秒，0 ignored），证据 `api36-r75-reader-navigation-final.txt`。正文路径覆盖链接分组/边界/滚动与点击确认、图片预览、失效节点无边界且不可点击、翻页动作命名、菜单关闭/恢复以及直接退出搜索菜单后 importance 恢复；Main 测试也通过。R75 R8 普通触控另查看 RSS 阅读记录与收藏空态、规则弹窗取消、源管理与新源表单标签切换/CookieJar 可达，及 Backup 系统 picker 取消返回、Log 空态返回和 QR 相机权限拒绝后的返回；新源未提交，Backup 未选目录或执行备份。此前正文 fixture 超时和主机查询失败不是产品失败结论，具体诊断见[验收记录](ui-redesign-acceptance.md)。
- **发现的问题：** 初次 R75 批次的 Main 报错明确读到 SystemUI ANR 窗口；同批正文节点超时与 ANR 时间重叠，但当时没有正文窗口诊断，不能单独归因系统。后续主机 GPU 复验另遇首次密码弹窗，Body 查询隔离失败；诊断发现 UiAutomation 默认 `INCLUDE_NOT_IMPORTANT_VIEWS` 会读到正文节点，即使应用设置了 importance。
- **已执行优化：** `exitSearchMenu` 直接 Back 后恢复正文 importance 的源码修复已在 R75 最终复验中通过。正文测试仅清理 UiAutomation 这一个查询 flag、按应用状态断言，并在 `finally` 恢复。切换到 API36 模拟器后的复验没有启用 TalkBack。R75 分批 debug instrumentation 合计 17 个 unique tests 均有通过结果，不代表一次全套运行、R8 测试或全应用通过。
- **剩余可提升空间：** TalkBack 的真实焦点恢复检查为条件执行、未运行；其他历史成功、空态、拒绝和错误路径不代表所有页面状态通过。真实 AI/TTS Provider 效果按用户接受边界未验证。

## 10. 性能 Performance

- **当前状态：** R36 API 36 模拟器混合会话记录 548 帧、470 帧卡顿（85.77%），p50 53ms、p90 150ms、p95 300ms；该数据为负向历史证据，性能尚未通过。R75 首批运行有 Main 被 SystemUI ANR 窗口遮挡的记录（`api36-r75-anr-events.txt`）；后续 host GPU 验证窗口没有新 ANR，只支持该测试窗口的所测路径，不能作为性能通过证据或归因。R70、R72 与 R74 smoke 的零事件记录也仅限各自实际路径。API35 本轮验证功能路径，未测真机性能或动效。
- **发现的问题：** R36 采样混合了首次使用、录屏与导航，不是隔离稳态测量；录像缺帧仍保留，不能单独归因为产品或模拟器，也不是有效的主导航性能采样。普通点击导航用例只检查选中状态，不量化流畅度。
- **已执行优化：** 列表按需加载和连接生命周期有源码层调整，但现有记录没有可对照的隔离 FPS/帧时数据，不能据此声称量化性能改善。
- **剩余可提升空间：** 性能结论仍未通过/未完成。用户已明确本轮真机性能与动效未验证；R36 高 jank 与录像缺帧继续保留，不能据此或据导航选中测试量化本轮改善。

## 11. 无障碍 Accessibility

- **当前状态：** R75 API36 定向运行的 ReaderMenu 10 项均有通过记录；API35 也有同一菜单 10 项 focused 通过。QR 手电筒 48dp/状态名称和自动亮度文案属于更早的 API36 局部证据。R71 TalkBack bridge 1/1 仅验证 framework focus/action/selection，属历史结果；TalkBack 保持关闭且不再启用。人工确认仅限已记录的两批确认与最初中文试听，不是全页语音或手势验收。
- **发现的问题：** 开关如果只暴露控件名称或格式化数值，屏幕阅读器用户无法得知当前开关状态；较小热区也会增加触控难度。
- **已执行优化：** QR 开关增加明确状态名称并达到 48dp；ReaderMenu 标签改为本地化的启用/禁用状态，滑块语义修正。R71 bridge 只作为历史自动化证据保留；切换到 API36 模拟器后的复验没有开启 TalkBack。Web fixture `scripts/state-a11y.cjs` 的 137/137 结果及 R74 SearchView 检查仍是各自构建下的历史局部证据。
- **剩余可提升空间：** 全页人工读屏、完整跨页焦点顺序、真实 TalkBack 语音/手势、QR 相机允许/恢复、其他权限路径及所有触控边界未验收；用户明确要求 TalkBack 保持关闭。实体设备性能/动效和真实 AI/TTS Provider 效果仍未验证。

## 12. 原创性与品牌 Originality & Brand

- **当前状态：** Android 与 Web 回到 Legado 原版布局、信息层级和操作位置。R75 API36 QA 包的 About 实际首屏显示 Legado-Z、仓库与版本信息；普通触控打开/关闭帮助和 CrashLog 空态，检查 GPL-3 许可证文档与免责声明入口，截图见 `api36-r75-about*.png`。API35 另有 About 内容与局部 2 倍字号检查；Web 有五种宽度的浅/深主题代表页覆盖。
- **发现的问题：** 此前的新首页概念偏离用户要求的原版结构；只看单个组件容易误把局部改造当成全产品视觉定稿。
- **已执行优化：** 放弃新首页方向，围绕 Legado 原有信息关系优化排版、状态反馈、Insets、键盘和错误提示；About 使用项目真实身份信息并精简重复文案，不改变熟悉的业务入口和字段组织。
- **剩余可提升空间：** 当前 About 与导航证据只覆盖所列页面和路径，不代表全 App 已完成最终美术评审；不据此宣称获奖或整体视觉已最终定稿。

## 最终自检结论

**最终自检结论：当前验收候选，尚未完成。** R75 本地签名 R8 QA 包已构建；API36 模拟器分批 debug instrumentation 17 个 unique tests 均有通过记录，R8 普通触控 smoke 则另行覆盖 Main→My→About、RSS 登录页/空态/源表单浏览返回、Backup 系统 picker 取消返回、Log 空态返回及 QR 相机权限拒绝后返回。Meow云未登录、账号内容未读取；新源未保存/提交；Backup 未选目录或执行备份/恢复；QR 权限允许/恢复和扫码未验。上述结果不等于全 RSS 服务、账户流程或全应用验收。API35 结果仅支持所列旧真机路径，未来操作仍限定既有 API36 模拟器。不得写成全应用没有缺陷或最终视觉验收完成；`final=false`。切换到 API36 模拟器后的复验没有启用 TalkBack，当前保持关闭且不再启用；全页语音/手势、真机性能与动效、真实 AI/TTS Provider 效果未验证。
