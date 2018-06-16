package ru.spbau.farutin.arch_test.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;

public class MainApplication extends Application {
    public static void main(String[] args) {
        Application.launch(MainApplication.class, args);
    }

    @Override
    public void start(Stage stage) throws IOException {
        stage.setTitle("Arch Test");
        stage.setResizable(false);

        FXMLLoader fxmlLoader = new FXMLLoader(
                new File("./src/main/resources/main_menu.fxml").toURI().toURL());
        Parent root = fxmlLoader.load();
        Scene scene = new Scene(root, 600, 700);

        stage.setScene(scene);
        stage.show();
    }
}
