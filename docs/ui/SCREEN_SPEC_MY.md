# 我的与设置分组规格

## 目标与边界

“我的”保持现有管理工具与设置入口，以阅读目录式分组建立扫描层次。页面继续使用原工具栏、帮助菜单、底部导航与 Preference 模型，不增加账号、统计、快捷首页或新业务。

本次只重新组织 `pref_main.xml` 的视觉分组，并调整共享 `ComposePreferenceScreen` 的分组标题。`MyFragment` 的监听器、Intent、主题切换和 WebService 生命周期保持原样。RSS、搜索、发现、书架与 Book 数据模型不在范围内。

## 信息结构

| 分组 | 依次排列的原有 key |
| --- | --- |
| 内容与规则 | `bookSourceManage`、`txtTocRuleManage`、`replaceManage`、`dictRuleManage` |
| 外观与服务 | `themeMode`、`webService` |
| 设置 | `web_dav_setting`、`theme_setting`、`setting` |
| 其他 | `bookmark`、`readRecord`、`fileManage`、`about`、`exit` |

前两组只新增无 key 的 PreferenceCategory。所有原 key、条目属性、默认值、相对顺序和 `aboutCategory` 均保留；不重建或代理 Preference 对象。新增标题提供英文、简体中文、台湾繁体和香港繁体资源，其他语言使用默认资源回退。

## 构图与组件

- 沿用 `DESIGN_SYSTEM.md` 的阅读工具气质与语义主题色。采用连续平面列表，不新增卡片、阴影、头像或装饰性统计。
- 分组标题：`titleLarge` 局部覆盖为系统 Serif，20/28sp、Medium、0sp 字距；颜色为 `onSurface`。正文与控件继续使用原 Material 3 Default 字体角色，避免把衬线扩散到配置值和说明。
- 标题容器左右 16dp、上 24dp、下 8dp。标题下方 8dp 放置 `outlineVariant` 细线，作为稳定的分组边界。既有 divider 标志产生的额外 8dp 留白继续保留。
- 标题无固定高度或单行截断，随语言与字体缩放自然增高，并提供 heading 语义。列表保留滚动、系统 inset 和底部留白。
- 原条目的至少 60dp 高度、24dp 图标、summary、开关、选项值和按压反馈保持不变。分组标题本身不可点击，不增加动效；条目的状态变化继续由 `LocalLegadoMotion` 与原模型驱动。

## 共享影响

共享渲染器由 `PreferenceFragment` 使用，新的标题排版也会作用于其承载的其他设置页面；仅有实际可见、非空分组标题的页面改变。无分组标题的 loose rows、Preference 行、对话框和各页面独立 header 不改变。隐藏组与空组沿用现有收集规则。

代表回归选取固定设置 fixture：两个分组包含普通入口和禁用入口，验证标题 heading 语义、组与行顺序、禁用行为及点击回调；My XML 测试验证四组结构、原 key 顺序和两个默认值。测试复用 `ComposePreferenceScreenTest`，不新增测试框架或生产测试开关。

## 验收与证据边界

本轮检查覆盖 XML 条目属性与顺序、资源完整性、共享标题语义，以及设置 fixture 的点击和禁用行为。真实宿主的窗口、字号、主题与代表设置页截图，按实际执行范围记录在 [VISUAL_REVIEW.md](../../VISUAL_REVIEW.md)。WebService 网络连接、真机性能与 TalkBack 实际朗读列入后续验证。
