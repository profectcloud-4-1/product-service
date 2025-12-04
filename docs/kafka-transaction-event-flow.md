# Kafka Transaction Event Flow

## @TransactionalEventListener 동작 원리

### 핵심 개념

`@TransactionalEventListener`는 **현재 활성 트랜잭션 컨텍스트**에서 발행된 이벤트를 감지합니다.

### 실행 흐름

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. StockConsumer.handleStockRollbackRequestedEvent()            │
│    (Kafka 메시지 수신)                                            │
└────────────────────┬────────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│ 2. adjustStockService.tryIncreaseStocks(requestedQuantityMap,   │
│    orderId) 호출                                                  │
│    ┌─────────────────────────────────────────────────────────┐  │
│    │ @Transactional 어노테이션으로 트랜잭션 시작              │  │
│    │ → 새로운 트랜잭션 컨텍스트 생성                          │  │
│    └─────────────────────────────────────────────────────────┘  │
└────────────────────┬────────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│ 3. tryIncreaseStocks() 내부 실행                                 │
│    - 재고 증가 로직 실행 (DB 업데이트)                           │
│    - eventPublisher.publishEvent() 호출                         │
│      ┌───────────────────────────────────────────────────────┐  │
│      │ Spring이 현재 활성 트랜잭션에 이벤트 연결              │  │
│      │ TransactionSynchronizationManager 사용                 │  │
│      │ → 이벤트는 트랜잭션과 매핑됨                           │  │
│      │ → 아직 EventHandler는 실행되지 않음!                   │  │
│      └───────────────────────────────────────────────────────┘  │
└────────────────────┬────────────────────────────────────────────┘
                     │
         ┌───────────┴───────────┐
         │                       │
         ▼                       ▼
    [성공 케이스]          [실패 케이스]
         │                       │
         │                       │
┌────────┴────────┐      ┌────────┴────────┐
│ 트랜잭션 커밋   │      │ 예외 발생       │
│                │      │ 트랜잭션 롤백   │
└────────┬────────┘      └────────┬────────┘
         │                       │
         ▼                       ▼
┌─────────────────────────────────────────────────────────────────┐
│ 4. 트랜잭션 완료 후 EventHandler 실행                            │
│                                                                 │
│ ✅ 성공: AFTER_COMMIT phase                                     │
│    handleStockRollbackCompletedEvent() 실행                    │
│    → Kafka로 완료 이벤트 발행                                    │
│                                                                 │
│ ❌ 실패: AFTER_ROLLBACK phase                                   │
│    handleStockRollbackFailedEvent() 실행                       │
│    → Kafka로 실패 이벤트 발행                                    │
└─────────────────────────────────────────────────────────────────┘
```

### 상세 설명

#### 1. 트랜잭션 시작
```java
@Transactional  // ← 이 어노테이션이 트랜잭션을 시작
public void tryIncreaseStocks(Map<UUID, Integer> requestedQuantityMap, UUID orderId) {
    // 트랜잭션 컨텍스트가 활성화됨
}
```

#### 2. 이벤트 발행 (트랜잭션 내부)
```java
// 트랜잭션 내부에서 실행
eventPublisher.publishEvent(new StockRollbackCompletedEvent(orderId));

// 이 시점:
// - 이벤트는 현재 트랜잭션 컨텍스트에 연결됨
// - EventHandler는 아직 실행되지 않음
// - 트랜잭션이 완료될 때까지 대기
```

#### 3. 트랜잭션 완료 후 이벤트 처리

**성공 케이스:**
```java
// 트랜잭션이 성공적으로 커밋됨
// → TransactionPhase.AFTER_COMMIT
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handleStockRollbackCompletedEvent(...) {
    // 이제 실행됨!
}
```

**실패 케이스:**
```java
// 예외 발생 → 트랜잭션 롤백
// → TransactionPhase.AFTER_ROLLBACK
@TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
public void handleStockRollbackFailedEvent(...) {
    // 이제 실행됨!
}
```

### 중요한 포인트

1. **트랜잭션 컨텍스트 매핑**
   - `eventPublisher.publishEvent()`는 현재 활성 트랜잭션에 이벤트를 연결
   - Spring의 `TransactionSynchronizationManager`가 이를 관리
   - 다른 트랜잭션에서 발행된 이벤트는 감지하지 않음

2. **트랜잭션 범위**
   - `AdjustStockService.tryIncreaseStocks()`의 `@Transactional` 트랜잭션만 감지
   - 이 메서드를 호출한 상위 메서드의 트랜잭션이 있어도, 이 메서드의 트랜잭션이 우선

3. **비동기 실행**
   - `@Async`로 인해 EventHandler는 별도 스레드에서 실행
   - 트랜잭션 완료 후 비동기로 Kafka 메시지 발행

### 예시 시나리오

#### 시나리오 1: 성공 케이스
```
1. StockConsumer → tryIncreaseStocks() 호출
2. 트랜잭션 시작
3. 재고 증가 성공
4. StockRollbackCompletedEvent 발행 (트랜잭션에 연결)
5. 트랜잭션 커밋
6. AFTER_COMMIT → handleStockRollbackCompletedEvent() 실행
7. Kafka로 완료 이벤트 발행
```

#### 시나리오 2: 실패 케이스 (상품 없음)
```
1. StockConsumer → tryIncreaseStocks() 호출
2. 트랜잭션 시작
3. 상품 조회 실패 → IllegalArgumentException 발생
4. catch 블록에서 StockRollbackFailedEvent 발행 (트랜잭션에 연결)
5. 예외 재발생 → 트랜잭션 롤백
6. AFTER_ROLLBACK → handleStockRollbackFailedEvent() 실행
7. Kafka로 실패 이벤트 발행
```

### 주의사항

⚠️ **트랜잭션 전파 (Propagation)**
- `@Transactional(propagation = Propagation.REQUIRES_NEW)`를 사용하면 새로운 트랜잭션이 시작됨
- 이 경우 새로운 트랜잭션 컨텍스트에 이벤트가 연결됨

⚠️ **트랜잭션 없이 이벤트 발행**
- 트랜잭션이 없는 상태에서 `publishEvent()`를 호출하면:
  - `@TransactionalEventListener`는 이벤트를 감지하지 않음
  - 대신 `@EventListener`를 사용해야 함

### 참고 자료

- [Spring Transaction Event Listener](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/transactional-event-listener.html)
- [TransactionSynchronizationManager](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/support/TransactionSynchronizationManager.html)

