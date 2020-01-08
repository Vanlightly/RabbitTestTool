package com.jackvanlightly.rabbittesttool.clients;

import com.rabbitmq.client.SocketConfigurator;

import java.io.IOException;
import java.net.Socket;

public class WithNagleSocketConfigurator implements SocketConfigurator {


    @Override
    public void configure(Socket socket) throws IOException {
        socket.setTcpNoDelay(false);
    }
}
