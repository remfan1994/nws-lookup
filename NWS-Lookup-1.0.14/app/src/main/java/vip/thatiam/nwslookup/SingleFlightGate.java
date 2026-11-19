package vip.thatiam.nwslookup;

final class SingleFlightGate {
    private boolean running;
    private long generation;

    synchronized long tryStart() {
        if (running) return 0L;
        running = true;
        generation++;
        if (generation == 0L) generation++;
        return generation;
    }

    synchronized boolean finish(long token) {
        if (!running || token == 0L || token != generation) return false;
        running = false;
        return true;
    }

    synchronized boolean isRunning() {
        return running;
    }
}
