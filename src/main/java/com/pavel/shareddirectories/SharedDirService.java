package com.pavel.shareddirectories;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class SharedDirService {
    /** сигнал для отправки нового файла от клиента к серверу */
    static final int NEW_FILE = 1;
    /** сигнал для отправки измененного файла от клиента к серверу */
    static final int MODIFY_FILE = 2;
    /** сигнал для удаления файла от клиента к серверу */
    static final int DELETE_FILE = 3;
    /** сигнал об успешности операции */
    static final int SUCCESS_SIGNAL = 0;
    /** сигнал об неудачности операции */
    static final int BAD_SIGNAL = 1;
    private static final Logger LOGGER = LoggerFactory.getLogger(SharedDirService.class);
    /** объект для синхронизации монитора и сервера */
    private static final Object locker = new Object();
    /** коллекция, в которой хранится подтвержденное состояние директории */
    private final ConcurrentHashMap<File, Long> sharedDirIndex = new ConcurrentHashMap<>();
    /** путь до используемой директории */
    private final File sharedDirPath;
    /** ip адрес удаленного пользователя */
    private final String ipAddress;
    /** порт сервера текущего пользователя */
    private final int serverPort;
    /** порт сервера удаленного пользователя */
    private final int remoteServerPort;
    private Monitor monitor;

    private ChangesReceiverServer server;
    /** флаг, защищающий от повторного старта SharedDirService*/
    private volatile boolean startGuard = true;
    /** флаг, защищающий от повторного финиша SharedDirService*/
    private volatile boolean finishGuard = true;


    public SharedDirService(String sharedDirPath, String ipAddress, int serverPort, int remoteServerPort) {
        this.sharedDirPath = new File(sharedDirPath);
        this.ipAddress = ipAddress;
        this.serverPort = serverPort;
        this.remoteServerPort = remoteServerPort;
    }

    /**запускает SharedDirService
     */
    public synchronized void start(){
        if(startGuard){
            startGuard=false;
            if (!sharedDirPath.isDirectory()) {
                throw new FileAccessException("Wrong path to directory");
            }
            if (!sharedDirPath.canWrite()) {
                throw new FileAccessException("Readonly directory");
            }
            File downloadPath = new File(this.sharedDirPath, ".download");
            if (!downloadPath.mkdir() && !downloadPath.exists()) {
                throw new DownloadCacheException("Cannot create download cache directory");
            } else {
                for (File element : Objects.requireNonNull(downloadPath.listFiles())) {
                    if (!element.delete()) {
                        throw new FileAccessException("Download cache clean failed");
                    }
                }
            }
            for (File file : Objects.requireNonNull(sharedDirPath.listFiles())) {
                sharedDirIndex.put(file, file.lastModified());
            }
            ChangesReceiver receiver = new SocketReceiver(sharedDirIndex, this.sharedDirPath, downloadPath);
            server = new ChangesReceiverServer(receiver, locker, serverPort);
            ChangesSender sender = new SocketChangesSender(ipAddress, remoteServerPort);
            monitor = new Monitor(sharedDirIndex, sender, this.sharedDirPath, locker);
            monitor.start();
            server.start();
            LOGGER.info("SharedDIrService started");
        }
    }

    /** пытается остановить сервер и монитор за выделенное время
     * @param serverTimeout время для корректного завершения цикла сервера
     * @param monitorTimeout время для корректного завершения цикла монитора
     */
    public synchronized void finish(long serverTimeout,long monitorTimeout) {
        if (finishGuard && !startGuard) {
            finishGuard = false;
            server.close(serverTimeout);
            monitor.close(monitorTimeout);
        }
    }

}

