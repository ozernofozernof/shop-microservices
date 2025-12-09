package com.shop.orderservice.kafka;

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

        log.info("OutboxPublisher: found {} NEW messages to send", messages.size());

        for (OutboxMessage msg : messages) {
            Long orderId = msg.getAggregateId();
            Long outboxId = msg.getId();

            try {
                log.info("OutboxPublisher: sending outboxId={} for orderId={} to topic={}",
                        outboxId, orderId, ordersTopic);

                kafkaTemplate.send(
                        ordersTopic,
                        orderId != null ? orderId.toString() : null,
                        msg.getPayload()
                );

                msg.setStatus(OutboxStatus.SENT);

                log.info("OutboxPublisher: message sent successfully, outboxId={}, orderId={}, newStatus={}",
                        outboxId, orderId, msg.getStatus());
            } catch (Exception e) {
                log.error("OutboxPublisher: failed to send outboxId={} for orderId={}",
                        outboxId, orderId, e);
                msg.setStatus(OutboxStatus.FAILED);
            }
        }
    }
}




