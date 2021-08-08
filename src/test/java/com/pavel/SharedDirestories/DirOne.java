package com.pavel.SharedDirestories;
import java.util.Scanner;
import com.pavel.shareddirectories.*;


public class DirOne {
    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        String directory ="src/test/resources/FirstDirectory";

        SharedDirService sharedDirService = new SharedDirService(directory,"localhost",6052, 6053);

        sharedDirService.start();

        while (!in.next().equals("q")){
            // do nothing
        }
        sharedDirService.finish(60000,60000);
    }
}
