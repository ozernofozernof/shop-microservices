package com.shop.inventoryservice.config;

import com.shop.inventoryservice.kafka.OrderCreatedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Конфигурация Kafka-консьюмера для inventory-service.
 * <p>
 * Здесь настраиваем:
 * <ul>
 *     <li>bootstrap-серверы Kafka;</li>
 *     <li>группу консьюмера {@code inventory-service};</li>
 *     <li>JSON-десериализацию события {@link OrderCreatedEvent}.</li>
 * </ul>
 * Консьюмер использует эту конфигурацию через {@code orderKafkaListenerContainerFactory}.
 */
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    /**
     * Адрес(а) Kafka-брокера, подставляется из
     * {@code spring.kafka.bootstrap-servers}.
     */
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Фабрика Kafka-консьюмера для чтения {@link OrderCreatedEvent}.
     *
     * @return настроенный {@link ConsumerFactory} для строчного ключа и JSON-события заказа
     */
    @Bean
    public ConsumerFactory<String, OrderCreatedEvent> orderConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "inventory-service");

        JsonDeserializer<OrderCreatedEvent> valueDeserializer =
                new JsonDeserializer<>(OrderCreatedEvent.class);
        // Разрешаем десериализацию нашего пакета с событиями
        valueDeserializer.addTrustedPackages("com.shop.inventoryservice.kafka");

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                valueDeserializer
        );
    }

    /**
     * Фабрика контейнеров для {@link KafkaListener}, работающих
     * с событиями {@link OrderCreatedEvent}.
     * <p>
     * Используется в {@link org.springframework.kafka.annotation.KafkaListener#containerFactory()}.
     *
     * @return {@link ConcurrentKafkaListenerContainerFactory} для обработки заказов
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderCreatedEvent> orderKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, OrderCreatedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(orderConsumerFactory());
        return factory;
    }
}



