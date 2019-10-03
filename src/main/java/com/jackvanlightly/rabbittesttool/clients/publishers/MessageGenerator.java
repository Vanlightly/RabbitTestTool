package com.jackvanlightly.rabbittesttool.clients.publishers;

import com.jackvanlightly.rabbittesttool.clients.MessagePayload;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class MessageGenerator {
    private ByteBuffer messageBuf;

    public synchronized void setBaseMessageSize(int bytes) {
        if(bytes <= MessagePayload.MinimumMessageSize)
            bytes = MessagePayload.MinimumMessageSize;

        messageBuf = ByteBuffer.allocate(bytes);
    }

    public synchronized byte[] getMessageBytes(MessagePayload mp) throws IOException {
        messageBuf.position(0);
        messageBuf.putInt(mp.getStream());
        messageBuf.putInt(mp.getSequenceNumber());
        messageBuf.putLong(mp.getTimestamp());

        return messageBuf.array();
    }

    public static MessagePayload toMessagePayload(byte[] body) throws IOException {
        DataInputStream data = new DataInputStream(new ByteArrayInputStream(body));
        Integer stream = data.readInt();
        Integer seqNumber = data.readInt();
        long timestamp = data.readLong();

        MessagePayload mp = new MessagePayload();
        mp.setStream(stream);
        mp.setSequenceNumber(seqNumber);
        mp.setTimestamp(timestamp);

        return mp;
    }
}
