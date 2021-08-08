package com.pavel.shareddirectories;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.Scanner;

public class SharedDirStarter {
    private static final Logger LOGGER = LoggerFactory.getLogger(SharedDirStarter.class);
    public static void main(String[] args) {
        LOGGER.info("start");
        String propertyPath = "config.properties"; //default value
        if (args.length > 0) {
            propertyPath = args[0];
        }
        try {
            FileInputStream  fis = new FileInputStream(new File(propertyPath));
            Properties config = new Properties();
            config.load(fis);
            Scanner in = new Scanner(System.in);
            SharedDirService sharedDirService = new SharedDirService(config.getProperty("directoryPath"),
                    config.getProperty("ipAdress"),Integer.parseInt(config.getProperty("serverPort")),
                    Integer.parseInt(config.getProperty("remoteserverPort")));
            sharedDirService.start();
            //noinspection StatementWithEmptyBody
            while (!in.next().equals("q")){
                // do nothing
            }
            sharedDirService.finish(60000, 60000);
        } catch (IOException ioe){
            LOGGER.error("Properties loading failed");
        }

    }

}

