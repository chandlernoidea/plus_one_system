# 代码审查 · V0.8.0

> 审查对象：`feat/solo-studio` 上 P1–P8 的全部代码（单人版 100 个 Java 文件 + 前端 `views/solo`、`components/solo`），
> 以及与它共用的鉴权 / 异常 / 上传 / 安全配置。
> 四个角度：网络安全、功能性、接口规范、注释完整性。**发现问题当场能修的已修**，其余列为建议。

## 结论摘要

| 类别 | 发现 | 已修 | 建议（未改） |
|---|---|---|---|
| 网络安全 | 12 | 3 | 9 |
| 功能性 | 10 | 2（导入长度与行数校验、取消入参校验） | 8 |
| 接口规范 | 8 | 3 | 5 |
| 注释 | 41 个类缺类级说明 | 41 | — |

已修的三处安全问题：**越权访问、CSV 公式注入、500 错误信息泄露**。
新增 6 例单测（`CsvCellTest`），后端单测从 12 例增至 18 例，全绿。

---

## 一、网络安全

### S1 · 越权：员工/顾客账号能读写老板的全部数据（高，已修）

**位置**：`config/SecurityConfig.java`

**问题**：`/api/**` 原本只要求「已登录」。而库里同时存在多角色演示账号
（`staff1` / `cust1`，密码写在 `data.sql` 里），JWT 过滤器会给它们 `ROLE_STAFF` / `ROLE_CUSTOMER`。
也就是说，任何知道演示口令的人都能读老板的客户档案、定金流水、财务明细，甚至改预约、收定金。

**修复**：`/api/solo/**` 收紧为 `hasRole("ADMIN")`，并补了 JSON 的 403 响应（原先会回 Spring 默认 HTML 错误页）。

```java
.requestMatchers("/uploads/**").permitAll()
.requestMatchers("/api/solo/**").hasRole("ADMIN")   // 新增
.requestMatchers("/api/**").authenticated()
```

**实测**：

```
admin  GET /api/solo/clients            → HTTP 200
staff1 GET /api/solo/clients            → HTTP 403 {"code":403,"message":"没有权限访问该接口"}
staff1 GET /api/service-types（旧接口）  → HTTP 200（多角色版不受影响）
未登录 GET /api/solo/clients            → HTTP 401
```

### S2 · CSV 公式注入（中，已修）

**位置**：`solo/service/ExportService.java`

**问题**：导出只处理了逗号 / 引号 / 换行，没有防「单元格公式」。
客户昵称里写成 `=HYPERLINK("http://evil","点我")`，老板用 Excel 打开导出的 CSV 就是一个可点链接；
更狠的 `=cmd|' /C calc'!A0`（DDE）在旧版 Excel 上能拉起进程。

**修复**：抽出 `solo/support/CsvCell.java`，对 `= + - @ \t \r` 开头的单元格加前导单引号；
纯数字（含负数金额）保持原样，避免金额列变文本。

**实测**（导出真实数据）：

```
#C217,"'=HYPERLINK(""http://evil"",""点我"")",,,,,,,
```

**单测**：`CsvCellTest` 6 例覆盖普通文本、含逗号引号换行、五种公式前缀、负数保数字、null、制表符。

### S3 · 500 错误把内部异常回给前端（中，已修）

**位置**：`exception/GlobalExceptionHandler.java`

**问题**：`ApiResponse.error(500, e.getMessage())`——SQL 异常会把表名/字段名/驱动信息直接吐给浏览器，
也方便攻击者判断后端实现。

**修复**：响应固定文案，异常详情只进日志（`log.error` 带堆栈）。

### S4 · 登录无频率限制（中，未改）

同一 IP 可以无限次撞密码（`/api/auth/login` 无锁、无验证码、无限流）。单人版账号就是老板本人，
一旦部署到公网就是个明确的攻击面。

**建议**：nginx `limit_req_zone` 对 `/api/auth/login` 限速（5 次/分），或在应用层加失败计数。
这条属于运维配置，没改代码。

### S5 · JWT 的先天约束（中，接受）

- HS256，密钥来自 `jwt.secret`，**仓库里带了一个默认值**（DEPLOY 已要求替换）；
- 24 小时有效，无刷新、无吊销（登出只是前端删 token）；
- token 存在 `localStorage`，理论上可被同源 XSS 读取。

**现状核对**：前端没有 `v-html` / `innerHTML`，Vue 默认转义，XSS 面很小。
**建议**：生产环境加 CSP 与 `X-Content-Type-Options: nosniff`（nginx 一行），并把 token 改成短期 + 刷新，或改用 HttpOnly Cookie（要配 CSRF）。

### S6 · 默认口令与默认密钥（中，文档已覆盖）

`data.sql` 每次启动都会把 `admin` 密码写回 `123456`（`ON DUPLICATE KEY UPDATE`），
`application.yml` 里 `jwt.secret` 是示例值。DEPLOY 第 6 节要求改密码并把 `spring.sql.init.mode` 关掉。

**建议**（未改）：启动时检测到默认密钥/默认口令就打 WARN，或在生产 profile 下直接拒绝启动。

### S7 · CORS 放开所有来源（低，未改）

`allowedOriginPatterns("*")`，但 `allowCredentials` 为 false，且鉴权走 `Authorization` 头而不是 Cookie，
恶意站点拿不到浏览器里的 token，实际风险低。

**建议**：生产环境收敛为具体域名。

### S8 · 上传文件公开可读（低，未改）

`/uploads/**` 是 `permitAll`。文件名是 UUID，猜不到，但拿到 URL 的人（比如把链接转发出去）都能看。

**建议**：如需私密，改成带鉴权的下载接口或短时签名 URL。

### S9 · 上传只校验扩展名（低，未改）

`FileStorageService` 校验了大小（5MB）、扩展名白名单（jpg/jpeg/png/webp）、并做了路径穿越检查
（`target.startsWith(uploadDir)`）。没有校验文件头（magic bytes）：把 HTML 改名成 `.png` 能传上去，
但由 Spring 静态资源按扩展名回 `image/png`，浏览器不会当 HTML 执行。

**建议**：需要更严时读前 12 字节判断真实类型。

### S10 · 缺少安全响应头（低，未改）

没有 CSP / X-Frame-Options / Referrer-Policy / HSTS。

**建议**：nginx 统一加，`add_header` 四条即可。

### S11 · SQL 注入面（无问题）

全部走 Spring Data 派生查询或 `@Query` 具名参数（`ClientRepository.search` 用 `:keyword` 绑定），
没有字符串拼接 SQL。CSV 导入的字段最终通过 JPA 保存，不参与 SQL 拼装。

### S12 · 存储型 XSS（无问题）

前端没有 `v-html`，所有客户昵称/备注/话术都走 Vue 插值转义；话术复制走 `navigator.clipboard`。

---

## 二、功能性分析

### F1 · 定金「抵扣」在界面上没有入口（功能缺口，建议）

数据模型与接口都支持四条流水（`COLLECT / OFFSET / REFUND / FORFEIT`），后端校验也覆盖了 OFFSET；
但前端只有「收定金 / 退定 / 没收」三个动作，**OFFSET（完成时抵扣尾款）没有任何入口**。
brief 里「定金收取/抵扣/退还/没收四条流水」实际只跑通三条。

**建议**：在「完成」时加一步「定金抵扣尾款」，或作业单卡片上加「抵扣」按钮。

### F2 · 完成单不自动写收入流水（设计决定）

P6 明确留给「记一笔」手动录入，避免金额算错。已在文档与 CHANGELOG 标注。

### F3 · 改期后的待通知没并入提醒（设计遗留）

`booking_change.notified=0` 只在排期页展示；每日提醒（`ReminderService`）没有把这批变更分组进去。
建议并入「今天」组，话术用「时间从 X 改到 Y」。

### F4 · 材料清单与库存按名称匹配（设计限制）

`MaterialService.needsOfDay` 用款式 `material_json` 里的 `name` 直接匹配库存 `name`。
物料改名后历史款式清单会失配，缺口会算成「没建库存」。建议后续加 `material_id` 关联。

### F5 · 待定金不占档（设计决定，已文档化）

`WAIT_DEPOSIT` 不参与冲突判断，两单可以同时待定金；收定金那一刻会补做冲突校验（409）。
这条在 P2 文档里写明了，属于有意为之。

### F6 · 顺延不校验营业时间（边界缺口）

`shift()` 只保证「不和其它单撞」，不保证平移后仍在营业时间内（可能平移到 23:00 之后）。

**建议**：顺延时用 `SlotAvailabilityService.openWindows` 校验落点，超出的单拦下并提示。

### F7 · 并发竞态（低）

- `ClientService.nextCode()` 先查最大 id 再加一，并发下可能撞唯一键（单人使用，概率极低）；
- `DepositService.add` 的「累计不超过定金上限」在并发下可能被突破。

**建议**：真要防，给这两处加行锁（`SELECT ... FOR UPDATE`）或数据库唯一约束兜底。

### F8 · 一次导入的规模（已修）

原实现无行数上限，一段超大文本能撑爆内存；已加 **2000 行**上限并给出明确提示。

### F9 · 导入字段长度（已修）

原实现直接把 CSV 字段塞进实体，超过列宽（昵称 50 / 来源 30 / 备注 500）会抛数据库异常 → 500。
现在在预览阶段就判为无效行并给出提示，导入时跳过。

### F10 · 时间与时区（运维风险）

全链路用 `LocalDateTime`，没有时区转换；服务器时区不是 `Asia/Shanghai` 时「今天」会算错。
DEPLOY 的 FAQ 已写，属于部署约束。

---

## 三、接口规范性

### I1 · 响应封装一致（通过）

所有接口统一 `{code, message, data}`，前端 `request.js` 按 `code` 判断，401 自动跳登录。

### I2 · HTTP 状态码语义不一致（建议）

业务异常走的是 **HTTP 200 + body.code=400/404**，只有「冲突」是真正的 409。
好处是前端处理简单，坏处是与 REST 语义不符，监控/网关无法按状态码统计失败率。

**建议**：统一为真实状态码（404/400/409），前端拦截器已经能读 `error.response.data.message`，改动成本不高。
本轮没动，是因为这套 handler 被 `main` 的多角色版共用。

### I3 · 用响应 DTO 当请求体（已修）

`PUT /api/solo/settings` 原来直接收 `SettingsResponse`，校验只能写在 Service 里。
已新增 `SettingsRequest`，把 `@NotNull / @Pattern / @Min / @DecimalMin` 放到请求体上，
Service 里的检查保留作为兜底（防御性重复，不算冗余——入参可能绕过控制器）。

### I4 · 命名不统一（建议）

| 现状 | 问题 |
|---|---|
| `GET /clients/{id}/detail` | `detail` 冗余，`GET /clients/{id}` 就是详情 |
| `GET /materials/todo` | todo 是内部叫法，语义上应是 `needs` |
| `POST /jumps`、`POST /shifts` | 动词当资源名 |
| `POST /bookings/{id}/arrive\|start\|complete\|no-show` | 动词路径（RPC 风格） |
| `PUT /bookings/{id}/reschedule` 实际用 POST | 文档与实现一致，但与 REST 习惯不同 |

这套命名是 P2 定下并写进文档的（`P2-接口与规则.md`），改名的收益低于牵动前端的成本，
**建议下一版统一**：`/bookings/{id}/status` + `action` 字段，或显式声明为 RPC 风格。

### I5 · 列表没有分页（约束内，建议）

brief 与仓库约束都写了「列表接口不分页」。单人版数据量小（一年也就几百单），短期没问题；
但 `/clients`、`/bookings`、`/materials` 没有任何上限，建议至少加 `limit` 参数兜底。

### I6 · 参数校验覆盖（已补齐关键点）

- 请求体普遍有 `@Valid`；本轮补了 CSV 导入的字段长度与行数上限；
- `POST /bookings/{id}/cancel` 的 body 是 `@RequestBody(required = false)` 且原本没加 `@Valid`，
  `reason` 的 `@Size(200)` 实际不生效（截断发生在数据库层）。**已修**：补上 `@Valid`。

### I7 · 时间与日期格式（通过）

查询参数 `date=YYYY-MM-DD`，请求体 `yyyy-MM-dd HH:mm:ss`，响应同格式，全链一致。

### I8 · 缺少机器可读的接口契约（建议）

只有 Markdown 文档，没有 OpenAPI。建议后续加 `springdoc-openapi`，让接口清单可被工具消费。

---

## 四、注释补全

审查发现 **41 个类没有类级说明**（字段/方法大多有注释，但打开文件看不出这个类是干什么的），
主要集中在 `solo/controller`（7 个）、`solo/dto`（22 个）、`solo/repository`（12 个）。已全部补上一句话说明，例如：

```java
/**
 * 顺延结果：影响 N 单的清单，预览与执行共用。
 */
public class BookingShiftPlanResponse { … }

/**
 * 客户查询入口（短号、同名、三合一搜索）。
 */
public interface ClientRepository extends JpaRepository<Client, Long> { … }
```

另外给这轮改动补了「为什么」注释（而不是复述代码）：

- `CsvCell` 类注释说明公式注入的攻击面与处理方式；
- `SecurityConfig` 标注为什么 `/api/solo/**` 要 `hasRole("ADMIN")`；
- `GlobalExceptionHandler` 标注异常详情只进日志；
- `ClientService.MAX_IMPORT_LINES` 标注防内存撑爆。

前端组件的文件头注释（`SoloLayout` / `BookingCard` / `CsvImportSheet` 等）本来就是有的，本轮未动。

---

## 五、本轮改动清单

| 文件 | 改动 |
|---|---|
| `config/SecurityConfig.java` | `/api/solo/**` 限 `hasRole("ADMIN")`，新增 JSON 403 |
| `exception/GlobalExceptionHandler.java` | 500 不再回传内部异常信息 |
| `solo/support/CsvCell.java`（新增） | CSV 转义 + 公式注入中和 |
| `solo/service/ExportService.java` | 改用 `CsvCell` |
| `solo/service/ClientService.java` | 导入字段长度校验、2000 行上限 |
| `solo/dto/SettingsRequest.java`（新增） | 设置请求体独立 + 校验注解 |
| `solo/service/SoloSettingService.java`、`SoloSettingController.java` | 改用 `SettingsRequest` |
| `solo/controller/SoloBookingController.java` | 取消接口补 `@Valid`，让 `reason` 的长度约束生效 |
| `solo/controller|dto|repository` 共 41 个类 | 补类级 javadoc |
| `solo/support/CsvCellTest.java`（新增） | 6 例单测 |

**未改动 `main` 分支的业务接口**：`SecurityConfig` 与 `GlobalExceptionHandler` 是两条线共用的，
改动都是向后兼容的（新增规则 + 收敛错误文案），旧接口行为不变（实测 `staff1` 访问 `/api/service-types` 仍是 200）。

## 六、验证

```
mvn -B test        Tests run: 18, Failures: 0, Errors: 0   （原 12 + 新增 6）
mvn -B compile     BUILD SUCCESS
运行期实测          见 S1 / S2 的 curl 输出
```

## 七、建议的后续优先级

1. **P0**：登录限流（S4）、生产环境 OSS 化的安全响应头（S10）——都是部署层，改 nginx 即可；
2. **P1**：启动时检测默认密钥/口令（S6）、取消接口补 `@Valid`（I6）、顺延的营业时间校验（F6）；
3. **P2**：定金抵扣入口（F1）、接口命名统一（I4）、真实 HTTP 状态码（I2）；
4. **P3**：OpenAPI（I8）、列表分页（I5）、物料与库存改 ID 关联（F4）。
