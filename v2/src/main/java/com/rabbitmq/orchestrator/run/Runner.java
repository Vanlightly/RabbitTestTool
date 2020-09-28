package com.rabbitmq.orchestrator.run;

import com.rabbitmq.orchestrator.model.Workload;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public interface Runner {
    void runMainLoad(Workload workload,
                     int runOrdinal,
                     Set<String> failedSystems,
                     AtomicBoolean isCancelled,
                     String runId,
                     String tags);
    void runBackroundLoad(Workload workload, Set<String> failedSystems, AtomicBoolean isCancelled);
}
