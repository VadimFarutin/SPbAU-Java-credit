package ru.spbau.farutin.arch_test.server;

import org.jetbrains.annotations.NotNull;
import ru.spbau.farutin.arch_test.util.BubbleSort;
import ru.spbau.farutin.arch_test.util.IntArrayProtos;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;

public class NonBlockingServer implements Server {
    private ServerSocketChannel serverSocketChannel = null;
    private boolean isStopped = true;
    private int portNumber;

    private static final int THREAD_POOL_SIZE = 4;
    private ExecutorService pool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    private WriteSelector writeSelector = null;

    public NonBlockingServer(int portNumber) {
        this.portNumber = portNumber;
    }

    @Override
    public void run() {
        try {
            ReadSelector readSelector = new ReadSelector();
            Thread readThread = new Thread(readSelector);
            readThread.start();

            writeSelector = new WriteSelector();
            Thread writeThread = new Thread(writeSelector);
            writeThread.start();

            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.socket().bind(new InetSocketAddress(portNumber));
            isStopped = false;

            while (!isStopped) {
                try {
                    SocketChannel clientChannel = serverSocketChannel.accept();
                    MessageBuffers buffers = new MessageBuffers(clientChannel);
                    readSelector.add(clientChannel, buffers);
                } catch (IOException e) {
                    if (!isStopped) {
                        System.err.println("Exception caught when working with client socket.");
                        System.err.println(e.getMessage());
                    }
                }
            }

            readThread.interrupt();
            writeThread.interrupt();
        } catch (IOException e) {
            System.err.println("Exception caught when trying to open server socket");
            System.err.println(e.getMessage());
        }
    }

    @Override
    public void stop() {
        isStopped = true;

        try {
            serverSocketChannel.close();
        } catch (IOException e) {
            System.err.println("Exception caught when trying to close server socket.");
            System.err.println(e.getMessage());
        }
    }

    private class ReadSelector implements Runnable {
        private Selector selector = null;
        private final Queue<ClientBufferPair> pairQueue = new LinkedList<>();

        private ReadSelector() throws IOException {
            selector = Selector.open();
        }

        @Override
        public void run() {
            try {
                while (true) {
                    synchronized (pairQueue) {
                        while (!pairQueue.isEmpty()) {
                            SocketChannel client = pairQueue.peek().getClient();
                            client.configureBlocking(false);
                            client.register(selector, SelectionKey.OP_READ, pairQueue.peek().getBuffers());
                            pairQueue.poll();
                        }
                    }

                    selector.select();

                    Set<SelectionKey> selectionKeys = selector.selectedKeys();
                    Iterator<SelectionKey> keyIterator = selectionKeys.iterator();

                    while (keyIterator.hasNext()) {
                        SelectionKey key = keyIterator.next();

                        SocketChannel channel = (SocketChannel) key.channel();
                        MessageBuffers buffers = (MessageBuffers) key.attachment();

                        try {
                            if (!buffers.readFrom(channel)) {
                                key.cancel();
                            }
                        } catch (IOException e) {
                            System.err.println("Exception caught when working with client socket.");
                            System.err.println(e.getMessage());
                        }

                        keyIterator.remove();
                    }
                }
            } catch (IOException e) {
                System.err.println("Exception in reading selector.");
                System.err.println(e.getMessage());
            }
        }

        public void add(@NotNull SocketChannel client, @NotNull MessageBuffers buffers) throws IOException {
            synchronized (pairQueue) {
                pairQueue.offer(new ClientBufferPair(client, buffers));
                selector.wakeup();
            }
        }


    }

    private class WriteSelector implements Runnable {
        private Selector selector = null;
        private final Queue<ClientBufferPair> pairQueue = new LinkedList<>();

        private WriteSelector() throws IOException {
            selector = Selector.open();
        }

        @Override
        public void run() {
            try {
                while (true) {
                    synchronized (pairQueue) {
                        while (!pairQueue.isEmpty()) {
                            SocketChannel client = pairQueue.peek().getClient();
                            client.register(selector, SelectionKey.OP_WRITE, pairQueue.peek().getBuffers());
                            pairQueue.poll();
                        }
                    }

                    selector.select();

                    Set<SelectionKey> selectionKeys = selector.selectedKeys();
                    Iterator<SelectionKey> keyIterator = selectionKeys.iterator();

                    while (keyIterator.hasNext()) {
                        SelectionKey key = keyIterator.next();

                        SocketChannel channel = (SocketChannel) key.channel();
                        MessageBuffers buffers = (MessageBuffers) key.attachment();

                        try {
                            synchronized (buffers.getClientChannel()) {
                                buffers.sendTo(channel);
                                buffers.messageSent();

                                if (buffers.getMessageCnt() <= 0) {
                                    key.cancel();
                                }
                            }
                        } catch (IOException e) {
                            System.err.println("Exception caught when working with client socket.");
                            System.err.println(e.getMessage());
                        }

                        keyIterator.remove();
                    }
                }
            } catch (IOException e) {
                System.err.println("Exception in writing selector.");
                System.err.println(e.getMessage());
            }
        }

        public void add(@NotNull SocketChannel client, @NotNull MessageBuffers buffers) throws IOException {
            synchronized (pairQueue) {
                pairQueue.offer(new ClientBufferPair(client, buffers));
                selector.wakeup();
            }
        }
    }

    private class ClientBufferPair {
        private SocketChannel client = null;
        private MessageBuffers buffers = null;

        ClientBufferPair(@NotNull SocketChannel client, @NotNull MessageBuffers buffers) {
            this.client = client;
            this.buffers = buffers;
        }

        public SocketChannel getClient() {
            return client;
        }

        public MessageBuffers getBuffers() {
            return buffers;
        }
    }

    private class SortTask implements Runnable {
        private List<Integer> data = null;
        private int size;
        private MessageBuffers buffers = null;

        public SortTask(@NotNull List<Integer> data,
                        @NotNull MessageBuffers buffers) {
            this.data = data;
            size = data.size();
            this.buffers = buffers;
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

            buffers.writeBytes(bytes, sortingStart, sortingEnd);
        }
    }

    private class MessageBuffers {
        private static final int MAX_SIZE = 1 << 20;

        private ByteBuffer read = ByteBuffer.allocate(MAX_SIZE);
        private final ByteBuffer write = ByteBuffer.allocate(MAX_SIZE);

        private final Queue<Long> queue = new LinkedList<>();
        private long clientStart = -1;
        private int size = -1;

        private final SocketChannel clientChannel;
        private long clientTime = 0;
        private long sortingTime = 0;

        private int messageCnt = 0;

        MessageBuffers(@NotNull SocketChannel clientChannel) {
            this.clientChannel = clientChannel;
        }

        public boolean readFrom(@NotNull SocketChannel src) throws IOException {
            if (clientStart == -1) {
                clientStart = System.currentTimeMillis();
            }

            int cnt;
            do {
                cnt = src.read(read);
            } while (cnt > 0);

            read.flip();

            if (size == -1) {
                if (read.remaining() >= 4) {
                    size = read.getInt();
                }
            }

            if (size != -1) {
                if (read.remaining() >= size) {
                    byte[] bytes = new byte[size];
                    for (int i = 0; i < size; i++) {
                        bytes[i] = read.get();
                    }

                    IntArrayProtos.IntArray message = IntArrayProtos.IntArray.parseFrom(bytes);

                    int messageSize = message.getSize();
                    List<Integer> data = message.getDataList();

                    if (messageSize == 0) {
                        IntArrayProtos.IntArray stats = IntArrayProtos.IntArray.newBuilder()
                                .setSize(2)
                                .addData((int)clientTime)
                                .addData((int)sortingTime)
                                .build();

                        bytes = stats.toByteArray();
                        writeBytes(bytes, 0, 0);

                        return false;
                    }

                    pool.submit(new SortTask(data, this));

                    synchronized (queue) {
                        queue.offer(clientStart);
                    }

                    size = -1;
                    clientStart = -1;
                }
            }

            read.compact();

            return true;
        }

        public void writeBytes(byte[] bytes, long sortingStart, long sortingEnd) {
            synchronized (write) {
                write.putInt(bytes.length);
                write.put(bytes);
            }

            synchronized (clientChannel) {
                messageCnt++;

                try {
                    writeSelector.add(clientChannel, this);
                } catch (IOException e) {
                    System.err.println("Exception caught when working with client socket.");
                    System.err.println(e.getMessage());
                }

            }

            sortingTime += sortingEnd - sortingStart;
        }

        public void sendTo(@NotNull SocketChannel dst) throws IOException {
            synchronized (write) {
                write.flip();

                int messageSize = write.getInt();
                byte[] messageBytes = new byte[messageSize];
                write.get(messageBytes, 0, messageSize);

                ByteBuffer messageBuffer = ByteBuffer.allocate(4 + messageSize);
                messageBuffer.putInt(messageSize);
                messageBuffer.put(messageBytes);

                messageBuffer.flip();

                while (messageBuffer.remaining() > 0) {
                    dst.write(messageBuffer);
                }

                messageBuffer.compact();
                write.compact();
            }

            synchronized (queue) {
                if (!queue.isEmpty()) {
                    clientTime += System.currentTimeMillis() - queue.poll();
                }
            }
        }

        public void messageSent() {
            messageCnt--;
        }

        public int getMessageCnt() {
            return messageCnt;
        }

        public SocketChannel getClientChannel() {
            return clientChannel;
        }
    }
}
