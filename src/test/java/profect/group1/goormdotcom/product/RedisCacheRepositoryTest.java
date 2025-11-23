package profect.group1.goormdotcom.product;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import profect.group1.goormdotcom.product.repository.RedisCacheRepository;
import profect.group1.goormdotcom.product.repository.entity.ProductListItemEntity;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisCacheRepository 메서드 테스트")
class RedisCacheRepositoryTest {

    @InjectMocks
    private RedisCacheRepository repository;

    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ObjectMapper objectMapper;

    @Mock private ValueOperations<String, String> valueOperations;

    @Test
    @DisplayName("getCartProductListItemsBulk: multiGet null/empty면 빈 리스트")
    void getBulk_returnsEmptyOnNullOrEmpty() {
        // given
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.multiGet(anyList())).thenReturn(null);

        // when
        List<ProductListItemEntity> resultNull = repository.getCartProductListItemsBulk(ids);
        assertThat(resultNull).isEmpty();

        // and when
        when(valueOperations.multiGet(anyList())).thenReturn(Collections.emptyList());
        List<ProductListItemEntity> resultEmpty = repository.getCartProductListItemsBulk(ids);

        // then
        assertThat(resultEmpty).isEmpty();
    }

    @Test
    @DisplayName("getCartProductListItemsBulk: non-null 값만 역직렬화하여 반환, 키 순서대로 조회")
    void getBulk_deserializesNonNullAndSkipsNulls() throws Exception {
        // given
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();
        List<UUID> ids = List.of(id1, id2, id3);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // multiGet은 요청 키 순서대로 값을 반환 (중간 null 포함)
        when(valueOperations.multiGet(anyList())).thenAnswer(inv -> {
            List<String> keys = inv.getArgument(0);
            // 키 순서 검증은 아래에서 captor로 확인
            return Arrays.asList("{\"a\":1}", "{\"b\":2}", null);
        });

        // 역직렬화 스텁 (내용은 중요치 않음)
        when(objectMapper.readValue(anyString(), eq(ProductListItemEntity.class)))
                .thenReturn(new ProductListItemEntity());

        // when
        List<ProductListItemEntity> result = repository.getCartProductListItemsBulk(ids);

        // then: null 하나 건너뛰고 2개만 반환
        assertThat(result).hasSize(2);

        // multiGet 호출 키가 기대 프리픽스 + id 형태인지 확인
        ArgumentCaptor<List<String>> keyCaptor = ArgumentCaptor.forClass(List.class);
        verify(valueOperations).multiGet(keyCaptor.capture());
        List<String> usedKeys = keyCaptor.getValue();
        assertThat(usedKeys).containsExactly(
                "product-list-item:cart:" + id1,
                "product-list-item:cart:" + id2,
                "product-list-item:cart:" + id3
        );
    }

    @Test
    @DisplayName("putCartProductListItemsBulk: JSON 직렬화 후 setEx 파이프라인 호출")
    void putBulk_serializesAndPipelinesWithTTL() throws Exception {
        // given
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        ProductListItemEntity e1 = new ProductListItemEntity(id1, "A", 100, "u1", "AVAILABLE");
        ProductListItemEntity e2 = new ProductListItemEntity(id2, "B", 200, "u2", "SOLD_OUT");
        List<ProductListItemEntity> list = new ArrayList<>();
        list.add(e1);
        list.add(e2);

        when(objectMapper.writeValueAsString(eq(e1))).thenReturn("J1");
        when(objectMapper.writeValueAsString(eq(e2))).thenReturn("J2");

        // Redis 파이프라인 환경 설정
        StringRedisSerializer serializer = new StringRedisSerializer();
        when(redisTemplate.getStringSerializer()).thenReturn(serializer);

        RedisConnection connection = mock(RedisConnection.class);
        RedisStringCommands strCmd = mock(RedisStringCommands.class);
        when(connection.stringCommands()).thenReturn(strCmd);

        when(redisTemplate.executePipelined(any(RedisCallback.class))).thenAnswer(inv -> {
            RedisCallback<?> cb = inv.getArgument(0);
            cb.doInRedis(connection); // 파이프라인 내부 로직 실행
            return List.of();
        });

        // when
        repository.putCartProductListItemsBulk(list, Duration.ofSeconds(60));

        // then: setEx가 각 항목에 대해 1회씩 호출되었는지 검증
        ArgumentCaptor<byte[]> kCap = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<Long> ttlCap = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<byte[]> vCap = ArgumentCaptor.forClass(byte[].class);

        verify(strCmd, times(2)).setEx(kCap.capture(), ttlCap.capture(), vCap.capture());

        List<String> gotKeys = kCap.getAllValues().stream()
                .map(b -> new String(b, StandardCharsets.UTF_8))
                .toList();
        List<String> gotVals = vCap.getAllValues().stream()
                .map(b -> new String(b, StandardCharsets.UTF_8))
                .toList();

        // 키/값 조합을 집합으로 비교(순서 무관)
        Set<String> pairs = new HashSet<>();
        for (int i = 0; i < gotKeys.size(); i++) {
            pairs.add(gotKeys.get(i) + "=" + gotVals.get(i));
        }

        Set<String> expected = Set.of(
                "product-list-item:cart:" + id1 + "=" + "J1",
                "product-list-item:cart:" + id2 + "=" + "J2"
        );

        assertThat(pairs).isEqualTo(expected);
        assertThat(new HashSet<>(ttlCap.getAllValues())).isEqualTo(Set.of(60L));
    }
}
