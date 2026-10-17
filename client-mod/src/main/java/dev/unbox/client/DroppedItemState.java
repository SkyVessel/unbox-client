package dev.unbox.client;

public interface DroppedItemState {
    void unbox$motion(boolean grounded, double speed);
    boolean unbox$grounded();
    double unbox$speed();
}
