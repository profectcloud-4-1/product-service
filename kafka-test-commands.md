# Kafka StockConsumer 테스트 가이드

## 📋 개요
`stockRollbackRequestedEvent`가 `stock-service-topic`으로 발행되면 `StockConsumer`가 자동으로 수신하여 처리합니다.

## 🚀 실행 방법

### 1단계: Spring Boot 애플리케이션 실행

```bash
# 프로젝트 루트에서 실행
cd /Users/bagchanhyeog/product-service

# 애플리케이션 실행 (dev 프로파일)
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
```

애플리케이션이 실행되면 `StockConsumer`가 `stock-service-topic`을 구독하기 시작합니다.

### 2단계: 테스트 메시지 발행

#### 방법 A: Kafka Console Producer 사용 (로컬 Kafka 설치된 경우)

```bash
# 터미널을 새로 열고 실행
kafka-console-producer.sh \
  --bootstrap-server localhost:9092 \
  --topic stock-service-topic
```

그 다음 아래 JSON 메시지를 입력하고 Enter:

```json
{"orderId":"550e8400-e29b-41d4-a716-446655440000","items":[{"productId":"660e8400-e29b-41d4-a716-446655440002","quantity":10},{"productId":"770e8400-e29b-41d4-a716-446655440003","quantity":20}]}
```

#### 방법 B: Docker로 Kafka 실행 중인 경우

```bash
# Kafka 컨테이너 내부에서 실행
docker exec -it <kafka-container-name> kafka-console-producer.sh \
  --bootstrap-server localhost:9092 \
  --topic stock-service-topic
```

#### 방법 C: echo와 파이프 사용 (한 줄로 발행)

```bash
echo '{"orderId":"550e8400-e29b-41d4-a716-446655440000","items":[{"productId":"660e8400-e29b-41d4-a716-446655440002","quantity":10},{"productId":"770e8400-e29b-41d4-a716-446655440003","quantity":20}]}' | \
kafka-console-producer.sh \
  --bootstrap-server localhost:9092 \
  --topic stock-service-topic
```

### 3단계: 로그 확인

애플리케이션 로그에서 다음 메시지들을 확인할 수 있습니다:

```
INFO  StockConsumer - 재고 롤백 요청 이벤트 수신: message={orderId=..., items=...}
INFO  StockConsumer - 재고 롤백 요청 이벤트 파싱 완료: orderId=..., items=2
INFO  StockConsumer - 재고 롤백 처리 완료: orderId=..., products=[...]
```

## 📝 메시지 형식

`StockConsumer`가 기대하는 메시지 형식:

```json
{
  "orderId": "UUID 문자열",
  "items": [
    {
      "productId": "UUID 문자열",
      "quantity": 숫자
    }
  ]
}
```

### 예시 메시지

```json
{
  "orderId": "550e8400-e29b-41d4-a716-446655440000",
  "items": [
    {
      "productId": "660e8400-e29b-41d4-a716-446655440002",
      "quantity": 10
    },
    {
      "productId": "770e8400-e29b-41d4-a716-446655440003",
      "quantity": 20
    }
  ]
}
```

## 🔍 메시지 확인 (Consumer로 읽기)

발행된 메시지가 제대로 있는지 확인하려면:

```bash
# 처음부터 모든 메시지 읽기
kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic stock-service-topic \
  --from-beginning \
  --property print.key=true \
  --property print.value=true
```

## ⚙️ 설정 확인

- **토픽 이름**: `stock-service-topic`
- **Consumer Group ID**: `test-consumer-group`
- **Kafka 서버**: `localhost:9092` (기본값)
- **Deserializer**: `JsonDeserializer` (Map<String, Object>로 자동 변환)

## 🐛 문제 해결

### 메시지를 받지 못하는 경우

1. Kafka가 실행 중인지 확인:
   ```bash
   # 로컬 Kafka
   jps | grep Kafka
   
   # Docker
   docker ps | grep kafka
   ```

2. 토픽이 존재하는지 확인:
   ```bash
   kafka-topics.sh --list --bootstrap-server localhost:9092
   ```

3. Consumer Group의 offset 확인:
   ```bash
   kafka-consumer-groups.sh \
     --bootstrap-server localhost:9092 \
     --group test-consumer-group \
     --describe
   ```

4. 애플리케이션 로그에서 Kafka 연결 오류 확인

