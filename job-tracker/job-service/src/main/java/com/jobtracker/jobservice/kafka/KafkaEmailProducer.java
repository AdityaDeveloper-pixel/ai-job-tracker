package com.jobtracker.jobservice.kafka;

import com.jobtracker.jobservice.config.KafkaConfig;
import com.jobtracker.jobservice.dto.EmailEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Publishes EmailEvent messages to the Kafka "email-send" topic.
 *
 * Uses recipientId as the message key, which ensures all retries for the same
 * recipient land on the same partition (preserving order per recipient).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaEmailProducer {

    private final KafkaTemplate<String, EmailEvent> kafkaTemplate;

    /**
     * Publish an email event asynchronously.
     * Returns a CompletableFuture so callers can optionally await confirmation.
     */
    public CompletableFuture<SendResult<String, EmailEvent>> publish(EmailEvent event) {
        String key = String.valueOf(event.getRecipientId());

        log.info("Publishing email event to Kafka — recipientId={}, hrEmail={} , hrName{}",
                event.getRecipientId(), event.getHrEmail(),event.getHrName());

        return kafkaTemplate.send(KafkaConfig.EMAIL_TOPIC, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish email event for recipientId={}: {}",
                                event.getRecipientId(), ex.getMessage());
                    } else {
                        log.info("Email event published — recipientId={}, partition={}, offset={}",
                                event.getRecipientId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
