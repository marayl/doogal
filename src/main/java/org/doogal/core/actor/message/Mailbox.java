package org.doogal.core.actor.message;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.doogal.core.actor.concurrent.FutureValue;
import org.doogal.core.actor.queue.Functional;
import org.doogal.core.actor.queue.Queue;
import org.doogal.core.actor.util.Monitor;
import org.doogal.core.actor.util.UnaryPredicate;
import org.doogal.core.actor.util.UpdateListener;
import org.doogal.core.util.Destroyable;

public final class Mailbox implements Destroyable, Inbox, Outbox {
    private static final int INACTIVE = 0;
    private static final int ACTIVE = 1;
    private static final int DESTROYED = 2;
    private final UpdateListener<Future<Object>> listener;
    private final Monitor monitor;
    private final List<Message> queue;
    private int state;

    private final Message popLocked() {
        // Pop last element if not empty.
        return queue.isEmpty() ? null : queue.remove(queue.size() - 1);
    }

    private final Future<Object> sendLocked(int type, Object request,
            MessageFunction op, UnaryPredicate<Message> pred) {
        if (DESTROYED == state)
            throw new RejectedExecutionException();
        final Message message = new Message(type, request, op.getResponse());
        Queue.<Message> enqueue(message, queue, op, pred);
        // The response may have change during the call to enqueue(). It may,
        // for example, have been updated to an existing response future.
        // Coalescing queues and interrupts can be implemented in this manner.
        return op.getResponse();
    }

    Mailbox(UpdateListener<Future<Object>> listener) {
        this.listener = listener;
        monitor = new Monitor();
        queue = new LinkedList<Message>();
        state = INACTIVE;
    }

    public final void destroy() {
        synchronized (monitor) {
            final int prev = state;
            state = DESTROYED;
            try {
                // Cancel all message responses if the Mailbox was destroyed
                // before it was activated.
                if (INACTIVE == prev)
                    for (final Message message : queue)
                        message.getResponse().cancel(true);
            } finally {
                monitor.notifyAll();
            }
        }
    }

    public final Message recv() throws InterruptedException {
        synchronized (monitor) {
            // Mailbox must be activated before messages are received.
            assert INACTIVE != state;
            while (DESTROYED != state && queue.isEmpty())
                monitor.wait();
            return popLocked();
        }
    }

    public final Message recv(long timeout, TimeUnit unit)
            throws InterruptedException, TimeoutException {
        final long abs = System.currentTimeMillis() + unit.toMillis(timeout);
        synchronized (monitor) {
            // Mailbox must be activated before messages are received.
            assert INACTIVE != state;
            while (DESTROYED != state && queue.isEmpty()) {
                final long ms = abs - System.currentTimeMillis();
                if (ms <= 0)
                    throw new TimeoutException();
                monitor.wait();
            }
            return popLocked();
        }
    }

    public final Future<Object> send(int type, Object request,
            MessageFunction op, UnaryPredicate<Message> pred) {
        synchronized (monitor) {
            final Future<Object> future = sendLocked(type, request, op, pred);
            monitor.notifyAll();
            return future;
        }
    }

    public final Future<Object> send(int type, Object request,
            MessageFunction op) {
        return send(type, request, op, Functional.<Message> all());
    }

    public final Future<Object> send(int type, Object request) {
        return send(type, request, Messages.pushBack(newFuture()), Functional
                .<Message> all());
    }

    public final void activate() {
        synchronized (monitor) {
            if (INACTIVE == state)
                state = ACTIVE;
        }
    }

    public final FutureValue<Object> newFuture() {
        return new FutureValue<Object>(listener);
    }
}
