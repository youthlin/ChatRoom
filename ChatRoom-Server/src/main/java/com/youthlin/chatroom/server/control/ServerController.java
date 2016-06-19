package com.youthlin.chatroom.server.control;

import com.youthlin.chatroom.server.Server;
import com.youthlin.chatroom.share.control.MsgListCell;
import com.youthlin.chatroom.share.model.Message;
import com.youthlin.chatroom.share.model.UserInfo;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.youthlin.chatroom.share.model.Message.CHAT;
import static com.youthlin.chatroom.share.model.Message.LOGOUT;

/**
 * Created by lin on 2016-06-08-008.
 * 控制器
 */
public class ServerController implements Initializable {
    public TextField serverPort;
    public Button startButton;
    public Button clearButton;
    public ListView<Message> msgListView;
    private ObservableList<Message> messages;
    public ListView<String> onlineUserList;
    private ObservableList<String> onlineUsers;
    public TextArea broadcastTextArea;
    public Button sendButton;
    private Server server;
    private static final Logger LOG = LoggerFactory.getLogger(ServerController.class);
    private boolean started;
    private ServerSocket serverSocket;
    private ThreadPoolExecutor executor;
    private final String sys = "系统消息";
    private final String srv = "服务器";
    private String srvIP = "localhost";
    private int port;
    private ConcurrentHashMap<String, UserInfo> clients;

    public Server getServer() {
        return server;
    }

    public void setServer(Server server) {
        this.server = server;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        LOG.trace("初始化控制器...");
        broadcastTextArea.setDisable(true);
        sendButton.setDisable(true);
        executor = new ThreadPoolExecutor(20, 30, 10, TimeUnit.MINUTES, new ArrayBlockingQueue<>(10));

        messages = FXCollections.observableArrayList();
        msgListView.setItems(messages);
        msgListView.setCellFactory((param) -> new MsgListCell());
        onlineUsers = FXCollections.observableArrayList();
        onlineUserList.setItems(onlineUsers);

        clients = new ConcurrentHashMap<>();
    }

    public void exit() {
        LOG.trace("即将退出服务器...");
        executor.shutdownNow();
    }

    public void onClickStartButton(ActionEvent actionEvent) {
        if (!started) {
            //开启服务器
            startServer();
        } else {
            //关闭服务器
            stopServer();
        }
    }

    private void startServer() {
        String portStr = serverPort.getText();
        if (portStr.trim().length() > 0) {
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                Message msg = new Message();
                msg.setContent("端口号不正确");
                msg.setUser(sys);
                msg.setDate(new Date());
                msg.setIp("localhost");
                msg.setType(CHAT);
                messages.add(msg);
                LOG.trace(msg.toString());
                return;
            }
            try {
                serverSocket = new ServerSocket(port);
                srvIP = serverSocket.getInetAddress().getHostAddress();
                setDisable(true);

                executor.execute(this::process);

            } catch (IOException e) {
                e.printStackTrace();
                setDisable(false);
                serverSocket = null;
            }
        }
    }

    private void stopServer() {
        LOG.trace("即将关闭服务器...");
        Message msg = new Message();
        msg.setUser(srv);
        msg.setType(Message.SERVER_SHUTDOWN);
        msg.setIp(srvIP);
        //在发送广播时会处理关闭事项
        executor.execute(() -> sendBroadCast(msg));
    }

    private void setDisable(boolean started) {
        this.started = started;
        broadcastTextArea.setDisable(!started);
        sendButton.setDisable(!started);
        serverPort.setDisable(started);
        Message msg = new Message();
        msg.setUser(sys);
        msg.setType(CHAT);
        msg.setIp(srvIP);
        if (started) {
            startButton.setText("关闭服务器");
            msg.setContent("服务器已开启,监听端口为" + port);
        } else {
            msg.setContent("服务器已关闭");
            startButton.setText("开启服务器");
        }
        Platform.runLater(() -> messages.add(msg));
    }

    private void process() {
        //服务器开启后调用
        //这里不再是FX线程了
        try {
            while (true) {
                if (!started) break;//服务器关闭则结束
                Socket socket = serverSocket.accept();//新的客户端连接
                executor.execute(new Handler(socket));//处理新的客户端
            }
        } catch (IOException e) {
            e.printStackTrace();
            LOG.trace(e.getMessage());
        }
    }

    private class Handler implements Runnable {
        private Socket socket;
        private ObjectOutputStream out;
        private ObjectInputStream in;

        Handler(Socket s) {
            socket = s;
            try {
                //服务器先in再out客户端就要先out再in。
                //只能new一次，否则会出异常，因此，之后要用in/out时需要把这里的传过去而不能通过socket获得
                out = new ObjectOutputStream(s.getOutputStream());
                in = new ObjectInputStream(s.getInputStream());
                LOG.trace("初始化输入输出流完毕");
            } catch (IOException e) {
                e.printStackTrace();
                LOG.trace(e.getMessage());
            }
        }

        @Override
        public void run() {
            try {
                Message msg = (Message) in.readObject();
                if (msg.getType() == Message.LOGIN) {
                    String name = msg.getUser();
                    Message loginResult = new Message();
                    loginResult.setUser(sys);
                    boolean loginFail = clients.containsKey(name);
                    if (loginFail) {
                        loginResult.setType(Message.LOGIN_FAIL);
                        loginResult.setContent("用户名已存在，请重新选择用户名");
                    } else {
                        loginResult.setType(Message.LOGIN_SUCCESS);
                        loginResult.setContent("登录成功");
                    }
                    out.writeObject(loginResult);
                    out.flush();
                    if (loginFail) return;//登录失败直接断开，线程结束

                    userLogin(name, new UserInfo(name, socket, in, out));

                    //region 登录成功循环处理消息
                    while (!socket.isClosed()) {
                        Message message = (Message) in.readObject();
                        LOG.trace("从[" + name + "]接收消息:" + message);
                        switch (message.getType()) {
                            case CHAT:
                                sendBroadCast(message);
                                break;
                            case LOGOUT:
                                userLogout(message.getUser(), clients.get(message.getUser()));
                        }
                    }//endregion
                } else {
                    LOG.trace("不是登录消息，不予理会");
                }
            } catch (IOException | ClassNotFoundException e) {
                LOG.trace(e.getMessage());
            }
        }

    }//handler

    private void userLogin(String name, UserInfo userInfo) {
        //添加在列表中
        clients.put(name, userInfo);
        Platform.runLater(() -> onlineUsers.add(name + "(" + userInfo.socket.getInetAddress().getHostAddress() + ")"));
        //发送新用户加入广播
        Message msg = new Message();
        msg.setContent("[" + name + "]已加入聊天室,当前人数为" + clients.size());
        msg.setType(CHAT);
        msg.setUser(sys);
        msg.setIp(srvIP);
        sendBroadCast(msg);

        Set<Map.Entry<String, UserInfo>> entrySet = clients.entrySet();
        for (Map.Entry<String, UserInfo> entry : entrySet) {
            //发送用户
            msg = new Message();
            msg.setIp(entry.getValue().socket.getInetAddress().getHostAddress());
            msg.setUser(entry.getKey());
            msg.setType(Message.UserAdd);
            sendBroadCast(msg);
        }

    }

    private void userLogout(String name, UserInfo userInfo) {
        clients.remove(name);
        Platform.runLater(() -> onlineUsers.remove(name + "(" + userInfo.socket.getInetAddress().getHostAddress() + ")"));
        Message msg = new Message();
        msg.setContent("[" + name + "]已退出聊天室,当前人数为" + clients.size());
        msg.setType(CHAT);
        msg.setUser(sys);
        msg.setIp(srvIP);
        sendBroadCast(msg);
        Message message = new Message();
        message.setType(Message.UserDelete);
        message.setUser(name);
        message.setIp(userInfo.socket.getInetAddress().getHostAddress());
        sendBroadCast(message);
    }

    public void onClickClearButton(ActionEvent actionEvent) {
        messages.clear();
    }

    public void onClickSendButton(ActionEvent actionEvent) {
        String content = broadcastTextArea.getText();
        if (content.trim().length() > 0) {
            Message msg = new Message();
            msg.setType(CHAT);
            msg.setUser(srv);
            msg.setIp(srvIP);
            msg.setContent(content);
            sendBroadCast(msg);
        }
        broadcastTextArea.setText("");
    }

    private void sendBroadCast(Message msg) {
        LOG.trace("发送广播消息:" + msg);
        Set<Map.Entry<String, UserInfo>> s = clients.entrySet();
        //发送给每一个客户端
        for (Map.Entry<String, UserInfo> entry : s)
            send(entry.getValue(), msg);
        if (msg.getType() == CHAT) {
            Platform.runLater(() -> messages.add(msg));//添加到服务器消息列表
        } else if (msg.getType() == Message.SERVER_SHUTDOWN) {
            //关闭服务器
            clients.clear();
            Platform.runLater(() -> {
                onlineUsers.clear();
                setDisable(false);
            });
            try {
                Socket socket = new Socket("localhost", port);
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                Message m = new Message();
                m.setType(Message.INVALID);
                //发送一个不是LOGIN类型的新的套接字会直接结束那个login线程
                out.writeObject(m);
                out.flush();
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            LOG.trace("已关闭服务器");
        }
    }

    private void send(UserInfo userInfo, Message msg) {
        LOG.trace("向" + userInfo.name + "发送消息" + msg);
        try {
            userInfo.out.writeObject(msg);
            userInfo.out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
