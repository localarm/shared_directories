package com.pavel.shareddirectories;

import java.io.File;
import java.io.IOException;


public interface ChangesSender {

    boolean sendNew(File fnew);

    boolean sendModify(File fmod);

    boolean sendDelete(File fdel);

    void close() throws IOException;
}
