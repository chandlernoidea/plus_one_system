# 00 · 三个参考项目总览

> 调研方式：只读源码、构建文件、SQL 脚本与文档，**未运行任何项目**（本机 JDK 17 / Maven 3.10 / Node 24 / MariaDB 客户端）。
> 调研时间：2026-10-20。下文所有结论都标了来源路径，便于逐个核对。
> 三个仓库均为干净工作区（`git status --porcelain` 无输出），本阶段未改动它们。

## 1. 一眼对比

| 项目 | 定位 | 后端栈 | 前端/视图 | 存储 | 端口 | 规模 |
| --- | --- | --- | --- | --- | --- | --- |
| `nail-appointment-app/` | 美甲店在线预约（用户端 + 管理端），完整可演示 | JDK 17 / Spring Boot 3.4.6 / MyBatis-Plus 3.5.17 | Vue 3.5 + Vite 8，双 HTML 入口，无路由库 | MySQL（`schema.sql`，8 表 + 种子数据） | 8080 / 5173 | 43 个主 Java 类 + 6 个测试类；前端 18 个 `.vue` + 5 个 `.js` |
| `BookingPlatform/` | 通用预约平台（机构/专家/服务），偏 DDD 学习项目 | JDK 21 / Spring Boot 3.5.12 / Spring Data JPA | SvelteKit 2 + Svelte 5 + Tailwind 4 | MySQL（无 SQL 脚本，`ddl-auto=update`） | 8085 / 5173 | 110 个主 Java 类；UI 约 20 个路由/组件 |
| `AppointmentScheduler/` | 服务者-客户预约 + 排班 + 发票 | JDK 8 / Spring Boot 2.2.6 / JPA（`javax.*`） | Thymeleaf + jQuery + FullCalendar | MySQL（`appointmentscheduler.sql`，含种子） | 8080 | 89 个主 Java 类 |

来源：各项目 `pom.xml`、`package.json`、`README.md`；目录计数为本机 `find` 结果。

---

## 2. nail-appointment-app

### 技术栈
- 后端：Spring Boot 3.4.6、MyBatis-Plus 3.5.17（`mybatis-plus-spring-boot3-starter` + `mybatis-plus-jsqlparser`）、MySQL Connector/J 8.0.33、Lombok、Jakarta Validation、Spotless（palantirJavaFormat）。
- 前端：Vue 3 Composition API + Vite 8；**无 vue-router、无 Pinia**，两个 HTML 入口各自挂一个根组件；样式直接从原型图抽出的 `home.css` / `admin.css`。
- 安全：未引入 Spring Security，自研 PBKDF2 口令 + HMAC 无状态令牌 + `HandlerInterceptor` 鉴权。
- 来源：`nail-appointment-app/backend/pom.xml`、`frontend/package.json`、`README.md` 技术栈表。

### 目录结构（只列关键层）
```
nail-appointment-app/
├── backend/doc/schema.sql              # 建库 nail + 8 表 + 初始数据
├── backend/src/main/java/com/artnail/nail/
│   ├── config/    AdminAuthInterceptor, MybatisPlusConfig, NailProperties, WebConfig
│   ├── entity/    8 个 @TableName 实体
│   ├── mapper/    8 个 extends BaseMapper<T> 的接口（无 XML、无注解 SQL）
│   ├── service/   AdminAuthService, BookingService, CatalogService, ShopConfigService, SlotService, SmsService
│   ├── support/   AppointmentStatus, CryptoSupport
│   └── web/       9 个 Controller + dto/{AdminDtos,ApiEnvelope,BookingDtos,SiteDtos}
├── backend/src/main/resources/application.yml（+ application-dev.yml）
├── backend/src/test/java/            # MybatisPlusTestSupport + 5 个测试类
├── frontend/index.html / admin.html  # 用户端 / 管理端两个入口
├── frontend/src/{api,home,admin,styles}/
├── 原型图/                            # 两份只读设计稿
└── scripts/{start-dev,format,smoke,ui-smoke}.ps1
```

### 最小启动步骤（从 README + 配置文件提取）
1. 建库：`mysql -uroot -p123456 < backend/doc/schema.sql`（要 MySQL 5.6+，脚本刻意兼容 5.6）。
2. 后端：`cd backend && mvn spring-boot:run` → <http://127.0.0.1:8080>；首次启动若 `t_admin_user` 为空，按配置创建 `admin/123456`。
3. 前端：`cd frontend && npm install && npm run dev` → <http://127.0.0.1:5173>（用户端）、`/admin.html`（管理端）；`/api` 由 Vite 代理到 8080。
- 一键脚本 `scripts/start-dev.ps1` **仅适用于 Windows PowerShell**（会预检、自动建库、双窗口启动、探活）；Linux/macOS 需按上面 3 步手动来。
- 可覆盖项：`MYSQL_*`、`ARTNAIL_ADMIN_*`、`ARTNAIL_TOKEN_SECRET`、`ARTNAIL_SMS_DEMO`；默认口令 `root/123456` 硬编码在 `application.yml`。

---

## 3. BookingPlatform

### 技术栈
- 后端：Spring Boot 3.5.12、Spring Data JPA/Hibernate、Spring Security + JWT（oauth2-resource-server）、MySQL、ModelMapper、Lombok、springdoc-openapi；`spring-kafka`（**默认已排除自动配置**）；Testcontainers（测试）。
- 前端：SvelteKit 2 / Svelte 5 + Tailwind 4 + Flowbite；`BookingPlatformUI`，默认 dev 端口 5173（CORS 允许 3000/5173）。
- 来源：`BookingPlatform/BookingServer/pom.xml`、`src/main/resources/application.properties`、`BookingPlatformUI/package.json`。

### 目录结构（按限界上下文分包，DDD 四层）
```
BookingPlatform/
├── BookingServer/
│   ├── docker-compose.yml             # 只有 Kafka + Zookeeper，没有 MySQL
│   └── src/main/java/com/server/
│       ├── booking/{api,application,domain,events,infrastructure/kafka}
│       ├── schedule/{api,application,domain,infrastructure}
│       ├── service/{api,application,domain,infrastructure}
│       ├── organization/{api,application,domain,infrastructure}
│       └── shared/{api,app,infrastructure/{email,security}}
├── BookingPlatformUI/src/{routes,lib}  # SvelteKit 路由 + api 封装
└── docs/01..06*.md                     # 6 篇设计文档（愿景/核心域/上下文/领域模型/生命周期/架构）
```
- 每个上下文都是「domain 纯 POJO + infrastructure 的 JpaEntity/Mapper/Adapter 双实体」：如 `booking/domain/Booking` 与 `booking/infrastructure/BookingJpaEntity` 分离。

### 最小启动步骤（README 几乎没写，以下从配置反推）
1. 准备 MySQL 8，建库 `booking_app`（Gradle/Maven 都不会自动建库；表由 `spring.jpa.hibernate.ddl-auto=update` 生成，**无 schema 脚本、无种子数据**）。
2. 设置环境变量：`DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`JWT_SECRET`、`MAIL_USERNAME`、`MAIL_PASSWORD`（`application.properties` 里是 `${...}` 占位，缺任一项启动即失败）。
3. 后端：`cd BookingPlatform/BookingServer && ./mvnw spring-boot:run` → 8085；Swagger 在 `/swagger-ui.html`。Kafka 已被 `spring.autoconfigure.exclude=...KafkaAutoConfiguration` 全局关掉。
4. 前端：`cd BookingPlatform/BookingPlatformUI && npm install && npm run dev`。
- 根 `README.md` 只有 4 行简介 + 技术名词清单，**没有启动步骤**；`BookingPlatformUI/README.md` 是 SvelteKit 脚手架默认模板。
- `pom.xml` 要求 `java.version=21`，本机 JDK 17 无法直接编译。

---

## 4. AppointmentScheduler

### 技术栈
- Spring Boot 2.2.6.RELEASE、JDK 8（`<java.version>1.8</java.version>`）、Spring Data JPA（`javax.persistence`）、Spring Security、Thymeleaf、JJWT 0.9.1、Flying Saucer（发票 PDF）、hibernate-types-52（JSON 列）、MySQL、FullCalendar/jQuery/Bootstrap（静态资源）。
- 测试：JUnit + Mockito + Testcontainers（MySQL）+ Selenium（UI IT）。
- 来源：`AppointmentScheduler/pom.xml`、`README.md`。

### 目录结构
```
AppointmentScheduler/src/main/java/com/example/slabiak/appointmentscheduler/
├── controller/   Appointment, Ajax, Customer, Provider, Work, Invoice, Notification, Exchange, Home
├── dao/          AppointmentRepository(JPQL), WorkingPlanRepository, WorkRepository, user/*
├── entity/       Appointment, AppointmentStatus, WorkingPlan, Work, Invoice, user/{User,Provider,Customer}
├── model/        TimePeroid, DayPlan, AppointmentRegisterForm, AppointmentSerializer
├── service/      + impl/（AppointmentServiceImpl 是核心，含 getAvailableHours）
├── security/     WebSecurityConfig, JWT, CustomUserDetails
├── validation/   自定义校验注解与分组
└── util/         PdfGeneratorUtil
src/main/resources/{appointmentscheduler.sql, templates/, static/}
```

### 最小启动步骤（README 有写，但与代码有出入）
1. 建库：`create database appointmentscheduler;` 再执行 `src/main/resources/appointmentscheduler.sql`。
2. 改 `src/main/resources/application.properties`：`spring.datasource.username/password`（默认 `user/password`）、`spring.mail.*`、`app.jwtSecret`（要求 Base64）。
3. `mvn spring-boot:run` → 8080，用 `admin/qwerty123` 登录。
- **出入点**：README 说「set env variables for JDBC `dbURL`…」，但 properties 里是硬编码值 + 少量 `${}`，并未引用 `dbURL` 变量；`mailing.enabled=true`，没有可用 SMTP 时创建预约会抛异常。
- **技术栈过旧**：Boot 2.2.6 + `javax.persistence`，在 JDK 17 下大概率起不来（Spring 5.2 的 CGLIB/ASM 不支持 17 字节码），需 JDK 8/11。
- 备选：`docker-compose.yml`（`slabiak/appointmentscheduler:0.0.1-SNAPSHOT` + `mysql:5.7`），依赖镜像可拉取。

---

## 5. 「有什么 / 没有什么」对比

| 能力 | nail-appointment-app | BookingPlatform | AppointmentScheduler |
| --- | --- | --- | --- |
| 用户端页面 | ✅ Vue 3，首页 + 预约表单 | ⚠️ SvelteKit 页面齐全，但与后端字段需逐个核对 | ✅ Thymeleaf 服务端渲染 |
| 管理后台 | ✅ 5 个视图（项目/美甲师/预约单/时段/文案） | ⚠️ `/admin` 单页 | ✅ 按角色（admin/provider/customer） |
| 登录鉴权 | ✅ 自研 HMAC 令牌 + 拦截器 | ✅ JWT + RBAC + 组织级鉴权 | ✅ Spring Security + JWT |
| 多租户/多门店 | ❌ 单店 | ✅ Organization 聚合 | ❌ 单机构（仅客户类型区分） |
| 服务项目 + 时长 | ✅ | ✅ 值对象 `ServiceDuration` | ✅ `Work.duration` |
| 服务者（美甲师/专家） | ✅ | ✅ Specialist（User + 角色） | ✅ Provider |
| 营业时间 | ✅ 全局固定档位表（每 30 分钟） | ✅ 每人每周 `WorkingDay` | ✅ 每人每周 JSON `DayPlan` |
| 午休 / breaks | ❌ 用「关闭档位」近似 | ❌ `WorkingDay` 只有起止，无 breaks 字段 | ✅ `breaks` 列表，区间做差 |
| 时段粒度 | 固定 30 分钟档 | 任意分钟（按服务时长推 end） | 任意分钟，按 `work.duration` 步进 |
| 同时段多人容量 | ✅ `min(门店容量, 在班人数)` | ❌ 一人一时段 | ❌ 一人一时段 |
| 非重叠校验 | ✅ 仅对「指定美甲师」查同档 | ✅ 通用区间重叠 | ✅ 区间差集（按人 + 按客户） |
| 服务时长参与排期 | ❌ 时长只做快照，一个 150 分钟项目仍只占一个 30 分钟档 | ✅ `end = start + duration` | ✅ 按时长切分候选时段 |
| 取消策略 | ⚠️ 管理端直接改状态，无时限规则 | ✅ 客户 `<2h` 禁止；服务者不限 | ✅ 客户每月 1 次且提前 24h |
| 通知 | ❌ 只有演示短信 | ✅ Kafka 事件 + 邮件 | ✅ 邮件 + 站内通知 |
| 发票/支付 | ❌ | ❌ | ✅ 发票 + PDF |
| 数据库脚本 | ✅ `schema.sql` + 种子 | ❌ 靠 `ddl-auto` | ✅ SQL + 种子 |
| 测试 | ✅ 59 个单测（README 声明，未复跑） | ⚠️ 少量领域单测 + Testcontainers IT | ✅ 单测 + IT + Selenium |
| 容器化 | ❌ | ⚠️ 只有 Kafka 的 compose | ✅ 全栈 compose |
| 文档质量 | ✅ 详尽 README + `AGENTS.md` | ⚠️ `docs/` 有设计文档，缺启动说明 | ✅ README 完整（个别与代码不符） |

---

## 6. 对「预约制美甲系统」的一句话参考价值

- **nail-appointment-app**：这是最贴近目标场景的**骨架级参考**——表结构、接口契约、时段面板交互可以整体借用，只需把持久层从 MyBatis-Plus 换成 JPA。
- **BookingPlatform**：只值得借**领域模型 + 三条例行校验**（区间重叠、按服务时长推 end、取消时限），其 DDD 双实体分层和 Kafka 事件链路对本项目是过度设计。
- **AppointmentScheduler**：只值得借 **`getAvailableHours` 的排除链思路与排班数据结构**，代码本身技术栈过旧且边界处理有缺陷，不宜照抄。

## 7. 本次调研的可信度边界

- 所有启动步骤来自 README/构建文件**静态阅读**，未实际执行；「能否跑起来」是推断而非实测，特别是 BookingPlatform（JDK 21）与 AppointmentScheduler（JDK 8）。
- 行号引用基于当前工作区快照，后续若参考项目更新会漂移。
- 两个 Java 项目的 README 自述测试数字（59 个单测 / 53 项冒烟）未复跑，本文只当作「作者声明」。
