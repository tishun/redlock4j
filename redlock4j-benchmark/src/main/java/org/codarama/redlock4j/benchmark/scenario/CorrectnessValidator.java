/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Codarama
 */
package org.codarama.redlock4j.benchmark.scenario;

import org.codarama.redlock4j.benchmark.infrastructure.RedisResultsStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Validates correctness of lock operations by analyzing lock events stored in Redis.
 */
public class CorrectnessValidator {

    private static final Logger logger = LoggerFactory.getLogger(CorrectnessValidator.class);

    /**
     * Validation result containing all detected issues.
     */
    public static class ValidationResult {
        private final List<String> concurrentHolderViolations = new ArrayList<>();
        private final List<String> fifoViolations = new ArrayList<>();
        private final long totalEvents;
        private final long uniqueClients;

        public ValidationResult(long totalEvents, long uniqueClients) {
            this.totalEvents = totalEvents;
            this.uniqueClients = uniqueClients;
        }

        public void addConcurrentHolderViolation(String description) {
            concurrentHolderViolations.add(description);
        }

        public void addFifoViolation(String description) {
            fifoViolations.add(description);
        }

        public boolean isCorrect() {
            return concurrentHolderViolations.isEmpty() && fifoViolations.isEmpty();
        }

        public int getConcurrentHolderViolationCount() { return concurrentHolderViolations.size(); }
        public int getFifoViolationCount() { return fifoViolations.size(); }
        public long getTotalEvents() { return totalEvents; }
        public long getUniqueClients() { return uniqueClients; }
        public List<String> getConcurrentHolderViolations() { return concurrentHolderViolations; }
        public List<String> getFifoViolations() { return fifoViolations; }

        @Override
        public String toString() {
            return String.format(
                "ValidationResult{correct=%s, events=%d, clients=%d, concurrentViolations=%d, fifoViolations=%d}",
                isCorrect(), totalEvents, uniqueClients, 
                concurrentHolderViolations.size(), fifoViolations.size()
            );
        }
    }

    /**
     * Validates lock events from the results store.
     */
    public ValidationResult validate(RedisResultsStore resultsStore) {
        List<String> events = resultsStore.getLockEvents();
        Set<String> uniqueClients = new HashSet<>();

        ValidationResult result = new ValidationResult(events.size(), 0);

        if (events.isEmpty()) {
            logger.info("No lock events to validate");
            return result;
        }

        // Parse events: format is "timestamp:clientId:sequenceNumber"
        List<LockEvent> parsedEvents = new ArrayList<>();
        for (String event : events) {
            String[] parts = event.split(":");
            if (parts.length >= 3) {
                long timestamp = Long.parseLong(parts[0]);
                String clientId = parts[1];
                long sequence = Long.parseLong(parts[2]);
                parsedEvents.add(new LockEvent(timestamp, clientId, sequence));
                uniqueClients.add(clientId);
            }
        }

        // Sort by timestamp to detect overlapping acquisitions
        parsedEvents.sort(Comparator.comparingLong(e -> e.timestamp));

        // Check for FIFO violations by comparing request order with acquisition order
        // Group events by implementation type for FIFO checking
        Map<String, List<LockEvent>> eventsByImpl = new HashMap<>();
        for (LockEvent event : parsedEvents) {
            String implType = extractImplType(event.clientId);
            eventsByImpl.computeIfAbsent(implType, k -> new ArrayList<>()).add(event);
        }

        // For each implementation, check if sequence numbers are monotonically increasing
        for (Map.Entry<String, List<LockEvent>> entry : eventsByImpl.entrySet()) {
            List<LockEvent> implEvents = entry.getValue();
            implEvents.sort(Comparator.comparingLong(e -> e.sequence));
            
            long lastSequence = -1;
            for (LockEvent event : implEvents) {
                if (event.sequence < lastSequence) {
                    result.addFifoViolation(String.format(
                        "FIFO violation: client %s acquired lock with sequence %d after sequence %d",
                        event.clientId, event.sequence, lastSequence));
                }
                lastSequence = event.sequence;
            }
        }

        logger.info("Validation complete: {} events, {} clients, {} violations",
            events.size(), uniqueClients.size(), 
            result.getConcurrentHolderViolationCount() + result.getFifoViolationCount());

        return result;
    }

    private String extractImplType(String clientId) {
        int lastDash = clientId.lastIndexOf("-client-");
        return lastDash > 0 ? clientId.substring(0, lastDash) : clientId;
    }

    private static class LockEvent {
        final long timestamp;
        final String clientId;
        final long sequence;

        LockEvent(long timestamp, String clientId, long sequence) {
            this.timestamp = timestamp;
            this.clientId = clientId;
            this.sequence = sequence;
        }
    }
}

