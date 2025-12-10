package profect.group1.goormdotcom.kafka.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultBackOffHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class KafkaConsumerConfig {
    
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.sasl.username}")
    private String saslUsername;

    @Value("${spring.kafka.sasl.password}")
    private String saslPassword;

    @Value("${spring.kafka.local}")
    private boolean local;

    private final ProducerFactory<String, Object> producerFactory;

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // 보안설정 (MSK 사용 시 필요)
        // 로컬로 할 때는 spring.kafka.sasl.username와 spring.kafka.sasl.password를 설정하지 않음 (.env에서 설정안하면 됨.)
        if (!local) {
            props.put("security.protocol", "SASL_SSL");
            props.put("sasl.mechanism", "SCRAM-SHA-512");

            // SCRAM username과 password를 동적으로 주입
            String jaasConfig = String.format(
                "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"%s\" password=\"%s\";",
                saslUsername,
                saslPassword
            );
            props.put("sasl.jaas.config", jaasConfig);
            props.put("ssl.endpoint.identification.algorithm", "https");
        }
        
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> stockKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        KafkaTemplate<String, Object> kafkaTemplate = new KafkaTemplate<>(producerFactory);
        factory.setCommonErrorHandler(stockKafkaErrorHandler(deadLetterPublishingRecoverer(kafkaTemplate)));
        return factory;
    }

    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(
            KafkaTemplate<String, Object> kafkaTemplate
    ) {
        return new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + "-dlq", record.partition())
        );
    }

    @Bean
    public DefaultErrorHandler stockKafkaErrorHandler(
            DeadLetterPublishingRecoverer deadLetterPublishingRecoverer
    ) {
        // 최대 재시도 3번
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);

        backOff.setInitialInterval(1_000L);   // 첫 대기 1초
        backOff.setMultiplier(2.0);           // 2배씩 증가 (1s -> 2s -> 4s ...)
        backOff.setMaxInterval(10_000L);      // 최대 대기 10초

        DefaultErrorHandler errorHandler =
                new DefaultErrorHandler(deadLetterPublishingRecoverer, backOff);

        errorHandler.addRetryableExceptions(
                IllegalStateException.class
        );
        errorHandler.addNotRetryableExceptions(
//                IllegalStateException.class,
                IllegalArgumentException.class
        );

        errorHandler.setRetryListeners((record, ex, deliveryAttempt) -> {
            log.warn(
                    "[KAFKA-RETRY] attempt={} topic={} partition={} offset={} key={}",
                    deliveryAttempt,
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    record.key(),
                    ex.getClass().getSimpleName(),
                    ex.getMessage()
            );
        });

        return errorHandler;
    }


}