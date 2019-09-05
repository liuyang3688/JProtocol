package com.leotech.protocal;

import java.awt.event.ActionEvent;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Arrays;

public class IecClient {
    private String host;
    private Socket socket;
    private int port;

    IecClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void connect() {
        SocketAddress addr = new InetSocketAddress(host, port);
        try {
            socket = new Socket();
            socket.connect(addr);
        } catch (IOException e) {
            System.out.println("客户端连接失败：" + e.getMessage());
        }
    }

    public boolean isConnected() {
        return socket.isConnected();
    }

    public void send(byte[] sendData, int sendLen) {
        try {
            DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
            outputStream.write(sendData, 0, sendLen);
            outputStream.flush();
            StringBuilder buidler = new StringBuilder("发送数据：");
            output(buidler, sendData, sendLen);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int recv(byte[] recvData) {
        int recvLen = 0;
        try {
            DataInputStream inputStream = new DataInputStream(socket.getInputStream());
            recvLen = inputStream.read(recvData);
            if (recvLen > 0) {
                StringBuilder buidler = new StringBuilder("接收数据：");
                output(buidler, recvData, recvLen);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return 0;
        }
        return recvLen;
    }

    public void output(StringBuilder builder, byte[] data, int len) {
        for (int i = 0; i < len; ++i) {
            builder.append(Integer.toHexString(data[i] & 0xff)).append(" ");
        }
    }
}
   