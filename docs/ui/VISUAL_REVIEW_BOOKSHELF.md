> 历史验收记录：对应书架阶段提交 `ff18ea38b`。下文“本轮”均指该阶段；不能代表后续备注、筛选及其他页面的当前结果。API26临时AVD已移除。

# 视觉验收 · Android 书架

本轮采用原创“书页边注”方向，在现有 Android Compose 书架上实现；MD3 分支仅作参考，没有复制其页面或替换主题系统。普通列表、紧凑列表、2–6 列偏好、书籍内容与原有操作保留。其他核心页面的 SCREEN_SPEC 已整理，尚未按本规范重绘，不能据本报告宣称全 App 完成。

## 证据与环境

- API 36 Google APIs x86_64 模拟器，420dpi。常规显示 1080×2400（约 411dp）；小屏显示覆盖为 840×1680（320dp），属于同一模拟器的两种窗口尺寸。
- 另外使用本机已安装的 API26 Google Play x86 镜像，在项目临时目录创建 Pixel 2 AVD，1080×1920、420dpi、SwiftShader；没有下载系统镜像或改变 SDK 配置。
- 大字体在独立 Compose fixture 中注入 `fontScale=2`，没有修改系统字体或用户偏好；不等同于所有 Android 版本的非线性字体缩放测试。
- `BookshelfVisualTest` 使用五本固定虚构书籍、真实 CoverImageView 和生产组件；不写数据库、不请求书源、不改主题。fixture 的工具按钮只用于构图；不能代表搜索/更多菜单集成通过。
- 最终 fixture 截图使用 UiAutomation 捕获完整窗口，在系统无障碍事件静默 3.5s 后采集；另保存完整 App 启动截图验证真实宿主。所有截图都是原始输出，未裁切、拼接或美化。
- 参考包 `io.legato.kazusa` 与本项目独立安装，只查看空书架与“我的”，没有导入账号或内容。生成的三张黑白概念图仅证明设计探索，不作为运行证据。

## 第一轮：布局与状态

| 位置 / 问题 | 影响 | 修改 | 验证方法与结果 |
| --- | --- | --- | --- |
| 普通书目：长书名只能一行，章节信息灰度与图标节奏接近 | 书籍识别过早省略，当前与最新章节不易区分 | 普通书名采用系统 Serif 18/26sp、最多两行；作者独立；章节以短标签和竖线归组，更新使用主要前景 | 对照 [改前普通列表](assets/runtime/before-regular-normal.png) 与本轮普通列表；断言普通长标题两行、紧凑长标题一行 |
| 空书架：整段指引作为重标题 | 文字重心过重，缺少清楚的主次 | 新增短标题，原指引降为辅助正文，居中并可滚动 | 对照 [改前空态](assets/runtime/before-regular-empty.png) 与本轮空态；顶部工具和底栏仍可达 |
| 整页错误：长原因没有滚动容器 | 小屏大字时重试离开可视区且无法滚动到达；改前 fixture 实际出现无 ScrollSemanticsAction 失败 | 空/错误内容使用可滚动容器，保留原因及原重试回调 | 长错误 fixture 滚动到重试并点击，断言回调一次；分别保存滚动前后截图 |

## 第二轮：小屏与两倍字体

| 位置 / 问题 | 影响 | 修改 | 验证方法与结果 |
| --- | --- | --- | --- |
| 章节标签：固定标签列在大字体下裁字，随后自然宽度版本缺少标签后间距 | “读至/更新”识别不完整或与章节粘连 | 标签按自然宽度展开，最小 28dp，后留 8dp | [改前大字标签](assets/runtime/large-before-normal.png) 对照本轮大字普通列表；检查完整标签与独立间距，并断言无文字溢出 |
| 高密度网格：大字未读数 `108` 换成多行，列数挤压内容 | 徽标失去紧凑状态语义，大字阅读困难 | 徽标限制单行；实际列数根据可用宽度和 `48dp × max(1,fontScale)` 最小单元格减少，保存偏好不变 | [改前大字网格](assets/runtime/large-before-grid6.png) 对照本轮网格；检查三位数字单行且不省略、点击目标至少 48dp |
| 正常态截图：按压残留或系统键盘语言 Toast 被拍入 | 无法把叠层或按压状态当作页面静止外观 | 默认画面在交互前捕获；等待系统事件稳定后捕获完整窗口 | 重新查看正常、网格与错误截图；系统 Toast 不计为 App 缺陷，也不通过修图隐藏 |

诊断记录：徽标文本的 `hasVisualOverflow` 曾返回宽度溢出。进一步诊断测得：文字 box 宽 54px、字形范围 24–78px（宽 54px）、缓存段落宽 102px；居中段落宽度与最终文字 box 并不相同。最终断言检查单行、不省略、不垂直溢出及字形跨度不超出测量宽（1px 舍入容差），不以段落内的绝对右边缘判断居中文字裁切。中间失败不计为通过，最终运行结论见下节。一次相互重叠的诊断启动中断了先前 instrumentation，属于无效运行，不计入最终结果。

采集诊断：一次 Compose 根截图实际报 `ComposeTimeoutException`（采集超过 2000ms），随后改用完整窗口。复核时误把同路径的旧预览当作当前图片，曾怀疑导航缺层；重新读取当前原始 PNG、检查导航区域像素，并以独立进程重拍后，确认当前工具栏与四项底栏完整，未确认产品缺层。该怀疑已撤销，不能写成产品缺陷或产品修复。最终图片没有通过修图补齐导航。

## 最终复验

当前生产源码的 API36 结果如下；同一运行中的截图全部来自最终完整窗口采集方式。

| 设备配置 | 实际执行 | 结果 |
| --- | --- | --- |
| 常规 1080×2400、字体 1× | `BookshelfVisualTest` 7 项 + 现有 `ComposeMainUiTest` 10 项 | 17/17 通过，122.883s |
| 小屏 840×1680、字体 1× | `BookshelfVisualTest` 全部 | 7/7 通过，63.072s |
| 小屏 840×1680、字体 2× | 普通、密集网格、空、长错误 | 4/4 通过，42.898s |
| 两倍字体错误态独立复拍 | `errorShelf` | 1/1 通过，10.680s；重试及底栏可见性通过 |
| API26 1080×1920、字体 1× | 普通/长错误 2 项 + `ComposeMainUiTest` 10 项 | 12/12 通过，32.802s |

现有回归覆盖导航隐藏/选择、书籍打开、分组长按/选择、更新时间与未读共存、网格隐藏标题与字段层级，以及书架显示设置草稿；新 fixture 另外覆盖普通/紧凑长标题、徽标单行与尺寸、空/加载/长错误及滚动重试。回调断言不等同于真实书源或阅读引擎集成验收。

| 截图 | 常规屏 | 小屏 | 小屏两倍字体 |
| --- | --- | --- | --- |
| 普通、长书名 | [普通](assets/runtime/regular-normal.png) | [普通](assets/runtime/small-normal.png) | [普通](assets/runtime/large-normal.png) |
| 紧凑 | [紧凑](assets/runtime/regular-compact.png) | [紧凑](assets/runtime/small-compact.png) | 未专项截图 |
| 三列网格 | [网格](assets/runtime/regular-grid.png) | [网格](assets/runtime/small-grid.png) | 未专项截图 |
| 六列偏好自适应 | [网格](assets/runtime/regular-grid6.png) | [网格](assets/runtime/small-grid6.png) | [网格](assets/runtime/large-grid6.png) |
| 空 / 加载 | [空](assets/runtime/regular-empty.png)、[加载](assets/runtime/regular-loading.png) | [空](assets/runtime/small-empty.png)、[加载](assets/runtime/small-loading.png) | [空](assets/runtime/large-empty.png)；加载未专项截图 |
| 长错误与可达重试 | [顶部](assets/runtime/regular-error-top.png)、[重试](assets/runtime/regular-error.png) | [顶部](assets/runtime/small-error-top.png)、[重试](assets/runtime/small-error.png) | [顶部](assets/runtime/large-error-top.png)、[重试](assets/runtime/large-error.png) |

最终逐图复核的三个重点：长标题/章节在限制行数内省略且保留完整语义；边注标签与章节之间有独立间隔；长错误滚动后重试和底栏保持可达。未再确认本轮书架范围内阻断使用的视觉问题。剩余长文字缺省封面及真实辅助功能覆盖见下节，不能以测试全绿替代用户对视觉方向的审美评审。

真实宿主另行检查：[默认主题](assets/runtime/app-final-regular.png)、[深色主题](assets/runtime/app-final-dark.png)、[墨水屏模式](assets/runtime/app-final-eink.png)。通过现有“我的 → 主题模式”切换，截图后恢复原来的“跟随系统”，再次读取页面确认原值恢复，并回到书架。墨水屏截图仅证明模拟器上的黑白模式，不能证明真实墨水屏的刷新与残影表现。

API26 截图：[普通书架](assets/runtime/api26-normal.png)、[长错误](assets/runtime/api26-error-top.png)、[重试](assets/runtime/api26-error.png)。该系统的中文系统 Serif 回退呈无衬线字形；API36 呈衬线字形。字号、边注、字段关系与操作仍一致，本轮接受平台系统字体差异；若后续要求各 Android 版本字形完全统一，需评估随包字体的体积与授权。

## 已实现与未实现

已实现：共享书架内容的原创排版、标签资源、空/加载/错误层级、长错误滚动、随大字体适配的网格和更新徽标。两个既有书架宿主的调用契约保持不变，没有数据库迁移、业务接口变更、新依赖或主题覆盖。

参考功能差异仅列为候选：书目备注、阅读状态虚拟分组等尚未接入。现有章节札记、物理键和累计阅读时长不被当作对应候选已完成的证据。

## 验证边界与剩余提升

- 当前检查不覆盖真实手机/平板、iOS、TalkBack 实际朗读、硬件键盘焦点、真实硬件性能或动效流畅度；截图不能替代这些证据。API26 只覆盖表中书架组件和导航回归，未宣称全 App 老系统兼容验收完成。
- 新增文字覆盖英文、简体中文和繁体地区资源；其他语言沿用 Android 英文回退，尚未逐语言验收。
- 用户自定义封面及长书名的缺省文字封面仍沿用既有 CoverImageView。极长名称在封面内部密集是既有行为，不能把外侧标题优化当作它已修复。
- 默认、夜间与墨水屏模式已实际截图；任意自定义主题组合未穷举，继续走原语义 token，不能把三组截图推广为全部主题通过。
- 发现、搜索、阅读器、订阅、我的与设置的后续规格不代表已实现结果；全 App 统一重绘与完整核心流程验收仍未完成。

## 构建核验记录

均由 `gradle-run` 管理包装器调用当前项目 Gradle，离线执行；编译成功只说明对应产物可构建，不替代 instrumentation 与截图。以下是每次非空 verification question 的原文与有界结论。

| Run | Question | Gradle task / answer |
| --- | --- | --- |
| 0001 | “Can the existing Android app and the focused bookshelf visual fixtures compile before the UI change?” | `:app:assembleAppDebug :app:assembleAppDebugAndroidTest`；成功，265.641s |
| 0002 | “Do the bookshelf typography, scrollable states, accessible grid sizing and focused visual tests compile?” | 同上；成功，300.891s |
| 0003 | “Do the deterministic Chinese bookshelf fixtures and compact-title assertion compile?” | `:app:assembleAppDebugAndroidTest`；成功，19.985s |
| 0004 | “Does the screenshot fixture compile after separating the resting capture from click and long-press feedback?” | 同上；成功，31.407s |
| 0005 | “Do the large-font label and badge overflow fixes compile with their visual assertions?” | `:app:assembleAppDebug :app:assembleAppDebugAndroidTest`；成功，70.203s |
| 0006 | “Does the final platform screenshot capture fixture compile?” | `:app:assembleAppDebugAndroidTest`；成功，19.922s；后续捕获方式改为 Compose，不能复用为最终源码证据 |
| 0007 | “Does the focused badge layout diagnostic fixture compile?” | 同上；成功，25.500s |
| 0008 | “Do the final chapter spacing and pixel-tolerant full badge assertions compile?” | `:app:assembleAppDebug :app:assembleAppDebugAndroidTest`；成功，78.063s；后续断言进一步诊断 |
| 0009 | “Does the focused single-line badge width diagnostic compile?” | `:app:assembleAppDebugAndroidTest`；成功，23.125s |
| 0010 | “Does the measured glyph-span assertion compile with the final bookshelf fixtures?” | `:app:assembleAppDebugAndroidTest`；成功，23.515s；最终测试源码 |
| 0011 | “Does the post-scroll full-window screenshot check compile?” | `:app:assembleAppDebugAndroidTest`；成功，31.515s；增加滚动后底栏可见断言与完整窗口截图 |
| 0012 | “Does the stable full-window capture compile with the final visual checks?” | `:app:assembleAppDebugAndroidTest`；成功，143.953s；最终采集源码 |

构建过程中未发现本次编译错误；既有弃用/注解警告未纳入无关修改。已执行 `gradle-run finish` 完成当前 workflow，仅清理包装器持有的临时日志；没有删除项目源码或验收截图。

环境收尾：API36 显示尺寸已恢复 1080×2400，主题恢复“跟随系统”，当前停留书架；未启用 TalkBack。已卸载本轮临时参考包，停止并清理本轮 API26 AVD、下载 APK、诊断 XML 与重复预览，保留本地最终截图和既有 API36 AVD。没有 push、PR、发布或部署。
