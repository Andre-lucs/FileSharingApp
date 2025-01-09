package com.andrelucs.filesharingapp.communication;

/**
 * Enum representing the protocol commands used in the file sharing application.
 * Each command has a specific format and may require certain attributes.
 */
public enum ProtocolCommand {
    // Client -> Server Requests
    /**
     * JOIN command.<br/>
     * Format: JOIN arg0<br/>
     * Attributes: [0] - Client IP address
     */
    JOIN("JOIN %s"),
    /**
     * LEAVE command.<br/>
     * Format: LEAVE<br/>
     * Attributes: None
     */
    LEAVE("LEAVE"),
    /**
     * CREATEFILE command.<br/>
     * Format: CREATEFILE arg0 arg1<br/>
     * Attributes: [0] - File name, [1] - File size
     */
    CREATEFILE("CREATEFILE %s %s"),
    /**
     * DELETEFILE command.<br/>
     * Format: DELETEFILE arg0<br/>
     * Attributes: [0] - File name
     */
    DELETEFILE("DELETEFILE %s"),
    /**
     * SEARCH command.<br/>
     * Format: SEARCH arg0<br/>
     * Attributes: [0] - Search pattern
     */
    SEARCH("SEARCH %s"),
    // Server -> Client Responses
    /**
     * CONFIRMJOIN response.<br/>
     * Format: CONFIRMJOIN<br/>
     * Attributes: None
     */
    CONFIRMJOIN("CONFIRMJOIN"),
    /**
     * CONFIRMLEAVE response.<br/>
     * Format: CONFIRMLEAVE<br/>
     * Attributes: None
     */
    CONFIRMLEAVE("CONFIRMLEAVE"),
    /**
     * CONFIRMCREATEFILE response.<br/>
     * Format: CONFIRMCREATEFILE arg0<br/>
     * Attributes: [0] - File name
     */
    CONFIRMCREATEFILE("CONFIRMCREATEFILE %s"),
    /**
     * CONFIRMDELETEFILE response.<br/>
     * Format: CONFIRMDELETEFILE arg0<br/>
     * Attributes: [0] - File name
     */
    CONFIRMDELETEFILE("CONFIRMDELETEFILE %s"),
    /**
     * FILE response.<br/>
     * Format: FILE arg0 arg1 arg2<br/>
     * Attributes: [0] - File name, [1] - File owner, [2] - File size
     */
    FILE("FILE %s %s %s"),

    // Client -> Client Requests
    /**
     * GET command.<br/>
     * Format: GET arg0 arg1-arg2<br/>
     * Attributes: [0] - File name, [1] - Start byte, [2] (Optional: "") - End byte
     */
    GET("GET %s %s-%s"),
    ;

    private final String template;

    ProtocolCommand(String template) {
        this.template = template;
    }

    public String format(Object... args) {
        return String.format(template, args);
    }

    public static ProtocolCommand fromString(String command) {
        try {
            return ProtocolCommand.valueOf(command);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e);
        }
    }
}
