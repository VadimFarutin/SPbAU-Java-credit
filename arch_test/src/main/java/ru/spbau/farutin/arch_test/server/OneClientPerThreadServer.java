package ru.spbau.farutin.arch_test.server;

import org.jetbrains.annotations.NotNull;
import ru.spbau.farutin.arch_test.util.BubbleSort;
import ru.spbau.farutin.arch_test.util.IntArrayProtos;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.ListIterator;

public class OneClientPerThreadServer implements Server {
    private ServerSocket serverSocket = null;
    private boolean isStopped = true;
    private int portNumber;

    public OneClientPerThreadServer(int portNumber) {
        this.portNumber = portNumber;
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(portNumber);
            isStopped = false;

            while (!isStopped) {
                try {
                    Socket clientSocket = serverSocket.accept();

                    ClientHandler clientHandler = new ClientHandler(
                            clientSocket.getInputStream(),
                            clientSocket.getOutputStream());

                    Thread thread = new Thread(clientHandler);
                    thread.setDaemon(true);
                    thread.start();
                } catch (IOException e) {
                    if (!isStopped) {
                        System.err.println("Exception caught when working with client socket.");
                        System.err.println(e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Exception caught when trying to open server socket");
            System.err.println(e.getMessage());
        }
    }

    @Override
    public void stop() {
        isStopped = true;

        try {
            serverSocket.close();
        } catch (IOException e) {
            System.err.println("Exception caught when trying to close server socket.");
            System.err.println(e.getMessage());
        }
    }

    public static class ClientHandler implements Runnable {
        private InputStream clientIn = null;
        private OutputStream clientOut = null;

        public ClientHandler(@NotNull InputStream clientIn,
                             @NotNull OutputStream clientOut) {
            this.clientIn = clientIn;
            this.clientOut = clientOut;
        }

        @Override
        public void run() {
            try (
                    DataInputStream in = new DataInputStream(clientIn);
                    DataOutputStream out = new DataOutputStream(clientOut)
            ) {
                long clientTime = 0;
                long sortingTime = 0;

                while (true) {
                    int bytesSize = in.readInt();
                    byte[] bytes = new byte[bytesSize];
                    int cnt = 0;
                    while (cnt < bytesSize) {
                        cnt += in.read(bytes, cnt, bytesSize - cnt);
                    }

                    IntArrayProtos.IntArray message = IntArrayProtos.IntArray.parseFrom(bytes);
                    long clientStart = System.currentTimeMillis();

                    int size = message.getSize();
                    List<Integer> data = message.getDataList();

                    if (size == 0) {
                        break;
                    }

                    int[] array = new int[size];
                    ListIterator<Integer> iterator = data.listIterator();

                    for (int i = 0; i < size; i++) {
                        array[i] = iterator.next();
                    }

                    long sortingStart = System.currentTimeMillis();
                    BubbleSort.sort(array);
                    long sortingEnd = System.currentTimeMillis();

                    IntArrayProtos.IntArray.Builder builder = IntArrayProtos.IntArray.newBuilder();
                    builder.setSize(size);

                    for (int i = 0; i < size; i++) {
                        builder.addData(array[i]);
                    }

                    IntArrayProtos.IntArray sorted = builder.build();
                    bytes = sorted.toByteArray();
                    out.writeInt(bytes.length);
                    out.write(bytes);
                    out.flush();

                    long clientEnd = System.currentTimeMillis();

                    clientTime += clientEnd - clientStart;
                    sortingTime += sortingEnd - sortingStart;
                }

                IntArrayProtos.IntArray stats = IntArrayProtos.IntArray.newBuilder()
                        .setSize(2)
                        .addData((int)clientTime)
                        .addData((int)sortingTime)
                        .build();

                byte[] bytes = stats.toByteArray();
                out.writeInt(bytes.length);
                out.write(bytes);
                out.flush();
            } catch (IOException e) {
                System.err.println("Exception caught when trying to interact with client");
                System.err.println(e.getMessage());
            }
        }
    }
}
