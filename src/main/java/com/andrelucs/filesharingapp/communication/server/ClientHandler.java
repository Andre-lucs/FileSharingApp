package com.andrelucs.filesharingapp.communication.server;

import com.andrelucs.filesharingapp.communication.FileInfo;
import com.andrelucs.filesharingapp.communication.ProtocolCommand;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static com.andrelucs.filesharingapp.communication.ProtocolCommand.*;

public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final Server server;
    private BufferedReader reader;
    private PrintWriter writer;

    public ClientHandler(Socket clientSocket, Server server) {
        this.clientSocket = clientSocket;
        this.server = server;
        try {
            reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
            writer = new PrintWriter(clientSocket.getOutputStream(), true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        String request;
        try {
            while ((request = reader.readLine()) != null) {
                System.out.println("Request received: " + request);
                var requestType = handleRequest(request);
                if (requestType == LEAVE) break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            stop();
        }
    }

    /**
     * Handles the client request and sends the appropriate response.
     *
     * @param request The client request.
     * @return The protocol command that was handled.
     */
    private ProtocolCommand handleRequest(String request) {
        String[] parts = request.split(" ", 2);
        String[] args = parts.length > 1 ? parts[1].split(" ") : new String[0];
        var requestType = ProtocolCommand.fromString(parts[0]);
        switch (requestType) {
            case JOIN -> joinRequest();
            case CREATEFILE -> createFileRequest(args);
            case DELETEFILE -> deleteFileRequest(args);
            case SEARCH -> fileSearchRequest(args);
            case LEAVE -> leaveRequest();
            default -> System.out.println("Unknown request: " + request);
        }
        return requestType;
    }

    private void joinRequest() {
        System.out.println("Confirming join request...");
        writer.println(CONFIRMJOIN.format());
    }

    private void createFileRequest(String[] args) {
        // The file name is the first part of the args array, and the file size is the last part
        // Making sure that if the filename has spaces, it is correctly parsed
        String fileName = Arrays.stream(args).filter(arg -> !arg.equals(args[args.length - 1])).reduce((a, b) -> a + " " + b).orElse("");
        Long fileSize = Long.parseLong(args[args.length - 1]);
        server.addToFileList(new FileInfo(fileName, clientIp(), fileSize));
        System.out.println("File created: " + fileName);
        writer.println(CONFIRMCREATEFILE.format(fileName));
    }

    private void deleteFileRequest(String[] args) {
        String fileName = args[0];
        server.removeFromFileList(clientIp(), fileName);
        System.out.println("File deleted: " + fileName);
        writer.println(CONFIRMDELETEFILE.format(fileName));
    }

    private void fileSearchRequest(String[] args) {
        String fileName = Arrays.stream(args).reduce((a, b) -> a + b).orElse("");
        var files = server.searchFile(fileName);
        System.out.println(files);
        files.forEach(fileInfo -> writer.println(FILE.format(fileInfo.name(), fileInfo.owner(), fileInfo.size())));
    }

    private void leaveRequest() {
        writer.println(CONFIRMLEAVE.format());
    }

    public String clientIp() {
        return clientSocket.getInetAddress().getHostAddress();
    }

    public void stop() {
        try {
            System.out.println("Client disconnected: " + clientIp());

            server.removeClient(clientSocket);
            reader.close();
            writer.close();
//            clientSocket.close(); // This line is commented because the server is closing the socket
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
