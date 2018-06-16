package ru.spbau.farutin.arch_test.ui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import ru.spbau.farutin.arch_test.client.Client;
import ru.spbau.farutin.arch_test.server.ServerType;
import ru.spbau.farutin.arch_test.util.Statistic;

import java.util.ArrayList;

/**
 * MainMenuController - controller for scene with main menu.
 */
public class MainMenuController {
    @FXML
    private TextField hostAddress;
    @FXML
    private TextField x;
    @FXML
    private TextField from;
    @FXML
    private TextField to;
    @FXML
    private TextField step;
    @FXML
    private TextField n;
    @FXML
    private TextField m;
    @FXML
    private TextField d;

    @FXML
    private ComboBox<String> typeCombo;
    @FXML
    private ComboBox<String> parameterCombo;

    /**
     * Starts testing.
     */
    @FXML
    public void start() {
        String host = hostAddress.getCharacters().toString();
        int queriesNumber = Integer.parseInt(x.getCharacters().toString());
        int arraySize = Integer.parseInt(n.getCharacters().toString());
        int clientNumber = Integer.parseInt(m.getCharacters().toString());
        int delta = Integer.parseInt(d.getCharacters().toString());

        int begin = Integer.parseInt(from.getCharacters().toString());
        int end = Integer.parseInt(to.getCharacters().toString());
        int inc = Integer.parseInt(step.getCharacters().toString());

        int incN = 0;
        int incM = 0;
        int incD = 0;

        String parameter = parameterCombo.getSelectionModel().getSelectedItem();

        if (parameter != null) {
            switch (parameter) {
                case "N":
                    arraySize = begin;
                    incN = inc;
                    break;
                case "M":
                    clientNumber = begin;
                    incM = inc;
                    break;
                case "d":
                    delta = begin;
                    incD = inc;
                    break;
            }
        }

        int serverType = Integer.parseInt(typeCombo.getSelectionModel().getSelectedItem());

        Statistic statistic = new Statistic(
                ServerType.values()[serverType],
                queriesNumber,
                arraySize, incN,
                clientNumber, incM,
                delta, incD,
                begin, end, inc);

        ArrayList<Thread> threads = new ArrayList<>();

        for (int i = 0; i < clientNumber; i++) {
            threads.add(new Thread());
        }

        for (int current = begin; current <= end; current += inc) {
            Client client = new Client(host, arraySize, queriesNumber, delta);

            for (int i = 0; i < clientNumber; i++) {
                threads.set(i, new Thread(client));
            }

            for (int i = 0; i < clientNumber; i++) {
                threads.get(i).start();
            }

            for (int i = 0; i < clientNumber; i++) {
                try {
                    threads.get(i).join();
                } catch (InterruptedException e) {
                    System.err.println("Interrupted while waiting for all clients!");
                    return;
                }
            }

            double clientTimeServer = client.getClientTimeServer() / queriesNumber / clientNumber;
            double queryTimeServer = client.getQueryTimeServer() / queriesNumber / clientNumber;
            double clientTime = client.getClientTime() / queriesNumber / clientNumber;

            statistic.update(clientTimeServer, queryTimeServer, clientTime);

            arraySize += incN;
            delta += incD;
            clientNumber += incM;

            for (int i = 0; i < incM; i++) {
               threads.add(new Thread());
            }
        }

        statistic.save();
    }

    /**
     * Exits application.
     */
    @FXML
    public void exit() {
        Platform.exit();
    }
}
