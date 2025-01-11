package com.andrelucs.filesharingapp.communication.client;

import com.andrelucs.filesharingapp.communication.FileInfo;
import com.andrelucs.filesharingapp.communication.ProtocolCommand;

import java.util.ArrayList;
import java.util.List;

public class ServerResponseHandler {

    private final Client client;
    private final List<SearchEventListener> searchResultListeners;

    public ServerResponseHandler(Client client) {
        this.client = client;
        this.searchResultListeners = new ArrayList<>();
    }

    public void handleResponse(String response) {
        var parts = response.split(" ");
        var responseType = ProtocolCommand.fromString(parts[0]);
        switch (responseType) {
            case CONFIRMJOIN -> client.setConnected(true);
            case CONFIRMLEAVE -> client.setConnected(false);
            case CONFIRMCREATEFILE -> client.confirmFileSharing(response.split(" ")[1]);
            case CONFIRMDELETEFILE -> client.confirmUnsharedFile(response.split(" ")[1]);
            case FILE -> fileRequestHandler(parts);
            default -> System.out.println("Unknown response: " + response);
        }
    }

    private void fileRequestHandler(String[] parts) {
        try {
            String fileName = Client.decodeFileName(parts[1]);
            String fileOwner = parts[2];
            long fileSize = Long.parseLong(parts[3]);
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