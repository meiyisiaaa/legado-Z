# Legado-Z

Legado-Z 是基于阅读 Sigma（Legado-E）继续开发的 Android 阅读器，继承 Legado 的自定义书源、本地阅读、订阅和听书能力，并加入 AI 阅读辅助、单文件 JS 书源、MCP 书源维护与分角色朗读。

软件不内置书源或书籍内容，需要自行导入书源或本地文件。

[使用帮助](app/src/main/assets/web/help/md/appHelp.md) · [API 文档](api.md) · [验收记录](docs/verification/legado-porting.md) · [开源许可](LICENSE)

## 支持与升级

- **最低 Android 8.0（API 26）**。Android 5–7 请继续使用旧版，并保留旧版安装包和备份。
- 升级沿用当前项目的包名和签名配置，数据库会自动迁移。覆盖安装仍要求包名和签名一致，升级前请先备份。
- 包含单文件 JS 书源的新备份不保证可由旧版恢复；需要回退时应使用升级前的备份。
- 脚本引擎保留 **Mozilla Rhino 1.8.1**。单文件 JS 书源适配阅读 T 的同步协议；Rhino 不支持的语法或缺少必需函数时会给出错误提示。

## 主要能力

| 能力 | 说明 |
| --- | --- |
| 阅读与书架 | 自定义书源搜索和发现、本地 TXT / EPUB、阅读排版、翻页、替换净化与订阅。 |
| 书源编辑 | Android 和 Web 编辑、JSON / 单文件 JS 导入导出、备份恢复，保留传统规则书源。 |
| AI 书源助手 | 生成、批量生成和诊断书源；支持未保存草稿试跑、结果预览及选择诊断材料。 |
| 选区问答 | 对当前选区提问或赏析，支持多轮对话，当前问答可保存为原位置札记。 |
| MCP 书源维护 | 手动开启调试会话，向客户端提供书源读取、保存、删除、校验和调试工具。 |
| 分角色朗读 | 手选章节生成分镜，管理角色、别名和音色绑定，复用系统 TTS 或已有 HTTP TTS 配置。 |
| 可靠性修复 | 跨站跳转认证隔离、快速切书任务隔离、网页任务取消、大文本编辑与筛选拖动排序修复。 |

## 开始使用

### 阅读与书源

导入自己的书源，或打开本地 TXT / EPUB 文件即可开始阅读。传统规则书源继续使用原有解析方式；单文件 JS 书源使用同步协议。

JSON 导出和 App 备份用于保留书源代码及分组、启停、排序等用户设置。原始 `.js` 导出只包含脚本，适合单独分享或编辑脚本。

在 Web 端管理书源前，需要先在 App 设置中开启 Web 服务。JS 导入会先解析并显示预览，检查后仍需手动保存。接口行为见 [Web/API 文档](api.md)。

### AI 书源与阅读问答

1. 在「设置 → 其他设置 → AI 配置」填写 OpenAI 兼容服务的接口地址、API Key 和模型名，也可从 AI 面板进入配置。
2. 在「书源管理 → AI 书源助手」生成或诊断书源。诊断可先试跑未保存草稿，再选择已完成结果，以及需要发送的日志或 HTML 片段；试跑本身不保存书源或导入书籍。
3. 启用读者助手后，在阅读器长按选中文字，选择「AI 提问」或「AI 赏析」。

阅读问答的正文上下文仅来自当前选区，并沿用选区长度限制；模型还会收到当前问题和同一选区下的成功问答历史。历史最多保留 **4 个问答对、8,000 字符**，当前问题最多 **2,000 字符**。失败或取消的轮次不进入模型历史。

旋转屏幕保留会话；显式关闭面板、清空会话、换选区或换模型时取消请求并清空上下文。札记只保存当前问答，并绑定原选区位置。

书源调试和批量校验一次只运行一项；已有任务占用时，其他入口会提示忙状态。App、WebSocket 和 MCP 使用相同的调试约束。

### 分角色朗读

1. 在阅读器朗读面板打开「角色朗读」，添加、改名或合并角色，并绑定系统 TTS 音色或已有的 HTTP TTS 配置。
2. 配置好 AI 服务后，手动选择需要处理的章节并启动分镜。生成分镜与开启角色朗读是两个独立操作。
3. 开启角色朗读后，已准备的章节按角色播放；缺少有效分镜或音色时使用普通旁白。

分镜只处理手动选中的章节，不会自动分析下一章。离开准备流程或停止时取消未完成工作，保留已完成缓存。角色与人工音色绑定随书架备份；分镜和音频属于可重建缓存。

### MCP 调试会话

在「设置 → 其他设置 → MCP 书源调试会话」中手动启动服务，默认端口为 `1236`，地址为 `http://手机局域网IP:1236/mcp`。客户端需要使用面板中复制的 Bearer 令牌。

服务默认关闭，需要在 App 前台开启。面板和通知栏均可停止；停止或系统超时后关闭监听，不自动重启。令牌可重新生成，独立加密保存，不进入备份。

提供六类工具：`list_sources`、`get_source`、`save_source`、`delete_sources`、`check_source`、`debug_source`。保存默认拒绝覆盖已有 URL；草稿调试不落库，也不提供书架正文或阅读记录接口。参数、鉴权与 Host / Origin 要求见 [MCP API 文档](api.md#mcp-书源维护)。

## 本地构建

### Android

需要 Git、JDK 17 和 Android SDK Platform 36，并通过 `ANDROID_HOME` 或 `local.properties` 指定 SDK。使用仓库自带的 Gradle Wrapper：

```powershell
.\gradlew.bat :app:assembleAppDebug
```

macOS / Linux 对应命令为：

```sh
./gradlew :app:assembleAppDebug
```

Debug APK 位于 `app/build/outputs/apk/app/debug/`。

`debug` 与 `release` 使用不同包名。发布签名由 `RELEASE_STORE_FILE`、`RELEASE_STORE_PASSWORD`、`RELEASE_KEY_ALIAS`、`RELEASE_KEY_PASSWORD` 配置；提供这些属性时，debug 构建也会使用该签名配置。详见 [App 构建配置](app/build.gradle)。

### Web 前端（可选）

前端位于 `modules/web`，需要 Node.js 20+、pnpm 9+：

```sh
cd modules/web
pnpm install
pnpm build
```

本地产物位于 `modules/web/dist/`。当前脚本在本地不会自动复制产物到 Android 静态资源目录；若要让 App 使用前端改动，需要将产物同步到 `app/src/main/assets/web/vue/` 后重新构建 App。Web 开发服务需要 App 提供后端，详见 [Web 开发说明](modules/web/README.md)。

## 验证范围与已知限制

- 已有 API 26 / API 36 模拟器定向回归与真实 MCP 客户端验证记录。本轮 API 36 另完成生产 R8 包的 Web HTTP/WebSocket 联动、原生配置与备份恢复补验，以及 12 项 JS 仪器回归。
- 新增 AI 与角色朗读已完成接口适配；真实模型、系统 / HTTP TTS Provider、音质及跨引擎播放尚未验收。自动化测试和合成响应不代表真实服务效果。
- Web 书源与订阅源编辑器已适配窄屏，320 / 390 / 768 / 1280px 检查无页面横向溢出。窄屏下改为纵向布局，功能标签可横向滚动。
- Android Chrome 360px 已实际验证软键盘输入、返回与保存；JSON 导入支持单对象或数组，替换列表后会清除旧筛选和选择。JS 配置解析超时会显示明确错误，随后可继续解析。
- 单文件 JS 以当前 Rhino 和同步协议支持范围为准，不包含定制引擎专属语法、批量正文协议或独立事件系统。

逐项结果、测试边界与本地产物信息见 [移植验收记录](docs/verification/legado-porting.md)。

## 文档与开源来源

- [App 使用帮助](app/src/main/assets/web/help/md/appHelp.md)
- [HTTP、Content Provider 与 MCP API](api.md)
- [更新日志](app/src/main/assets/updateLog.md)
- [Legado 上游帮助](https://www.yuque.com/legado/wiki)

本项目沿用 **GPL-3.0** 许可，详见 [LICENSE](LICENSE)。基础代码继承自 [Legado-E / 阅读 Sigma](https://github.com/Luoyacheng/legado-E) 与 [Legado](https://github.com/gedoor/legado)；本次适配参考了 [LegadoTeam](https://github.com/LegadoTeam/legado)、[阅读 T](https://github.com/skybbk1001/legadoT) 和 [Legado NG](https://github.com/wuge0/legado_NG) 的相关实现与设计。

固定参考版本、署名及第三方组件许可见 [NOTICE](NOTICE)。
