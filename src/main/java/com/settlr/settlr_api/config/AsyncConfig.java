package com.settlr.settlr_api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Dedicated thread pool for async balance fetching.
 *
 * Using a virtual-thread-per-task executor (Java 21+) gives us lightweight threads
 * that are ideal for I/O-bound work (DB queries). If on Java < 21, replace with
 * Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2).
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "balanceExecutor")
    public Executor balanceExecutor() {
        // Fixed thread pool — bounded to avoid runaway thread creation
        return Executors.newFixedThreadPool(
                Math.max(4, Runtime.getRuntime().availableProcessors() * 2));
    }
}
