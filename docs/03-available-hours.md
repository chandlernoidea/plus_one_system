# 03 · AppointmentScheduler · `getAvailableHours` 完全拆解

> 路径前缀：`src/main/java/com/example/slabiak/appointmentscheduler/`（下文简写为 `…/`）。
> 该项目是 Spring Boot 2.2.6 + JDK 8 + `javax.persistence`，**代码可读、技术栈不可直接复用**；本文只抽取算法与数据结构。
> 方法签名：`List<TimePeroid> getAvailableHours(int providerId, int customerId, int workId, LocalDate date)`（`…/service/AppointmentService.java:43`）。

## 1. 排除链（按实际执行顺序）

| # | 排除什么 | 代码位置 | 说明 |
| --- | --- | --- | --- |
| 0 | 取服务者的周排班 | `AppointmentServiceImpl.getAvailableHours:97` → `userService.getProviderById(providerId).getWorkingPlan()` | `Provider` 与 `WorkingPlan` 是 1:1 |
| 1 | 取「这一天是星期几」的日计划 | `WorkingPlan.getDay(:77)`，入参 `date.getDayOfWeek().toString().toLowerCase()` | 返回 `DayPlan`；**没有判空**，该天没配置会 NPE |
| 2 | 从工作时间中扣掉午休/休息段 | `DayPlan.timePeroidsWithBreaksExcluded:16` | 把 `workingHours` 减去 `breaks`，可能拆成多段 |
| 3 | 扣掉**该服务者**当天的预约 | `AppointmentServiceImpl.getAppointmentsByProviderAtDay:87` + `excludeAppointmentsFromTimePeroids:160` | 查询条件只有 `provider.id = ? and start ∈ [当天 00:00, 次日 00:00]`，**不过滤状态** |
| 4 | 扣掉**该客户**当天的预约 | `getAppointmentsByCustomerAtDay:92` + 同一个 `excludeAppointmentsFromTimePeroids` | 目的：一个客户同一天不能同时段跑两个店/两个项目 |
| 5 | 按项目时长切出可开始的时刻 | `calculateAvailableHours:146` | 在每个剩余区间内，从区间起点开始，每 `work.duration` 分钟切一段，要求 `起点 + 时长 <= 区间结束` |

调用链与入口：

- 页面选完「项目 → 服务者 → 日期」后，前端请求 `GET /api/availableHours/{providerId}/{workId}/{date}`（`controller/AjaxController.java:44`），把结果转成 `AppointmentRegisterForm(workId, providerId, start, end)` 列表返回。
- 下单时复用同一函数：`AppointmentServiceImpl.isAvailable:360` 先用 `workService.isWorkForCustomer(workId, customerId)` 判断项目是否对该客户类型开放，再把「开始 + 时长」构造成 `TimePeroid`，用 `getAvailableHours(...).contains(timePeroid)` 判断是否可约；`createNewAppointment` 不可约就 `throw new RuntimeException()`。

**一句话总结**：`工作时间 − 休息 − 服务者已约 − 客户已约 → 按时长切片`。README 里 1~7 步的描述与代码一致，只是把「取休息」与「扣休息」算作了两步。

## 2. 排班 / 工作时间的数据结构

### 2.1 数据库

表 `working_plans`（`src/main/resources/appointmentscheduler.sql:128`）：一人一行，**每天一列 JSON**。

```sql
CREATE TABLE IF NOT EXISTS `working_plans` (
  `id_provider` int(11) NOT NULL,
  `monday` TEXT, `tuesday` TEXT, `wednesday` TEXT, `thursday` TEXT,
  `friday` TEXT, `saturday` TEXT, `sunday` TEXT,
  PRIMARY KEY (`id_provider`)
)
```

每列的 JSON 形如（种子数据 `:313`）：

```json
{"workingHours":{"start":[6,0],"end":[18,0]},"breaks":[],"timePeroidsWithBreaksExcluded":[{"start":[6,0],"end":[18,0]}]}
```

注意 `timePeroidsWithBreaksExcluded` 是**派生结果的快照**：`DayPlan` 里没有对应 setter，运行时由 `workingHours − breaks` 现算。反序列化时这个多余键是否被忽略，取决于 hibernate-types 的 `ObjectMapper` 配置（本次未实测），迁移时不要保留这个冗余字段。

### 2.2 Java 对象

```
WorkingPlan (entity, id = providerId)
├── DayPlan monday … sunday        @Type(type="json") + JsonStringType (hibernate-types 2.4.2)
└── getDay(String day)             手写 switch，返回对应 DayPlan

DayPlan (model)
├── TimePeroid workingHours        {start: LocalTime, end: LocalTime}
├── List<TimePeroid> breaks        午休、交接等
└── timePeroidsWithBreaksExcluded()  由 workingHours 减去 breaks，返回排好序的区间列表

TimePeroid (model)
├── LocalTime start, end
├── compareTo → 按 start 升序
└── equals/hashCode → start + end 都相等
```

- 默认排班：`WorkingPlan.generateDefaultWorkingPlan()` 给周一到周日都设为 `06:00–18:00`、无休息。
- 修改入口：`WorkingPlanServiceImpl.updateWorkingPlan` 只更新 7 天的 `workingHours`，**不动 breaks**；`addBreakToWorkingPlan` / `deleteBreakFromWorkingPlan` 单独增删休息段。
- 数据粒度是 `LocalTime`（无日期、无时区），`WorkingDay`/`WorkingHours` 那套类属于 **BookingPlatform**，别和这里混。

## 3. 预约状态的生命周期

枚举：`entity/AppointmentStatus.java`，共 9 个值，实际流转用到 8 个（`DENIED` 从未赋值）。

| 状态 | 进入条件 | 由谁触发 | 代码位置 |
| --- | --- | --- | --- |
| `SCHEDULED` | 新建预约 | 客户 | `AppointmentServiceImpl.createNewAppointment` |
| `FINISHED` | `now >= appointment.end` | 定时任务（每 30 分钟） | `updateAllAppointmentsStatuses:203` + `ScheduledTasksServiceImpl:22` |
| `CONFIRMED` | `FINISHED` 且 `end <= now - 24h`（客户未提异议） | 定时任务 | 同上 |
| `INVOICED` | 已确认的单在每月 1 日 00:00 自动开票 | 定时任务（cron `0 0 0 1 * ?`） | `InvoiceServiceImpl.issueInvoicesForConfirmedAppointments:106` |
| `CANCELED` | 客户/服务者点取消；记录 `canceler` + `canceled_at` | 客户或服务者 | `cancelUserAppointmentById:230` |
| `REJECTION_REQUESTED` | 客户在 `FINISHED` 且结束后 24h 内主张「没到店」 | 客户（可用邮件链接） | `requestAppointmentRejection` / `isCustomerAllowedToRejectAppointment` |
| `REJECTED` | 服务者接受异议 | 服务者 | `acceptRejection` |
| `EXCHANGE_REQUESTED` | 发起换约 | 客户 | `ExchangeServiceImpl.requestExchange:91` |
| `DENIED` | **枚举里有，代码中从未赋值** | — | 仅 `AppointmentStatus.java:9` |

```
                  ┌────────────(客户/服务者取消)──────────► CANCELED
                  │
SCHEDULED ──时间到──► FINISHED ──24h 无异议──► CONFIRMED ──每月1日──► INVOICED
                  │        │
                  │        └──客户申请──► REJECTION_REQUESTED ──服务者接受──► REJECTED
                  │
                  └──换约请求──► EXCHANGE_REQUESTED ──拒绝/超时──► SCHEDULED
```

**要点/坑：**

- 取消规则（每月 1 次、提前 24h）**只在 `getCancelNotAllowedReason:317` 里算**，用于页面决定「取消」按钮是否可见（`AppointmentController:64`）；真正执行取消的 `POST /appointments/cancel`（`AppointmentController:165`）**不再复检**，直接 POST 可以绕过所有规则。
- `updateUserAppointmentsStatuses`（按用户更新）把 `FINISHED` 直接改成 `INVOICED`，而 `updateAllAppointmentsStatuses`（全局）改成 `CONFIRMED`，两条路径口径不一致。
- 状态流转没有统一入口（有的在 Service、有的在定时任务、有的在 `ExchangeServiceImpl`），也没有状态机校验，任何地方都能 `setStatus`。
- 与「可用时间」直接相关的一条：`getAvailableHours` 排除了**所有状态**的预约，包括 `CANCELED`、`REJECTED`。也就是说取消一单并不会把时段放回去。

## 4. 用中文伪代码重写核心逻辑

```text
函数 计算可预约时段(服务者ID, 客户ID, 项目ID, 日期) -> 时段列表:

    # 第 0~1 步：拿到当天的营业骨架
    排班 = 查询服务者的周排班(服务者ID)
    日计划 = 排班.取星期(日期.是星期几)          # 例：monday
    如果 日计划 不存在: 返回 空列表               # 原实现会 NPE，这里补上

    工作时段 = 日计划.工作时间                    # [开始时刻, 结束时刻]，只有时分

    # 第 2 步：扣掉休息（午休、交接）
    区间集合 = [工作时段]
    对 每个 休息段 in 日计划.休息段列表:
        休息段 = 休息段.裁剪到(工作时段)          # 超出营业时间的部分截掉
        区间集合 = 区间集合.减去(休息段)          # 相交则拆分，完全覆盖则整段删除
    区间集合 = 区间集合.丢弃空段().按开始时间排序()
    如果 区间集合 为空: 返回 空列表

    # 第 3~4 步：扣掉两个维度的已占区间
    服务者预约 = 查询预约(服务者=服务者ID, 开始时间 ∈ [日期 00:00, 日期+1天 00:00])
    客户预约   = 查询预约(客户=客户ID,     开始时间 ∈ [日期 00:00, 日期+1天 00:00])
                                          # 原实现不过滤状态 → 取消单也占位
    区间集合 = 区间集合.减去(服务者预约.每条的[开始, 结束])
    区间集合 = 区间集合.减去(客户预约.每条的[开始, 结束])
    区间集合 = 区间集合.丢弃空段().按开始时间排序()

    # 第 5 步：按项目时长切片
    时长 = 查询项目(项目ID).时长分钟
    结果 = []
    对 每个 区间 in 区间集合:
        起点 = 区间.开始
        当 起点 + 时长 <= 区间.结束:               # 正好贴到结束也算可约
            结果.添加([起点, 起点 + 时长])
            起点 = 起点 + 时长                    # 步长 = 项目时长，不是固定 30 分钟
    返回 结果
```

**另一个入口（下单校验）**：`是否可约 = 计算可预约时段(...).包含([开始, 开始+时长])`。所以「可用时段列表」和「下单校验」共用同一份逻辑，不会出现前端能选、后端拒单的错位——这一点值得保留。

## 5. 迁移到本项目时必须补的坑

1. **过去时间没排除**：函数不看 `now`，当天上午就能约当天 06:00；日期选更早的过去日期也会返回时段。必须加 `时刻 > now + 最小提前量`。
2. **状态没过滤**：`findByProviderIdWithStartInPeroid` / `findByCustomerIdWithStartInPeroid`（`dao/AppointmentRepository.java:26,29`）没有 `status` 条件，取消/驳回的单仍占位。应只排除「占位状态」。
3. **区间减法实现有边界缺陷**（`excludeAppointmentsFromTimePeroids:160`，只读代码推断，未跑测试）：
   - 当一条预约**完全覆盖**某个区间时，第二个 `if` 的判断因 `||` 优先级会退化为「只要 `预约.end == 区间.end` 就截断」，可能产生 `end < start` 的非法区间；若 `预约.end > 区间.end`，该区间根本不会被扣掉。
   - `toAdd` 的新增段在所有预约处理完后才并回 `peroids`，所以**后一条预约不会与刚拆出的段比较**，多条预约时可能残留重叠。
   - 建议改为「收集所有占用区间 → 排序 → 线性扫描做差集」的独立工具方法，并补单测。
4. **`LocalTime` 没有日期**：跨天营业（如 22:00–02:00）无法表达；`WorkingDay` 那类「同星期 + 起止比较」也不支持跨天。本项目若只做 10:00–21:00 可以沿用，最好统一用 `LocalDateTime`。
5. **休息段裁剪有副作用**：`DayPlan.timePeroidsWithBreaksExcluded` 会直接 `break.setStart/setEnd` 修改传入的 break 对象，而它们来自 JPA 实体集合，等于改了待持久化数据。新实现应返回新对象。
6. **并发无保护**：两个客户同时查同一空档都能看到可约，落库时没有唯一约束/锁。需要「同一美甲师 + 时间区间」的数据库级约束或悲观锁。
7. **粒度**：原实现按「项目时长」步进（60 分钟项目 → 10:00、11:00、12:00…），而美甲场景更常见「30 分钟对齐 + 按项目时长占用多档」。这一点要结合 nail 项目的 `t_slot_config` 设计重新取舍。
8. **性能**：每次都把某个服务者/客户**全部历史**预约捞进内存再过滤；应按 `[日期起, 日期止)` + 状态建索引查询。
