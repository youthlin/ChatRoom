package com.youthlin.chatroom.client.control;

import com.youthlin.chatroom.client.Client;
import com.youthlin.chatroom.share.model.Message;
import com.youthlin.chatroom.share.model.UserInfo;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ConnectException;
import java.net.Socket;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * Created by lin on 2016-06-08-008.
 * 登录控制器
 */
public class LoginController implements Initializable {
    public TextField urlTextFiled;
    public TextField portTextField;
    public TextField nameTextField;
    public Button loginButton;
    public Label tipLabel;
    private static final Logger LOG = LoggerFactory.getLogger(LoginController.class);
    private Client client;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        LOG.trace("初始化登录控制器");
    }

    public void setClient(Client c) {
        client = c;
    }

    public void setTip(String tip) {
        tipLabel.setText(tip);
    }

    public void onLoginButtonClick(ActionEvent actionEvent) {
        String serverUrl = urlTextFiled.getText().trim();
        int serverPort;
        if (nameTextField.getText().trim().equals("")) {
            tipLabel.setText("用户名不能为空");
            return;
        }
        try {
            serverPort = Integer.parseInt(portTextField.getText().trim());
        } catch (NumberFormatException e) {
            serverPort = 5678;
            tipLabel.setText("端口格式错误,尝试默认端口");
        }
        try {
            Socket socket = new Socket(serverUrl, serverPort);
            new Thread(new LoginHandler(socket)).start();

        } catch (ConnectException e) {
            tipLabel.setText("连接失败，请检查地址、端口及网络连通性");
        } catch (IOException e) {
            e.printStackTrace();
            tipLabel.setText("连接失败，网络错误");
        }
    }

    private class LoginHandler implements Runnable {
        Socket socket;
        private ObjectOutputStream out;
        private ObjectInputStream in;

        LoginHandler(Socket socket) {
            this.socket = socket;
            try {
                //服务器先in再out客户端就要先out再in。
                //只能new一次，否则会出异常，因此，之后要用in/out时需要把这里的传过去而不能通过socket获得
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());
                LOG.trace("输出化输入输出流完毕");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            Message msg = new Message();
            msg.setType(Message.LOGIN);
            msg.setUser(nameTextField.getText());
            msg.setIp(socket.getInetAddress().getHostAddress());
            try {
                out.writeObject(msg);
                out.flush();
                msg = (Message) in.readObject();
                LOG.trace("接收消息=" + msg);
                if (msg.getType() == Message.LOGIN_SUCCESS) {
                    client.enterMain(new UserInfo(nameTextField.getText(), socket, in, out));
                } else {
                    String content = msg.getContent();
                    Platform.runLater(() -> tipLabel.setText(content));
                }
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }

        }
    }
}
