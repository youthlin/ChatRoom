package com.youthlin.chatroom.client;

import com.youthlin.chatroom.client.control.LoginController;
import com.youthlin.chatroom.client.control.MainController;
import com.youthlin.chatroom.share.model.UserInfo;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.fxml.JavaFXBuilderFactory;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by lin on 2016-06-08-008.
 * 客户端
 */
public class Client extends Application {
    private static final Logger LOG = LoggerFactory.getLogger(Client.class);
    private Stage stage;
    private Initializable controller;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        stage = primaryStage;
        stage.setTitle("聊天室 - 客户端");
        login("");
    }

    @Override
    public void stop() {
        LOG.trace("关闭程序");
        if (controller instanceof MainController)
            ((MainController) controller).logout("");
        System.exit(0);
    }

    public void login(String tip) {
        try {
            LoginController loginController = (LoginController) replace("/client_login.fxml");
            loginController.setClient(this);
            loginController.setTip(tip);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void enterMain(UserInfo u) {
        LOG.trace("即将进入主界面...");
        try {
            MainController mainController = (MainController) replace("/client_main.fxml");
            mainController.init(u);
            mainController.setClient(this);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Initializable replace(String fxml) throws IOException {
        FXMLLoader loader = new FXMLLoader();
        InputStream in = getClass().getResourceAsStream(fxml);
        loader.setBuilderFactory(new JavaFXBuilderFactory());
        loader.setLocation(getClass().getResource(fxml));
        VBox vBox = loader.load(in);
        in.close();
        Platform.runLater(() -> {
            Scene scene = new Scene(vBox);
            stage.setScene(scene);
            stage.sizeToScene();
            stage.centerOnScreen();
            stage.show();
        });
        LOG.trace("已设置主界面 " + fxml);
        controller = loader.getController();
        return controller;
    }
}
