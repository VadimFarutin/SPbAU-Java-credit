package ru.spbau.farutin.arch_test.util;

import org.jetbrains.annotations.NotNull;
import ru.spbau.farutin.arch_test.server.ServerType;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

public class Statistic {
    private ServerType serverType = null;
    private int x;
    private int n;
    private int incN;
    private int m;
    private int incM;
    private int d;
    private int incD;
    private int from;
    private int to;
    private int step;

    private ArrayList<Double> clientTimeServerValues = new ArrayList<>();
    private ArrayList<Double> queryTimeServerValues = new ArrayList<>();
    private ArrayList<Double> clientTimeValues = new ArrayList<>();

    public Statistic(@NotNull ServerType serverType,
              int x,
              int n, int incN,
              int m, int incM,
              int d, int incD,
              int from, int to, int step) {
        this.serverType = serverType;
        this.x = x;
        this.n = n;
        this.incN = incN;
        this.m = m;
        this.incM = incM;
        this.d = d;
        this.incD = incD;
        this.from = from;
        this.to = to;
        this.step = step;
    }

    public void update(double clientTimeServer, double queryTimeServer, double clientTime) {
        clientTimeServerValues.add(clientTimeServer);
        queryTimeServerValues.add(queryTimeServer);
        clientTimeValues.add(clientTime);
    }

    public void save() {
        try {
            StringBuilder builder = new StringBuilder();
            builder.append("./");
            builder.append(serverType.toString());
            builder.append('_');
            builder.append(incN);
            builder.append('_');
            builder.append(incM);
            builder.append('_');
            builder.append(incD);
            builder.append(".txt");

            File file = new File(builder.toString());

            if (!file.exists()) {
                if (!file.createNewFile()) {
                    throw new IOException();
                }
            }

            PrintWriter out = new PrintWriter(file);

            out.println(serverType.toString());
            out.println("X: " + x);
            out.println("N: " + n);
            out.println("M: " + m);
            out.println("d: " + d);

            if (incN != 0) {
                out.println("N changes:");
                out.print("step: " + incN);
            } else if (incM != 0) {
                out.println("M changes:");
                out.print("step: " + incM);
            } else {
                out.println("delta changes:");
                out.print("step: " + incD);
            }

            out.println(" from: " + from + " to: " + to);

            for (int cur = from; cur <= to; cur += step) {
                out.print(cur);
                out.print(' ');
            }

            out.println();
            out.println("client on server: ");

            for (Double t : clientTimeServerValues) {
                out.print(t);
                out.print(' ');
            }

            out.println();
            out.println("query on server: ");

            for (Double t : queryTimeServerValues) {
                out.print(t);
                out.print(' ');
            }

            out.println();
            out.println("client local: ");

            for (Double t : clientTimeValues) {
                out.print(t);
                out.print(' ');
            }

            out.println();

            out.close();
        } catch (IOException e) {
            System.err.println("Failed to save to file");
        }
    }
}
