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
import com.redhat.rhn.common.messaging.MessageQueue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Test spy for intercepting MessageQueue.publish() calls without executing handlers.
 *
 * Usage:
 * MessageQueueSpy spy = new MessageQueueSpy();
 * spy.install();
 * try {
 *     // Code that publishes events
 *     service.doSomethingAsync();
 *
 *     // Verify events were published
 *     List<XptoEvent> events = spy.getEvents(XptoEvent.class);
 *     assertEquals(1, events.size());
 *     assert("expected value", events.get(0).getSomeProperty());
 * }
 * finally {
 *     spy.uninstall();
 * }
 */
public class MessageQueueSpy implements MessageQueue.PublishInterceptor {

    private final List<EventMessage> capturedEvents = Collections.synchronizedList(new ArrayList<>());

    /**
     * Install this spy to intercept all MessageQueue.publish() calls.
     */
    public void install() {
        MessageQueue.setPublishInterceptor(this);
    }

    /**
     * Uninstall this spy and restore normal MessageQueue behavior.
     */
    public void uninstall() {
        MessageQueue.setPublishInterceptor(null);
        capturedEvents.clear();
    }

    @Override
    public void intercept(EventMessage event) {
        capturedEvents.add(event);
    }

    /**
     * Get all captured events of a specific type.
     * @param <T> event type
     * @param eventClass the event class
     * @return list of captured events of the specified type
     */
    @SuppressWarnings("unchecked")
    public <T extends EventMessage> List<T> getEvents(Class<T> eventClass) {
        List<T> result = new ArrayList<>();
        for (EventMessage event : capturedEvents) {
            if (eventClass.isInstance(event)) {
                result.add((T) event);
            }
        }
        return result;
    }

    /**
     * Get all captured events.
     * @return list of all captured events
     */
    public List<EventMessage> getAllEvents() {
        return new ArrayList<>(capturedEvents);
    }

    /**
     * Get the count of captured events of a specific type.
     * @param eventClass the event class
     * @return number of captured events of the specified type
     */
    public int getEventCount(Class<? extends EventMessage> eventClass) {
        return getEvents(eventClass).size();
    }

    /**
     * Clear all captured events.
     */
    public void clear() {
        capturedEvents.clear();
    }
}
