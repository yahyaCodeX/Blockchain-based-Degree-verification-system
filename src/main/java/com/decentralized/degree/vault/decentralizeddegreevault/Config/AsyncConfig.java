package com.decentralized.degree.vault.decentralizeddegreevault.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async configuration for batch degree processing.
 * Defines a dedicated thread pool for batch operations to prevent
 * blocking the main request-handling threads.
 */
// KAFKA-READY: When migrating to Kafka, this async executor becomes less critical
// since Kafka consumers naturally handle concurrency via consumer groups and partitions.
// The @Async methods would be replaced by @KafkaListener methods consuming from
// topic 'degree-batch-commands'. The thread pool would be managed by the Kafka consumer
// container factory (ConcurrentKafkaListenerContainerFactory) instead.
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Creates a ThreadPoolTaskExecutor for batch degree processing.
     * <p>
     * Core pool: 4 threads (always alive)
     * Max pool: 10 threads (scales under load)
     * Queue capacity: 100 (buffered tasks before rejection)
     * Thread prefix: "BatchDegree-" (for easy identification in logs/thread dumps)
     *
     * @return configured Executor bean
     */
    @Bean(name = "batchExecutor")
    public Executor batchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("BatchDegree-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
