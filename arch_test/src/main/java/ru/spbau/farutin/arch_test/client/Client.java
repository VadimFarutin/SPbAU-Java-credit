package ru.spbau.farutin.arch_test.client;

import org.jetbrains.annotations.NotNull;
import ru.spbau.farutin.arch_test.server.MainServer;
import ru.spbau.farutin.arch_test.util.IntArrayProtos;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Random;

/**
 * Client which sends arrays to server.
 */
public class Client implements Runnable {
    private String hostAddress;
    private int queriesNumber;
    private int delta;
    private int size;
    private int[] array;

    private int clientTimeServer = 0;
    private int queryTimeServer = 0;
    private int clientTime = 0;

    public Client(@NotNull String hostAddress, int arraySize, int queriesNumber, int delta) {
        this.hostAddress = hostAddress;
        this.size = arraySize;
        this.queriesNumber = queriesNumber;
        this.delta = delta;

        Random random = new Random();

        array = new int[size];
        for (int i = 0; i < size; i++) {
            array[i] = random.nextInt(1000);
        }
    }

    public int getClientTimeServer() {
        return clientTimeServer;
    }

    public int getQueryTimeServer() {
        return queryTimeServer;
    }

    public int getClientTime() {
        return clientTime;
    }

    @Override
    public void run() {
        try {
            InetAddress ipAddress = InetAddress.getByName(hostAddress);
            try (Socket socket = new Socket(ipAddress, MainServer.PORT_NUMBER)) {
                serverInteract(socket.getInputStream(), socket.getOutputStream());
            }
        } catch (UnknownHostException e) {
            System.err.println("Don't know about host " + hostAddress);
        } catch (IOException e) {
            System.err.println("Couldn't get I/O for the connection to " + hostAddress);
            System.err.println(e.getMessage());
        }
    }

    private void serverInteract(@NotNull InputStream serverIn,
                                @NotNull OutputStream serverOut) throws IOException {
        try (
                DataInputStream in = new DataInputStream(serverIn);
                DataOutputStream out = new DataOutputStream(serverOut)
        ) {
            long start = System.currentTimeMillis();

            IntArrayProtos.IntArray.Builder builder = IntArrayProtos.IntArray.newBuilder();
            builder.setSize(size);

            for (int i = 0; i < size; i++) {
                builder.addData(array[i]);
            }

            IntArrayProtos.IntArray message = builder.build();
            byte[] messageBytes = message.toByteArray();

            for (int i = 0; i < queriesNumber; i++) {
                out.writeInt(messageBytes.length);
                out.write(messageBytes);
                out.flush();
                System.out.println("wrote " + messageBytes.length + " bytes");

                int responseSize = in.readInt();
                byte[] responseBytes = new byte[responseSize];
                int cnt = 0;
                while (cnt < responseSize) {
                    cnt += in.read(responseBytes, cnt, responseSize - cnt);
                }

                IntArrayProtos.IntArray response = IntArrayProtos.IntArray.parseFrom(responseBytes);

                try {
                    Thread.sleep(delta);
                } catch (InterruptedException e) {
                    System.err.println("Client interrupted!");
                    return;
                }
            }

            builder = IntArrayProtos.IntArray.newBuilder().setSize(0);
            message = builder.build();

            messageBytes = message.toByteArray();
            out.writeInt(messageBytes.length);
            out.write(messageBytes);
            out.flush();

            int statsSize = in.readInt();
            byte[] statsBytes = new byte[statsSize];
            int cnt = 0;
            while (cnt < statsSize) {
                cnt += in.read(statsBytes, cnt, statsSize - cnt);
            }

            IntArrayProtos.IntArray stats = IntArrayProtos.IntArray.parseFrom(statsBytes);

            long end = System.currentTimeMillis();

            synchronized (this) {
                clientTimeServer += stats.getData(0);
                queryTimeServer += stats.getData(1);
                clientTime += (int) (end - start);
            }
        }
    }
}
