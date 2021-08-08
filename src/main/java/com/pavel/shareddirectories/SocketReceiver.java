package com.pavel.shareddirectories;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.concurrent.ConcurrentHashMap;

public class SocketReceiver implements ChangesReceiver {
    private static final Logger LOGGER = LoggerFactory.getLogger(SocketReceiver.class);
    /** коллекция, в которой хранится подтвержденное состояние директории */
    private final ConcurrentHashMap<File, Long> sharedDirIndex;
    /** путь до используемой директории */
    private final File sharedDirPath;
    /** путь до кеш-директории */
    private final File downloadDirPath;

    SocketReceiver( ConcurrentHashMap<File, Long> sharedDirIndex, File sharedDirPath, File downloadDirPath) {
        this.sharedDirIndex = sharedDirIndex;
        this.sharedDirPath = sharedDirPath;
        this.downloadDirPath= downloadDirPath;
    }

    /**Получает необходимую информацию о создании/изменении/удалении файла и выполняет это действие, временно хранит
     * в {@link SocketReceiver#downloadDirPath} скачиваемый файл.
     * @param in input stream
     * @param onlyDeleteFile флаг, если true, то запускает инструкции удаления файла, иначе инструкции создания/изменения
     * @return File если файл успешно получен/изменён/удалён, null в иных случаях
     */
    private File receive(InputStream in, boolean onlyDeleteFile) {
        File temporaryFile= null;
        try (DataInputStream ins = new DataInputStream(new BufferedInputStream(in))){
            File file = new File(sharedDirPath,ins.readUTF());
            LOGGER.debug("received file {}", file.getName());
            if (file.isDirectory()) {
                LOGGER.warn("Names conflict (Directory with same name already exists)");
                return null;
            }
            if (onlyDeleteFile){//if received delete file signal
                if (file.exists() && !file.delete()){
                    throw new IOException("Cannot delete file");
                }
            }else {//if received create or modify file signal
                LOGGER.debug("entered create/modify block");
                temporaryFile = new File(downloadDirPath, file.getName());
                if (!temporaryFile.createNewFile()) {//never happened in usual situation
                    LOGGER.warn("Temporary file was not delete early");
                    temporaryFile.delete();
                    if (!temporaryFile.createNewFile()) {
                    throw new IOException("Temporary file with same name already exists");
                    }
                }
                LOGGER.debug("created file{}", temporaryFile.getName());
                fillTemporaryFile(ins, temporaryFile);
                LOGGER.debug("temporaryFile {} downloaded", temporaryFile.getName());
                if (file.exists() && !file.delete()) {
                    throw new IOException("Cannot delete existing file");
                }
                if(!temporaryFile.renameTo(file)){
                    throw new IOException("Cannot replace existing file with received one");
                }
            }
            return file;
        } catch (IOException e){
            LOGGER.debug("receiver IO exception", e);
            if (temporaryFile!=null) {
                if (!temporaryFile.delete() && temporaryFile.exists()) {
                LOGGER.error("Temporary file {} delete issue", temporaryFile.getName());
                }
            }
            return null;
        }
    }

    /**
     * try to create new file and put in
     * @param in input stream with needful data
     * @return file path if receive operation ends correctly, null otherwise
     */
    @Override
    public File receiveToCreate(InputStream in){
        LOGGER.debug("try to receive new file");
        File file = receive(in,false);
        if (file!=null){
            LOGGER.debug("new file received");
            sharedDirIndex.put(file,file.lastModified());
        }
        else
            LOGGER.debug("file receive failed");
        return file;
    }
    @Override
    public File receiveToMod(InputStream in){
        LOGGER.debug("try to receive modify file");
        File file = receive(in,false);
        if (file!=null){
            LOGGER.debug("modify file received");
            sharedDirIndex.put(file,file.lastModified());
        } else {
            LOGGER.debug("file receive failed");
        }
        return file;
    }
    @Override
    public File receiveToDel(InputStream in){
        LOGGER.debug("try to receive del file");
        File file = receive(in,true);
        if (file!=null) {
            LOGGER.debug("file deleted");
            sharedDirIndex.remove(file);
        }
        else
            LOGGER.debug("file receive failed");
        return file;
    }

    /**получает содержимое файла через input stream и записывает его в указанный файл
     * @param ins input stream, который содержит необходимую для наполнения файла информацию (размер файла и его содержание)
     * @param temporaryFile путь до существующего файла
     * @throws IOException если размер полученного файла не совпадает с указанным размером
     */
    private void fillTemporaryFile (DataInputStream ins, File temporaryFile) throws IOException {
        long fileLength = ins.readLong();
        LOGGER.debug("started to write content in temporaryFile");
        fileLength-=IOUtils.copyLarge(ins, new FileOutputStream(temporaryFile),0,fileLength);
        LOGGER.debug("ended to write content in temporaryFile");
        if (fileLength > 0) {
            throw new IOException("File was not received full");
        }
    }
}
