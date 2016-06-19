package com.youthlin.chatroom.client.control;

import com.youthlin.chatroom.client.Client;
import com.youthlin.chatroom.share.control.MsgListCell;
import com.youthlin.chatroom.share.model.Message;
import com.youthlin.chatroom.share.model.UserInfo;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.youthlin.chatroom.share.model.Message.*;

/**
 * Created by lin on 2016-06-08-008.
 * 客户端控制器
 */
public class MainController implements Initializable {
    public Label myNameLabel;
    public Button logoutButton;
    public ListView<Message> chatMsgListView;
    private ObservableList<Message> chatMsg;
    public ListView<String> onlineUserList;
    private ObservableList<String> users;
    public Button clearButton;
    public TextArea msgTextArea;
    public Button sendButton;
    private static final Logger LOG = LoggerFactory.getLogger(MainController.class);
    private ThreadPoolExecutor executor;
    private Client client;
    private boolean exit;
    private UserInfo userInfo;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        LOG.trace("初始化主界面控制器");
        executor = new ThreadPoolExecutor(10, 20, 10, TimeUnit.MINUTES, new ArrayBlockingQueue<>(10));
        chatMsg = FXCollections.observableArrayList();
        chatMsgListView.setItems(chatMsg);
        chatMsgListView.setCellFactory((param) -> new MsgListCell());
        users = FXCollections.observableArrayList();
        onlineUserList.setItems(users);
    }

    public void init(UserInfo info) {
        this.userInfo = info;
        myNameLabel.setText("欢迎您，" + userInfo.name);
        executor.execute(this::process);
    }

    public void setClient(Client c) {
        client = c;
    }

    private void process() {
        LOG.trace("监听服务器消息...");
        try {
            while (!exit) {
                Message msg = (Message) userInfo.in.readObject();
                LOG.trace("已接收消息:" + msg);
                switch (msg.getType()) {
                    case CHAT:
                        Platform.runLater(() -> addChatMsg(msg));
                        break;
                    case UserAdd:
                        Platform.runLater(() -> userAdd(msg.getUser() + "(" + msg.getIp() + ")"));
                        break;
                    case UserDelete:
                        Platform.runLater(() -> userDelete(msg.getUser() + "(" + msg.getIp() + ")"));
                        break;
                    case SERVER_SHUTDOWN:
                        exit = true;
                        client.login("服务器已关闭");
                        break;
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            logout("服务器已关闭");
        }
    }

    public void onLogoutButtonClick(ActionEvent actionEvent) {
        executor.execute(()->logout(""));
    }

    public void onClearButtonClick(ActionEvent actionEvent) {
        chatMsg.clear();
    }

    public void onClickSendButton(ActionEvent actionEvent) {
        String content = msgTextArea.getText();
        if (content.trim().length() > 0) {
            Message msg = new Message();
            msg.setType(CHAT);
            msg.setContent(content);
            msg.setUser(userInfo.name);
            msg.setIp(userInfo.socket.getInetAddress().getHostAddress());
            send(msg);
        }
        msgTextArea.setText("");
    }

    public void logout(String tip) {
        Message msg = new Message();
        msg.setType(Message.LOGOUT);
        msg.setUser(userInfo.name);
        msg.setIp(userInfo.socket.getInetAddress().getHostAddress());
        msg.setContent("用户注销");
        send(msg);
        exit = true;
        client.login(tip);
    }

    private void addChatMsg(Message msg) {
        chatMsg.add(msg);
    }

    private void userAdd(String name) {
        if (!users.contains(name))
            users.add(name);
    }

    private void userDelete(String name) {
        users.remove(name);
    }

    private void send(Message msg) {
        try {
            LOG.trace("发送消息:" + msg.toString());
            userInfo.out.writeObject(msg);
            userInfo.out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
