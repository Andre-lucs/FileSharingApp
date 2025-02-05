package com.andrelucs.filesharingapp.communication.client.file;

import java.io.*;
import java.util.Properties;

public class FileShareTracker {
    private static FileShareTracker instance;
    private final Properties shareCounts = new Properties();
    private final File storageFile;

    public FileShareTracker() throws IOException {
        this.storageFile = new File("sharingAmount.properties");
        if(!storageFile.exists()) {
            if(!storageFile.createNewFile()){
                throw new IOException("Failed to create file for tracking how many times a file was shared.");
            }
        }
        if (storageFile.exists()) {
            try (FileInputStream in = new FileInputStream(storageFile)) {
                shareCounts.load(in);
            }
        }
    }

    public static FileShareTracker getInstance() {
        if(instance == null) {
            try {
                instance = new FileShareTracker();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return instance;
    }

    // Increment the share count for a file
    public void incrementShareCount(String fileName) throws IOException {
        int count = getShareCount(fileName) + 1;
        shareCounts.setProperty(fileName, Integer.toString(count));
        save();
    }

    // Get the share count for a file
    public int getShareCount(String fileName) {
        return Integer.parseInt(shareCounts.getProperty(fileName, "0"));
    }

    // Reset the share count for a file
    public void resetShareCount(String fileName) throws IOException {
        shareCounts.remove(fileName);
        save();
    }

    // Save the share counts to the file
    private void save() throws IOException {
        try (FileOutputStream out = new FileOutputStream(storageFile)) {
            shareCounts.store(out, null);
        }
    }
}