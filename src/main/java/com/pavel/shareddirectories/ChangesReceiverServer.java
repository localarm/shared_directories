package com.pavel.shareddirectories;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class ChangesReceiverServer{
    private static final Logger LOGGER = LoggerFactory.getLogger(ChangesReceiverServer.class);
    private final ChangesReceiver receiver;
    /** объект для синхронизации монитора и сервера */
    private final Object locker;
    /** порт сервера текущего пользователя */
    private final int port;
    /** сокет для прослушивания входящих соединений от удаленного пользователя*/
    private volatile ServerSocket serverSocket;
    /** сокет для коммуникации с удаленным пользователем */
    private volatile Socket client;
    /** поток в котором запускается проверка серверного сокета*/
    private volatile Thread server;
    private volatile boolean serverClosed;

    public ChangesReceiverServer(ChangesReceiver reciever, Object locker, int port) {
        this.receiver = reciever;
        this.port = port;
        this.locker = locker;
    }
    /**запускает сервер в отдельном потоке */
    public synchronized void start(){
        if (server== null) {
            serverClosed = false;
            server = new Thread(this::check);
            server.setName("server Thread");
            server.start();
            LOGGER.debug("server started");
        } else {
            LOGGER.debug("Start denied. Server already started");
        }
    }
    /**Прослушивает {@link ChangesReceiverServer#serverSocket}, создает {@link ChangesReceiverServer#client} соединение,
     * проверяет полученную инструкцию, перенаправляя ее исполнение на {@link ChangesReceiverServer#receiver}. По резултату
     * выполнения {@link ChangesReceiverServer#receiver} отправляет подтверждающий сигнал на сервер.
     */
    private void check(){
        try (ServerSocket s = new ServerSocket(port)) {
            serverSocket = s;
            while (!serverClosed) {
                LOGGER.debug("server start new iteration");
                try (Socket client = serverSocket.accept();
                    DataInputStream in = new DataInputStream(new BufferedInputStream(client.getInputStream()));
                    DataOutputStream outanswer = new DataOutputStream(
                    new BufferedOutputStream(client.getOutputStream()))) {
                    this.client = client;
                    File successFile = null;
                    client.setSoTimeout(50000);
                    synchronized (locker) {
                        switch (in.readInt()) {
                            case (SharedDirService.NEW_FILE):
                                LOGGER.debug("server want to receive new file");
                                successFile = receiver.receiveToCreate(in);
                                break;
                            case (SharedDirService.MODIFY_FILE):
                                LOGGER.debug("server want to receive mod file");
                                successFile = receiver.receiveToMod(in);
                                break;
                            case (SharedDirService.DELETE_FILE):
                                LOGGER.debug("server want to receive del file");
                                successFile = receiver.receiveToDel(in);
                                break;
                        }
                        if (successFile != null) {
                            LOGGER.debug("File successfully received");
                            outanswer.writeInt(SharedDirService.SUCCESS_SIGNAL);
                            LOGGER.debug("sent success signal");
                        } else {
                            outanswer.writeInt(SharedDirService.BAD_SIGNAL);
                            LOGGER.debug("sent bad signal");
                        }
                        outanswer.flush();
                    }
                } catch (IOException e) {
                    if (!serverClosed) {
                        LOGGER.warn("server IO exception ", e);
                    }
                }
                LOGGER.debug("server end iteration");
            }
        } catch (IOException e) {
            LOGGER.error("Problem with server socket", e);
        }
        LOGGER.debug("server end of check");
    }

    /** Пытается корректно завершить метод {@link ChangesReceiverServer#check()} за выделенное время, иначе обрывает все
     * соединения
     * @param timeoutMs время, выделяемое для штатного завершения цикла, в милисекундах
     */
     public synchronized void close(long timeoutMs){
        serverClosed = true;
        try {
            try {
                server.join(timeoutMs);
            } catch (InterruptedException e) {
                LOGGER.warn("Server join interrupted ", e);
            }
            if (client!=null && !client.isClosed()) {
                client.close();
            }
            if (serverSocket!=null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            LOGGER.info("Exception occurred during socket closing", e);
        }
    }
}
