package vip.thatiam.nwslookup;

import java.lang.ref.WeakReference;

final class WeakListenerSlot<T> {
    private WeakReference<T> listener = new WeakReference<>(null);

    synchronized void attach(T value) {
        listener = new WeakReference<>(value);
    }

    synchronized void detach(T value) {
        if (listener.get() == value) listener.clear();
    }

    synchronized T get() {
        return listener.get();
    }
}
