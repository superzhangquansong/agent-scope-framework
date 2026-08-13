---
name: api-integration
description: AgentScope Framework 对接 API 文档（AuthController 登录认证 + HomeController 房屋查询 + ChatController 流式聊天 + InterruptController 中断接口）。面向第三方系统/AI 工具快速接入，涵盖请求方式、Header、Body、入参出参、字段来源、多语言示例、SSE 全事件枚举与报文样例。
triggers:
  - 对接 AgentScope Framework
  - 接入智能助手 API
  - HDL 登录接口
  - 房屋列表查询
  - 切换房屋
  - SSE 流式聊天接口
  - 会话中断接口
  - HITL 权限确认
  - AuthController
  - HomeController
  - ChatController
  - InterruptController
  - /api/auth/login
  - /api/auth/logout
  - /api/auth/status
  - /api/auth/switchHome
  - /api/home/list
  - /api/v1/chat/stream
  - /api/v1/chat/confirm
  - /api/v1/chat/interrupt
---

# AgentScope Framework API 对接文档

## 0. 基础信息

| 项目 | 值 |
|---|---|
| 服务名 | agent-scope-framework |
| 默认端口 | 8788 |
| Base URL | `http://{host}:8788` |
| 协议 | HTTP/1.1 + SSE（Server-Sent Events） |
| 字符编码 | UTF-8（请求体、响应体、SSE 事件均强制 UTF-8） |
| 鉴权方式 | 二选一：① `X-Session-Token`（HDL 登录会话）② `Authorization: Bearer {jwt}` |
| 并发限制 | SSE 并发连接上限 500，超过返回 `RATE_002` |
| SSE 超时 | 5 分钟（300000ms），超时自动断开 |

### 接口清单

| 接口 | 方法 | 路径 | 说明 |
|---|---|---|---|
| HDL 登录 | POST | `/api/auth/login` | HDL 账号密码登录，返回 sessionToken |
| 登出 | POST | `/api/auth/logout` | 销毁本地会话 |
| 会话状态查询 | GET | `/api/auth/status` | 查询登录状态、当前房屋、房屋列表 |
| 切换房屋 | POST | `/api/auth/switchHome` | 切换当前控制房屋 |
| 房屋列表 | POST | `/api/home/list` | 查询用户所有房屋列表 |
| 流式聊天 | POST | `/api/v1/chat/stream` | 发送用户消息，返回 SSE 事件流（含思考链、工具调用、结果） |
| 权限确认 | POST | `/api/v1/chat/confirm` | HITL 人机交互：用户确认/拒绝敏感工具调用后恢复 Agent |
| 中断会话 | POST | `/api/v1/chat/interrupt` | 中断指定会话的 Agent 执行，双重中断机制（框架 + 订阅） |

### 对接流程概览

```
1. 登录：POST /api/auth/login → 获取 sessionToken
2. 查询状态：GET /api/auth/status → 获取房屋列表
3. 切换房屋：POST /api/auth/switchHome → 选定控制目标
4. 流式聊天：POST /api/v1/chat/stream → 智能对话与设备控制
5. （可选）权限确认：POST /api/v1/chat/confirm → HITL 确认
6. （可选）中断会话：POST /api/v1/chat/interrupt → 停止 Agent
7. 登出：POST /api/auth/logout → 释放会话
```

---

## 1. 接口一：HDL 登录 `POST /api/auth/login`

### 1.1 请求说明

| 项 | 值 |
|---|---|
| 请求方式 | POST |
| 请求路径 | `/api/auth/login` |
| 完整 URL | `http://{host}:8788/api/auth/login` |
| Content-Type | `application/json` |
| 响应类型 | JSON |

### 1.2 Header 描述

| Header 名 | 是否必填 | 描述 | 示例 |
|---|---|---|---|
| `Content-Type` | 必填 | 固定 `application/json` | `application/json` |

> 登录接口无需鉴权 Header，任何客户端均可调用。

### 1.3 Body 描述

请求体为 JSON 对象。

#### 入参字段总表

| 字段名 | 类型 | 是否必填 | 字段来源 | 字段描述 | 字段示例 |
|---|---|---|---|---|---|
| `loginName` | string | 必填 | 前端用户输入 | HDL 登录用户名（手机号） | `"19210818109"` |
| `loginPwd` | string | 必填 | 前端用户输入 | HDL 登录密码（明文，HTTPS 传输） | `"password123"` |

### 1.4 入参示例

```json
{
  "loginName": "19210818109",
  "loginPwd": "password123"
}
```

### 1.5 出参说明

响应为 `Response<LoginResult>` 统一包装结构。

#### 出参字段总表

| 字段名 | 类型 | 字段来源 | 字段描述 | 字段示例 |
|---|---|---|---|---|
| `code` | int | `Response.code` | 业务响应码（200=成功） | `200` |
| `message` | string | `Response.message` | 响应消息 | `"success"` |
| `success` | boolean | `Response.success` | 是否成功 | `true` |
| `requestId` | string | `UUID.randomUUID()` | 请求唯一标识 | `"550e8400-..."` |
| `timestamp` | long | `System.currentTimeMillis()` | 响应时间戳（毫秒） | `1719500000000` |
| `data` | object | `LoginResult` | 登录结果数据 | 见下方 |
| `data.sessionToken` | string | `UserSession.sessionToken` | 会话 Token（前端持有，后续请求携带 `X-Session-Token` 头） | `"abc123def456"` |
| `data.loginName` | string | 请求入参 | 登录用户名 | `"19210818109"` |
| `data.expiresIn` | long | HDL 返回 | Token 过期时间（秒） | `86400` |

### 1.6 出参示例

#### 成功响应

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "sessionToken": "abc123def456",
    "loginName": "19210818109",
    "expiresIn": 86400
  },
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": 1719500000000,
  "success": true
}
```

#### 失败响应

```json
{
  "code": 20001,
  "message": "用户名或密码错误",
  "data": null,
  "requestId": "550e8400-e29b-41d4-a716-446655440001",
  "timestamp": 1719500000001,
  "success": false
}
```

### 1.7 多语言请求示例

#### curl

```bash
curl -X POST "http://localhost:8788/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"loginName":"19210818109","loginPwd":"password123"}'
```

#### JavaScript

```javascript
async function login(loginName, loginPwd) {
  const response = await fetch("http://localhost:8788/api/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ loginName, loginPwd })
  });
  const result = await response.json();
  if (result.success) {
    localStorage.setItem("sessionToken", result.data.sessionToken);
  }
  return result;
}
```

#### Python

```python
import requests

response = requests.post(
    "http://localhost:8788/api/auth/login",
    json={"loginName": "19210818109", "loginPwd": "password123"}
)
result = response.json()
if result["success"]:
    session_token = result["data"]["sessionToken"]
```

#### Java

```java
OkHttpClient client = new OkHttpClient();
String json = "{\"loginName\":\"19210818109\",\"loginPwd\":\"password123\"}";
Request request = new Request.Builder()
    .url("http://localhost:8788/api/auth/login")
    .post(RequestBody.create(json, MediaType.parse("application/json")))
    .build();
try (Response response = client.newCall(request).execute()) {
    System.out.println(response.body().string());
}
```

#### Go

```go
body := strings.NewReader(`{"loginName":"19210818109","loginPwd":"password123"}`)
resp, _ := http.Post("http://localhost:8788/api/auth/login", "application/json", body)
data, _ := io.ReadAll(resp.Body)
fmt.Println(string(data))
```

---

## 2. 接口二：登出 `POST /api/auth/logout`

### 2.1 请求说明

| 项 | 值 |
|---|---|
| 请求方式 | POST |
| 请求路径 | `/api/auth/logout` |
| 完整 URL | `http://{host}:8788/api/auth/logout` |
| 响应类型 | JSON |

### 2.2 Header 描述

| Header 名 | 是否必填 | 描述 | 示例 |
|---|---|---|---|
| `X-Session-Token` | 必填 | 登录时获取的 sessionToken | `abc123def456` |

### 2.3 Body 描述

无请求体。

### 2.4 出参说明

响应为 `Response<Void>` 统一包装结构，`data` 为 `null`。

#### 出参字段总表

| 字段名 | 类型 | 字段来源 | 字段描述 | 字段示例 |
|---|---|---|---|---|
| `code` | int | `Response.code` | 业务响应码（200=成功） | `200` |
| `message` | string | `Response.message` | 响应消息 | `"success"` |
| `success` | boolean | `Response.success` | 是否成功 | `true` |
| `data` | null | - | 无业务数据 | `null` |

### 2.5 出参示例

```json
{
  "code": 200,
  "message": "success",
  "data": null,
  "requestId": "550e8400-e29b-41d4-a716-446655440002",
  "timestamp": 1719500000002,
  "success": true
}
```

### 2.6 多语言请求示例

#### curl

```bash
curl -X POST "http://localhost:8788/api/auth/logout" \
  -H "X-Session-Token: abc123def456"
```

#### JavaScript

```javascript
async function logout() {
  await fetch("http://localhost:8788/api/auth/logout", {
    method: "POST",
    headers: { "X-Session-Token": localStorage.getItem("sessionToken") }
  });
  localStorage.removeItem("sessionToken");
}
```

#### Python

```python
import requests

response = requests.post(
    "http://localhost:8788/api/auth/logout",
    headers={"X-Session-Token": "abc123def456"}
)
```

---

## 3. 接口三：会话状态查询 `GET /api/auth/status`

### 3.1 请求说明

| 项 | 值 |
|---|---|
| 请求方式 | GET |
| 请求路径 | `/api/auth/status` |
| 完整 URL | `http://{host}:8788/api/auth/status` |
| 响应类型 | JSON |

### 3.2 Header 描述

| Header 名 | 是否必填 | 描述 | 示例 |
|---|---|---|---|
| `X-Session-Token` | 可选 | 登录时获取的 sessionToken。不传或无效时返回 `loggedIn: false` | `abc123def456` |

### 3.3 出参说明

响应为 `Response<SessionStatus>` 统一包装结构。

#### 出参字段总表

| 字段名 | 类型 | 字段来源 | 字段描述 | 字段示例 |
|---|---|---|---|---|
| `code` | int | `Response.code` | 业务响应码（200=成功） | `200` |
| `message` | string | `Response.message` | 响应消息 | `"success"` |
| `success` | boolean | `Response.success` | 是否成功 | `true` |
| `data` | object | `SessionStatus` | 会话状态数据 | 见下方 |
| `data.loggedIn` | boolean | `UserSession.isLoggedIn()` | 是否已登录（有 HDL Token 视为登录） | `true` |
| `data.loginName` | string | `UserSession.loginName` | 登录用户名 | `"19210818109"` |
| `data.currentHomeId` | string | `UserSession.currentHomeId` | 当前房屋 ID | `"2017059891497046018"` |
| `data.currentHomeName` | string | `UserSession.currentHomeName` | 当前房屋名称 | `"我的家"` |
| `data.homes` | array | `SessionStatus.homes` | 房屋列表（未缓存时自动查询 HDL） | 见下方 |
| `data.homes[].homeId` | string | `HdlHome.homeId` | 房屋 ID | `"2017059891497046018"` |
| `data.homes[].homeName` | string | `HdlHome.homeName` | 房屋名称 | `"我的家"` |
| `data.homes[].homeType` | string | `HdlHome.homeType` | 房屋类型（apartment/villa） | `"apartment"` |
| `data.homes[].deviceCount` | int | `HdlHome.deviceCount` | 设备数量 | `15` |
| `data.homes[].remoteControl` | boolean | `HdlHome.remoteControl` | 是否支持远程控制 | `true` |
| `data.needSelectHome` | boolean | `SessionStatus.needSelectHome` | 是否需要选择房屋（currentHomeId 为 null 时为 true） | `false` |
| `data.homeQueryFailed` | boolean | `SessionStatus.homeQueryFailed` | 房屋列表查询是否失败 | `false` |

### 3.4 出参示例

#### 已登录且已选房屋

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "loggedIn": true,
    "loginName": "19210818109",
    "currentHomeId": "2017059891497046018",
    "currentHomeName": "我的家",
    "homes": [
      {
        "homeId": "2017059891497046018",
        "homeName": "我的家",
        "homeType": "apartment",
        "deviceCount": 15,
        "remoteControl": true
      }
    ],
    "needSelectHome": false,
    "homeQueryFailed": false
  },
  "requestId": "550e8400-...",
  "timestamp": 1719500000000,
  "success": true
}
```

#### 已登录但未选房屋

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "loggedIn": true,
    "loginName": "19210818109",
    "currentHomeId": null,
    "currentHomeName": null,
    "homes": [
      {"homeId": "2017059891497046018", "homeName": "我的家", "homeType": "apartment", "deviceCount": 15, "remoteControl": true},
      {"homeId": "2017059891497046019", "homeName": "别墅", "homeType": "villa", "deviceCount": 30, "remoteControl": true}
    ],
    "needSelectHome": true,
    "homeQueryFailed": false
  },
  "requestId": "550e8400-...",
  "timestamp": 1719500000000,
  "success": true
}
```

#### 未登录

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "loggedIn": false
  },
  "requestId": "550e8400-...",
  "timestamp": 1719500000000,
  "success": true
}
```

### 3.5 多语言请求示例

#### curl

```bash
curl -X GET "http://localhost:8788/api/auth/status" \
  -H "X-Session-Token: abc123def456"
```

#### JavaScript

```javascript
async function checkStatus() {
  const response = await fetch("http://localhost:8788/api/auth/status", {
    method: "GET",
    headers: { "X-Session-Token": localStorage.getItem("sessionToken") }
  });
  const result = await response.json();
  if (result.data.loggedIn && result.data.needSelectHome) {
    // 展示房屋选择弹框
    showHomeSelectModal(result.data.homes);
  }
  return result;
}
```

#### Python

```python
import requests

response = requests.get(
    "http://localhost:8788/api/auth/status",
    headers={"X-Session-Token": "abc123def456"}
)
status = response.json()["data"]
if status["loggedIn"] and status["needSelectHome"]:
    print("请选择房屋:", [h["homeName"] for h in status["homes"]])
```

---

## 4. 接口四：切换房屋 `POST /api/auth/switchHome`

### 4.1 请求说明

| 项 | 值 |
|---|---|
| 请求方式 | POST |
| 请求路径 | `/api/auth/switchHome` |
| 完整 URL | `http://{host}:8788/api/auth/switchHome` |
| Content-Type | `application/json` |
| 响应类型 | JSON |

### 4.2 Header 描述

| Header 名 | 是否必填 | 描述 | 示例 |
|---|---|---|---|
| `X-Session-Token` | 必填 | 登录时获取的 sessionToken | `abc123def456` |

### 4.3 Body 描述

请求体为 JSON 对象。

#### 入参字段总表

| 字段名 | 类型 | 是否必填 | 字段来源 | 字段描述 | 字段示例 |
|---|---|---|---|---|---|
| `homeName` | string | 必填 | `GET /api/auth/status` 返回的 `homes[].homeName` | 房屋名称（需与房屋列表中的名称完全匹配） | `"我的家"` |

### 4.4 入参示例

```json
{
  "homeName": "我的家"
}
```

### 4.5 出参说明

响应为 `Response<SessionStatus>` 统一包装结构。

#### 出参字段总表

| 字段名 | 类型 | 字段来源 | 字段描述 | 字段示例 |
|---|---|---|---|---|
| `code` | int | `Response.code` | 业务响应码（200=成功，10004=房屋不存在） | `200` |
| `message` | string | `Response.message` | 响应消息 | `"success"` |
| `success` | boolean | `Response.success` | 是否成功 | `true` |
| `data` | object | `SessionStatus` | 切换后的会话状态 | 见下方 |
| `data.loggedIn` | boolean | `SessionStatus.loggedIn` | 是否已登录 | `true` |
| `data.currentHomeId` | string | `UserSession.currentHomeId` | 切换后的房屋 ID | `"2017059891497046018"` |
| `data.currentHomeName` | string | `UserSession.currentHomeName` | 切换后的房屋名称 | `"我的家"` |

### 4.6 出参示例

#### 成功响应

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "loggedIn": true,
    "currentHomeId": "2017059891497046018",
    "currentHomeName": "我的家"
  },
  "requestId": "550e8400-...",
  "timestamp": 1719500000000,
  "success": true
}
```

#### 失败响应（房屋不存在）

```json
{
  "code": 10004,
  "message": "未找到房屋: 不存在的家",
  "data": null,
  "requestId": "550e8400-...",
  "timestamp": 1719500000000,
  "success": false
}
```

### 4.7 多语言请求示例

#### curl

```bash
curl -X POST "http://localhost:8788/api/auth/switchHome" \
  -H "Content-Type: application/json" \
  -H "X-Session-Token: abc123def456" \
  -d '{"homeName":"我的家"}'
```

#### JavaScript

```javascript
async function switchHome(homeName) {
  const response = await fetch("http://localhost:8788/api/auth/switchHome", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Session-Token": localStorage.getItem("sessionToken")
    },
    body: JSON.stringify({ homeName })
  });
  return await response.json();
}
```

#### Python

```python
import requests

response = requests.post(
    "http://localhost:8788/api/auth/switchHome",
    json={"homeName": "我的家"},
    headers={"X-Session-Token": "abc123def456"}
)
print(response.json())
```

#### Java

```java
OkHttpClient client = new OkHttpClient();
String json = "{\"homeName\":\"我的家\"}";
Request request = new Request.Builder()
    .url("http://localhost:8788/api/auth/switchHome")
    .post(RequestBody.create(json, MediaType.parse("application/json")))
    .addHeader("X-Session-Token", "abc123def456")
    .build();
try (Response response = client.newCall(request).execute()) {
    System.out.println(response.body().string());
}
```

---

## 5. 接口五：房屋列表 `POST /api/home/list`

### 5.1 请求说明

| 项 | 值 |
|---|---|
| 请求方式 | POST |
| 请求路径 | `/api/home/list` |
| 完整 URL | `http://{host}:8788/api/home/list` |
| 响应类型 | JSON（`ToolResultVO` 结构） |

### 5.2 Header 描述

| Header 名 | 是否必填 | 描述 | 示例 |
|---|---|---|---|
| `X-Session-Token` | 必填 | 登录时获取的 sessionToken | `abc123def456` |

### 5.3 Body 描述

无请求体（所有信息从 `X-Session-Token` 解析）。

### 5.4 出参说明

响应为 `ToolResultVO` 结构（非 `Response` 包装），直接返回工具结果。

#### 出参字段总表

| 字段名 | 类型 | 字段来源 | 字段描述 | 字段示例 |
|---|---|---|---|---|
| `success` | boolean | `ToolResultVO.success` | 查询是否成功 | `true` |
| `message` | string | `ToolResultVO.message` | 成功/失败消息 | `"成功查询到房屋列表"` |
| `data` | array | `ToolResultVO.data` | 房屋列表数据 | 见下方 |
| `data[].homeId` | string | HDL API 返回 | 房屋 ID | `"2017059891497046018"` |
| `data[].homeName` | string | HDL API 返回 | 房屋名称 | `"我的家"` |
| `data[].homeType` | string | HDL API 返回 | 房屋类型 | `"apartment"` |
| `data[].deviceCount` | int | HDL API 返回 | 设备数量 | `15` |
| `data[].remoteControl` | boolean | HDL API 返回 | 是否支持远程控制 | `true` |
| `routePath` | string | `ToolResultVO.routePath` | 前端路由路径 | `"/home/list"` |
| `errorCode` | int | `ToolResultVO.errorCode` | 错误码（失败时填充） | `401` |

### 5.5 出参示例

#### 成功响应

```json
{
  "success": true,
  "message": "成功查询到房屋列表",
  "data": [
    {
      "homeId": "2017059891497046018",
      "homeName": "我的家",
      "homeType": "apartment",
      "deviceCount": 15,
      "remoteControl": true
    },
    {
      "homeId": "2017059891497046019",
      "homeName": "别墅",
      "homeType": "villa",
      "deviceCount": 30,
      "remoteControl": true
    }
  ],
  "routePath": "/home/list"
}
```

#### 失败响应（未登录）

```json
{
  "success": false,
  "message": "未登录或会话已过期",
  "data": null,
  "errorCode": 401,
  "routePath": "/result/error"
}
```

### 5.6 多语言请求示例

#### curl

```bash
curl -X POST "http://localhost:8788/api/home/list" \
  -H "X-Session-Token: abc123def456"
```

#### JavaScript

```javascript
async function getHomeList() {
  const response = await fetch("http://localhost:8788/api/home/list", {
    method: "POST",
    headers: { "X-Session-Token": localStorage.getItem("sessionToken") }
  });
  const result = await response.json();
  if (result.success) {
    result.data.forEach(home => console.log(home.homeName, home.deviceCount));
  }
  return result;
}
```

#### Python

```python
import requests

response = requests.post(
    "http://localhost:8788/api/home/list",
    headers={"X-Session-Token": "abc123def456"}
)
result = response.json()
if result["success"]:
    for home in result["data"]:
        print(f"{home['homeName']}: {home['deviceCount']}台设备")
```

#### Java

```java
OkHttpClient client = new OkHttpClient();
Request request = new Request.Builder()
    .url("http://localhost:8788/api/home/list")
    .post(RequestBody.create("", null))
    .addHeader("X-Session-Token", "abc123def456")
    .build();
try (Response response = client.newCall(request).execute()) {
    System.out.println(response.body().string());
}
```

#### Go

```go
resp, _ := http.Post(
    "http://localhost:8788/api/home/list",
    "application/json",
    nil,
)
// 注意：需手动设置 X-Session-Token 头，此处省略
body, _ := io.ReadAll(resp.Body)
fmt.Println(string(body))
```

---

## 6. 接口六：流式聊天 `POST /api/v1/chat/stream`

### 6.1 请求说明

| 项 | 值 |
|---|---|
| 请求方式 | POST |
| 请求路径 | `/api/v1/chat/stream` |
| 完整 URL | `http://{host}:8788/api/v1/chat/stream` |
| Content-Type | `application/json` |
| Accept | `text/event-stream` |
| 响应类型 | SSE 事件流 |

### 6.2 Header 描述

| Header 名 | 是否必填 | 描述 | 示例 |
|---|---|---|---|
| `Content-Type` | 必填 | 固定 `application/json` | `application/json` |
| `Accept` | 必填 | 固定 `text/event-stream` | `text/event-stream` |
| `X-Session-Token` | 二选一 | HDL 登录会话 Token，服务端据此解析 userId/accessToken/houseId | `abc123def456` |
| `Authorization` | 二选一 | `Bearer {jwt}`，从 JWT 提取 accessToken（兜底鉴权） | `Bearer eyJhbGciOi...` |

> 鉴权优先级：`X-Session-Token` > `Authorization`。若两者都传，以 `X-Session-Token` 解析出的用户信息为准。

### 6.3 Body 描述

请求体为 JSON 对象，对应 `ChatStreamDTO`。

#### 入参字段总表

| 字段名 | 类型 | 是否必填 | 默认值 | 字段来源 | 字段描述 | 字段示例 |
|---|---|---|---|---|---|---|
| `sessionId` | string | 必填 | - | 前端生成（UUID v4） | 会话 ID，用于多轮对话上下文隔离与 Agent 状态持久化 | `"b0f0ad3e-80cd-4de5-a488-94f7843ef03e"` |
| `userId` | string | 条件必填 | - | `X-Session-Token` 自动注入 | 用户 ID（HDL loginName）。使用 `X-Session-Token` 时可不传，服务端自动注入 | `"19210818109"` |
| `houseId` | string | 可选 | - | `X-Session-Token` 自动注入 | 家庭 ID，用于业务数据隔离。使用 `X-Session-Token` 且用户有当前家庭时自动注入 | `"2017059891497046018"` |
| `userMessage` | string | 必填 | - | 前端用户输入 | 用户文本消息（自然语言指令） | `"开灯"` / `"RGB开蓝色亮度65"` |
| `accessToken` | string | 可选 | - | `X-Session-Token` 或 `Authorization` 注入 | HDL API 访问令牌，供下游工具鉴权。一般由服务端自动注入 | `"hdl_token_xxx"` |
| `images` | string[] | 可选 | `null` | 前端上传 | 图片列表，元素为 URL 或 Base64（取决于 `imageType`） | `["https://example.com/a.png"]` |
| `imageType` | string | 可选 | `"url"` | 前端指定 | 图片数据类型：`url` 或 `base64` | `"url"` |
| `audios` | string[] | 可选 | `null` | 前端上传 | 音频列表，元素为 URL 或 Base64 | `["https://example.com/a.mp3"]` |
| `audioType` | string | 可选 | `"url"` | 前端指定 | 音频数据类型：`url` 或 `base64` | `"url"` |
| `videos` | string[] | 可选 | `null` | 前端上传 | 视频列表，元素为 URL 或 Base64 | `["https://example.com/a.mp4"]` |
| `videoType` | string | 可选 | `"url"` | 前端指定 | 视频数据类型：`url` 或 `base64` | `"url"` |

#### 入参字段详细说明

- **sessionId**：UUID v4 格式（8-4-4-4-12 十六进制）。同一 sessionId 下的多轮对话共享 Agent 上下文（记忆、权限、状态）。建议前端首次对话时生成并缓存。
- **userId**：当使用 `X-Session-Token` 鉴权时，服务端从会话中解析 loginName 自动注入，前端可不传。若未使用 `X-Session-Token` 且 `userId` 为空，返回 `GLOBAL_002` 错误。
- **houseId**：业务隔离维度。设备控制、场景等业务均基于 houseId。使用 `X-Session-Token` 时自动从用户当前家庭注入。
- **userMessage**：用户自然语言指令。支持单意图（"开灯"）和多意图（"开灯并查设备列表"）。
- **images/audios/videos**：多模态输入。当任一非空时，将与 `userMessage` 一起构建为多模态消息传递给视觉模型。
- **imageType/audioType/videoType**：`url` 表示元素为可访问 URL（http/https/file）；`base64` 表示元素为 Base64 编码数据（不含 `data:` 前缀）。

### 6.4 入参示例

#### 最简文本对话

```json
{
  "sessionId": "b0f0ad3e-80cd-4de5-a488-94f7843ef03e",
  "userMessage": "开灯"
}
```

#### 带 X-Session-Token 鉴权（Header）

```
X-Session-Token: abc123def456
Content-Type: application/json
Accept: text/event-stream
```

```json
{
  "sessionId": "b0f0ad3e-80cd-4de5-a488-94f7843ef03e",
  "userMessage": "RGB开蓝色亮度65调光开暖色98"
}
```

#### 多模态（图片 + 文本）

```json
{
  "sessionId": "b0f0ad3e-80cd-4de5-a488-94f7843ef03e",
  "userMessage": "这张户型图里有哪些房间？",
  "images": ["https://example.com/floorplan.png"],
  "imageType": "url"
}
```

### 6.5 出参说明

响应为 SSE 事件流，每个事件格式：

```
event: {事件类型}
data: {JSON 字符串}

```

> 每个事件的 `data` 字段为一个 JSON 对象，公共字段为 `type`、`sessionId`、`eventId`、`timestamp`。

#### 出参公共字段

| 字段名 | 类型 | 字段来源 | 字段描述 | 字段示例 |
|---|---|---|---|---|
| `type` | string | `AgentEventEnum.desc` | 事件类型（见下方枚举） | `"text_delta"` |
| `sessionId` | string | 请求入参 | 会话 ID | `"b0f0ad3e-..."` |
| `eventId` | string | `UUID.randomUUID()` | 事件唯一标识（仅 agent_start/agent_end/error/permission_paused 等框架事件携带） | `"550e8400-..."` |
| `timestamp` | string | `System.currentTimeMillis()` | 事件生成时间戳（毫秒，仅框架事件携带） | `1719500000000` |

> **注意**：14 个 AgentScope 事件 BO（text_delta 等）仅包含 `type`、`sessionId` 及事件特有字段，不含 `eventId`/`timestamp`；`agent_start`/`agent_end`/`error`/`permission_paused`/`result`/`done` 等框架事件包含 `eventId`/`timestamp`。

### 6.6 多语言请求示例

#### curl

```bash
# 流式聊天（X-Session-Token 鉴权）
curl -N -X POST "http://localhost:8788/api/v1/chat/stream" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -H "X-Session-Token: abc123def456" \
  -d '{
    "sessionId": "b0f0ad3e-80cd-4de5-a488-94f7843ef03e",
    "userMessage": "开灯"
  }'
```

```bash
# 流式聊天（Authorization 鉴权）
curl -N -X POST "http://localhost:8788/api/v1/chat/stream" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." \
  -d '{
    "sessionId": "b0f0ad3e-80cd-4de5-a488-94f7843ef03e",
    "userId": "19210818109",
    "houseId": "2017059891497046018",
    "userMessage": "我有哪些设备"
  }'
```

#### JavaScript（浏览器 EventSource 替代方案）

> 浏览器原生 `EventSource` 仅支持 GET，本接口为 POST，需使用 `fetch` + `ReadableStream` 手动解析 SSE。

```javascript
async function streamChat() {
  const response = await fetch("http://localhost:8788/api/v1/chat/stream", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Accept": "text/event-stream",
      "X-Session-Token": "abc123def456"
    },
    body: JSON.stringify({
      sessionId: "b0f0ad3e-80cd-4de5-a488-94f7843ef03e",
      userMessage: "开灯"
    })
  });

  const reader = response.body.getReader();
  const decoder = new TextDecoder("utf-8");
  let buffer = "";

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });

    // 按 SSE 协议解析：双换行分隔事件块
    const blocks = buffer.split("\n\n");
    buffer = blocks.pop(); // 保留最后一个不完整块

    for (const block of blocks) {
      const lines = block.split("\n");
      let eventType = "";
      let data = "";
      for (const line of lines) {
        if (line.startsWith("event:")) eventType = line.slice(6).trim();
        else if (line.startsWith("data:")) data += line.slice(5).trim();
      }
      console.log(`[${eventType}]`, data);
      if (eventType === "done") return; // 流结束
    }
  }
}
```

#### Python（requests + SSE 解析）

```python
import requests
import uuid

url = "http://localhost:8788/api/v1/chat/stream"
headers = {
    "Content-Type": "application/json",
    "Accept": "text/event-stream",
    "X-Session-Token": "abc123def456"
}
payload = {
    "sessionId": str(uuid.uuid4()),
    "userMessage": "开灯"
}

response = requests.post(url, json=payload, headers=headers, stream=True)
for line in response.iter_lines(decode_unicode=True):
    if line.startswith("event:"):
        event_type = line[6:].strip()
    elif line.startswith("data:"):
        data = line[5:].strip()
        print(f"[{event_type}] {data}")
        if event_type == "done":
            break
```

#### Java（OkHttp + SSE）

```java
OkHttpClient client = new OkHttpClient.Builder()
    .readTimeout(5, TimeUnit.MINUTES) // SSE 长连接
    .build();

String json = "{\"sessionId\":\"b0f0ad3e-80cd-4de5-a488-94f7843ef03e\",\"userMessage\":\"开灯\"}";
RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));

Request request = new Request.Builder()
    .url("http://localhost:8788/api/v1/chat/stream")
    .post(body)
    .addHeader("Accept", "text/event-stream")
    .addHeader("X-Session-Token", "abc123def456")
    .build();

try (Response response = client.newCall(request).execute()) {
    BufferedReader reader = new BufferedReader(
        new InputStreamReader(response.body().byteStream(), StandardCharsets.UTF_8));
    String line;
    while ((line = reader.readLine()) != null) {
        if (line.startsWith("event:")) {
            System.out.println("事件: " + line.substring(6).trim());
        } else if (line.startsWith("data:")) {
            System.out.println("数据: " + line.substring(5).trim());
        }
    }
}
```

#### Go（net/http + bufio.Scanner）

```go
package main

import (
	"bufio"
	"fmt"
	"io"
	"net/http"
	"strings"
)

func main() {
	body := strings.NewReader(`{"sessionId":"b0f0ad3e-80cd-4de5-a488-94f7843ef03e","userMessage":"开灯"}`)
	req, _ := http.NewRequest("POST", "http://localhost:8788/api/v1/chat/stream", body)
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "text/event-stream")
	req.Header.Set("X-Session-Token", "abc123def456")

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		panic(err)
	}
	defer resp.Body.Close()

	scanner := bufio.NewScanner(resp.Body)
	var eventType string
	for scanner.Scan() {
		line := scanner.Text()
		if strings.HasPrefix(line, "event:") {
			eventType = strings.TrimSpace(line[6:])
		} else if strings.HasPrefix(line, "data:") {
			fmt.Printf("[%s] %s\n", eventType, strings.TrimSpace(line[5:]))
			if eventType == "done" {
				return
			}
		}
	}
}
```

---

## 7. 接口七：权限确认 `POST /api/v1/chat/confirm`

### 7.1 请求说明

| 项 | 值 |
|---|---|
| 请求方式 | POST |
| 请求路径 | `/api/v1/chat/confirm` |
| 完整 URL | `http://{host}:8788/api/v1/chat/confirm` |
| Content-Type | `application/json` |
| Accept | `text/event-stream` |
| 响应类型 | SSE 事件流（恢复执行后的后续事件） |

### 7.2 Header 描述

与 [6.2 Header 描述](#62-header-描述) 完全一致。

### 7.3 Body 描述

请求体为 JSON 对象，对应 `PermissionConfirmDTO`。

#### 入参字段总表

| 字段名 | 类型 | 是否必填 | 字段来源 | 字段描述 | 字段示例 |
|---|---|---|---|---|---|
| `sessionId` | string | 必填 | 原 stream 请求的 sessionId | 会话 ID（与待确认的权限请求同会话） | `"b0f0ad3e-..."` |
| `userId` | string | 必填 | `X-Session-Token` 注入或前端传 | 用户 ID | `"19210818109"` |
| `houseId` | string | 可选 | `X-Session-Token` 注入 | 家庭 ID | `"2017059891497046018"` |
| `accessToken` | string | 可选 | `X-Session-Token` 或 `Authorization` 注入 | HDL 访问令牌 | `"hdl_token_xxx"` |
| `userMessage` | string | 可选 | 前端用户输入 | 自然语言确认/拒绝（如"继续"/"取消"）。不传 `confirms` 时通过此字段判断 | `"继续"` |
| `confirms` | ConfirmItem[] | 可选 | 前端构建 | 确认项列表，每个待确认工具调用一项 | 见下方示例 |

#### ConfirmItem 子对象

| 字段名 | 类型 | 是否必填 | 字段来源 | 字段描述 | 字段示例 |
|---|---|---|---|---|---|
| `toolCallId` | string | 必填 | `permission_ask` 事件的 `toolCalls[].toolCallId` | 工具调用 ID | `"call_abc123"` |
| `toolName` | string | 必填 | `permission_ask` 事件的 `toolCalls[].toolName` | 工具名称 | `"batch_control_device"` |
| `allowed` | boolean | 必填 | 用户选择 | 是否允许执行此工具调用 | `true` / `false` |

#### 入参字段详细说明

- **confirms vs userMessage**：两种确认方式二选一。
  - 方式一（推荐）：传 `confirms` 数组，精确控制每个工具调用的允许/拒绝。
  - 方式二：仅传 `userMessage`（如"继续"/"取消"），服务端检测拒绝关键词（取消/拒绝/不要等）判断 allowed。
- **toolCallId 来源**：来自 SSE 流式聊天中收到的 `permission_ask` 事件的 `toolCalls[].toolCallId` 字段。

### 7.4 入参示例

#### 方式一：精确确认（confirms 数组）

```json
{
  "sessionId": "b0f0ad3e-80cd-4de5-a488-94f7843ef03e",
  "confirms": [
    {
      "toolCallId": "call_abc123",
      "toolName": "batch_control_device",
      "allowed": true
    }
  ]
}
```

#### 方式二：自然语言确认

```json
{
  "sessionId": "b0f0ad3e-80cd-4de5-a488-94f7843ef03e",
  "userMessage": "继续"
}
```

### 7.5 出参说明

响应为 SSE 事件流，包含 Agent 恢复执行后的后续事件（与 stream 接口事件类型一致）。事件枚举详见 [第 9 节：SSE 事件枚举](#9-sse-事件枚举)。

### 7.6 多语言请求示例

#### curl

```bash
curl -N -X POST "http://localhost:8788/api/v1/chat/confirm" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -H "X-Session-Token: abc123def456" \
  -d '{
    "sessionId": "b0f0ad3e-80cd-4de5-a488-94f7843ef03e",
    "confirms": [
      {"toolCallId": "call_abc123", "toolName": "batch_control_device", "allowed": true}
    ]
  }'
```

#### JavaScript

```javascript
async function confirmPermission(sessionId, toolCallId, allowed) {
  const response = await fetch("http://localhost:8788/api/v1/chat/confirm", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Accept": "text/event-stream",
      "X-Session-Token": "abc123def456"
    },
    body: JSON.stringify({
      sessionId: sessionId,
      confirms: [{ toolCallId, toolName: "batch_control_device", allowed }]
    })
  });
  // SSE 解析逻辑同 stream 接口
}
```

#### Python

```python
import requests

response = requests.post(
    "http://localhost:8788/api/v1/chat/confirm",
    json={
        "sessionId": "b0f0ad3e-80cd-4de5-a488-94f7843ef03e",
        "confirms": [
            {"toolCallId": "call_abc123", "toolName": "batch_control_device", "allowed": True}
        ]
    },
    headers={
        "Content-Type": "application/json",
        "Accept": "text/event-stream",
        "X-Session-Token": "abc123def456"
    },
    stream=True
)
for line in response.iter_lines(decode_unicode=True):
    print(line)
```

---

## 8. 接口八：中断会话 `POST /api/v1/chat/interrupt`

### 8.1 请求说明

| 项 | 值 |
|---|---|
| 请求方式 | POST |
| 请求路径 | `/api/v1/chat/interrupt` |
| 完整 URL | `http://{host}:8788/api/v1/chat/interrupt?userId={userId}&sessionId={sessionId}` |
| Content-Type | 无（参数通过 Query String 传递） |
| 响应类型 | JSON（非 SSE） |

### 8.2 Header 描述

| Header 名 | 是否必填 | 描述 | 示例 |
|---|---|---|---|
| `X-Session-Token` | 可选 | 用于鉴权（若系统开启了鉴权过滤器） | `abc123def456` |
| `Authorization` | 可选 | `Bearer {jwt}`（兜底鉴权） | `Bearer eyJhbGci...` |

### 8.3 Query 参数描述

| 参数名 | 类型 | 是否必填 | 格式要求 | 字段来源 | 字段描述 | 字段示例 |
|---|---|---|---|---|---|---|
| `userId` | string | 必填 | 非空字符串 | 原 stream 请求的 userId | 用户 ID（loginName） | `"19210818109"` |
| `sessionId` | string | 必填 | UUID v4 格式（8-4-4-4-12 十六进制） | 原 stream 请求的 sessionId | 待中断的会话 ID | `"b0f0ad3e-80cd-4de5-a488-94f7843ef03e"` |

### 8.4 出参说明

响应为 `Response<Map<String, Object>>` 统一包装结构。

#### 出参字段总表

| 字段名 | 类型 | 字段来源 | 字段描述 | 字段示例 |
|---|---|---|---|---|
| `code` | int | `Response.code` | 业务响应码（200=成功） | `200` |
| `message` | string | `Response.message` | 响应消息 | `"success"` |
| `success` | boolean | `Response.success` | 是否成功 | `true` |
| `requestId` | string | `UUID.randomUUID()` | 请求唯一标识 | `"550e8400-..."` |
| `timestamp` | long | `System.currentTimeMillis()` | 响应时间戳（毫秒） | `1719500000000` |
| `data` | object | `Response.data` | 业务数据 | 见下方 |
| `data.code` | int | `BusinessConst.HTTP_OK` | 中断操作状态码 | `200` |
| `data.message` | string | 中断结果描述 | 中断结果消息 | `"中断成功，SSE 流已终止"` |
| `data.userId` | string | 请求入参 | 用户 ID | `"19210818109"` |
| `data.sessionId` | string | 请求入参 | 会话 ID | `"b0f0ad3e-..."` |
| `data.subscriptionDisposed` | boolean | `chatService.interruptSession()` 返回值 | 订阅是否已 dispose | `true` |

### 8.5 出参示例

#### 成功响应

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "code": 200,
    "message": "中断成功，SSE 流已终止",
    "userId": "19210818109",
    "sessionId": "b0f0ad3e-80cd-4de5-a488-94f7843ef03e",
    "subscriptionDisposed": true
  },
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": 1719500000000,
  "success": true
}
```

#### 会话已结束（中断信号已发送但未找到活跃订阅）

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "code": 200,
    "message": "中断信号已发送，会话可能已结束",
    "userId": "19210818109",
    "sessionId": "b0f0ad3e-80cd-4de5-a488-94f7843ef03e",
    "subscriptionDisposed": false
  },
  "requestId": "550e8400-e29b-41d4-a716-446655440001",
  "timestamp": 1719500000001,
  "success": true
}
```

### 8.6 多语言请求示例

#### curl

```bash
curl -X POST "http://localhost:8788/api/v1/chat/interrupt?userId=19210818109&sessionId=b0f0ad3e-80cd-4de5-a488-94f7843ef03e" \
  -H "X-Session-Token: abc123def456"
```

#### JavaScript

```javascript
async function interruptSession(userId, sessionId) {
  const response = await fetch(
    `http://localhost:8788/api/v1/chat/interrupt?userId=${userId}&sessionId=${sessionId}`,
    {
      method: "POST",
      headers: { "X-Session-Token": "abc123def456" }
    }
  );
  return await response.json();
}
```

#### Python

```python
import requests

response = requests.post(
    "http://localhost:8788/api/v1/chat/interrupt",
    params={"userId": "19210818109", "sessionId": "b0f0ad3e-80cd-4de5-a488-94f7843ef03e"},
    headers={"X-Session-Token": "abc123def456"}
)
print(response.json())
```

#### Java

```java
OkHttpClient client = new OkHttpClient();
Request request = new Request.Builder()
    .url("http://localhost:8788/api/v1/chat/interrupt?userId=19210818109&sessionId=b0f0ad3e-80cd-4de5-a488-94f7843ef03e")
    .post(RequestBody.create("", null))
    .addHeader("X-Session-Token", "abc123def456")
    .build();
try (Response response = client.newCall(request).execute()) {
    System.out.println(response.body().string());
}
```

#### Go

```go
resp, _ := http.Post(
    "http://localhost:8788/api/v1/chat/interrupt?userId=19210818109&sessionId=b0f0ad3e-80cd-4de5-a488-94f7843ef03e",
    "application/json",
    nil,
)
body, _ := io.ReadAll(resp.Body)
fmt.Println(string(body))
```

---

## 9. SSE 事件枚举

### 9.1 事件总览

流式聊天接口（`/stream` 和 `/confirm`）通过 SSE 推送以下事件。事件分为三类：

| 分类 | 事件数 | 说明 |
|---|---|---|
| AgentScope 框架事件 | 14 | 对应 `AgentEventEnum`，描述 ReAct 推理链的每一步 |
| 框架生命周期事件 | 4 | agent_start / agent_end / error / done |
| 业务事件 | 2 | result / permission_paused |

### 9.2 事件枚举总表

| 事件类型 (event) | 编码 | 来源类 | 触发时机 | 前端处理建议 |
|---|---|---|---|---|
| `agent_start` | - | `ChatService.sendEvent` | Agent 开始执行（stream 和 confirm 恢复时各发一次） | 显示"AI 思考中"状态 |
| `agent_end` | - | `ChatService.sendEvent` | Agent 执行结束（正常完成/中断/快速通道） | 隐藏 loading，朗读回复 |
| `error` | - | `ChatService.sendEvent` | Agent 执行异常或限流 | 展示错误提示 |
| `done` | - | `ChatService.sendDone` | SSE 流结束标记（固定 data 为 `[DONE]`） | 关闭 SSE 连接 |
| `result` | - | `ToolResultEndHandler` / `ChatService.handleFastPathResult` | 工具返回含 routePath 的结构化数据 | 按 routePath 路由渲染业务界面 |
| `permission_paused` | - | `RequireUserConfirmHandler` | 敏感工具调用被权限系统拦截（HITL） | 展示确认弹框，调用 confirm 接口 |
| `permission_ask` | 14 | `RequireUserConfirmHandler` | 权限确认请求（内部事件，含完整工具调用信息） | 提取 toolCallId 供 confirm 使用 |
| `text_delta` | 1 | `TextBlockDeltaHandler` | 模型输出文本增量 | 累加 delta 渲染打字机效果 |
| `text_end` | 2 | `TextBlockEndHandler` | 文本块结束 | 标记本次文本输出完成 |
| `thinking_start` | 3 | `ThinkingBlockStartHandler` | 思考块开始 | 展示"思考过程"折叠面板 |
| `thinking_delta` | 4 | `ThinkingBlockDeltaHandler` | 思考过程增量文本 | 累加 delta 渲染思考过程 |
| `thinking_end` | 5 | `ThinkingBlockEndHandler` | 思考块结束 | 折叠思考过程面板 |
| `tool_call_start` | 6 | `ToolCallStartHandler` | 工具调用开始（模型决定调用工具） | 展示"正在执行: {工具名}" |
| `tool_call_delta` | 7 | `ToolCallDeltaHandler` | 工具入参增量（arguments JSON 片段） | 累加 delta 还原完整入参 |
| `tool_call_end` | 8 | `ToolCallEndHandler` | 工具入参构造完成 | 展示完整工具入参 |
| `tool_result_start` | 9 | `ToolResultStartHandler` | 工具开始执行 | 展示"执行中"状态 |
| `tool_result_text_delta` | 10 | `ToolResultTextDeltaHandler` | 工具结果文本增量 | 累加 delta 还原完整结果 |
| `tool_result_end` | 11 | `ToolResultEndHandler` | 工具执行结束 | 展示执行状态（SUCCESS/ERROR） |
| `model_call_start` | 12 | `ModelCallStartHandler` | LLM 模型调用开始 | 记录调用起始时间 |
| `model_call_end` | 13 | `ModelCallEndHandler` | LLM 模型调用结束 | 更新 Token 用量统计 |
| `other` | 0 | `ChatService.forwardAgentEvent` | 兜底事件（未注册处理器的 AgentScope 事件） | 忽略或记录日志 |

### 9.3 各事件报文样例

#### 9.3.1 `agent_start` — Agent 开始执行

```
event: agent_start
data: {"type":"agent_start","eventId":"550e8400-e29b-41d4-a716-446655440000","sessionId":"b0f0ad3e-80cd-4de5-a488-94f7843ef03e","timestamp":1719500000000}
```

#### 9.3.2 `agent_end` — Agent 执行结束

**正常结束：**
```
event: agent_end
data: {"type":"agent_end","eventId":"550e8400-...","sessionId":"b0f0ad3e-...","timestamp":1719500001000}
```

**快速通道结束（携带回复文本）：**
```
event: agent_end
data: {"type":"agent_end","eventId":"550e8400-...","sessionId":"b0f0ad3e-...","timestamp":1719500001000,"reply":"已为您开灯","broadcastText":"已为您开灯","fastPath":true}
```

**中断结束：**
```
event: agent_end
data: {"type":"agent_end","eventId":"550e8400-...","sessionId":"b0f0ad3e-...","timestamp":1719500001000,"interrupted":true,"message":"Agent 已被用户中断"}
```

#### 9.3.3 `error` — 错误事件

```
event: error
data: {"type":"error","eventId":"550e8400-...","sessionId":"b0f0ad3e-...","timestamp":1719500002000,"error":{"code":"AGENT_ERROR","message":"Agent执行异常"}}
```

**限流错误：**
```
event: error
data: {"type":"error","eventId":"550e8400-...","sessionId":"b0f0ad3e-...","timestamp":1719500002000,"error":{"code":"RATE_LIMITED","message":"请求过于频繁，请稍后重试"}}
```

#### 9.3.4 `done` — 流结束标记

```
event: done
data: [DONE]
```

> **注意**：`done` 事件的 data 固定为字符串 `[DONE]`，不是 JSON。收到此事件后应关闭 SSE 连接。

#### 9.3.5 `result` — 结构化结果（前端路由渲染）

```
event: result
data: {"type":"result","sessionId":"b0f0ad3e-...","routePath":"/device/list","data":[{"deviceId":"123","name":"客厅灯","spk":"..."}],"message":"成功查询到设备信息"}
```

**快速通道 result 事件：**
```
event: result
data: {"type":"result","sessionId":"b0f0ad3e-...","routePath":"/device/status","data":{"success":true},"message":"成功批量控制设备"}
```

**routePath 枚举值：**

| routePath | 含义 | 前端渲染界面 |
|---|---|---|
| `/device/list` | 设备列表 | DeviceListPage |
| `/device/detail` | 设备详情 | DeviceDetailPage |
| `/device/status` | 设备控制结果 | DeviceStatusPage |
| `/scene/list` | 场景列表 | SceneListPage |
| `/home/list` | 房屋列表 | HomeListPage |
| `/product/list` | 产品列表 | ProductListPage |
| `/product/detail` | 产品详情 | ProductDetailPage |
| `/cart/list` | 购物车 | CartListPage |
| `/result/generic` | 通用结果页 | GenericResultPage |
| `/result/error` | 错误结果页 | ErrorResultPage |
| `/floor-plan/result` | 户型图分析结果 | FloorPlanPage |

#### 9.3.6 `permission_paused` — 权限暂停（HITL）

```
event: permission_paused
data: {"type":"permission_paused","message":"即将执行 设备控制 操作，请确认","toolCalls":[{"toolCallId":"call_abc123","toolName":"batch_control_device","input":{"actionsJson":"[{...}]"},"content":"...","suggestedRules":[...]}]}
```

#### 9.3.7 `permission_ask` — 权限确认请求（内部事件）

```
event: permission_ask
data: {"type":"permission_ask","sessionId":"b0f0ad3e-...","replyId":"reply_001","toolCalls":[{"toolCallId":"call_abc123","toolName":"batch_control_device","input":{"actionsJson":"[{\"deviceId\":\"123\",\"userInput\":\"开灯\"}]"},"content":"{\"actionsJson\":\"...\"}","suggestedRules":[{"toolName":"batch_control_device","ruleContent":null,"behavior":"ALLOW","source":"USER"}]}]}
```

#### 9.3.8 `text_delta` — 文本增量

```
event: text_delta
data: {"type":"text_delta","sessionId":"b0f0ad3e-...","delta":"已"}
```

```
event: text_delta
data: {"type":"text_delta","sessionId":"b0f0ad3e-...","delta":"为您"}
```

```
event: text_delta
data: {"type":"text_delta","sessionId":"b0f0ad3e-...","delta":"开灯"}
```

#### 9.3.9 `text_end` — 文本块结束

```
event: text_end
data: {"type":"text_end","sessionId":"b0f0ad3e-...","blockId":"block_001"}
```

#### 9.3.10 `thinking_start` — 思考块开始

```
event: thinking_start
data: {"type":"thinking_start","sessionId":"b0f0ad3e-...","replyId":"reply_001","blockId":"thinking_001"}
```

#### 9.3.11 `thinking_delta` — 思考增量

```
event: thinking_delta
data: {"type":"thinking_delta","sessionId":"b0f0ad3e-...","replyId":"reply_001","blockId":"thinking_001","delta":"用户说开灯，我需要调用 batch_control_device 工具..."}
```

#### 9.3.12 `thinking_end` — 思考块结束

```
event: thinking_end
data: {"type":"thinking_end","sessionId":"b0f0ad3e-...","replyId":"reply_001","blockId":"thinking_001"}
```

#### 9.3.13 `tool_call_start` — 工具调用开始

```
event: tool_call_start
data: {"type":"tool_call_start","sessionId":"b0f0ad3e-...","toolCallId":"call_abc123","toolName":"batch_control_device"}
```

#### 9.3.14 `tool_call_delta` — 工具入参增量

```
event: tool_call_delta
data: {"type":"tool_call_delta","sessionId":"b0f0ad3e-...","toolCallId":"call_abc123","toolName":"batch_control_device","delta":"{\"actionsJson\":\"[{\"deviceId\":\"123\""}
```

```
event: tool_call_delta
data: {"type":"tool_call_delta","sessionId":"b0f0ad3e-...","toolCallId":"call_abc123","toolName":"batch_control_device","delta":",\"userInput\":\"开灯\"}]\"}"}
```

#### 9.3.15 `tool_call_end` — 工具入参构造完成

```
event: tool_call_end
data: {"type":"tool_call_end","sessionId":"b0f0ad3e-...","toolCallId":"call_abc123","toolName":"batch_control_device"}
```

#### 9.3.16 `tool_result_start` — 工具执行开始

```
event: tool_result_start
data: {"type":"tool_result_start","sessionId":"b0f0ad3e-...","toolCallId":"call_abc123","toolName":"batch_control_device"}
```

#### 9.3.17 `tool_result_text_delta` — 工具结果增量

```
event: tool_result_text_delta
data: {"type":"tool_result_text_delta","sessionId":"b0f0ad3e-...","toolCallId":"call_abc123","toolName":"batch_control_device","delta":"{\"success\":true,\"message\":\"成功批量控制设备\""}
```

#### 9.3.18 `tool_result_end` — 工具执行结束

```
event: tool_result_end
data: {"type":"tool_result_end","sessionId":"b0f0ad3e-...","toolCallId":"call_abc123","toolName":"batch_control_device","state":"SUCCESS"}
```

> `state` 可选值：`SUCCESS` / `ERROR` / `UNKNOWN`

#### 9.3.19 `model_call_start` — 模型调用开始

```
event: model_call_start
data: {"type":"model_call_start","sessionId":"b0f0ad3e-...","replyId":"reply_001"}
```

#### 9.3.20 `model_call_end` — 模型调用结束

```
event: model_call_end
data: {"type":"model_call_end","sessionId":"b0f0ad3e-...","replyId":"reply_001","inputTokens":150,"outputTokens":80,"totalTokens":230}
```

#### 9.3.21 `other` — 兜底事件

```
event: AgentStartEvent
data: {"type":"AgentStartEvent","sessionId":"b0f0ad3e-...","eventType":"AGENT_START"}
```

> 未注册处理器的事件（如 `AgentStartEvent`、`ExceedMaxItersEvent`）会以类名作为 event name 推送。

### 9.4 完整 SSE 流报文示例（一次设备控制对话）

```
event: agent_start
data: {"type":"agent_start","eventId":"a1b2c3d4-...","sessionId":"b0f0ad3e-80cd-4de5-a488-94f7843ef03e","timestamp":1719500000000}

event: model_call_start
data: {"type":"model_call_start","sessionId":"b0f0ad3e-...","replyId":"reply_001"}

event: thinking_start
data: {"type":"thinking_start","sessionId":"b0f0ad3e-...","replyId":"reply_001","blockId":"thinking_001"}

event: thinking_delta
data: {"type":"thinking_delta","sessionId":"b0f0ad3e-...","replyId":"reply_001","blockId":"thinking_001","delta":"用户说开灯，需要调用 batch_control_device 工具"}

event: thinking_end
data: {"type":"thinking_end","sessionId":"b0f0ad3e-...","replyId":"reply_001","blockId":"thinking_001"}

event: tool_call_start
data: {"type":"tool_call_start","sessionId":"b0f0ad3e-...","toolCallId":"call_abc123","toolName":"batch_control_device"}

event: tool_call_delta
data: {"type":"tool_call_delta","sessionId":"b0f0ad3e-...","toolCallId":"call_abc123","toolName":"batch_control_device","delta":"{\"actionsJson\":\"[{\\\"deviceId\\\":\\\"123\\\",\\\"userInput\\\":\\\"开灯\\\"}]\"}"}

event: tool_call_end
data: {"type":"tool_call_end","sessionId":"b0f0ad3e-...","toolCallId":"call_abc123","toolName":"batch_control_device"}

event: model_call_end
data: {"type":"model_call_end","sessionId":"b0f0ad3e-...","replyId":"reply_001","inputTokens":150,"outputTokens":80,"totalTokens":230}

event: tool_result_start
data: {"type":"tool_result_start","sessionId":"b0f0ad3e-...","toolCallId":"call_abc123","toolName":"batch_control_device"}

event: tool_result_text_delta
data: {"type":"tool_result_text_delta","sessionId":"b0f0ad3e-...","toolCallId":"call_abc123","toolName":"batch_control_device","delta":"{\"success\":true,\"message\":\"成功批量控制设备\",\"routePath\":\"/device/status\",\"data\":{\"controlled\":1}}"}

event: tool_result_end
data: {"type":"tool_result_end","sessionId":"b0f0ad3e-...","toolCallId":"call_abc123","toolName":"batch_control_device","state":"SUCCESS"}

event: result
data: {"type":"result","sessionId":"b0f0ad3e-...","routePath":"/device/status","data":{"controlled":1},"message":"成功批量控制设备"}

event: model_call_start
data: {"type":"model_call_start","sessionId":"b0f0ad3e-...","replyId":"reply_002"}

event: text_delta
data: {"type":"text_delta","sessionId":"b0f0ad3e-...","delta":"已"}

event: text_delta
data: {"type":"text_delta","sessionId":"b0f0ad3e-...","delta":"为您开灯"}

event: text_end
data: {"type":"text_end","sessionId":"b0f0ad3e-...","blockId":"block_001"}

event: model_call_end
data: {"type":"model_call_end","sessionId":"b0f0ad3e-...","replyId":"reply_002","inputTokens":200,"outputTokens":20,"totalTokens":220}

event: agent_end
data: {"type":"agent_end","eventId":"e5f6g7h8-...","sessionId":"b0f0ad3e-...","timestamp":1719500005000}

event: done
data: [DONE]
```

---

## 10. SSE 事件注意事项

### 10.1 事件顺序与生命周期

1. **固定首事件**：`agent_start` 总是第一个事件（stream 和 confirm 恢复时各发一次）。
2. **固定尾事件**：`done` 总是最后一个事件（data 固定为 `[DONE]`），收到后必须关闭 SSE 连接。
3. **ReAct 多轮循环**：一次对话可能包含多轮 `model_call_start → thinking_* → tool_call_* → tool_result_* → model_call_end` 循环，直到模型不再调用工具。
4. **agent_end 时机**：仅在 Agent 完全结束（正常完成/中断/快速通道）后发送，之后紧跟 `done`。
5. **permission_paused 特殊性**：收到此事件时，SSE 流会暂停（不发 `agent_end` 和 `done`），前端需展示确认弹框并调用 `/confirm` 接口，恢复后会在新的 SSE 流中继续接收事件。

### 10.2 增量事件累加规则

| 事件 | 累加字段 | 累加方式 | 说明 |
|---|---|---|---|
| `text_delta` | `delta` | 字符串拼接 | 按 `replyId` 分组累加，得到完整回复文本 |
| `thinking_delta` | `delta` | 字符串拼接 | 按 `blockId` 分组累加，得到完整思考过程 |
| `tool_call_delta` | `delta` | 字符串拼接 | 按 `toolCallId` 分组累加，得到完整工具入参 JSON |
| `tool_result_text_delta` | `delta` | 字符串拼接 | 按 `toolCallId` 分组累加，得到完整工具结果 JSON |

### 10.3 快速通道事件序列

当用户输入命中本地快速通道（明确设备控制指令）时，SSE 事件序列简化为：

```
event: result          → 结构化数据（routePath + data）
event: agent_end       → 携带 reply、broadcastText、fastPath=true
event: done            → [DONE]
```

**特征**：无 `model_call_*`、`thinking_*`、`tool_call_*`、`tool_result_*` 事件，响应时间约 500ms（0 云端 token）。

### 10.4 中断后的事件行为

调用 `/interrupt` 接口后：
1. 被中断的 SSE 流会收到一个 `agent_end` 事件（含 `interrupted: true`）和 `done` 事件。
2. 框架内部 ReAct 循环在下一检查点优雅终止，AgentState 保存到 Redis。
3. 中断后可通过相同 `sessionId` 继续对话，Agent 会从上次中断点恢复上下文。

### 10.5 多实例部署中断

当服务多实例部署时，`/interrupt` 接口通过 Redis Pub/Sub 广播中断消息到所有实例，确保无论用户的 SSE 连接在哪个实例上都能被中断。

### 10.6 错误码对照表

| 错误码 | HTTP 状态 | 含义 | 触发场景 |
|---|---|---|---|
| `GLOBAL_001` | 500 | 系统内部错误 | 未捕获异常 |
| `GLOBAL_002` | 400 | 参数校验失败 | sessionId/userMessage 为空、userId 缺失 |
| `CHAT_001` | 500 | Agent 执行异常 | ReAct 循环报错 |
| `CHAT_003` | 500 | SSE 事件转发失败 | 事件处理器异常 |
| `PERMISSION_001` | 400 | 未找到待确认权限请求 | confirm 接口无对应待确认数据（超时或已处理） |
| `PERMISSION_003` | 400 | 残留权限确认状态 | 上一轮会话未完成 HITL 确认 |
| `INTERRUPT_001` | 400 | 中断参数格式错误 | userId 为空或 sessionId 非 UUID 格式 |
| `RATE_001` | 429 | 请求被限流 | Redis 滑动窗口限流触发 |
| `RATE_002` | 429 | SSE 并发数达上限 | 超过 500 并发 SSE 连接 |

### 10.7 SSE 连接管理建议

1. **超时设置**：客户端 HTTP 超时建议设为 5 分钟以上（与服务端 SSE 超时一致）。
2. **断线重连**：SSE 连接断开后不建议自动重连（每次对话是独立的 stream 请求），应由用户重新发送消息。
3. **心跳保活**：服务端未实现 SSE 心跳，长连接可能被中间代理超时切断。建议部署时调整 Nginx/GoEdge 的 `proxy_read_timeout` 为 300s 以上。
4. **编码**：所有 SSE 事件 data 均为 UTF-8 编码的 JSON 字符串（`done` 事件除外，为纯文本 `[DONE]`）。
5. **并发限制**：单服务实例最多 500 个并发 SSE 连接，超过返回 `RATE_002`。多实例部署时总并发 = 实例数 × 500。

### 10.8 前端事件处理流程图

```
用户发送消息
    │
    ▼
POST /api/v1/chat/stream → 建立 SSE 连接
    │
    ▼
接收 agent_start → 显示"AI 思考中"
    │
    ▼
接收 model_call_start → 记录调用开始
    │
    ├── 接收 thinking_start/delta/end → 渲染思考过程（可折叠）
    │
    ├── 接收 tool_call_start/delta/end → 渲染"正在执行: {工具名}"
    │   │
    │   ▼
    │   接收 tool_result_start/delta/end → 渲染工具执行状态
    │   │
    │   ▼
    │   接收 result → 按 routePath 渲染业务界面 ★
    │
    ├── 接收 permission_paused → 展示确认弹框
    │   │
    │   ▼
    │   用户确认/拒绝 → POST /api/v1/chat/confirm（新 SSE 流）
    │
    ├── 接收 text_delta → 累加渲染打字机效果
    │
    ▼
接收 model_call_end → 更新 Token 用量
    │
    ▼
接收 agent_end → 隐藏 loading，朗读 broadcastText
    │
    ▼
接收 done → 关闭 SSE 连接
```

---

## 11. 对接快速接入清单

### 11.1 最小可用对接（4 步）

1. **登录获取 Token**：POST `/api/auth/login`，传 `loginName` 和 `loginPwd`，获取 `sessionToken`。
2. **选择房屋**：POST `/api/auth/switchHome`，传 `homeName`，设定控制目标。
3. **调用 stream 接口**：POST `/api/v1/chat/stream`，传 `sessionId`（UUID）和 `userMessage`，监听 SSE 事件。
4. **处理关键事件**：
   - `text_delta`：累加显示回复文本
   - `result`：按 `routePath` 渲染业务界面
   - `agent_end`：标记对话结束
   - `done`：关闭 SSE 连接

### 11.2 完整功能对接（8 步）

1. **登录获取 Token**：POST `/api/auth/login`。
2. **查询状态**：GET `/api/auth/status`，获取房屋列表。
3. **切换房屋**：POST `/api/auth/switchHome`，选定控制目标。
4. **调用 stream 接口**：POST `/api/v1/chat/stream`，监听全部 21 个事件。
5. **处理 permission_paused**：展示确认弹框，POST `/api/v1/chat/confirm` 恢复。
6. **处理中断**：用户点击"停止"时 POST `/api/v1/chat/interrupt`。
7. **多轮对话**：复用同一 `sessionId` 保持上下文。
8. **多模态**：传 `images`/`audios`/`videos` 字段支持图片/音频/视频输入。

### 11.3 AI 工具接入说明

本文档设计为 AI 友好格式，AI 工具（如 Cursor、Trae、Copilot）可直接基于以下要点生成对接代码：

1. **接口规范**：8 个接口 — 2 个认证（login/logout）+ 2 个会话（status/switchHome）+ 1 个房屋查询（home/list）+ 2 个 SSE 流式（stream/confirm）+ 1 个中断（interrupt）。
2. **鉴权**：登录接口无需鉴权；其余接口 Header 传 `X-Session-Token`（登录返回的 sessionToken）。
3. **响应格式**：`/api/home/list` 返回 `ToolResultVO` 结构（直接含 success/data/routePath）；其余非 SSE 接口返回 `Response<T>` 包装结构（含 code/message/data/success/requestId/timestamp）。
4. **SSE 解析**：按 `\n\n` 分割事件块，每块内 `event:` 行为类型、`data:` 行为 JSON 数据。
5. **事件累加**：`text_delta`/`thinking_delta`/`tool_call_delta`/`tool_result_text_delta` 需按对应 ID 分组累加。
6. **终止条件**：收到 `done` 事件（data 为 `[DONE]`）后关闭连接。
7. **HITL 流程**：`permission_paused` → 展示弹框 → POST `/confirm` → 新 SSE 流接收后续事件。
8. **中断流程**：POST `/interrupt?userId={userId}&sessionId={sessionId}` → 原 SSE 流收到 `agent_end(interrupted=true)` + `done`。
9. **房屋选择**：登录后必须调用 `/api/auth/switchHome` 选择房屋，否则设备控制等业务无法定位目标房屋。
