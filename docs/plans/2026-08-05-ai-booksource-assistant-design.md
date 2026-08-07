# AI 书源生成/调试助手 v1 设计文档

> 状态：brainstorming 收敛结论（已与用户确认）
> 日期：2026-08-05
> 范围：阅读Sigma Android 主工程（legado-Z），标准 SOP 流程

## 1. 背景与目标

阅读Sigma（Legado 衍生版）目前**无任何 AI 能力**。经多轮 brainstorming 收敛，确定 v1 头条功能为 **AI 书源生成/调试助手**：

- **痛点**：书源（BookSource）是 Legado 生态的命根子，但"写书源规则 / 调试抓不到内容"是社区最大门槛，学习曲线陡。
- **目标**：用 LLM 把"网页/需求"变成合规书源规则 JSON，或诊断现有书源为何抓不到，降低书源创作门槛。
- **差异化**：目前市面无任何阅读器提供 AI 书源创作辅助 → 极致差异化。
- **约束**：开源软件、不绑厂商；用户自带 API key/端点；token 必须省。

## 2. 范围决策

- ✅ **v1 做**：AI 书源**生成** + AI 书源**调试**
- ❌ **不做（留后续）**：② 检索问答+赏析、③ 人物/剧情图谱、④ AI 听书旁白、全书级 RAG 摘要

> ② 检索问答+赏析 是已验证的高价值读者向方向，作为 v1 之后的自然跟随，不在本次范围。

## 3. 接入模型

用户自有云端 API：OpenAI 兼容 `/chat/completions`。用户自填：
- `endpoint`（如 `https://api.openai.com/v1` 或自建/Ollama）
- `apiKey`
- `model`（如 `gpt-4o-mini` / `deepseek-chat`）
- `temperature`、`maxTokens`

零成本、不绑厂商，沿用现有 `Server` / `HttpTTS` 的"用户自配远端服务"范式，配置仅存本地。

## 4. 功能定义

### 4.1 书源生成（Generate）
- 用户输入：目标网站 URL，或粘贴网页 HTML / 内容片段，并用自然语言描述想要抓什么（如"抓这个站的搜索结果、目录、正文"）。
- 流程：可选由 App 用 OkHttp 抓取页面 → 截取相关 HTML 片段（控制 token）→ `PromptBuilder` 组装（system=书源规则专家 + 网页片段 + 用户需求）→ `OpenAiClient` 请求 → 解析 LLM 输出的书源 JSON → 按 `BookSource` 结构校验 → 展示 → **一键导入**（复用现有书源导入流程）。

### 4.2 书源调试（Debug）
- 用户输入：粘贴一个现有书源 JSON + 一个测试 URL / 样例响应（可选抓取的 HTML）。
- 流程：LLM 诊断"为什么抓不到/抓错" → 返回修正建议，或修正后的书源 JSON → 展示 → 一键导入/覆盖。

## 5. 架构（新增 `io.legado.app.ai` 包，复用现有能力）

| 组件 | 职责 |
|------|------|
| `AiConfig` | 偏好存储：endpoint/key/model/temp/maxTokens，仅本地（DataStore 或 DB） |
| `OpenAiClient` | 基于现有 OkHttp + 协程的 OpenAI 兼容 `/chat/completions` 流式/一次性客户端 |
| `PromptBuilder` | 组装书源专家 system prompt + 网页片段/现有源 + 用户需求 |
| `SourceGenEngine` | 编排：抓取(可选)→调 LLM→解析 JSON→按 `BookSource` schema 校验 |
| `SourceDebugEngine` | 编排：诊断现有源 + 样例 → 返回修正建议/JSON |
| UI | `AiSourceHelperDialog`/`Fragment`：输入 URL/HTML，选 生成/调试，展示结果，"导入书源"按钮（复用 `BookSource` 导入） |

**复用点（关键，避免重复造轮子）：**
- 书源 JSON 结构 = 现有 `BookSource` 实体（ruleSearch / ruleBookInfo / ruleToc / ruleContent 等）。
- 导入 = 现有书源导入链路（`AddBookSourceDialog` / 导入逻辑）。
- 网络 = 现有 OkHttp 实例与协程作用域。
- 配置范式 = 现有 `Server` / `HttpTTS` 用户自配远端服务的 UI/存储模式。

## 6. 隐私与成本

- 只发送网页片段 + 用户意图（或现有源 + 样例），不发送书籍正文、不发送用户隐私。
- 配置仅存本地，不上报任何服务端。
- 失败/超时/鉴权错误给明确降级提示，不崩溃。

## 7. 测试策略与已知限制

- ✅ **沙箱内可跑 JVM 单测**：`PromptBuilder`（书源 prompt 组装）、`SourceGenEngine`（JSON 解析与 `BookSource` schema 校验）、`AiConfig` 校验、HTML 片段截取逻辑。
- ⚠️ **沙箱无法编译 Android**：gradle wrapper 在沙箱不可用，网络层/UI 代码写完需**用户本机构建或 CI 验证**。逻辑单测可跑，APK 编译验证归用户。

## 8. 后续（非 v1）

- ② 检索问答 + 选中赏析（读者向，本地检索只发片段）
- ③ 人物/剧情图谱（追更大部头）
- ④ AI 听书旁白（呼应 Sigma 音视频差异化）
