# 阅读[API](/app/src/main/java/io/legado/app/api/controller)

## MCP 书源维护

在「设置 → 其他设置 → MCP 书源调试会话」中手动开启。默认端口为 `1236`，
Streamable HTTP 地址为 `http://手机局域网IP:1236/mcp`。客户端须发送
`Authorization: Bearer <访问令牌>`；可在同一面板复制或重新生成令牌。
服务默认关闭，不随 App 或系统启动。通知和设置面板均可停止；移除 App 任务或系统时限到达也会停止。

| 工具 | 参数 | 行为 |
| --- | --- | --- |
| `list_sources` | `query?`, `page?`, `limit?` | 分页摘要，单页最多 100 条 |
| `get_source` | `url`, `format?` (`json`/`js`) | 导出 JSON 或原始单文件 JS |
| `save_source` | `source`, `overwrite?` | 接收 JSON/JS；默认拒绝覆盖，显式覆盖时保留现有分组、启停、排序等用户设置 |
| `delete_sources` | `urls` | 仅删除明确列出的书源，最多 100 个 |
| `check_source` | `url` 或 `source` | 无网络、无写入的本地结构校验，返回 `validationOnly=true` |
| `debug_source` | `url` 或 `draft`, `key` | 运行已保存书源或内存草稿；两者互斥，草稿不落库 |

调试与 App 调试页、WebSocket、批量校验共用运行所有权，其他会话占用时返回 `BUSY`。
调试返回有界、脱敏的阶段诊断，不提供书架正文或阅读记录。书源导出本身保留可还原的代码，
因此请仅向可信客户端提供令牌。浏览器 `Origin` 与请求 `Host` 必须匹配当前监听地址及端口。
令牌只保存在设备加密存储中，不进入 App 备份或系统备份。

## 对于[Web](/app/src/main/java/io/legado/app/web/)的配置

您需要先在设置中启用"Web 服务"。

`POST /parseBookSource` 接受 `{"text":"JSON 或单文件 JS 内容"}`，仅在受限作用域中解析并返回书源预览，不保存书源。Web 编辑器的 `.js` 导入使用此入口；检查后仍需手动保存。JSON 导出包含用户设置和 `mainJs`，原始 JS 导出仅包含脚本。

## 使用

### Web

以下说明假设您的操作在本机进行，且开放端口为1234。  
如果您要从远程计算机访问[阅读]()，请将`127.0.0.1`替换成手机IP。

#### 插入单个书源

请求BODY内容为`JSON`字符串，  
格式参考[这个文件](/app/src/main/java/io/legado/app/data/entities/BookSource.kt)

```
URL = http://127.0.0.1:1234/saveBookSource
Method = POST
```

#### 插入多个书源or订阅源

请求BODY内容为`JSON`字符串，  
格式参考[这个文件](/app/src/main/java/io/legado/app/data/entities/BookSource.kt)，**为数组格式**。

```
URL = http://127.0.0.1:1234/saveBookSources
URL = http://127.0.0.1:1234/saveRssSources
Method = POST
```

#### 获取书源

```
URL = http://127.0.0.1:1234/getBookSource?url=xxx
URL = http://127.0.0.1:1234/getRssSource?url=xxx
Method = GET
``` 

#### 获取所有书源or订阅源

```
URL = http://127.0.0.1:1234/getBookSources
URL = http://127.0.0.1:1234/getRssSources
Method = GET
```

#### 删除多个书源or订阅源

请求BODY内容为`JSON`字符串，  
格式参考[这个文件](/app/src/main/java/io/legado/app/data/entities/BookSource.kt)，**为数组格式**。

```
URL = http://127.0.0.1:1234/deleteBookSources
URL = http://127.0.0.1:1234/deleteRssSources
Method = POST
```

#### 调试源

key为书源搜索关键词，tag为源链接

```
URL = ws://127.0.0.1:1235/bookSourceDebug
URL = ws://127.0.0.1:1235/rssSourceDebug
Message = { key: [String], tag: [String] }
```

#### 获取替换规则

```
URL = http://127.0.0.1:1234/getReplaceRules
Method = GET
```

#### 替换规则管理

请求BODY内容为`JSON`字符串，  
替换规则参考[这个文件](/app/src/main/java/io/legado/app/data/entities/ReplaceRule.kt)。

##### 删除

```
URL = http://127.0.0.1:1234/deleteReplaceRule
Method = POST
Body = [ReplaceRule]
```
##### 插入

```
URL = http://127.0.0.1:1234/saveReplaceRule
Method = POST
Body = [ReplaceRule]
```

##### 测试

返回测试文本text替换结果

```
URL = http://127.0.0.1:1234/testReplaceRule
Method = POST
Body = { rule: [ReplaceRule], text: [String] }
```

#### 搜索在线书籍

若想获取对应的书籍的目录正文 请先**插入书籍**以启用缓存，如果试读后决定不添加到书籍，请**删除书籍**

```
URL = ws://127.0.0.1:1235/searchBook
Message = { key: [String] }
```

#### 插入书籍

请求BODY内容为`JSON`字符串，  
格式参考[这个文件](/app/src/main/java/io/legado/app/data/entities/Book.kt)。

```
URL = http://127.0.0.1:1234/saveBook
Method = POST
```

#### 删除书籍

```
URL = http://127.0.0.1:1234/deleteBook
Method = POST
```

#### 获取所有书籍

```
URL = http://127.0.0.1:1234/getBookshelf
Method = GET
```

获取APP内的所有书籍。

#### 获取书籍章节列表

```
URL = http://127.0.0.1:1234/getChapterList?url=xxx
Method = GET
```

获取指定图书的章节列表。

#### 获取书籍内容

```
URL = http://127.0.0.1:1234/getBookContent?url=xxx&index=1
Method = GET
```

获取指定图书的第`index`章节的文本内容。

#### 获取封面

```
URL = http://127.0.0.1:1234/cover?path=xxxxx
Method = GET
```

#### 获取正文图片

```
URL = http://127.0.0.1:1234/image?url=${bookUrl}&path=${picUrl}&width=${width}
Method = GET
```

#### 保存书籍进度

请求BODY内容为`JSON`字符串，  
格式参考[这个文件](/app/src/main/java/io/legado/app/data/entities/BookProgress.kt)。

```
URL = http://127.0.0.1:1234/saveBookProgress
Method = POST
```

### [Content Provider](/app/src/main/java/io/legado/app/api/ReaderProvider.kt)


* 需声明`io.legado.READ_WRITE`权限
* `providerHost`为`包名.readerProvider`, 如`io.legado.app.release.readerProvider`,不同包的地址不同,防止冲突安装失败
* 以下出现的`providerHost`请自行替换

#### 插入单个书源or订阅源

创建`Key="json"`的`ContentValues`，内容为`JSON`字符串，  
格式参考[这个文件](/app/src/main/java/io/legado/app/data/entities/BookSource.kt)

```
URL = content://providerHost/bookSource/insert
URL = content://providerHost/rssSource/insert
Method = insert
```

#### 插入多个书源or订阅源

创建`Key="json"`的`ContentValues`，内容为`JSON`字符串，  
格式参考[这个文件](/app/src/main/java/io/legado/app/data/entities/BookSource.kt)，**为数组格式**。

```
URL = content://providerHost/bookSources/insert
URL = content://providerHost/rssSources/insert
Method = insert
```

#### 获取书源or订阅源

获取指定URL对应的书源信息。  
用`Cursor.getString(0)`取出返回结果。

```
URL = content://providerHost/bookSource/query?url=xxx
URL = content://providerHost/rssSource/query?url=xxx
Method = query
```

#### 获取所有书源or订阅源

获取APP内的所有订阅源。  
用`Cursor.getString(0)`取出返回结果。

```
URL = content://providerHost/bookSources/query
URL = content://providerHost/rssSources/query
Method = query
```

#### 删除多个书源or订阅源

创建`Key="json"`的`ContentValues`，内容为`JSON`字符串，  
格式参考[这个文件](/app/src/main/java/io/legado/app/data/entities/BookSource.kt)，**为数组格式**。

```
URL = content://providerHost/bookSources/delete
URL = content://providerHost/rssSources/delete
Method = delete
```

#### 插入书籍

创建`Key="json"`的`ContentValues`，内容为`JSON`字符串，  
格式参考[这个文件](/app/src/main/java/io/legado/app/data/entities/Book.kt)。

```
URL = content://providerHost/book/insert
Method = insert
```

#### 获取所有书籍

获取APP内的所有书籍。  
用`Cursor.getString(0)`取出返回结果。

```
URL = content://providerHost/books/query
Method = query
```

#### 获取书籍章节列表

获取指定图书的章节列表。   
用`Cursor.getString(0)`取出返回结果。

```
URL = content://providerHost/book/chapter/query?url=xxx
Method = query
```

#### 获取书籍内容

获取指定图书的第`index`章节的文本内容。     
用`Cursor.getString(0)`取出返回结果。

```
URL = content://providerHost/book/content/query?url=xxx&index=1
Method = query
```

#### 获取封面

```
URL = content://providerHost/book/cover/query?path=xxxx
Method = query
```
