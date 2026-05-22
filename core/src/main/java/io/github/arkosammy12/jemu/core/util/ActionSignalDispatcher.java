package io.github.arkosammy12.jemu.core.util;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongHeapPriorityQueue;
import it.unimi.dsi.fastutil.longs.LongPriorityQueue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

public class ActionSignalDispatcher {

    private final List<Signal> signals = new ArrayList<>();
    private long ticks;

    public int addSignal(IntConsumer action) {
        int id = this.signals.size();
        this.signals.add(new Signal(action));
        return id;
    }

    public void trigger(int id, int delay, int value) {
        Signal signal = this.signals.get(id);
        long fireAt = this.ticks + (long) delay;
        signal.timers.enqueue(fireAt);
        signal.pendingValues.put(fireAt, value);
    }

    public void tick() {
        this.ticks++;
        for (Signal signal : this.signals) {
            while (!signal.timers.isEmpty() && signal.timers.firstLong() <= this.ticks) {
                signal.action.accept(signal.pendingValues.remove(signal.timers.dequeueLong()));
            }
        }
    }

    private static final class Signal {

        final IntConsumer action;
        final LongPriorityQueue timers = new LongHeapPriorityQueue();
        final Long2IntMap pendingValues = new Long2IntOpenHashMap();

        private Signal(IntConsumer action) {
            this.action = action;
        }

    }

}
