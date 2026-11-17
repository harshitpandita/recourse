package com.recourse.engine;

public enum Direction {
    SUPPORTS, CONTRADICTS, NEUTRAL;

    public Direction opposite() {
        return switch (this) {
            case SUPPORTS -> CONTRADICTS;
            case CONTRADICTS -> SUPPORTS;
            case NEUTRAL -> NEUTRAL;
        };
    }
}
