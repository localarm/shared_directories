package com.pavel.shareddirectories;

import java.io.File;
import java.io.InputStream;

public interface ChangesReceiver {

    File receiveToCreate(InputStream in);

    File receiveToMod(InputStream in);

    File receiveToDel(InputStream in);
}
