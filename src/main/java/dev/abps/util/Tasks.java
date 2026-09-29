package dev.abps.util;

import dev.abps.AbpsMod;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/** A tiny tick scheduler that runs on the server thread. */
public final class Tasks {

    private static final class Task {
        long runAt;
        final long period;
        int remaining;
        int step;
        final IntConsumer body;
        boolean cancelled;

        Task(long runAt, long period, int count, IntConsumer body) {
            this.runAt = runAt;
            this.period = period;
            this.remaining = count;
            this.body = body;
        }
    }

    /** Handle to cancel a scheduled task. */
    public static final class Handle {
        private final Task task;

        private Handle(Task task) {
            this.task = task;
        }

        public void cancel() {
            task.cancelled = true;
        }
    }

    private static final List<Task> tasks = new ArrayList<>();
    private static final List<Task> added = new ArrayList<>();
    private static long tick;

    private Tasks() {
    }

    public static long now() {
        return tick;
    }

    public static Handle later(long delayTicks, Runnable r) {
        return schedule(delayTicks, 1, 1, step -> r.run());
    }

    /** Runs body count times, every period ticks, starting now. Body gets the step number from 0. */
    public static Handle repeat(int count, long period, IntConsumer body) {
        return schedule(0, period, count, body);
    }

    public static Handle schedule(long delay, long period, int count, IntConsumer body) {
        Task t = new Task(tick + Math.max(0, delay), Math.max(1, period), count, body);
        added.add(t);
        return new Handle(t);
    }

    public static void tick() {
        tick++;
        tasks.addAll(added);
        added.clear();
        tasks.removeIf(t -> {
            if (t.cancelled) return true;
            if (tick < t.runAt) return false;
            try {
                t.body.accept(t.step++);
            } catch (Exception e) {
                AbpsMod.LOGGER.warn("A scheduled effect stopped: {}", e.toString());
                return true;
            }
            t.remaining--;
            t.runAt = tick + t.period;
            return t.remaining <= 0;
        });
    }

    public static void clear() {
        tasks.clear();
        added.clear();
    }
}
