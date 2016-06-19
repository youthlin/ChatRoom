package com.youthlin.chatroom.server;
/**
 * Created by lin on 2016-06-08-008.
 * 服务器
 */

import com.youthlin.chatroom.server.control.ServerController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.fxml.JavaFXBuilderFactory;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

public class Server extends Application {
    private static final Logger LOG = LoggerFactory.getLogger(Server.class);
    private Initializable controller;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        LOG.trace("程序启动");
        String fxml = "/server.fxml";
        FXMLLoader loader = new FXMLLoader();
        InputStream in = getClass().getResourceAsStream(fxml);
        loader.setBuilderFactory(new JavaFXBuilderFactory());
        loader.setLocation(getClass().getResource(fxml));
        Parent root = loader.load(in);
        controller = loader.getController();
        if (controller instanceof ServerController)
            ((ServerController) controller).setServer(this);
        primaryStage.setTitle("聊天室 - 服务器");
        primaryStage.setScene(new Scene(root));
        primaryStage.sizeToScene();
        primaryStage.centerOnScreen();
        primaryStage.show();
    }

    @Override
    public void stop() {
        if (controller instanceof ServerController){
            ((ServerController) controller).exit();}
    }
}
