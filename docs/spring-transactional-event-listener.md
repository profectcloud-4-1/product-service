# Spring Transactional Event Listener 기능

## ✅ Spring Framework 표준 기능입니다!

`ApplicationEventPublisher`와 `@TransactionalEventListener`는 **Spring Framework의 공식 기능**입니다.

## 📚 Spring Framework 버전

- **`ApplicationEventPublisher`**: Spring Framework 1.0부터 존재 (오래된 기능)
- **`@TransactionalEventListener`**: Spring Framework **4.2**부터 추가됨 (2015년)
- 현재 프로젝트는 Spring Boot 3.5.6 사용 중

## 🔧 자동 설정 및 동작 원리

### 1. ApplicationEventPublisher 자동 주입

```java
@Service
@RequiredArgsConstructor
public class AdjustStockService {
    // Spring이 자동으로 주입해줌 (별도 설정 불필요)
    private final ApplicationEventPublisher eventPublisher;
}
```

**동작 원리:**
- `ApplicationContext`가 `ApplicationEventPublisher` 인터페이스를 구현
- Spring이 자동으로 빈을 주입
- 별도의 설정이나 Bean 등록 불필요

### 2. @TransactionalEventListener 자동 감지

```java
@Component
public class EventHandler {
    // Spring이 자동으로 이 메서드를 감지하고 등록
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleStockRollbackCompletedEvent(StockRollbackCompletedEvent event) {
        // ...
    }
}
```

**동작 원리:**
- Spring의 `EventListenerMethodProcessor`가 `@TransactionalEventListener`를 스캔
- `TransactionSynchronizationManager`를 통해 현재 트랜잭션과 이벤트를 연결
- 트랜잭션 완료 후 적절한 phase에서 실행

### 3. @EnableAsync 설정

```java
@EnableAsync  // ← 이 어노테이션만 있으면 됨
@SpringBootApplication
public class GoormdotcomApplication {
    // ...
}
```

**동작 원리:**
- `@EnableAsync`가 `@Async` 메서드를 비동기로 실행
- 별도의 ThreadPoolTaskExecutor 설정 없으면 기본 스레드 풀 사용
- 커스텀 설정도 가능 (AsyncConfig 참고)

## 🎯 Spring 내부 동작 흐름

### 1. 이벤트 발행 시점

```java
// AdjustStockService.tryIncreaseStocks() 내부
eventPublisher.publishEvent(new StockRollbackCompletedEvent(orderId));
```

**Spring 내부 처리:**
1. `ApplicationEventPublisher`는 실제로 `ApplicationContext` 구현체
2. `publishEvent()` 호출 시:
   - 현재 활성 트랜잭션이 있는지 확인 (`TransactionSynchronizationManager.isSynchronizationActive()`)
   - 트랜잭션이 있으면 → `TransactionSynchronization`에 이벤트 등록
   - 트랜잭션이 없으면 → 즉시 이벤트 처리

### 2. 트랜잭션 완료 후 이벤트 처리

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Async
public void handleStockRollbackCompletedEvent(...) {
    // 트랜잭션 커밋 후 실행됨
}
```

**Spring 내부 처리:**
1. 트랜잭션 커밋/롤백 시점에 `TransactionSynchronization` 콜백 실행
2. `TransactionPhase`에 따라:
   - `AFTER_COMMIT`: 커밋 후 실행
   - `AFTER_ROLLBACK`: 롤백 후 실행
   - `BEFORE_COMMIT`: 커밋 전 실행
   - `AFTER_COMPLETION`: 완료 후 실행 (커밋/롤백 모두)

## 📖 공식 문서

### Spring Framework 공식 문서

1. **ApplicationEventPublisher**
   - [Spring Framework Docs - ApplicationEventPublisher](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events)
   - Spring 1.0부터 존재하는 표준 기능

2. **@TransactionalEventListener**
   - [Spring Framework Docs - Transactional Event Listener](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/transactional-event-listener.html)
   - Spring 4.2부터 추가됨

3. **TransactionSynchronizationManager**
   - [Spring Framework API - TransactionSynchronizationManager](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/support/TransactionSynchronizationManager.html)
   - 트랜잭션 컨텍스트 관리

## 🔍 코드에서 확인할 수 있는 부분

### 1. ApplicationEventPublisher는 ApplicationContext

```java
// Spring 내부적으로
public interface ApplicationContext extends ApplicationEventPublisher {
    // ApplicationContext가 ApplicationEventPublisher를 상속
}
```

### 2. TransactionSynchronizationManager 사용

Spring 내부에서 다음과 같이 동작:

```java
// Spring 내부 코드 (의사 코드)
if (TransactionSynchronizationManager.isSynchronizationActive()) {
    // 트랜잭션이 활성화되어 있음
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // AFTER_COMMIT phase 실행
                eventHandler.handleStockRollbackCompletedEvent(event);
            }
        }
    );
} else {
    // 트랜잭션이 없으면 즉시 실행
    eventHandler.handleStockRollbackCompletedEvent(event);
}
```

## ✅ 결론

1. **Spring Framework 표준 기능**입니다
2. **별도 라이브러리나 설정 불필요**합니다
3. **자동으로 동작**합니다:
   - `ApplicationEventPublisher` 자동 주입
   - `@TransactionalEventListener` 자동 감지
   - 트랜잭션과 이벤트 자동 연결
4. **Spring 4.2 이상**에서 사용 가능 (현재 프로젝트는 Spring Boot 3.5.6)

## 🎓 추가 학습 자료

- [Spring Events - Baeldung](https://www.baeldung.com/spring-events)
- [Spring Transaction Events - Baeldung](https://www.baeldung.com/spring-transaction-events)
- [Spring Framework GitHub - TransactionalEventListener](https://github.com/spring-projects/spring-framework/blob/main/spring-tx/src/main/java/org/springframework/transaction/event/TransactionalEventListener.java)

