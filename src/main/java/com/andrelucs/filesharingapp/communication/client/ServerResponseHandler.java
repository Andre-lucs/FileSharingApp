package com.andrelucs.filesharingapp.communication.client;

import com.andrelucs.filesharingapp.communication.FileInfo;
import com.andrelucs.filesharingapp.communication.ProtocolCommand;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ServerResponseHandler {

    private final Client client;
    private final List<SearchEventListener> searchResultListeners;

    public ServerResponseHandler(Client client) {
        this.client = client;
        this.searchResultListeners = new ArrayList<>();
    }

    public void handleResponse(String response) {
        System.out.println("Response: " + response);
        var parts = response.split(" ");
        var responseType = ProtocolCommand.fromString(parts[0]);
        String filename = null;
        if(responseType == ProtocolCommand.CONFIRMCREATEFILE || responseType == ProtocolCommand.CONFIRMDELETEFILE) {
            filename = response.substring(response.indexOf(" ") + 1); // Filename starts after the first space
        }
        switch (responseType) {
            case CONFIRMJOIN -> client.setConnected(true);
            case CONFIRMLEAVE -> client.setConnected(false);
            case CONFIRMCREATEFILE -> client.confirmFileSharing(filename);
            case CONFIRMDELETEFILE -> client.confirmUnsharedFile(filename);
            case FILE -> fileRequestHandler(parts);
            case null, default -> System.out.println("Unknown response: " + response);
        }
    }

    private void fileRequestHandler(String[] parts) {
        try {
            String fileName = Arrays.asList(parts).subList(1, parts.length - 2).stream().reduce((a, b) -> a + " " + b).orElse("");
            String fileOwner = parts[parts.length - 2];
            long fileSize = Long.parseLong(parts[parts.length - 1]);
            FileInfo file = new FileInfo(fileName, fileOwner, fileSize);
            searchResultListeners.forEach(listener -> listener.onFileReceived(file));
        } catch (NumberFormatException e) {
            System.out.println("Error parsing file size: " + e.getMessage());
        }
    }

    public void addSearchResultListener(SearchEventListener listener) {
        searchResultListeners.add(listener);
    }

    public void removeSearchResultListener(SearchEventListener listener) {
        searchResultListeners.remove(listener);
    }
}