# 01 · nail-appointment-app 骨架拆解

> 目标：把「美甲预约」这套骨架拆到可迁移的粒度。**只读**，未改动参考项目。
> 所有路径相对 `nail-appointment-app/`；行号对应当前快照。

## 1. 后端包结构（按包列类名）

根包 `com.artnail.nail`，共 43 个主类 + 6 个测试类。

| 包 | 类 | 职责 |
| --- | --- | --- |
| `config` | `NailProperties` `WebConfig` `AdminAuthInterceptor` `MybatisPlusConfig` | `nail.*` 业务参数；CORS/拦截器注册；管理端令牌拦截；分页插件 |
| `entity` | `AdminUser` `Appointment` `ServiceItem` `ShopConfig` `SlotConfig` `SmsCode` `StyleCase` `Stylist` | 8 张表一一对应，全部 `@TableName` + Lombok `@Data` |
| `mapper` | `AdminUserMapper` `AppointmentMapper` `ServiceItemMapper` `ShopConfigMapper` `SlotConfigMapper` `SmsCodeMapper` `StyleCaseMapper` `StylistMapper` | 全部是空接口 `extends BaseMapper<T>`，没有 XML、没有 `@Select` |
| `service` | `AdminAuthService` `BookingService` `CatalogService` `ShopConfigService` `SlotService` `SmsService` | 登录/下单/目录/文案/时段/验证码 |
| `support` | `AppointmentStatus` `CryptoSupport` | 状态枚举（含中文 label 与「占名额状态」集合）；PBKDF2/HMAC |
| `web` | `SiteController` `BookingController` `SmsController` `AdminAuthController` `AdminCatalogController` `AdminSlotController` `AdminAppointmentController` `AdminConfigController` `ApiExceptionHandler` | 用户端 3 个 + 管理端 5 个 + 全局异常 |
| `web.dto` | `ApiEnvelope` `SiteDtos` `BookingDtos` `AdminDtos` | `record` 定义请求/响应；统一壳 `{code,message,data}` |
| `test` | `MybatisPlusTestSupport` `BookingServiceTest` `SlotServiceTest` `SmsServiceTest` `AppointmentStatusTest` `CryptoSupportTest` | Mockito 隔离单测，不连真实 MySQL |

## 2. 数据库表清单（`backend/doc/schema.sql`）

库名 `nail`，`utf8mb4_general_ci`，**无外键**，主键统一 `BIGINT AUTO_INCREMENT`。`create_time`/`update_time` 已在下面合并为一行「审计」。

**`t_admin_user`** 管理端账号
| 字段 | 类型 | 注释 |
| --- | --- | --- |
| `id` | BIGINT PK | 主键 |
| `username` | VARCHAR(32) UNIQUE | 登录账号 |
| `password_hash` | VARCHAR(128) | PBKDF2WithHmacSHA256 结果（Base64） |
| `salt` | VARCHAR(64) | 每账号独立随机盐（Base64） |
| `name` | VARCHAR(32) | 显示名 |
| `status` | TINYINT | 1 启用 / 0 停用 |
| `last_login_time` | DATETIME | 最近登录 |
| 审计 | DATETIME | `create_time` / `update_time` |

**`t_service_item`** 服务项目
| 字段 | 类型 | 注释 |
| --- | --- | --- |
| `id` | BIGINT PK | 主键 |
| `name` | VARCHAR(48) | 项目名称 |
| `category` | VARCHAR(16) | 护理 / 色彩 / 艺术 / 延长 |
| `duration_min` | INT | 预计耗时（分钟） |
| `price` | INT | 价格（元） |
| `description` | VARCHAR(128) | 一句话简介 |
| `enabled` | TINYINT | 1 展示并可约 / 0 隐藏 |
| `sort_no` | INT | 排序，小的在前 |
| 审计 | DATETIME | 同上 |

**`t_stylist`** 美甲师
| 字段 | 类型 | 注释 |
| --- | --- | --- |
| `id` | BIGINT PK | 主键 |
| `name` | VARCHAR(32) | 姓名 |
| `title` | VARCHAR(32) | 头衔（如「店主 · 8 年」） |
| `phone` | VARCHAR(16) | 手机号 |
| `enabled` | TINYINT | 1 在班（可被指定）/ 0 停用 |
| `sort_no` | INT | 排序 |
| 审计 | DATETIME | 同上 |

**`t_style_case`** 美学图鉴（纯展示）
| 字段 | 类型 | 注释 |
| --- | --- | --- |
| `id` | BIGINT PK | 主键 |
| `title` / `subtitle` | VARCHAR(32) / VARCHAR(48) | 中文标题 / 英文副标题 |
| `image_url` | VARCHAR(500) | 图片地址 |
| `enabled` / `sort_no` | TINYINT / INT | 是否展示 / 排序 |
| 审计 | DATETIME | 同上 |

**`t_slot_config`** 可预约时段与容量（时段面板的数据源）
| 字段 | 类型 | 注释 |
| --- | --- | --- |
| `id` | BIGINT PK | 主键 |
| `slot_time` | CHAR(5) UNIQUE | 时刻 `HH:mm` |
| `period` | VARCHAR(16) | `MORNING` / `AFTERNOON` / `EVENING` |
| `capacity` | INT | 门店容量（人），实际名额还要和在班人数取小 |
| `enabled` | TINYINT | 1 开放预约 / 0 关闭 |
| `sort_no` | INT | 排序 |
| 审计 | DATETIME | 同上 |

**`t_appointment`** 预约单
| 字段 | 类型 | 注释 |
| --- | --- | --- |
| `id` | BIGINT PK | 主键 |
| `appointment_no` | VARCHAR(24) UNIQUE | 单号，形如 `BB20260922A1B2` |
| `customer_name` | VARCHAR(32) | 客人称呼 |
| `phone` | VARCHAR(16) | 手机号 |
| `service_id` | BIGINT | 服务项目 id（逻辑引用） |
| `service_name` / `service_price` / `duration_min` | VARCHAR(48) / INT / INT | **下单快照**：名称/价格/耗时 |
| `stylist_id` | BIGINT NULL | 指定美甲师；NULL = 不限 |
| `stylist_name` | VARCHAR(32) NULL | 姓名快照 |
| `appoint_date` / `appoint_time` | DATE / CHAR(5) | 到店日期 / `HH:mm` |
| `status` | VARCHAR(16) | PENDING / CONFIRMED / COMPLETED / CANCELLED |
| `note` | VARCHAR(200) | 备注（色系、甲况） |
| 审计 | DATETIME | 同上 |

索引：`uk_appointment_no`、`idx_appoint_date_time(appoint_date,appoint_time)`、`idx_appointment_phone`、`idx_appointment_status`、`idx_appointment_stylist(stylist_id,appoint_date,appoint_time)`。

**`t_sms_code`** 短信验证码
| 字段 | 类型 | 注释 |
| --- | --- | --- |
| `id` | BIGINT PK | 主键 |
| `phone` | VARCHAR(16) | 手机号 |
| `code` | CHAR(6) | 6 位数字 |
| `scene` | VARCHAR(16) | 业务场景，默认 `BOOKING` |
| `expire_time` | DATETIME | 过期时间 |
| `used` / `attempts` | TINYINT / INT | 是否已用 / 已尝试次数 |
| `create_time` | DATETIME | 创建时间（重发间隔按它算） |

**`t_shop_config`** 站点文案（键值对）
| 字段 | 类型 | 注释 |
| --- | --- | --- |
| `id` | BIGINT PK | 主键 |
| `cfg_key` | VARCHAR(64) UNIQUE | 配置键（`shop.name`、`about.paragraphs`…） |
| `cfg_value` | VARCHAR(1000) | 配置值 |
| `remark` | VARCHAR(200) | 这行配置干什么 |
| `update_time` | DATETIME | 更新时间 |

## 3. 前端页面清单

| 文件 | 功能 |
| --- | --- |
| `frontend/index.html` → `src/home/main.js` → `HomeApp.vue` | 用户端入口；首页六个区块（导航/首屏/品牌故事/图鉴/价格表/预约/页脚），进页拉 4 个只读接口，全部文案有兜底值 |
| `frontend/admin.html` → `src/admin/main.js` → `AdminApp.vue` | 管理端入口；登录态、侧栏、hash 视图切换、toast、删除确认、401 回登录 |
| `src/home/components/BookingForm.vue` | 预约表单总编排：称呼/手机/项目/美甲师/日期/时段 + 备注；5 条字段校验、验证码弹窗、提交、回执、再约一单 |
| `src/home/components/CalendarGrid.vue` | 自绘日历：今天起 `maxDaysAhead` 天内可选、roving tabindex、方向键 ±1/±7、PageUp/PageDown 翻月 |
| `src/home/components/TimeSlots.vue` | **时段面板**（详见第 4 节） |
| `src/home/components/BasePicker.vue` | 字段外壳：触发器 + 浮动标签 + 弹层 + 下方放不下向上弹 + 关闭后焦点还给触发器 |
| `src/home/components/OptionList.vue` | 下拉选项列表：上下键移动、回车选中、Esc 关闭 |
| `src/home/components/TextField.vue` | 文本/多行输入：浮动标签、字数限制、错误提示与抖动 |
| `src/home/components/VerifyModal.vue` | 6 格验证码弹窗：发送/倒计时/自动进位/粘贴；`/sms/verify` 只校验不消费 |
| `src/admin/views/LoginView.vue` | 账号密码登录，令牌存 localStorage |
| `src/admin/views/ServiceView.vue` | 服务项目 CRUD（弹窗表单、分类/时长下拉） |
| `src/admin/views/StylistView.vue` | 美甲师 CRUD（含在班开关） |
| `src/admin/views/AppointmentView.vue` | 预约单列表：日期范围/状态/关键字筛选、分页、状态计数、确认/完成/取消 |
| `src/admin/views/SlotView.vue` | 营业时段：每行改容量/分组/开关，展示「门店容量 vs 实际名额 vs 已约」 |
| `src/admin/views/ConfigView.vue` | 站点文案：批量编辑、统一保存、只记 dirty 数 |
| `src/admin/components/{AdminDatePicker,AdminModal,AdminSelect}.vue` | 复用 `CalendarGrid` 的日期选择器 / 通用弹窗 / 弹窗内下拉 |
| `src/api/{http,siteApi,adminApi}.js` | `fetch` 封装、错误归一（HTTP 非 2xx 与 `code!==0` 合并）、令牌存取 |

## 4. 时段面板：文件与核心交互

**文件**：`frontend/src/home/components/TimeSlots.vue`（渲染）+ `BookingForm.vue`（状态与请求）+ `frontend/src/api/siteApi.js`（`fetchSlots`）。

1. **数据不是前端算的**：`GET /api/site/slots?date=&stylistId=` 返回每档 `{time, period, periodLabel, booked, cap, state}`；前端只把 `state` 翻译成文案/样式（`TimeSlots.vue` 的 `slotText`/`slotClass`）。
2. **选日期**：`chooseDate(iso)` → `loadSlots()` → 带当前 `stylistId` 请求 → 写入 `slots`；请求失败清空并给出 `formError`。
3. **选美甲师**：`chooseStylist()` 时若已有日期**立即重拉**时段，因为「这位美甲师哪几档被占」会变（`BookingForm.vue`）。
4. **可点判定**：只有 `FREE / SOME / HOT` 可点；`PAST / CLOSED / FULL / TAKEN` 一律 `disabled`。
5. **TAKEN 的真实含义**：总名额没满，但所选美甲师这一档已被别人占 → `TAKEN`，在「选」的时候就看得见冲突，避免填完验证码才失败（`SlotService.availability(date, stylistId)`）。
6. **选中之后**：`emit('select', slot.time)` → `chooseTime` 记住 `time` 并关闭弹层；弹层内与弹层外各有一处常驻提示，显示「当前 N 人预约 / 剩 M 席」，`HOT` 时提示「可能已被抢先」。
7. **状态失效自动清理**：日期/美甲师变化后 `pruneSelectedTime()` 检查已选时段是否仍为可点状态，否则清空，避免留着一个实际选不动的值。
8. **无障碍**：roving tabindex（只有一个 `tabindex=0`）、左右 ±1、上下 ±3（每行 3 个）、`aria-pressed`、`aria-label` 拼「时间 + 状态」。
9. **空态文案分三种**：全是 `TAKEN` → 建议换美甲师/换天；全是 `CLOSED/PAST` → 该日无可约；否则 → 已约满。

## 5. 预约相关后端接口

> 来源：`backend/src/main/java/com/artnail/nail/web/{BookingController,SiteController,SmsController,AdminAppointmentController}.java` 与 `service/{BookingService,SlotService,SmsService}.java`；URL 均已核对 `@RequestMapping` + 方法注解。

| 方法 + URL | 入参 | 出参 | 内部逻辑要点 |
| --- | --- | --- | --- |
| `POST /api/bookings` | `BookingCreateRequest`：`name`(≤20)、`phone`(11 位)、`serviceId`、`stylistId`(可空)、`date`、`time`(HH:mm)、`note`(≤200)、`code`(6 位) | `BookingResult`：单号、状态 + 中文 label、称呼、手机、项目名/价/时长、美甲师、日期、时刻、备注、创建时间 | `BookingService.create`(:57)：验证码**校验并消费** → 项目在售/美甲师在班 → 日期在 `maxDaysAhead` 内 → 时段开放且状态非 PAST/CLOSED/FULL → 指定美甲师无撞档 → 同手机号当天未超限 → 写快照 + `PENDING`；整体 `@Transactional` |
| `GET /api/bookings/lookup?no=&phone=` | 单号 + 手机号 | 同 `BookingResult` | `BookingService.lookup`：两个条件都 eq 才返回，404 文案统一「没有查到这张预约单」 |
| `GET /api/site/slots?date=&stylistId=` | ISO 日期、可选美甲师 | `SlotDayView{date, stylistCount, slots[]}` | `SlotService.availability(:90)`：`cap=min(容量, 在班人数)`；状态 `state(:212)` 判定顺序 PAST → CLOSED → FULL → HOT → SOME → FREE；指定美甲师时叠加 `TAKEN` |
| `POST /api/sms/code` | `{phone}` | `{demo, demoCode, expireSeconds, resendSeconds, message}` | `SmsService.send`：60 秒重发间隔 → 每日 10 次 → 落库 → 演示模式回显 |
| `POST /api/sms/verify` | `{phone, code}` | `null` | `SmsService.check`：只校验不消费，给弹窗「确认并提交」用 |
| `GET /api/admin/appointments` | `from/to/status/keyword/page/size` | `{items,total,page,size,counts}` | `BookingService.adminList`：动态条件 + 按到店时间正序 + 当前范围状态计数 |
| `PUT /api/admin/appointments/{id}/status` | `{status}` PENDING/CONFIRMED/COMPLETED/CANCELLED | `AppointmentView` | `updateStatus`：仅改状态字段；**取消后名额自动释放**（统计只算 PENDING/CONFIRMED） |

补充规则：`AppointmentStatus.activeCodes()` 只含 PENDING + CONFIRMED，取消/完成不占名额；单号 `BB + yyyyMMdd + 4 位随机数`，撞唯一索引就重摇 5 次。

## 6. 换成 JPA 时**不能照抄**的写法

1. **实体注解**：`@TableName("t_appointment")` / `@TableId(type = IdType.AUTO)` → 必须换 `@Entity` / `@Table(name = "t_appointment")` / `@Id @GeneratedValue(strategy = IDENTITY)`。
2. **Mapper 继承**：`interface XxxMapper extends BaseMapper<Xxx>` 的空接口在 JPA 下不存在 → 换 `JpaRepository<Xxx, Long>`（需要动态条件再加 `JpaSpecificationExecutor`）。
3. **`LambdaQueryWrapper` 链式查询**：`new LambdaQueryWrapper<Appointment>().eq(...).ge(...).in(...).like(...).orderByAsc(...)` 是 MyBatis-Plus 专属，JPA 用派生查询、`@Query` 或 `Specification`；`wrapper.and(w -> w.like(...).or().like(...))` 的分组语义要显式写成 `cb.or(...)`。
4. **分页**：`selectPage(new Page<>(pageNo, pageSize), wrapper)` + `PaginationInnerInterceptor` + `setMaxLimit(200)` → JPA 用 `Pageable`/`PageRequest`（`Pageable.ofSize` 上限需自己 clamp），页大小上限不再由插件兜底。
5. **`selectCount` 返回 `Long` 且可能为 null**：代码里到处 `count != null && count > 0`，JPA `count()` 返回 `long`，这层判空可以删，但**不要**无脑保留（会误导后续读者以为 JPA 也返回空）。
6. **`updateById(entity)` 的语义**：MyBatis-Plus 默认只更新非 null 字段；JPA `save` 是整实体合并/脏检查。`BookingService.updateStatus` 先查再改再 `updateById`，迁到 JPA 后应改成「查出来 set 状态，事务提交自动 flush」，不要「new 一个只带 id+status 的对象去 save」（会把其它字段写成 null）。
7. **列裁剪 `select(Appointment::getStatus)`**：MyBatis-Plus 可以只查一列；JPA 要么查整实体，要么用接口投影/`@Query` 构造器表达式，不能照搬。
8. **布尔映射**：`Boolean enabled` 对应 `TINYINT`，MyBatis 自动转换；JPA 下 `Boolean` 默认映射 `bit(1)`，接既有 `TINYINT` 列需显式 `@Column(columnDefinition = "tinyint")` 或在 DDL 里改成 `bit`/`boolean`，否则可能出现取值异常。
9. **无外键 + 只有 id 字段**：实体里 `serviceId`/`stylistId` 是裸 `Long`，**不要**顺手改成 `@ManyToOne`——参考库本身刻意不做外键与级联，改成关联会引入隐式 join、懒加载和删除约束，破坏「删项目不影响历史单」的设计。
10. **表名策略**：MyBatis-Plus 靠 `@TableName` 显式给 `t_` 前缀；JPA 默认命名策略不会加前缀，每个实体都要写 `@Table(name = "t_xxx")`，并建议 `ddl-auto=none/validate`，继续用现成 `schema.sql` 而不是让 Hibernate 建表。
11. **配置项**：`mybatis-plus.configuration.map-underscore-to-camel-case`、`global-config.db-config.id-type`、`MybatisPlusConfig` 整个类都不再需要；替换为 `spring.jpa.hibernate.naming.physical-strategy`（默认已是 snake_case）与 `spring.jpa.open-in-view=false`。
12. **可以保留的**：`@Transactional`、`ApiEnvelope` 响应壳、`ApiException`/`ApiExceptionHandler`、`record` DTO、`AppointmentStatus` 枚举与 `activeCodes()`、`SlotService.state()` 的状态机——这些与持久层无关。
