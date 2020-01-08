package com.jackvanlightly.rabbittesttool.clients.publishers;

import java.util.ArrayList;
import java.util.List;

public class StreamUtils {
    private static int Current = 1;

    public static Integer getAndIncrement() {
        Integer copy = new Integer(Current);
        Current++;

        return copy;
    }

    public static List<Integer> getStreams(int number) {
        List<Integer> streams = new ArrayList<>();
        for(int i=0; i<number; i++) {
            streams.add(StreamUtils.getAndIncrement());
        }

        return streams;
    }
}
