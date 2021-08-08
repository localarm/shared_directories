package com.pavel.shareddirectories;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;

public class SocketChangesSender implements ChangesSender {
    private static final Logger LOGGER = LoggerFactory.getLogger(SocketChangesSender.class);
    /** ip адрес удаленного пользователя */
    private final String ipAddress;
    /** порт сервера удаленного пользователя */
    private final int remoteServerPort;
    /** сокет для коммуникации с удаленным пользователем */
    private volatile Socket socket;

    public SocketChangesSender(String clientIp, int remoteServerPort) {
        this.ipAddress = clientIp;
        this.remoteServerPort = remoteServerPort;
    }

    /**Отправляет файл и его содержимое на сервер через сокет
     * @param file путь к отправляемому файлу
     * @param receiverInstruction сигнал для сервера с инструкциями (1 - новый файл, 2 модифицированный файл, 3 - удаленный файл)
     * @return true если файл был отправлен и получен подтверждающий это сигнал от сервера, false в остальных случаях
     */
    private boolean send(File file, int receiverInstruction) {
            try (Socket client = new Socket(ipAddress, remoteServerPort);
                 DataOutputStream out = new DataOutputStream((new BufferedOutputStream(client.getOutputStream())));
                 DataInputStream inwaiter = new DataInputStream((new BufferedInputStream(client.getInputStream())))) {
                 socket = client;
                 client.setSoTimeout(50000);
                 out.writeInt(receiverInstruction);
                 LOGGER.debug("client sent instruction to server: {}", receiverInstruction);
                 out.writeUTF(file.getName());
                 LOGGER.debug("client sent name to server {}", file.getName());
                 out.flush();
                 sendFileContent(out, file);
                 LOGGER.debug("client finished send to server, starts to wait answer from server");
                 boolean success = inwaiter.readInt() == SharedDirService.SUCCESS_SIGNAL;
                 LOGGER.debug("client received {} answer from server ", success);
                 return success;
            } catch (FileNotFoundException e) {
                 LOGGER.warn("File {} does not exist", file.getName());
                 return false;
            } catch (IOException e) {
                LOGGER.warn("IO exception during file sending", e);
                return false;
            }
    }

    @Override
    public boolean sendNew(File fnew) {
        LOGGER.debug("client try to send new file");
        return send(fnew, SharedDirService.NEW_FILE);
    }

    @Override
    public boolean sendModify(File fmod) {
        LOGGER.debug("client try to send mod file");
        return send(fmod, SharedDirService.MODIFY_FILE);
    }

    @Override
    public boolean sendDelete(File fdel) {
        LOGGER.debug("client try to send del file");
        return send(fdel, SharedDirService.DELETE_FILE);
    }

    @Override
    public void close() throws IOException {
        if(socket!=null) {
            socket.close();
        }
    }

    /**читает содержимое указанного файла и отправляет его через output stream
     * @param out OutputStream через который отправляется файл
     * @param file путь к файлу, котоырй необходимо отправить
     * @throws IOException if I/O errors occurred
     */
    private void sendFileContent(DataOutputStream out, File file) throws IOException{
        if (file.exists()) {
            InputStream fileReader = new FileInputStream(file);
            out.writeLong(file.length());
            LOGGER.debug("client start send content of file to server");
            IOUtils.copy(fileReader, out);
            out.flush();
        }
    }
}
