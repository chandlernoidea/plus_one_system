# 02 · BookingPlatform 领域模型与校验逻辑

> 路径前缀统一省略为 `BookingServer/src/main/java/com/server/`（下文只写相对部分）。
> 该项目是一个 DDD 学习项目：`domain` 是纯 POJO，`infrastructure` 用 `JpaEntity + Mapper + Adapter` 落库，两层实体分离。
> **本项目不引入 Kafka**，第 5 节说明哪些东西不要带过来。

## 1. 领域模型

### 1.1 实体/聚合清单

| 概念 | 类 | 关键字段 | 说明 |
| --- | --- | --- | --- |
| 预约（聚合根） | `booking/domain/Booking` | `id`、`clientId`、`specialistId`、`serviceId`、`TimeSlot timeSlot`、`BookingStatus status`、`createdAt` | 构造时校验三个 id 非空；状态初值 `PENDING`；生命周期方法都在这个类里 |
| 时间段（值对象） | `booking/domain/TimeSlot` | `LocalDateTime start`、`LocalDateTime end` | `record`；构造校验非空且 `end > start`；提供 `overlaps` 与 `toDuration` |
| 预约状态 | `booking/domain/BookingStatus` | `PENDING`、`CONFIRMED`、`CANCELLED` | 仅 3 态，与 `docs/05` 里的 HOLD/EXPIRED 不一致（文档先行、代码未实现） |
| 服务项目 | `service/domain/Service` | `id`、`ServiceName name`、`organizationId`、`description`、`ServiceDuration durationMinutes`、`ServicePrice price`、`ServiceCategory category` | 构造校验全部必填；提供 `changeXxx` 方法而非 setter |
| 服务时长（值对象） | `service/domain/ServiceDuration` | `int minutes` | 构造校验 `0 < minutes <= 480` |
| 排班（每人一份） | `schedule/domain/Schedule` | `id`、`specialistId`、`List<WorkingDay> workingDays` | `isAvailable(WorkingHours)` = 任一天允许 |
| 工作日 | `schedule/domain/WorkingDay` | `DayOfWeek dayOfWeek`、`LocalTime start/end` | `allows(WorkingHours)`：同星期且 `start >= day.start` 且 `end <= day.end` |
| 工作时间（值对象） | `schedule/domain/WorkingHours` | `LocalDateTime start/end` | `overlaps` 与 TimeSlot 同构（但没被用到） |
| 机构 | `organization/domain/organizations/Organization` | 名称/地址/电话/邮箱等值对象 | 多租户边界 |
| 用户 | `organization/domain/users/User` | 账号、`UserEmail`、`UserPassword` | 专家 = User + 角色，不是独立表 |
| 机构成员 | `organization/domain/organizationMembers/OrganizationMember` | `userId`、`organizationId`、`Role` | 成员关系 |

来源：`booking/domain/*`、`schedule/domain/*`、`service/domain/*`、`docs/04-domain-model.md`。

### 1.2 关系

```
Organization 1 ── N OrganizationMember N ── 1 User
Organization 1 ── N Service
User(specialist 角色) 1 ── 1 Schedule 1 ── N WorkingDay
Booking N ── 1 User(client)      （仅存 clientId，无 JPA 关联）
Booking N ── 1 User(specialist)  （仅存 specialistId）
Booking N ── 1 Service           （仅存 serviceId）
Booking 内嵌 TimeSlot(start, end) → bookings.start_time / end_time 两列
```

关键取舍：**Booking 不持有对象引用，只存 id**；`BookingJpaEntity`（`booking/infrastructure/BookingJpaEntity.java`）把 `TimeSlot` 摊平成 `start_time`/`end_time` 两列，`BookingMapper` 负责来回转换。好处是聚合边界清楚、没有懒加载；代价是多一层映射代码。

## 2. 「非重叠预约」校验

**位置**：领域服务 `booking/domain/BookingAvailabilityService.java`，判断逻辑在值对象 `TimeSlot.overlaps`，调用点在应用服务 `booking/application/BookingService.createBooking`。

**判断方式**：半开区间相交——`start < other.end && end > other.start`，因此「上一单 10:00-10:30、下一单 10:30-11:00」**允许**（首尾相接不算冲突）。

```java
// BookingServer/src/main/java/com/server/booking/domain/TimeSlot.java:19
public boolean overlaps(TimeSlot other) {
    return start.isBefore(other.end()) && end.isAfter(other.start());
}

// BookingServer/src/main/java/com/server/booking/domain/BookingAvailabilityService.java:7
public boolean hasNoConflicts(TimeSlot requestedSlot, List<Booking> existingBookings) {
    return existingBookings.stream()
            .noneMatch(booking -> booking.getTimeSlot().overlaps(requestedSlot));
}

// BookingServer/src/main/java/com/server/booking/application/BookingService.java:99
List<Booking> existingBookings = bookingRepository.getBookingsBySpecialistId(command.specialistId());
if (!availabilityService.hasNoConflicts(requestedSlot, existingBookings)) {
    throw new IllegalStateException("Time slot already booked");
}
```

**要点与坑：**

- 只按 `specialistId` 取历史单，**没有按日期过滤**，靠 `overlaps` 在内存里筛；数据量大时要改成 `where specialist_id=? and start_time < ? and end_time > ?`。
- `getBookingsBySpecialistId` 返回**所有状态**（`JpaBookingRepository` → Spring Data 派生查询），所以 `CANCELLED` 的单子**仍然会挡住新预约**。这是一个真实缺陷，迁移时必须在查询里加状态条件。
- 没有数据库层约束（无唯一索引、无排他锁），两个并发请求可以同时通过 `hasNoConflicts`；项目文档也承认「concurrent booking attempts」是目标，但代码没实现。
- 校验顺序是先查排班再查冲突，错误类型都是 `IllegalStateException`，没有区分「不营业」与「已被占」。

## 3. 服务时长校验

三处，分别在不同层：

1. **值对象自校验**：`service/domain/ServiceDuration.java` 构造器要求 `minutes > 0` 且 `<= 480`（8 小时），否则 `IllegalArgumentException`。
2. **由开始时间推算结束时间**：`BookingService.createBooking` 第 87 行
   ```java
   LocalDateTime endTime = command.start().plusMinutes(service.getDurationMinutes().minutes());
   TimeSlot requestedSlot = new TimeSlot(command.start(), endTime);
   ```
   客户端只传 `start`，**不传 end**——end 一定由服务时长推导，避免「客户端谎报结束时间」。
3. **必须落在营业时间内**：`schedule.isAvailable(new WorkingHours(command.start(), endTime))`，最终由 `WorkingDay.allows` 判断：`start >= day.start && end <= day.end`，且 `start` 的星期几必须匹配。

**未做的校验（迁移时要补）：**

- 不校验开始时间是否对齐到某个粒度（任意分钟都能约，如 10:07）。
- 不校验 `start` 是否在过去。
- 不校验时长是否超出「闭店前最后一单」之外的其它门店规则（如清洁缓冲）。
- 文档 `docs/05-booking-lifecycle.md` 说确认时会「再次校验时长」，但 `Booking.confirmBySpecialist()` 只检查状态，没有二次校验。

## 4. 取消策略

全部在聚合根 `booking/domain/Booking.java`，应用层只做归属校验与持久化：

| 方法 | 规则 | 异常 |
| --- | --- | --- |
| `cancelByClient(now)` :52 | 已取消 → 拒绝；`now > start - 2h` → 拒绝；否则置 `CANCELLED` | `IllegalStateException`（"Booking already cancelled" / "Client cannot cancel less than 2h before"） |
| `cancelBySpecialist()` :63 | 只拦「已取消」，**无时间限制** | `IllegalStateException` |
| `confirmBySpecialist()` :71 | 只有 `PENDING` 能确认 | `IllegalStateException` |

```java
// BookingServer/src/main/java/com/server/booking/domain/Booking.java:52
public void cancelByClient(LocalDateTime now) {
    if (status == BookingStatus.CANCELLED) {
        throw new IllegalStateException("Booking already cancelled");
    }
    if (now.isAfter(timeSlot.start().minusHours(2))) {
        throw new IllegalStateException("Client cannot cancel less than 2h before");
    }
    this.status = BookingStatus.CANCELLED;
}
```

应用层（`BookingService`）与接口层（`BookingController`）的配套：

- `cancelBookingByClient(bookingId, clientId)`：先比对 `booking.getClientId() != clientId` → `AccessDeniedException`。
- `cancelBookingBySpecialist(bookingId, specialistId)`：比对 `specialistId` → `SecurityException`。
- `cancelBooking(bookingId)`（对应 `PATCH /{id}/cancel`）**不校验归属**，靠 Controller 上的 `@PreAuthorize("@orgAccessEvaluator.canManageBooking(#id)")` 兜。
- 取消只改状态，**不删除记录、不做退款、不写审计**；`docs/05` 里提到的 HOLD/EXPIRED 与「取消释放 slot」在代码里没有对应实现（因为没有 hold 概念）。
- 单测：`src/test/java/com/servertest/unitTests/BookingTest.java` 覆盖了「提前 3h 可取消 / 提前 90 分钟拒绝 / 重复取消拒绝 / 仅 PENDING 可确认」。

## 5. Kafka：明确不引入

| 组件 | 位置 | 作用 |
| --- | --- | --- |
| `BookingEventProducer` | `booking/infrastructure/kafka/` | `@ConditionalOnProperty(name="kafka.enabled", havingValue="true")`，向 `booking.created` 发事件 |
| `BookingNotificationListener` | 同上 | 消费事件、发通知邮件 |
| `KafkaTopicConfig` | 同上 | 建 topic |
| `BookingCreatedEvent` / `TimeSlotEvent` | `booking/events/` | 事件载荷 |
| `docker-compose.yml` | `BookingServer/` | 只起 Kafka + Zookeeper，**没有 MySQL** |

现状：`application.properties` 里已经 `spring.autoconfigure.exclude=...KafkaAutoConfiguration`，且 producer 是 `@Autowired(required = false)`，所以**默认根本不会发事件**。本项目不引入 Kafka，因此：

- 不要搬 `events/`、`infrastructure/kafka/`、`KafkaTemplate`、`@ConditionalOnProperty` 这套。
- 需要「下单后通知」时，直接在事务提交后同步调用通知服务（或 `@TransactionalEventListener(AFTER_COMMIT)`），不要为了解耦引入消息中间件。
- 需要保留的只有「事件载荷」这个思路：`BookingCreatedEvent(bookingId, clientId, specialistId, serviceId, timeSlot, createdAt)` 可以作为一个普通 DTO 传给通知模块。

## 6. 可借鉴 / 不要借鉴

**值得借鉴**

1. `TimeSlot` 值对象 + `overlaps` 的半开区间写法，可直接用于「美甲师同一时刻不能被两单占用」。
2. `end = start + service.duration` 的推导方式，以及「客户端不传 end」的接口设计。
3. 取消规则下沉到聚合根（`Booking.cancelByClient(now)`），而不是散在 Service 里的一堆 if。
4. `ServiceDuration` 把「时长合法范围」收进值对象，避免每个入口各写一遍。

**不要照搬**

1. **DDD 双实体分层**：`domain.Booking` + `infrastructure.BookingJpaEntity` + `BookingMapper` + 仓储接口 + 适配器，一个聚合四五个文件；对本项目的小团队是明显负担。建议只保留「JPA 实体 + 领域方法」两层，把 `overlaps` 放进实体方法或工具类。
2. **Kafka 事件链**（见第 5 节）。
3. **所有校验抛 `IllegalStateException`**：没有错误码、没有可展示文案，Controller 层只能靠 `GlobalExceptionHandler` 兜成 500/400；本项目应改为带 code 的业务异常（参考 nail 项目的 `ApiException`）。
4. **查询全部历史单再内存过滤**：应改成带时间窗 + 状态条件的查询。
5. **`docs/` 与代码不一致**：文档写 HOLD/EXPIRED/SlotHold，代码只有 PENDING/CONFIRMED/CANCELLED；不要按文档去设计表。
