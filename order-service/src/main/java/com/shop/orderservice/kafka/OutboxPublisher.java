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

/**
 * Публикатор outbox-сообщений в Kafka.
 * <p>
 * Реализация паттерна Outbox:
 * <ul>
 *     <li>Заказ и запись в outbox сохраняются в одной транзакции.</li>
 *     <li>Этот сервис периодически (каждую секунду) читает сообщения
 *     со статусом {@link OutboxStatus#NEW} из БД.</li>
 *     <li>Отправляет payload в Kafka и обновляет статус на {@link OutboxStatus#SENT}
 *     или {@link OutboxStatus#FAILED} в случае ошибки.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final OutboxMessageRepository outboxMessageRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * Топик, куда отправляются события о создании заказов.
     * Значение берётся из конфигурации {@code app.kafka.orders-topic}.
     */
    @Value("${app.kafka.orders-topic}")
    private String ordersTopic;

    /**
     * Периодическая задача публикации новых outbox-сообщений.
     * <p>
     * Работает в транзакции:
     * <ul>
     *     <li>Читает до 100 сообщений со статусом {@link OutboxStatus#NEW}.</li>
     *     <li>Пытается отправить каждое сообщение в Kafka.</li>
     *     <li>При успехе помечает сообщение как {@link OutboxStatus#SENT}.</li>
     *     <li>При ошибке помечает как {@link OutboxStatus#FAILED} и логирует исключение.</li>
     * </ul>
     * Период запуска — каждые 1000 мс.
     */
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




