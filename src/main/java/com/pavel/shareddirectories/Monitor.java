package com.pavel.shareddirectories;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Monitor{
    private static final Logger LOGGER = LoggerFactory.getLogger(Monitor.class);
    /** коллекция, в которой хранится подтвержденное состояние директории */
    private final ConcurrentHashMap<File, Long> sharedDirIndex;
    private final ChangesSender changesSender;
    /** путь до используемой директории */
    private final File sharedDirPath;
    /** объект для синхронизации монитора и сервера */
    private final Object locker;
    /** поток в котором запускается проверка директории */
    private volatile Thread client;
    /** флаг для остановки проверки директории */
    private volatile boolean clientClosed;

    public Monitor(ConcurrentHashMap <File, Long> sharedDirIndex, ChangesSender changesSender, File sharedDirPath,
                   Object locker) {
        this.changesSender = changesSender;
        this.sharedDirPath = sharedDirPath;
        this.sharedDirIndex = sharedDirIndex;
        this.locker = locker;
    }
    /** запускает {@link Monitor#check()} в отдельном демон-потоке */
    public synchronized void start() {
        if (client==null) {
            client = new Thread(this::check);
            client.setName("monitor daemon thread");
            client.setDaemon(true);
            client.start();
            LOGGER.debug("com.pavel.shareddirectories.Monitor started");
        } else {
            LOGGER.debug("start denied. com.pavel.shareddirectories.Monitor already started");
           }
    }

    /**Проверяет произошедшие изменения в указанной директории {@link Monitor#sharedDirPath}(создание, изменение или
     * удаление файла) и отправляет их на сервер с помощью {@link ChangesSender}. после подтверждения записывает изменения
     * в {@link Monitor#sharedDirIndex}.
     * @see SharedDirService
     * @see ChangesSender
     */
    private void check() {
        while (!clientClosed) {
            LOGGER.debug("client start new iteration");
            Set<File> filesToCreate = new HashSet<>();
            Set<File> filesToModify = new HashSet<>();
            Set<File> filesToDelete = new HashSet<>();
            HashSet<File> currentFilesInPath = new HashSet<>();
            synchronized (locker) {
                LOGGER.debug("client entered synchronized block");
                for (File element : Objects.requireNonNull(sharedDirPath.listFiles())) {
                    if (element.isHidden() || element.isDirectory()){
                        continue;
                    }
                    currentFilesInPath.add(element);
                    if (!sharedDirIndex.containsKey(element)) {
                        LOGGER.debug("client found new file {}", element.getName());
                        filesToCreate.add(element);
                    } else if (sharedDirIndex.get(element) != element.lastModified()) {
                        LOGGER.debug("client found mod file {}", element.getName());
                        filesToModify.add(element);
                    }
                }
                for (File element : sharedDirIndex.keySet())
                    if (!currentFilesInPath.contains(element)) {
                        LOGGER.debug("client found del file {}", element.getName());
                        filesToDelete.add(element);
                    }
                LOGGER.debug("client end of synchronized block");
            }
            LOGGER.debug("client start send block");
            for (File element : filesToCreate) {
                if (changesSender.sendNew(element)) {
                    sharedDirIndex.put(element, element.lastModified());
                }
            }
            for (File element : filesToModify){
                if (changesSender.sendModify(element)){
                    sharedDirIndex.put(element,element.lastModified());
                }
            }
            for (File element : filesToDelete) {
                if (changesSender.sendDelete(element)) {
                    sharedDirIndex.remove(element);
                }
            }
            LOGGER.debug("client end send block and iteration");
            try {
                //noinspection BusyWait
                Thread.sleep(2000);
            } catch (InterruptedException ignore) { }
        }
    }
    /** Пытается корректно завершить метод {@link Monitor#check()} за выделенное время, иначе обрывает все
     * соединения
     * @param timeoutMs время, выделяемое для штатного завершения цикла, в милисекундах
     */
    public synchronized void close(long timeoutMs) {
        clientClosed = true;
        try {
            try {
                client.join(timeoutMs);
            } catch (InterruptedException e) {
                LOGGER.info("Thread interrupted ",e);
            }
            changesSender.close();
        } catch (IOException e) {
            LOGGER.info("Exception occurred during socket closing", e);
        }

    }

}
