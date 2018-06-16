package ru.spbau.farutin.arch_test.server;

import org.jetbrains.annotations.NotNull;
import ru.spbau.farutin.arch_test.util.BubbleSort;
import ru.spbau.farutin.arch_test.util.IntArrayProtos;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.*;

public class BlockingServer implements Server {
    private ServerSocket serverSocket = null;
    private boolean isStopped = true;
    private int portNumber;

    private static final int THREAD_POOL_SIZE = 4;
    private ExecutorService pool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

    public BlockingServer(int portNumber) {
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
                            clientSocket.getOutputStream(),
                            pool);

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
        private ExecutorService pool = null;
        private ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();
        private ArrayList<Future> results = new ArrayList<>();

        private long clientTime = 0;
        private long sortingTime = 0;

        public ClientHandler(@NotNull InputStream clientIn,
                             @NotNull OutputStream clientOut,
                             @NotNull ExecutorService pool) {
            this.clientIn = clientIn;
            this.clientOut = clientOut;
            this.pool = pool;
        }

        @Override
        public void run() {
            try (
                    DataInputStream in = new DataInputStream(clientIn);
                    DataOutputStream out = new DataOutputStream(clientOut)
            ) {
                while (true) {
                    int bytesSize = in.readInt();
                    byte[] bytes = new byte[bytesSize];
                    int cnt = 0;
                    while (cnt < bytesSize) {
                        cnt += in.read(bytes, cnt, bytesSize - cnt);
                    }

                    long clientStart = System.currentTimeMillis();
                    IntArrayProtos.IntArray message = IntArrayProtos.IntArray.parseFrom(bytes);

                    int size = message.getSize();
                    List<Integer> data = message.getDataList();

                    if (size == 0) {
                        break;
                    }

                    results.add(pool.submit(new SortTask(out, data, clientStart)));
                }

                for (Future future : results) {
                    future.get();
                }

                singleThreadExecutor.shutdown();
                singleThreadExecutor.awaitTermination(1, TimeUnit.DAYS);

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
            } catch (InterruptedException e) {
                System.err.println("Interrupted while waiting for all tasks to end");
                System.err.println(e.getMessage());
            } catch (ExecutionException e) {
                System.err.println("Failed to execute task");
                System.err.println(e.getMessage());
            }
        }

        private class SortTask implements Runnable {
            private DataOutputStream out = null;
            private List<Integer> data = null;
            private int size;
            private long clientStart;

            public SortTask(@NotNull DataOutputStream out,
                            @NotNull List<Integer> data,
                            long clientStart) {
                this.out = out;
                this.data = data;
                size = data.size();
                this.clientStart = clientStart;
            }

            @Override
            public void run() {
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
                byte[] bytes = sorted.toByteArray();

                singleThreadExecutor.submit(new SendTask(out, bytes,
                        clientStart, sortingStart, sortingEnd));
            }
        }

        private class SendTask implements Runnable {
            private DataOutputStream out = null;
            private byte[] data = null;
            private long clientStart;
            private long sortingStart;
            private long sortingEnd;

            public SendTask(@NotNull DataOutputStream out,
                            @NotNull byte[] data,
                            long clientStart,
                            long sortingStart,
                            long sortingEnd) {
                this.out = out;
                this.data = data;
                this.clientStart = clientStart;
                this.sortingStart = sortingStart;
                this.sortingEnd = sortingEnd;
            }

            @Override
            public void run() {
                try {
                    out.writeInt(data.length);
                    out.write(data);
                    out.flush();

                    long clientEnd = System.currentTimeMillis();

                    synchronized (ClientHandler.this) {
                        clientTime += clientEnd - clientStart;
                        sortingTime += sortingEnd - sortingStart;
                    }
                } catch (IOException e) {
                    System.err.println("Exception caught when trying to interact with client");
                    System.err.println(e.getMessage());
                }
            }
        }
    }
}
