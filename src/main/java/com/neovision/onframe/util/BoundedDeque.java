package com.neovision.onframe.util;

import java.util.ArrayDeque;
import java.util.Deque;

public class BoundedDeque<T> {
    private final int capacity;
    private final Deque<T> deque = new ArrayDeque<>();

    public BoundedDeque(int capacity) { this.capacity = capacity; }

    public synchronized void add(T item) {
        if (deque.size() >= capacity) deque.removeFirst();
        deque.addLast(item);
    }

    public synchronized Deque<T> snapshot() {
        return new ArrayDeque<>(deque);
    }
}