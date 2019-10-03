package com.jackvanlightly.rabbittesttool.topology;

public class TopologyException extends RuntimeException {
    public TopologyException() {
    }

    public TopologyException(String s) {
        super(s);
    }

    public TopologyException(String s, Throwable throwable) {
        super(s, throwable);
    }

    public TopologyException(Throwable throwable) {
        super(throwable);
    }

    public TopologyException(String s, Throwable throwable, boolean b, boolean b1) {
        super(s, throwable, b, b1);
    }
}
