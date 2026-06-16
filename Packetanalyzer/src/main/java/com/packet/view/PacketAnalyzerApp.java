package com.packet.view;

import com.packet.controller.MainController;
import javafx.application.Application;
import javafx.stage.Stage;

/**
 * JavaFX application entry — launches the UI through {@link MainController}.
 */
public final class PacketAnalyzerApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        new MainController(primaryStage).show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
