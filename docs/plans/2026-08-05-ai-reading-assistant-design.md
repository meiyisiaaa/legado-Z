# AI 智能阅读助手 v1 设计文档

> 状态：brainstorming 收敛完成，待 PM 出 PRD → 架构师分解 → 工程师实现 → QA 验证
> 日期：2026-08-05
> 负责人：软件开发团队（齐活林 / 许清楚 / 高见远 / 寇豆码 / 严过关）

## 1. 背景与目标
阅读Sigma（Legado 衍生版）目前**无任何 AI 能力**。目标：在「用户自带 API key / 端点」的开源约束下，新增一个**读者向 AI 助手**，解决「几百万字小说记不住、读不懂」的痛点，且满足：
- 不与现有**字典规则（DictRule）**撞车（字典规则已覆盖字面翻译）；
- **不传整本书**出设备（解决全书摘要 token 爆炸的问题）；
- 复用现有架构（本地全文、OkHttp、用户自配远端服务范式）。

## 2. 范围决策（经 brainstorming 收敛）
- ✅ **v1 做**：检索问答（轻量 RAG） + 选中文本 AI 赏析/解释
- ❌ **不做（留后续）**：全书摘要（token 贵）、独立 AI 翻译（与字典规则重叠）、书源生成/调试助手、人物/剧情图谱、AI 听书旁白

### 范围取舍依据
| 能力 | 差异化 | 实用性 | token 效率 | 结论 |
|------|:---:|:---:|:---:|------|
| 检索问答（轻量 RAG） | 高 | 高 | 极高（只发命中片段） | ✅ v1 头条 |
| 选中赏析/解释 | 高 | 高（每日高频） | 极高（只发选区） | ✅ v1 |
| 全书摘要 | 中 | 中 | 低（整本 token 贵） | ❌ |
| 独立 AI 翻译 | 低 | 中 | 中 | ❌ 与字典规则重叠 |

## 3. 接入模型
**用户自有云端 API**：OpenAI 兼容 `/chat/completions`，用户自填 `endpoint` + `apiKey` + `model` + `temperature` + `maxTokens`。
- 零成本、不绑厂商，可接 OpenAI / DeepSeek / 通义 / 本地 Ollama 等；
- 沿用 `Server` / `HttpTTS` 的「用户自配远端服务」范式，无新增架构负担。

## 4. 架构（新增 `io.legado.app.ai` 包，不污染现有阅读引擎）
- **`AiConfig`**：偏好存储（endpoint / apiKey / model / temperature / maxTokens），仅存本地（DataStore 或 DB）。
- **`OpenAiClient`**：基于现有 OkHttp + 协程的 OpenAI 兼容流式客户端（各家 OpenAI 兼容端点通吃，做字段容错）。
- **`RetrievalEngine`**：本地检索——对当前书章节做关键词 / top-K 片段召回。**复用本地全文，零新增存储**，整本书始终不出设备。
- **`PromptBuilder`**：组装 `system`（角色=阅读助手）+ 上下文（检索片段 or 选中文本）+ 用户问题。
- **UI**：
  - `AiChatBottomSheet`：问答入口，阅读页调出；
  - `AiExplainDialog`：选中赏析弹窗（接入 `BookTextView` 长按选区菜单）；
  - `AiSettingsFragment`：AI 服务配置。

## 5. 数据流
- **问答**：用户输入问题 → `RetrievalEngine` 本地捞 top-K 片段 → `PromptBuilder` 组装 → `OpenAiClient` 流式请求 → 答案流式渲染到 `AiChatBottomSheet`。全书文本永不外传，只发命中片段。
- **赏析**：长按选区 → `AiExplainDialog` → 仅选区文本进 prompt → 流式返回赏析/典故/语境解释。
- **降级**：超时 / 鉴权失败给明确提示，绝不崩溃。

## 6. 隐私与成本
- 默认只发检索命中片段 / 选中文本；可开关「允许发送更多上下文」。
- 配置仅存本地，不上报任何服务端。
- 失败 / 超时降级提示，不泄露 key。

## 7. 测试策略与已知限制
- ✅ **沙箱内可跑 JVM 单测**：`RetrievalEngine` 检索逻辑、`PromptBuilder`、`AiConfig` 校验。
- ⚠️ **沙箱无法编译 Android**（gradle wrapper 不可用）：网络层 / UI 代码写完需**用户本机构建或 CI 验证**。团队保证代码正确性与逻辑单测，APK 编译验证归用户侧。

## 8. 后续（非 v1）
书源生成 / 调试助手（最高差异化候选）、人物 / 剧情图谱、AI 听书旁白、全书级 RAG 摘要。
