package com.shop.orderservice.outbox;

import com.shop.orderservice.entity.OutboxMessage;
import com.shop.orderservice.entity.OutboxStatus;
import com.shop.orderservice.repository.OutboxMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final OutboxMessageRepository outboxMessageRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${app.kafka.orders-topic}")
    private String ordersTopic;

    @Transactional
    @Scheduled(fixedDelay = 1000)
    public void publishNewMessages() {
        List<OutboxMessage> messages =
                outboxMessageRepository.findTop100ByStatusOrderByCreatedAt(OutboxStatus.NEW);

        if (messages.isEmpty()) {
            return;
        }

        log.info("Outbox: found {} NEW messages", messages.size());

        for (OutboxMessage msg : messages) {
            try {
                kafkaTemplate.send(
                        ordersTopic,
                        msg.getAggregateId().toString(),
                        msg.getPayload() //шлём уже готовый JSON
                );
                msg.setStatus(OutboxStatus.SENT);
            } catch (Exception e) {
                log.error("Failed to send outbox message id={}", msg.getId(), e);
                msg.setStatus(OutboxStatus.FAILED);
            }
        }
    }
}



