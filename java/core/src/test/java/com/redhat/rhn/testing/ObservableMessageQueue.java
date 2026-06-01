/*
 * Copyright (c) 2026 SUSE LLC
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 */
package com.redhat.rhn.testing;

import com.redhat.rhn.common.messaging.EventMessage;
import com.redhat.rhn.common.messaging.MessageAction;
import com.redhat.rhn.common.messaging.MessageQueue;
import com.redhat.rhn.manager.errata.ErrataManager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Observable MessageQueue for tests - captures and awaits published events.
 * Uses Observer pattern with CountDownLatch for instant notification (no polling).
 *
 * Usage:
 * @BeforeEach
 * public void setUp() {
 *     ObservableMessageQueue.registerCapturingAction(XptoEvent.class);
 * }
 *
 * @AfterEach
 * public void tearDown() {
 *     ObservableMessageQueue.unregisterCapturingAction(XptoEvent.class);
 * }
 *
 * @Test
 * public void testWhereAnEventIsPublished() throws Exception {
 *     // Await and fetch (blocks until available or timeout)
 *     XptoEvent event = ObservableMessageQueue.awaitAndFetchNext(XptoEvent.class);
 *     assertEquals("expected value", event.getSomeProperty());
 *
 *     // Or await multiple
 *     List<XptoEvent> events = ObservableMessageQueue.awaitAndFetch(XptoEvent.class, 2);
 *     assertEquals(2, events.size());
 * }
 */
public class ObservableMessageQueue {

    private static Logger LOG = LogManager.getLogger(ObservableMessageQueue.class);


    private static final Map<Class<? extends EventMessage>, CapturingAction> CAPTURING_ACTIONS =
        new HashMap<>();

    /**
     * Observer-based capturing action that notifies observers when events are captured.
     * Uses CountDownLatch for true wait/notify pattern (no polling).
     */
    private static class CapturingAction implements MessageAction {
        private final List<EventMessage> capturedMessages = Collections.synchronizedList(new ArrayList<>());
        private final List<CountDownLatch> latches = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void execute(EventMessage msg) {
            capturedMessages.add(msg);

            // Notify all observers by counting down their latches
            synchronized (latches) {
                for (CountDownLatch latch : latches) {
                    latch.countDown();
                }
            }
        }

        public List<EventMessage> getCaptured() {
            return new ArrayList<>(capturedMessages);
        }

        public CountDownLatch createLatch(int count) {
            CountDownLatch latch = new CountDownLatch(count);
            synchronized (latches) {
                latches.add(latch);
            }
            return latch;
        }

        public void removeLatch(CountDownLatch latch) {
            synchronized (latches) {
                latches.remove(latch);
            }
        }

        public void clear() {
            capturedMessages.clear();
            synchronized (latches) {
                latches.clear();
            }
        }
    }

    /**
     * Register a capturing action for a specific event type.
     * This must be called BEFORE MessageQueue.startMessaging() or the event is published.
     *
     * @param eventClass the event class to capture
     */
    public static void registerCapturingAction(Class<? extends EventMessage> eventClass) {
        CapturingAction action = new CapturingAction();
        CAPTURING_ACTIONS.put(eventClass, action);
        MessageQueue.registerAction(action, eventClass);
        LOG.debug("DEBUG: Registered capturing action for {}", eventClass.getSimpleName());
    }

    /**
     * Unregister the capturing action and clear captured messages.
     *
     * @param eventClass the event class to stop capturing
     */
    public static void unregisterCapturingAction(Class<? extends EventMessage> eventClass) {
        CapturingAction action = CAPTURING_ACTIONS.remove(eventClass);
        if (action != null) {
            // MessageQueue doesn't have an unregister method
            // The action will remain registered but we clear our reference
            action.clear();
        }
    }


    /**
     * Get all captured messages of all registered types.
     *
     * @return list of all captured messages
     */
    public static List<EventMessage> getAllCapturedMessages() {
        List<EventMessage> all = new ArrayList<>();
        for (CapturingAction action : CAPTURING_ACTIONS.values()) {
            all.addAll(action.getCaptured());
        }
        return all;
    }


    /**
     * Clear all captured messages without unregistering.
     */
    public static void clearAllCapturedMessages() {
        for (CapturingAction action : CAPTURING_ACTIONS.values()) {
            action.clear();
        }
    }

    /**
     * Clear captured messages for a specific event type.
     *
     * @param eventClass the event class to clear
     */
    public static void clearCapturedMessages(Class<? extends EventMessage> eventClass) {
        CapturingAction action = CAPTURING_ACTIONS.get(eventClass);
        if (action != null) {
            action.clear();
        }
    }

    /**
     * Get captured messages of a specific type.
     *
     * @param <T> the event message type
     * @param eventClass the class of events to retrieve
     * @return list of captured messages of the specified type
     */
    @SuppressWarnings("unchecked")
    public static <T extends EventMessage> List<T> getCapturedMessages(Class<T> eventClass) {
        CapturingAction action = CAPTURING_ACTIONS.get(eventClass);
        if (action == null) {
            return Collections.emptyList();
        }
        return action.getCaptured().stream()
            .map(msg -> (T) msg)
            .collect(Collectors.toList());
    }

    /**
     * Get the count of captured messages of a specific type.
     *
     * @param eventClass the event type to count
     * @return number of messages of this type
     */
    public static int getMessageCount(Class<? extends EventMessage> eventClass) {
        CapturingAction action = CAPTURING_ACTIONS.get(eventClass);
        return action != null ? action.getCaptured().size() : 0;
    }

    /**
     * Wait for messages using Observer pattern (no polling!).
     * Uses CountDownLatch for true wait/notify pattern - thread blocks until notified.
     *
     * @param eventClass the event type to wait for
     * @param expectedCount the number of messages to wait for
     * @param timeoutMs maximum time to wait in milliseconds
     * @return true if the expected count was reached, false if timeout
     * @throws InterruptedException if interrupted while waiting
     */
    public static boolean waitForMessages(
            Class<? extends EventMessage> eventClass,
            int expectedCount,
            long timeoutMs
    ) throws InterruptedException {
        CapturingAction action = CAPTURING_ACTIONS.get(eventClass);
        if (action == null) {
            return false;
        }

        // Check if we already have enough messages
        if (action.getCaptured().size() >= expectedCount) {
            return true;
        }

        // Create latch for remaining messages - will be counted down by execute()
        int remaining = expectedCount - action.getCaptured().size();
        CountDownLatch latch = action.createLatch(remaining);

        try {
            // Block until latch reaches 0 or timeout - NO POLLING!
            return latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        } finally {
            action.removeLatch(latch);
        }
    }

    /**
     * Await and fetch the next captured message (blocks until available or timeout).
     * Default timeout: 5 seconds.
     *
     * @param <T> the event message type
     * @param eventClass the event type to await
     * @return the next captured message
     * @throws InterruptedException if interrupted while waiting
     * @throws IllegalStateException if timeout is reached
     */
    public static <T extends EventMessage> T awaitAndFetchNext(Class<T> eventClass) throws InterruptedException {
        return awaitAndFetchNext(eventClass, 5000);
    }

    /**
     * Await and fetch the next captured message (blocks until available or timeout).
     *
     * @param <T> the event message type
     * @param eventClass the event type to await
     * @param timeoutMs maximum time to wait in milliseconds
     * @return the next captured message
     * @throws InterruptedException if interrupted while waiting
     * @throws IllegalStateException if timeout is reached
     */
    public static <T extends EventMessage> T awaitAndFetchNext(Class<T> eventClass, long timeoutMs)
            throws InterruptedException {
        CapturingAction action = CAPTURING_ACTIONS.get(eventClass);
        if (action == null) {
            throw new IllegalStateException("No capturing action registered for " + eventClass.getSimpleName());
        }

        int currentCount = action.getCaptured().size();
        boolean success = waitForMessages(eventClass, currentCount + 1, timeoutMs);

        if (!success) {
            throw new IllegalStateException(
                "Timeout waiting for next message of type " + eventClass.getSimpleName()
            );
        }

        @SuppressWarnings("unchecked")
        T message = (T) action.getCaptured().get(currentCount);
        return message;
    }

    /**
     * Await for a specific count of messages to be captured, then fetch all of them.
     * Default timeout: 5 seconds.
     *
     * @param <T> the event message type
     * @param eventClass the event type to await
     * @param count the number of messages to wait for
     * @return list of captured messages
     * @throws InterruptedException if interrupted while waiting
     * @throws IllegalStateException if timeout is reached
     */
    public static <T extends EventMessage> List<T> awaitAndFetch(Class<T> eventClass, int count)
            throws InterruptedException {
        return awaitAndFetch(eventClass, count, 5000);
    }

    /**
     * Await for a specific count of messages to be captured, then fetch all of them.
     *
     * @param <T> the event message type
     * @param eventClass the event type to await
     * @param count the number of messages to wait for
     * @param timeoutMs maximum time to wait in milliseconds
     * @return list of captured messages
     * @throws InterruptedException if interrupted while waiting
     * @throws IllegalStateException if timeout is reached
     */
    @SuppressWarnings("unchecked")
    public static <T extends EventMessage> List<T> awaitAndFetch(
            Class<T> eventClass,
            int count,
            long timeoutMs
    ) throws InterruptedException {
        boolean success = waitForMessages(eventClass, count, timeoutMs);

        if (!success) {
            throw new IllegalStateException(
                "Timeout waiting for " + count + " messages of type " + eventClass.getSimpleName()
            );
        }

        return getCapturedMessages(eventClass);
    }

}
