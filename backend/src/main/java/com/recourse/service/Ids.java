package com.recourse.service;

import java.util.UUID;

final class Ids {
    private Ids() {}
    static String next() { return UUID.randomUUID().toString(); }
}
