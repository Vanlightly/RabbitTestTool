package com.jackvanlightly.rabbittesttool.topology.model.publishers;

public class PublisherMode {
    private boolean useConfirms;
    private int inFlightLimit;

    public static PublisherMode withConfirms(int inFlightLimit) {
        PublisherMode pm = new PublisherMode();
        pm.setUseConfirms(true);
        pm.setInFlightLimit(inFlightLimit);

        return pm;
    }

    public static PublisherMode withoutConfirms() {
        return new PublisherMode();
    }

    public boolean isUseConfirms() {
        return useConfirms;
    }

    public void setUseConfirms(boolean useConfirms) {
        this.useConfirms = useConfirms;
    }

    public int getInFlightLimit() {
        return inFlightLimit;
    }

    public void setInFlightLimit(int inFlightLimit) {
        this.inFlightLimit = inFlightLimit;
    }
}
