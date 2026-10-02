package com.jobtracker.jobservice.config;

import com.jobtracker.jobservice.dto.EmailEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka configuration for the email pipeline.
 *
 * Topics:
 *   - email-send       : main topic — one message per email to send
 *   - email-send-dlq   : dead letter queue — permanently failed emails after max retries
 *
 * Consumer uses a DefaultErrorHandler with 3 retries (2s interval) before
 * forwarding to the DLQ topic.
 */
@Configuration
public class KafkaConfig {

    public static final String EMAIL_TOPIC = "email-send";
    public static final String EMAIL_DLQ_TOPIC = "email-send-dlq";

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    // ---- Topics ----

    @Bean
    public NewTopic emailTopic() {
        return TopicBuilder.name(EMAIL_TOPIC)
                .partitions(3)        // 3 partitions for parallel sending
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic emailDlqTopic() {
        return TopicBuilder.name(EMAIL_DLQ_TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }

    // ---- Producer ----

    @Bean
    public ProducerFactory<String, EmailEvent> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // Ensure messages are not lost — wait for all replicas to acknowledge
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        // Idempotent producer — prevents duplicate messages on retries
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, EmailEvent> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // ---- Consumer ----

    @Bean
    public ConsumerFactory<String, EmailEvent> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "email-sender-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.jobtracker.jobservice.dto");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, EmailEvent.class.getName());
        // Start reading from earliest offset if no committed offset exists
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, EmailEvent> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, EmailEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        // 3 concurrent consumers for parallel email sending
        factory.setConcurrency(3);

        // Retry failed messages 3 times with 2-second interval, then give up
        // (failed messages are forwarded to DLQ by the consumer logic)
        factory.setCommonErrorHandler(
                new DefaultErrorHandler(new FixedBackOff(2000L, 3L))
        );

        return factory;
    }


}
