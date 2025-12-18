package com.example.notification.config;

import com.example.notification.dto.OrderCreatedEvent;
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
 * Конфигурация Kafka-консьюмера для notification-service.
 * <p>
 * Здесь настраивается:
 * <ul>
 *     <li>подключение к Kafka-брокеру ({@code bootstrap-servers});</li>
 *     <li>группа консьюмера {@code notification-service};</li>
 *     <li>JSON-десериализация событий {@link OrderCreatedEvent};</li>
 *     <li>фабрика listener'ов для аннотации {@link org.springframework.kafka.annotation.KafkaListener}.</li>
 * </ul>
 */
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Фабрика Kafka-консьюмера для чтения {@link OrderCreatedEvent}.
     *
     * @return настроенный {@link ConsumerFactory} с JSON-десериализацией
     */
    @Bean
    public ConsumerFactory<String, OrderCreatedEvent> eventConsumerFactory() {

        JsonDeserializer<OrderCreatedEvent> deserializer =
                new JsonDeserializer<>(OrderCreatedEvent.class, false);
        // Доверяем всем пакетам, так как событие может прилетать из другого микросервиса
        deserializer.addTrustedPackages("*");
        // Игнорируем type headers, используем явно указаный target-класс
        deserializer.ignoreTypeHeaders();

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, deserializer);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "notification-service");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                deserializer
        );
    }

    /**
     * Фабрика контейнеров для Kafka listener'ов, которые читают {@link OrderCreatedEvent}.
     *
     * @return {@link ConcurrentKafkaListenerContainerFactory}, используемая в {@link KafkaListener}
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderCreatedEvent> eventKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, OrderCreatedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(eventConsumerFactory());
        return factory;
    }
}
