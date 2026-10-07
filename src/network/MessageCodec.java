package network;

import model.DisconnectMessage;
import model.JoinMessage;
import model.Message;
import model.SystemMessage;
import model.UserListMessage;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Codec for encoding and decoding Message and DisconnectMessage objects to/from wire protocol.
 * Supports four protocol types:
 * - CHAT|&lt;base64-sender&gt;|&lt;base64-text&gt;|&lt;ISO-timestamp&gt;
 * - DISCONNECT|&lt;base64-sender&gt;|&lt;ISO-timestamp&gt;
 * - JOIN|&lt;base64-sender&gt;|&lt;ISO-timestamp&gt;
 * - SYSTEM|&lt;event-type&gt;|&lt;base64-text&gt;|&lt;ISO-timestamp&gt;
 * - USER_LIST|&lt;base64-payload&gt;|&lt;ISO-timestamp&gt;
 */
public class MessageCodec {
    
    private static final String CHAT_PREFIX = "CHAT|";
    private static final String DISCONNECT_PREFIX = "DISCONNECT|";
    private static final String JOIN_PREFIX = "JOIN|";
    private static final String SYSTEM_PREFIX = "SYSTEM|";
    private static final String USER_LIST_PREFIX = "USER_LIST|";
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    
    /**
     * Protocol type enumeration.
     */
    public enum ProtocolType {
        CHAT,
        JOIN,
        DISCONNECT,
        SYSTEM,
        USER_LIST,
        UNKNOWN
    }
    
    private MessageCodec() {
        throw new AssertionError("No instances");
    }
    
    /**
     * Decode UTF-8 bytes strictly, rejecting malformed sequences.
     * @param bytes The bytes to decode
     * @param fieldName The field name for error messages
     * @return The decoded string
     * @throws MessageFormatException if UTF-8 is malformed
     */
    private static String decodeUtf8(byte[] bytes, String fieldName) throws MessageFormatException {
        try {
            return StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
        } catch (CharacterCodingException e) {
            throw new MessageFormatException("Malformed UTF-8 in " + fieldName, e);
        }
    }
    
    /**
     * Detect the protocol type of a line without fully decoding it.
     * @param line The wire line to inspect
     * @return The detected protocol type
     */
    public static ProtocolType detectType(String line) {
        if (line == null || line.trim().isEmpty()) {
            return ProtocolType.UNKNOWN;
        }
        
        if (line.startsWith(CHAT_PREFIX)) {
            return ProtocolType.CHAT;
        }

        if (line.startsWith(JOIN_PREFIX)) {
            return ProtocolType.JOIN;
        }
        
        if (line.startsWith(DISCONNECT_PREFIX)) {
            return ProtocolType.DISCONNECT;
        }

        if (line.startsWith(SYSTEM_PREFIX)) {
            return ProtocolType.SYSTEM;
        }

        if (line.startsWith(USER_LIST_PREFIX)) {
            return ProtocolType.USER_LIST;
        }
        
        return ProtocolType.UNKNOWN;
    }
    
    /**
     * Encode a Message to a wire protocol line.
     * @param message The message to encode
     * @return The encoded wire line
     * @throws IllegalArgumentException if message is null
     */
    public static String encode(Message message) {
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        
        String encodedSender = ENCODER.encodeToString(message.getSender().getBytes(StandardCharsets.UTF_8));
        String encodedText = ENCODER.encodeToString(message.getText().getBytes(StandardCharsets.UTF_8));
        String timestamp = message.getTimestamp().format(TIMESTAMP_FORMATTER);
        
        String result = CHAT_PREFIX + encodedSender + "|" + encodedText + "|" + timestamp;
        
        // Defensive check: codec invariant should never produce newlines or carriage returns
        if (result.contains("\n") || result.contains("\r")) {
            throw new IllegalStateException("Codec invariant violation: encoded output contains newline or carriage return");
        }
        
        return result;
    }
    
    /**
     * Encode a DisconnectMessage to a wire protocol line.
     * @param message The disconnect message to encode
     * @return The encoded wire line
     * @throws IllegalArgumentException if message is null
     */
    public static String encodeDisconnect(DisconnectMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("DisconnectMessage cannot be null");
        }
        
        String encodedSender = ENCODER.encodeToString(message.getSender().getBytes(StandardCharsets.UTF_8));
        String timestamp = message.getTimestamp().format(TIMESTAMP_FORMATTER);
        
        String result = DISCONNECT_PREFIX + encodedSender + "|" + timestamp;
        
        // Defensive check: codec invariant should never produce newlines or carriage returns
        if (result.contains("\n") || result.contains("\r")) {
            throw new IllegalStateException("Codec invariant violation: encoded output contains newline or carriage return");
        }
        
        return result;
    }
    
    /**
     * Decode a wire protocol line to a Message.
     * @param line The wire line to decode
     * @return The decoded Message
     * @throws MessageFormatException if the line is malformed or invalid
     */
    public static Message decode(String line) throws MessageFormatException {
        if (line == null) {
            throw new MessageFormatException("Line cannot be null");
        }
        if (line.trim().isEmpty()) {
            throw new MessageFormatException("Line cannot be blank");
        }
        if (!line.startsWith(CHAT_PREFIX)) {
            throw new MessageFormatException("Invalid protocol prefix");
        }
        
        String[] fields = line.split("\\|", -1);
        if (fields.length != 4) {
            throw new MessageFormatException("Expected 4 fields, got " + fields.length);
        }
        
        if (!fields[0].equals("CHAT")) {
            throw new MessageFormatException("Invalid protocol type: " + fields[0]);
        }
        
        String encodedSender = fields[1];
        String encodedText = fields[2];
        String timestampStr = fields[3];
        
        if (encodedSender.isEmpty()) {
            throw new MessageFormatException("Encoded sender is empty");
        }
        if (encodedText.isEmpty()) {
            throw new MessageFormatException("Encoded text is empty");
        }
        if (timestampStr.isEmpty()) {
            throw new MessageFormatException("Timestamp is empty");
        }
        
        String sender;
        String text;
        LocalDateTime timestamp;
        
        try {
            byte[] senderBytes = DECODER.decode(encodedSender);
            sender = decodeUtf8(senderBytes, "sender");
        } catch (IllegalArgumentException e) {
            throw new MessageFormatException("Invalid Base64 in sender", e);
        }
        
        try {
            byte[] textBytes = DECODER.decode(encodedText);
            text = decodeUtf8(textBytes, "text");
        } catch (IllegalArgumentException e) {
            throw new MessageFormatException("Invalid Base64 in text", e);
        }
        
        try {
            timestamp = LocalDateTime.parse(timestampStr, TIMESTAMP_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new MessageFormatException("Invalid timestamp format", e);
        }
        
        // Rely on Message constructor for semantic validation
        try {
            return new Message(sender, text, timestamp);
        } catch (IllegalArgumentException e) {
            throw new MessageFormatException("Invalid message content: " + e.getMessage(), e);
        }
    }
    
    /**
     * Decode a wire protocol line to a DisconnectMessage.
     * @param line The wire line to decode
     * @return The decoded DisconnectMessage
     * @throws MessageFormatException if the line is malformed or invalid
     */
    public static DisconnectMessage decodeDisconnect(String line) throws MessageFormatException {
        if (line == null) {
            throw new MessageFormatException("Line cannot be null");
        }
        if (line.trim().isEmpty()) {
            throw new MessageFormatException("Line cannot be blank");
        }
        if (!line.startsWith(DISCONNECT_PREFIX)) {
            throw new MessageFormatException("Invalid protocol prefix");
        }
        
        String[] fields = line.split("\\|", -1);
        if (fields.length != 3) {
            throw new MessageFormatException("Expected 3 fields, got " + fields.length);
        }
        
        if (!fields[0].equals("DISCONNECT")) {
            throw new MessageFormatException("Invalid protocol type: " + fields[0]);
        }
        
        String encodedSender = fields[1];
        String timestampStr = fields[2];
        
        if (encodedSender.isEmpty()) {
            throw new MessageFormatException("Encoded sender is empty");
        }
        if (timestampStr.isEmpty()) {
            throw new MessageFormatException("Timestamp is empty");
        }
        
        String sender;
        LocalDateTime timestamp;
        
        try {
            byte[] senderBytes = DECODER.decode(encodedSender);
            sender = decodeUtf8(senderBytes, "sender");
        } catch (IllegalArgumentException e) {
            throw new MessageFormatException("Invalid Base64 in sender", e);
        }
        
        try {
            timestamp = LocalDateTime.parse(timestampStr, TIMESTAMP_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new MessageFormatException("Invalid timestamp format", e);
        }
        
        // Rely on DisconnectMessage constructor for semantic validation
        try {
            return new DisconnectMessage(sender, timestamp);
        } catch (IllegalArgumentException e) {
            throw new MessageFormatException("Invalid disconnect message content: " + e.getMessage(), e);
        }
    }

    /**
     * Encode a JoinMessage to a wire protocol line.
     * Format: JOIN|&lt;base64-sender&gt;|&lt;ISO-timestamp&gt;
     * @param message The join message to encode
     * @return The encoded wire line
     * @throws IllegalArgumentException if message is null
     */
    public static String encodeJoin(JoinMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("JoinMessage cannot be null");
        }

        String encodedSender = ENCODER.encodeToString(message.getSender().getBytes(StandardCharsets.UTF_8));
        String timestamp = message.getTimestamp().format(TIMESTAMP_FORMATTER);

        String result = JOIN_PREFIX + encodedSender + "|" + timestamp;

        if (result.contains("\n") || result.contains("\r")) {
            throw new IllegalStateException("Codec invariant violation: encoded output contains newline or carriage return");
        }

        return result;
    }

    /**
     * Decode a wire protocol line to a JoinMessage.
     * @param line The wire line to decode
     * @return The decoded JoinMessage
     * @throws MessageFormatException if the line is malformed or invalid
     */
    public static JoinMessage decodeJoin(String line) throws MessageFormatException {
        if (line == null) {
            throw new MessageFormatException("Line cannot be null");
        }
        if (line.trim().isEmpty()) {
            throw new MessageFormatException("Line cannot be blank");
        }
        if (!line.startsWith(JOIN_PREFIX)) {
            throw new MessageFormatException("Invalid protocol prefix");
        }

        String[] fields = line.split("\\|", -1);
        if (fields.length != 3) {
            throw new MessageFormatException("Expected 3 fields, got " + fields.length);
        }

        if (!fields[0].equals("JOIN")) {
            throw new MessageFormatException("Invalid protocol type: " + fields[0]);
        }

        String encodedSender = fields[1];
        String timestampStr = fields[2];

        if (encodedSender.isEmpty()) {
            throw new MessageFormatException("Encoded sender is empty");
        }
        if (timestampStr.isEmpty()) {
            throw new MessageFormatException("Timestamp is empty");
        }

        String sender;
        LocalDateTime timestamp;

        try {
            byte[] senderBytes = DECODER.decode(encodedSender);
            sender = decodeUtf8(senderBytes, "sender");
        } catch (IllegalArgumentException e) {
            throw new MessageFormatException("Invalid Base64 in sender", e);
        }

        try {
            timestamp = LocalDateTime.parse(timestampStr, TIMESTAMP_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new MessageFormatException("Invalid timestamp format", e);
        }

        // Rely on JoinMessage constructor for semantic validation
        try {
            return new JoinMessage(sender, timestamp);
        } catch (IllegalArgumentException e) {
            throw new MessageFormatException("Invalid join message content: " + e.getMessage(), e);
        }
    }

    /**
     * Encode a SystemMessage to a wire protocol line.
     * Format: SYSTEM|&lt;event-type&gt;|&lt;base64-text&gt;|&lt;ISO-timestamp&gt;
     * Only trusted server code may originate SYSTEM records.
     * @param message The system message to encode
     * @return The encoded wire line
     * @throws IllegalArgumentException if message is null
     */
    public static String encodeSystem(SystemMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("SystemMessage cannot be null");
        }

        String encodedText = ENCODER.encodeToString(message.getText().getBytes(StandardCharsets.UTF_8));
        String timestamp = message.getTimestamp().format(TIMESTAMP_FORMATTER);

        String result = SYSTEM_PREFIX + message.getEventType().name() + "|" + encodedText + "|" + timestamp;

        if (result.contains("\n") || result.contains("\r")) {
            throw new IllegalStateException("Codec invariant violation: encoded output contains newline or carriage return");
        }

        return result;
    }

    /**
     * Decode a wire protocol line to a SystemMessage.
     * @param line The wire line to decode
     * @return The decoded SystemMessage
     * @throws MessageFormatException if the line is malformed or invalid
     */
    public static SystemMessage decodeSystem(String line) throws MessageFormatException {
        if (line == null) {
            throw new MessageFormatException("Line cannot be null");
        }
        if (line.trim().isEmpty()) {
            throw new MessageFormatException("Line cannot be blank");
        }
        if (!line.startsWith(SYSTEM_PREFIX)) {
            throw new MessageFormatException("Invalid protocol prefix");
        }

        String[] fields = line.split("\\|", -1);
        if (fields.length != 4) {
            throw new MessageFormatException("Expected 4 fields, got " + fields.length);
        }

        if (!fields[0].equals("SYSTEM")) {
            throw new MessageFormatException("Invalid protocol type: " + fields[0]);
        }

        String eventTypeStr = fields[1];
        String encodedText = fields[2];
        String timestampStr = fields[3];

        if (eventTypeStr.isEmpty()) {
            throw new MessageFormatException("Event type is empty");
        }
        if (encodedText.isEmpty()) {
            throw new MessageFormatException("Encoded text is empty");
        }
        if (timestampStr.isEmpty()) {
            throw new MessageFormatException("Timestamp is empty");
        }

        SystemMessage.EventType eventType;
        try {
            eventType = SystemMessage.EventType.valueOf(eventTypeStr);
        } catch (IllegalArgumentException e) {
            throw new MessageFormatException("Invalid event type: " + eventTypeStr, e);
        }

        String text;
        LocalDateTime timestamp;

        try {
            byte[] textBytes = DECODER.decode(encodedText);
            text = decodeUtf8(textBytes, "text");
        } catch (IllegalArgumentException e) {
            throw new MessageFormatException("Invalid Base64 in text", e);
        }

        try {
            timestamp = LocalDateTime.parse(timestampStr, TIMESTAMP_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new MessageFormatException("Invalid timestamp format", e);
        }

        // Rely on SystemMessage constructor for semantic validation
        try {
            return new SystemMessage(eventType, text, timestamp);
        } catch (IllegalArgumentException e) {
            throw new MessageFormatException("Invalid system message content: " + e.getMessage(), e);
        }
    }

    /**
     * Encode a UserListMessage to a wire protocol line.
     * Format: USER_LIST|&lt;base64-payload&gt;|&lt;ISO-timestamp&gt; where the payload
     * decodes to {@code &lt;count&gt;:&lt;len&gt;:&lt;name&gt;...} with lengths in
     * UTF-8 bytes. Only trusted server code may originate USER_LIST records.
     * @param message The user-list message to encode
     * @return The encoded wire line
     * @throws IllegalArgumentException if message is null
     */
    public static String encodeUserList(UserListMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("UserListMessage cannot be null");
        }

        StringBuilder payload = new StringBuilder();
        List<String> names = message.getUsernames();
        payload.append(names.size()).append(':');
        for (String name : names) {
            byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
            payload.append(nameBytes.length).append(':').append(name);
        }

        String encodedPayload = ENCODER.encodeToString(
                payload.toString().getBytes(StandardCharsets.UTF_8));
        String timestamp = message.getTimestamp().format(TIMESTAMP_FORMATTER);

        String result = USER_LIST_PREFIX + encodedPayload + "|" + timestamp;

        if (result.contains("\n") || result.contains("\r")) {
            throw new IllegalStateException("Codec invariant violation: encoded output contains newline or carriage return");
        }

        return result;
    }

    /**
     * Decode a wire protocol line to a UserListMessage.
     * @param line The wire line to decode
     * @return The decoded UserListMessage
     * @throws MessageFormatException if the line is malformed or invalid
     */
    public static UserListMessage decodeUserList(String line) throws MessageFormatException {
        if (line == null) {
            throw new MessageFormatException("Line cannot be null");
        }
        if (line.trim().isEmpty()) {
            throw new MessageFormatException("Line cannot be blank");
        }
        if (!line.startsWith(USER_LIST_PREFIX)) {
            throw new MessageFormatException("Invalid protocol prefix");
        }

        String[] fields = line.split("\\|", -1);
        if (fields.length != 3) {
            throw new MessageFormatException("Expected 3 fields, got " + fields.length);
        }

        if (!fields[0].equals("USER_LIST")) {
            throw new MessageFormatException("Invalid protocol type: " + fields[0]);
        }

        String encodedPayload = fields[1];
        String timestampStr = fields[2];

        if (encodedPayload.isEmpty()) {
            throw new MessageFormatException("Encoded payload is empty");
        }
        if (timestampStr.isEmpty()) {
            throw new MessageFormatException("Timestamp is empty");
        }

        byte[] payloadBytes;
        try {
            payloadBytes = DECODER.decode(encodedPayload);
        } catch (IllegalArgumentException e) {
            throw new MessageFormatException("Invalid Base64 in payload", e);
        }
        String payload = decodeUtf8(payloadBytes, "payload");

        List<String> names = parseUserListPayload(payload);

        LocalDateTime timestamp;
        try {
            timestamp = LocalDateTime.parse(timestampStr, TIMESTAMP_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new MessageFormatException("Invalid timestamp format", e);
        }

        // Rely on UserListMessage constructor for final semantic validation.
        try {
            return new UserListMessage(names, timestamp);
        } catch (IllegalArgumentException e) {
            throw new MessageFormatException("Invalid user-list content: " + e.getMessage(), e);
        }
    }

    /**
     * Parse a {@code &lt;count&gt;:&lt;len&gt;:&lt;name&gt;...} payload with
     * lengths measured in UTF-8 bytes.
     */
    private static List<String> parseUserListPayload(String payload) throws MessageFormatException {
        byte[] raw = payload.getBytes(StandardCharsets.UTF_8);
        int pos = 0;

        int count = readPayloadInt(raw, pos, "count");
        pos = payloadIntEnd(raw, pos);
        expectPayloadColon(raw, pos, "count");
        pos++;
        if (count <= 0) {
            throw new MessageFormatException("User count must be positive, got " + count);
        }
        if (count > 10000) {
            throw new MessageFormatException("User count unreasonably large: " + count);
        }

        List<String> names = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (pos >= raw.length) {
                throw new MessageFormatException("Truncated payload at user " + i);
            }
            int length = readPayloadInt(raw, pos, "length");
            pos = payloadIntEnd(raw, pos);
            expectPayloadColon(raw, pos, "length");
            pos++;
            if (length <= 0) {
                throw new MessageFormatException("Username length must be positive, got " + length);
            }
            if (pos + length > raw.length) {
                throw new MessageFormatException("Truncated username at user " + i);
            }
            byte[] nameBytes = new byte[length];
            System.arraycopy(raw, pos, nameBytes, 0, length);
            pos += length;
            names.add(decodeUtf8(nameBytes, "username"));
        }
        if (pos != raw.length) {
            throw new MessageFormatException("Trailing unparsed payload bytes: "
                    + (raw.length - pos));
        }
        return names;
    }

    private static int readPayloadInt(byte[] raw, int pos, String field) throws MessageFormatException {
        if (pos >= raw.length || raw[pos] < '0' || raw[pos] > '9') {
            throw new MessageFormatException("Malformed payload: expected " + field + " digits");
        }
        long value = 0;
        while (pos < raw.length && raw[pos] >= '0' && raw[pos] <= '9') {
            value = value * 10 + (raw[pos] - '0');
            if (value > Integer.MAX_VALUE) {
                throw new MessageFormatException("Malformed payload: " + field + " overflow");
            }
            pos++;
        }
        return (int) value;
    }

    private static int payloadIntEnd(byte[] raw, int pos) {
        while (pos < raw.length && raw[pos] >= '0' && raw[pos] <= '9') {
            pos++;
        }
        return pos;
    }

    private static void expectPayloadColon(byte[] raw, int pos, String field) throws MessageFormatException {
        if (pos >= raw.length || raw[pos] != ':') {
            throw new MessageFormatException("Malformed payload: expected ':' after " + field);
        }
    }
}
