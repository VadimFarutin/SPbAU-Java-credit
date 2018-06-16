package ru.spbau.farutin.arch_test.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class MainServer {
    public static final int PORT_NUMBER = 4444;

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("\nUsage: gradlew runServer -Parg=<server type>\n" +
                    "0 one client per thread\n" +
                    "1 blocking server\n" +
                    "2 non blocking server");
            return;
        }

        int index = Integer.parseInt(args[0]);

        if (index < 0 || index >= ServerType.values().length) {
            System.err.println("Wrong server type number");
            return;
        }

        ServerType serverType = ServerType.values()[index];
        Server server = null;

        switch (serverType) {
            case ONE_CLIENT_PER_THREAD:
                server = new OneClientPerThreadServer(PORT_NUMBER);
                break;
            case BLOCKING_SERVER:
                server = new BlockingServer(PORT_NUMBER);
                break;
            case NON_BLOCKING_SERVER:
                server = new NonBlockingServer(PORT_NUMBER);
                break;
        }

        Thread thread = new Thread(server);
        thread.start();

        System.out.println("\nType quit to stop.\n");

        BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
        try {
            while (true) {
                String input = stdIn.readLine();
                if (input != null && input.equals("quit")) {
                    break;
                }
            }
        } catch (IOException ignored) {
        }

        server.stop();
    }
}
