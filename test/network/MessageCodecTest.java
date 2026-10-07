package network;

import model.DisconnectMessage;
import model.JoinMessage;
import model.Message;
import model.SystemMessage;
import model.UserListMessage;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * Unit tests for MessageCodec class.
 */
public class MessageCodecTest {
    
    private static int testsPassed = 0;
    private static int testsFailed = 0;
    private static int testsSkipped = 0;
    
    public static void main(String[] args) {
        System.out.println("=== MessageCodec Tests ===");
        
        testBasicRoundTrip();
        testUnicodeRoundTrip();
        testPunctuationRoundTrip();
        testTimestampPreservation();
        testSingleLineEncoding();
        testNullEncodeRejection();
        testNullDecodeRejection();
        testBlankLineRejection();
        testIncorrectPrefixRejection();
        testMissingFieldRejection();
        testExtraFieldRejection();
        testInvalidBase64Sender();
        testInvalidBase64Text();
        testInvalidTimestamp();
        testEmptyDecodedSender();
        testEmptyDecodedText();
        testDecodedNewlineRejection();
        testDecodedOverMaximumRejection();
        testMalformedUtf8Sender();
        testMalformedUtf8Text();
        testSenderNewlineRejection();
        testSenderCarriageReturnRejection();
        testSenderOverMaximumRejection();
        testEncodedStartsWithPrefix();
        testEncodedFourFields();
        
        // Disconnect protocol tests
        testDisconnectRoundTrip();
        testUnicodeDisconnectSender();
        testDisconnectTimestampPreservation();
        testDisconnectSingleLineEncoding();
        testDisconnectNoCarriageReturn();
        testNullDisconnectEncodeRejection();
        testNullDisconnectDecodeRejection();
        testWrongDisconnectPrefixRejection();
        testMissingDisconnectFieldRejection();
        testExtraDisconnectFieldRejection();
        testInvalidDisconnectBase64Rejection();
        testMalformedDisconnectUtf8Rejection();
        testInvalidDisconnectTimestampRejection();
        testEmptyDisconnectSenderRejection();
        testDisconnectSenderNewlineRejection();
        testDisconnectSenderOverMaximumRejection();
        testDetectTypeChat();
        testDetectTypeDisconnect();
        testDetectTypeUnknown();
        testDetectTypeNullAndBlank();

        // JOIN protocol tests (Step 9B)
        testJoinRoundTrip();
        testUnicodeJoinUsername();
        testJoinTimestampPreservation();
        testJoinSingleLineEncoding();
        testJoinMissingFieldRejection();
        testJoinExtraFieldRejection();
        testJoinInvalidBase64Rejection();
        testJoinMalformedUtf8Rejection();
        testJoinInvalidTimestampRejection();
        testJoinInvalidUsernameRejection();
        testDetectTypeJoin();

        // SYSTEM protocol tests (Step 9B)
        testSystemRoundTripAllEventTypes();
        testUnicodeSystemText();
        testSystemTimestampPreservation();
        testSystemSingleLineEncoding();
        testSystemMissingFieldRejection();
        testSystemExtraFieldRejection();
        testSystemInvalidEventTypeRejection();
        testSystemInvalidBase64Rejection();
        testSystemMalformedUtf8Rejection();
        testSystemInvalidTimestampRejection();
        testSystemInvalidSemanticRejection();
        testDetectTypeSystem();

        // USER_LIST protocol tests (Step 9C-3B)
        testUserListRoundTrip();
        testUserListHostFirstOrdering();
        testUserListUnicodeUsernames();
        testUserListPunctuationUsernames();
        testUserListTimestampPreservation();
        testUserListSingleLineEncoding();
        testUserListThreeFields();
        testUserListNullEncodeRejection();
        testUserListNullDecodeRejection();
        testUserListBlankDecodeRejection();
        testUserListWrongPrefixRejection();
        testUserListMissingFieldRejection();
        testUserListExtraFieldRejection();
        testUserListInvalidBase64Rejection();
        testUserListMalformedUtf8Rejection();
        testUserListInvalidTimestampRejection();
        testUserListEmptyPayloadRejection();
        testUserListZeroUserRejection();
        testUserListNegativeCountRejection();
        testUserListCountMismatchRejection();
        testUserListInvalidLengthRejection();
        testUserListTruncatedRejection();
        testUserListTrailingPayloadRejection();
        testUserListInvalidSemanticRejection();
        testUserListOverMaximumRejection();
        testDetectTypeUserList();
        
        System.out.println("\n=== Test Summary ===");
        System.out.println("Passed: " + testsPassed);
        System.out.println("Failed: " + testsFailed);
        System.out.println("Skipped: " + testsSkipped);
        
        if (testsFailed > 0) {
            System.exit(1);
        }
    }
    
    private static void testBasicRoundTrip() {
        System.out.print("Test 1: Basic round trip... ");
        
        try {
            LocalDateTime timestamp = LocalDateTime.of(2026, 10, 3, 14, 32, 10);
            Message original = new Message("Alice", "Hello Bob", timestamp);
            
            String encoded = MessageCodec.encode(original);
            Message decoded = MessageCodec.decode(encoded);
            
            if (!decoded.getSender().equals(original.getSender())) {
                System.out.println("FAIL - Sender mismatch");
                testsFailed++;
                return;
            }
            if (!decoded.getText().equals(original.getText())) {
                System.out.println("FAIL - Text mismatch");
                testsFailed++;
                return;
            }
            if (!decoded.getTimestamp().equals(original.getTimestamp())) {
                System.out.println("FAIL - Timestamp mismatch");
                testsFailed++;
                return;
            }
            
            System.out.println("PASS");
            testsPassed++;
            
        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        }
    }
    
    private static void testUnicodeRoundTrip() {
        System.out.print("Test 2: Unicode round trip... ");
        
        try {
            LocalDateTime timestamp = LocalDateTime.of(2026, 10, 3, 14, 32, 10);
            // Use Unicode escapes for compatibility
            String sender = "\u0905\u0932\u093F\u0938"; // अलिस (Alice in Hindi)
            String text = "\u0928\u092E\u0938\u094D\u0924\u0947 Bob \uD83D\uDC4B"; // नमस्ते Bob 👋
            Message original = new Message(sender, text, timestamp);
            
            String encoded = MessageCodec.encode(original);
            Message decoded = MessageCodec.decode(encoded);
            
            if (!decoded.getSender().equals(original.getSender())) {
                System.out.println("FAIL - Sender mismatch");
                testsFailed++;
                return;
            }
            if (!decoded.getText().equals(original.getText())) {
                System.out.println("FAIL - Text mismatch");
                testsFailed++;
                return;
            }
            
            System.out.println("PASS");
            testsPassed++;
            
        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        }
    }
    
    private static void testPunctuationRoundTrip() {
        System.out.print("Test 3: Punctuation round trip... ");
        
        try {
            LocalDateTime timestamp = LocalDateTime.of(2026, 10, 3, 14, 32, 10);
            Message original = new Message("Alice", "Hello! How are you? I'm fine.", timestamp);
            
            String encoded = MessageCodec.encode(original);
            Message decoded = MessageCodec.decode(encoded);
            
            if (!decoded.getText().equals(original.getText())) {
                System.out.println("FAIL - Text mismatch");
                testsFailed++;
                return;
            }
            
            System.out.println("PASS");
            testsPassed++;
            
        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        }
    }
    
    private static void testTimestampPreservation() {
        System.out.print("Test 4: Timestamp preservation... ");
        
        try {
            LocalDateTime timestamp = LocalDateTime.of(2026, 10, 3, 14, 32, 10, 123456789);
            Message original = new Message("Alice", "Hello", timestamp);
            
            String encoded = MessageCodec.encode(original);
            Message decoded = MessageCodec.decode(encoded);
            
            if (!decoded.getTimestamp().equals(original.getTimestamp())) {
                System.out.println("FAIL - Timestamp not preserved");
                testsFailed++;
                return;
            }
            
            System.out.println("PASS");
            testsPassed++;
            
        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        }
    }
    
    private static void testSingleLineEncoding() {
        System.out.print("Test 5: Single-line encoding... ");
        
        try {
            LocalDateTime timestamp = LocalDateTime.of(2026, 10, 3, 14, 32, 10);
            Message message = new Message("Alice", "Hello Bob", timestamp);
            
            String encoded = MessageCodec.encode(message);
            
            if (encoded.contains("\n")) {
                System.out.println("FAIL - Encoded line contains newline");
                testsFailed++;
                return;
            }
            if (encoded.contains("\r")) {
                System.out.println("FAIL - Encoded line contains carriage return");
                testsFailed++;
                return;
            }
            
            System.out.println("PASS");
            testsPassed++;
            
        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        }
    }
    
    private static void testNullEncodeRejection() {
        System.out.print("Test 6: Null encode rejection... ");
        
        try {
            MessageCodec.encode(null);
            System.out.println("FAIL - Null message was accepted");
            testsFailed++;
        } catch (IllegalArgumentException e) {
            System.out.println("PASS");
            testsPassed++;
        }
    }
    
    private static void testNullDecodeRejection() {
        System.out.print("Test 7: Null decode rejection... ");
        
        try {
            MessageCodec.decode(null);
            System.out.println("FAIL - Null line was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        }
    }
    
    private static void testBlankLineRejection() {
        System.out.print("Test 8: Blank line rejection... ");
        
        try {
            MessageCodec.decode("   ");
            System.out.println("FAIL - Blank line was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        }
    }
    
    private static void testIncorrectPrefixRejection() {
        System.out.print("Test 9: Incorrect prefix rejection... ");
        
        try {
            MessageCodec.decode("NOT_CHAT|bad");
            System.out.println("FAIL - Incorrect prefix was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        }
    }
    
    private static void testMissingFieldRejection() {
        System.out.print("Test 10: Missing field rejection... ");
        
        try {
            MessageCodec.decode("CHAT|QWxpY2U=|SGVsbG8=");
            System.out.println("FAIL - Missing field was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        }
    }
    
    private static void testExtraFieldRejection() {
        System.out.print("Test 11: Extra field rejection... ");
        
        try {
            MessageCodec.decode("CHAT|QWxpY2U=|SGVsbG8=|2026-10-03T14:32:10|extra");
            System.out.println("FAIL - Extra field was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        }
    }
    
    private static void testInvalidBase64Sender() {
        System.out.print("Test 12: Invalid Base64 sender... ");
        
        try {
            MessageCodec.decode("CHAT|!!!invalid!!!|SGVsbG8=|2026-10-03T14:32:10");
            System.out.println("FAIL - Invalid Base64 sender was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        }
    }
    
    private static void testInvalidBase64Text() {
        System.out.print("Test 13: Invalid Base64 text... ");
        
        try {
            MessageCodec.decode("CHAT|QWxpY2U=|!!!invalid!!!|2026-10-03T14:32:10");
            System.out.println("FAIL - Invalid Base64 text was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        }
    }
    
    private static void testInvalidTimestamp() {
        System.out.print("Test 14: Invalid timestamp... ");
        
        try {
            MessageCodec.decode("CHAT|QWxpY2U=|SGVsbG8=|invalid-timestamp");
            System.out.println("FAIL - Invalid timestamp was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        }
    }
    
    private static void testEmptyDecodedSender() {
        System.out.print("Test 15: Empty decoded sender... ");
        
        try {
            // Base64 of empty string (empty sender field)
            MessageCodec.decode("CHAT||SGVsbG8=|2026-10-03T14:32:10");
            System.out.println("FAIL - Empty decoded sender was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        }
    }
    
    private static void testEmptyDecodedText() {
        System.out.print("Test 16: Empty decoded text... ");
        
        try {
            MessageCodec.decode("CHAT|QWxpY2U=||2026-10-03T14:32:10");
            System.out.println("FAIL - Empty decoded text was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        }
    }
    
    private static void testDecodedNewlineRejection() {
        System.out.print("Test 17: Decoded newline rejection... ");
        
        try {
            // Base64 of "Hello\nBob"
            String encodedText = "SGVsbG8KQm9i";
            MessageCodec.decode("CHAT|QWxpY2U=|" + encodedText + "|2026-10-03T14:32:10");
            System.out.println("FAIL - Decoded newline was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        }
    }
    
    private static void testDecodedOverMaximumRejection() {
        System.out.print("Test 18: Decoded over-maximum rejection... ");
        
        try {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Message.MAX_MESSAGE_LENGTH + 1; i++) {
                sb.append("a");
            }
            String longText = sb.toString();
            String encodedText = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(longText.getBytes(StandardCharsets.UTF_8));
            MessageCodec.decode("CHAT|QWxpY2U=|" + encodedText + "|2026-10-03T14:32:10");
            System.out.println("FAIL - Over-maximum decoded text was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        }
    }
    
    private static void testMalformedUtf8Sender() {
        System.out.print("Test 19: Malformed UTF-8 sender... ");
        
        try {
            // Malformed UTF-8 byte sequence: 0xC3 0x28 (invalid continuation byte)
            byte[] malformedBytes = {(byte) 0xC3, (byte) 0x28};
            String encodedSender = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(malformedBytes);
            MessageCodec.decode("CHAT|" + encodedSender + "|SGVsbG8=|2026-10-03T14:32:10");
            System.out.println("FAIL - Malformed UTF-8 sender was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        }
    }
    
    private static void testMalformedUtf8Text() {
        System.out.print("Test 20: Malformed UTF-8 text... ");
        
        try {
            // Malformed UTF-8 byte sequence: 0xC3 0x28 (invalid continuation byte)
            byte[] malformedBytes = {(byte) 0xC3, (byte) 0x28};
            String encodedText = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(malformedBytes);
            MessageCodec.decode("CHAT|QWxpY2U=|" + encodedText + "|2026-10-03T14:32:10");
            System.out.println("FAIL - Malformed UTF-8 text was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        }
    }
    
    private static void testSenderNewlineRejection() {
        System.out.print("Test 21: Sender newline rejection... ");

        try {
            // Base64 of "Alice\nBob"
            String sender = "Alice\nBob";
            String encodedSender = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(sender.getBytes(StandardCharsets.UTF_8));
            MessageCodec.decode("CHAT|" + encodedSender + "|SGVsbG8=|2026-10-03T14:32:10");
            System.out.println("FAIL - Sender with newline was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        }
    }
    
    private static void testSenderCarriageReturnRejection() {
        System.out.print("Test 22: Sender carriage return rejection... ");

        try {
            // Base64 of "Alice\rBob"
            String sender = "Alice\rBob";
            String encodedSender = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(sender.getBytes(StandardCharsets.UTF_8));
            MessageCodec.decode("CHAT|" + encodedSender + "|SGVsbG8=|2026-10-03T14:32:10");
            System.out.println("FAIL - Sender with carriage return was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        }
    }
    
    private static void testSenderOverMaximumRejection() {
        System.out.print("Test 23: Sender over-maximum rejection... ");
        
        try {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Message.MAX_SENDER_LENGTH + 1; i++) {
                sb.append("a");
            }
            String longSender = sb.toString();
            String encodedSender = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(longSender.getBytes(StandardCharsets.UTF_8));
            MessageCodec.decode("CHAT|" + encodedSender + "|SGVsbG8=|2026-10-03T14:32:10");
            System.out.println("FAIL - Over-maximum sender was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        }
    }
    
    private static void testEncodedStartsWithPrefix() {
        System.out.print("Test 24: Encoded starts with CHAT|... ");
        
        try {
            LocalDateTime timestamp = LocalDateTime.of(2026, 10, 3, 14, 32, 10);
            Message message = new Message("Alice", "Hello", timestamp);
            String encoded = MessageCodec.encode(message);
            
            if (!encoded.startsWith("CHAT|")) {
                System.out.println("FAIL - Encoded line does not start with CHAT|");
                testsFailed++;
                return;
            }
            
            System.out.println("PASS");
            testsPassed++;
            
        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        }
    }
    
    private static void testEncodedFourFields() {
        System.out.print("Test 25: Encoded has exactly four fields... ");
        
        try {
            LocalDateTime timestamp = LocalDateTime.of(2026, 10, 3, 14, 32, 10);
            Message message = new Message("Alice", "Hello", timestamp);
            String encoded = MessageCodec.encode(message);
            
            String[] fields = encoded.split("\\|", -1);
            if (fields.length != 4) {
                System.out.println("FAIL - Encoded line has " + fields.length + " fields, expected 4");
                testsFailed++;
                return;
            }
            
            System.out.println("PASS");
            testsPassed++;
            
        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        }
    }
    
    // Disconnect protocol tests
    
    private static void testDisconnectRoundTrip() {
        System.out.print("Test 26: Disconnect round trip... ");
        
        try {
            LocalDateTime timestamp = LocalDateTime.of(2026, 10, 3, 14, 32, 10);
            DisconnectMessage original = new DisconnectMessage("Alice", timestamp);
            
            String encoded = MessageCodec.encodeDisconnect(original);
            DisconnectMessage decoded = MessageCodec.decodeDisconnect(encoded);
            
            if (!decoded.getSender().equals(original.getSender())) {
                System.out.println("FAIL - Sender mismatch");
                testsFailed++;
                return;
            }
            if (!decoded.getTimestamp().equals(original.getTimestamp())) {
                System.out.println("FAIL - Timestamp mismatch");
                testsFailed++;
                return;
            }
            
            System.out.println("PASS");
            testsPassed++;
            
        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        }
    }
    
    private static void testUnicodeDisconnectSender() {
        System.out.print("Test 27: Unicode disconnect sender... ");
        
        try {
            LocalDateTime timestamp = LocalDateTime.of(2026, 10, 3, 14, 32, 10);
            String sender = "\u0905\u0932\u093F\u0938"; // अलिस (Alice in Hindi)
            DisconnectMessage original = new DisconnectMessage(sender, timestamp);
            
            String encoded = MessageCodec.encodeDisconnect(original);
            DisconnectMessage decoded = MessageCodec.decodeDisconnect(encoded);
            
            if (!decoded.getSender().equals(original.getSender())) {
                System.out.println("FAIL - Sender mismatch");
                testsFailed++;
                return;
            }
            
            System.out.println("PASS");
            testsPassed++;
            
        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        }
    }
    
    private static void testDisconnectTimestampPreservation() {
        System.out.print("Test 28: Disconnect timestamp preservation... ");
        
        try {
            LocalDateTime timestamp = LocalDateTime.of(2026, 10, 3, 14, 32, 10, 123456789);
            DisconnectMessage original = new DisconnectMessage("Alice", timestamp);
            
            String encoded = MessageCodec.encodeDisconnect(original);
            DisconnectMessage decoded = MessageCodec.decodeDisconnect(encoded);
            
            if (!decoded.getTimestamp().equals(original.getTimestamp())) {
                System.out.println("FAIL - Timestamp not preserved");
                testsFailed++;
                return;
            }
            
            System.out.println("PASS");
            testsPassed++;
            
        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        }
    }
    
    private static void testDisconnectSingleLineEncoding() {
        System.out.print("Test 29: Disconnect single-line encoding... ");
        
        try {
            LocalDateTime timestamp = LocalDateTime.of(2026, 10, 3, 14, 32, 10);
            DisconnectMessage message = new DisconnectMessage("Alice", timestamp);
            
            String encoded = MessageCodec.encodeDisconnect(message);
            
            if (encoded.contains("\n")) {
                System.out.println("FAIL - Encoded line contains newline");
                testsFailed++;
                return;
            }
            if (encoded.contains("\r")) {
                System.out.println("FAIL - Encoded line contains carriage return");
                testsFailed++;
                return;
            }
            
            System.out.println("PASS");
            testsPassed++;
            
        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        }
    }
    
    private static void testDisconnectNoCarriageReturn() {
        System.out.print("Test 30: Disconnect no carriage return... ");
        
        try {
            LocalDateTime timestamp = LocalDateTime.of(2026, 10, 3, 14, 32, 10);
            DisconnectMessage message = new DisconnectMessage("Alice", timestamp);
            
            String encoded = MessageCodec.encodeDisconnect(message);
            
            if (encoded.contains("\r")) {
                System.out.println("FAIL - Encoded line contains carriage return");
                testsFailed++;
                return;
            }
            
            System.out.println("PASS");
            testsPassed++;
            
        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        }
    }
    
    private static void testNullDisconnectEncodeRejection() {
        System.out.print("Test 31: Null disconnect encode rejection... ");
        
        try {
            MessageCodec.encodeDisconnect(null);
            System.out.println("FAIL - Null disconnect message was accepted");
            testsFailed++;
        } catch (IllegalArgumentException e) {
            System.out.println("PASS");
            testsPassed++;
        }
    }
    
    private static void testNullDisconnectDecodeRejection() {
        System.out.print("Test 32: Null disconnect decode rejection... ");
        
        try {
            MessageCodec.decodeDisconnect(null);
            System.out.println("FAIL - Null line was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        }
    }
    
    private static void testWrongDisconnectPrefixRejection() {
        System.out.print("Test 33: Wrong disconnect prefix rejection... ");
        
        try {
            MessageCodec.decodeDisconnect("CHAT|QWxpY2U=|2026-10-03T14:32:10");
            System.out.println("FAIL - Wrong prefix was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        }
    }
    
    private static void testMissingDisconnectFieldRejection() {
        System.out.print("Test 34: Missing disconnect field rejection... ");
        
        try {
            MessageCodec.decodeDisconnect("DISCONNECT|QWxpY2U=");
            System.out.println("FAIL - Missing field was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        }
    }
    
    private static void testExtraDisconnectFieldRejection() {
        System.out.print("Test 35: Extra disconnect field rejection... ");
        
        try {
            MessageCodec.decodeDisconnect("DISCONNECT|QWxpY2U=|2026-10-03T14:32:10|extra");
            System.out.println("FAIL - Extra field was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        }
    }
    
    private static void testInvalidDisconnectBase64Rejection() {
        System.out.print("Test 36: Invalid disconnect Base64 rejection... ");
        
        try {
            MessageCodec.decodeDisconnect("DISCONNECT|!!!invalid!!!|2026-10-03T14:32:10");
            System.out.println("FAIL - Invalid Base64 was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        }
    }
    
    private static void testMalformedDisconnectUtf8Rejection() {
        System.out.print("Test 37: Malformed disconnect UTF-8 rejection... ");
        
        try {
            // Malformed UTF-8 byte sequence: 0xC3 0x28 (invalid continuation byte)
            byte[] malformedBytes = {(byte) 0xC3, (byte) 0x28};
            String encodedSender = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(malformedBytes);
            MessageCodec.decodeDisconnect("DISCONNECT|" + encodedSender + "|2026-10-03T14:32:10");
            System.out.println("FAIL - Malformed UTF-8 was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        }
    }
    
    private static void testInvalidDisconnectTimestampRejection() {
        System.out.print("Test 38: Invalid disconnect timestamp rejection... ");
        
        try {
            MessageCodec.decodeDisconnect("DISCONNECT|QWxpY2U=|invalid-timestamp");
            System.out.println("FAIL - Invalid timestamp was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        }
    }
    
    private static void testEmptyDisconnectSenderRejection() {
        System.out.print("Test 39: Empty disconnect sender rejection... ");
        
        try {
            MessageCodec.decodeDisconnect("DISCONNECT||2026-10-03T14:32:10");
            System.out.println("FAIL - Empty sender was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        }
    }
    
    private static void testDisconnectSenderNewlineRejection() {
        System.out.print("Test 40: Disconnect sender newline rejection... ");
        
        try {
            String sender = "Alice\nBob";
            String encodedSender = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(sender.getBytes(StandardCharsets.UTF_8));
            MessageCodec.decodeDisconnect("DISCONNECT|" + encodedSender + "|2026-10-03T14:32:10");
            System.out.println("FAIL - Sender with newline was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        }
    }
    
    private static void testDisconnectSenderOverMaximumRejection() {
        System.out.print("Test 41: Disconnect sender over-maximum rejection... ");
        
        try {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Message.MAX_SENDER_LENGTH + 1; i++) {
                sb.append("a");
            }
            String longSender = sb.toString();
            String encodedSender = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(longSender.getBytes(StandardCharsets.UTF_8));
            MessageCodec.decodeDisconnect("DISCONNECT|" + encodedSender + "|2026-10-03T14:32:10");
            System.out.println("FAIL - Over-maximum sender was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        }
    }
    
    private static void testDetectTypeChat() {
        System.out.print("Test 42: Detect type CHAT... ");
        
        try {
            String line = "CHAT|QWxpY2U=|SGVsbG8=|2026-10-03T14:32:10";
            MessageCodec.ProtocolType type = MessageCodec.detectType(line);
            
            if (type != MessageCodec.ProtocolType.CHAT) {
                System.out.println("FAIL - Expected CHAT, got " + type);
                testsFailed++;
                return;
            }
            
            System.out.println("PASS");
            testsPassed++;
            
        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        }
    }
    
    private static void testDetectTypeDisconnect() {
        System.out.print("Test 43: Detect type DISCONNECT... ");
        
        try {
            String line = "DISCONNECT|QWxpY2U=|2026-10-03T14:32:10";
            MessageCodec.ProtocolType type = MessageCodec.detectType(line);
            
            if (type != MessageCodec.ProtocolType.DISCONNECT) {
                System.out.println("FAIL - Expected DISCONNECT, got " + type);
                testsFailed++;
                return;
            }
            
            System.out.println("PASS");
            testsPassed++;
            
        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        }
    }
    
    private static void testDetectTypeUnknown() {
        System.out.print("Test 44: Detect type UNKNOWN... ");
        
        try {
            String line = "INVALID|data";
            MessageCodec.ProtocolType type = MessageCodec.detectType(line);
            
            if (type != MessageCodec.ProtocolType.UNKNOWN) {
                System.out.println("FAIL - Expected UNKNOWN, got " + type);
                testsFailed++;
                return;
            }
            
            System.out.println("PASS");
            testsPassed++;
            
        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        }
    }
    
    private static void testDetectTypeNullAndBlank() {
        System.out.print("Test 45: Detect type null and blank... ");
        
        try {
            MessageCodec.ProtocolType type1 = MessageCodec.detectType(null);
            if (type1 != MessageCodec.ProtocolType.UNKNOWN) {
                System.out.println("FAIL - Expected UNKNOWN for null");
                testsFailed++;
                return;
            }
            
            MessageCodec.ProtocolType type2 = MessageCodec.detectType("   ");
            if (type2 != MessageCodec.ProtocolType.UNKNOWN) {
                System.out.println("FAIL - Expected UNKNOWN for blank");
                testsFailed++;
                return;
            }
            
            System.out.println("PASS");
            testsPassed++;
            
        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        }
    }

    // ---------------- JOIN protocol tests (Step 9B) ----------------

    private static void testJoinRoundTrip() {
        System.out.print("Test 46: Join round trip... ");

        try {
            LocalDateTime timestamp = LocalDateTime.of(2026, 10, 3, 14, 32, 10);
            JoinMessage original = new JoinMessage("Bob", timestamp);

            String encoded = MessageCodec.encodeJoin(original);
            JoinMessage decoded = MessageCodec.decodeJoin(encoded);

            if (!decoded.getSender().equals(original.getSender())) {
                System.out.println("FAIL - Sender mismatch");
                testsFailed++;
                return;
            }
            if (!decoded.getTimestamp().equals(original.getTimestamp())) {
                System.out.println("FAIL - Timestamp mismatch");
                testsFailed++;
                return;
            }

            System.out.println("PASS");
            testsPassed++;

        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        }
    }

    private static void testUnicodeJoinUsername() {
        System.out.print("Test 47: Unicode join username... ");

        try {
            LocalDateTime timestamp = LocalDateTime.of(2026, 10, 3, 14, 32, 10);
            String sender = "B\u00F8b \uD83D\uDC4B";
            JoinMessage original = new JoinMessage(sender, timestamp);

            String encoded = MessageCodec.encodeJoin(original);
            JoinMessage decoded = MessageCodec.decodeJoin(encoded);

            if (!decoded.getSender().equals(sender)) {
                System.out.println("FAIL - Sender mismatch");
                testsFailed++;
                return;
            }

            System.out.println("PASS");
            testsPassed++;

        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        }
    }

    private static void testJoinTimestampPreservation() {
        System.out.print("Test 48: Join timestamp preservation... ");

        try {
            LocalDateTime timestamp = LocalDateTime.of(2026, 10, 3, 14, 32, 10, 123456789);
            JoinMessage original = new JoinMessage("Bob", timestamp);

            String encoded = MessageCodec.encodeJoin(original);
            JoinMessage decoded = MessageCodec.decodeJoin(encoded);

            if (!decoded.getTimestamp().equals(original.getTimestamp())) {
                System.out.println("FAIL - Timestamp not preserved");
                testsFailed++;
                return;
            }

            System.out.println("PASS");
            testsPassed++;

        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        }
    }

    private static void testJoinSingleLineEncoding() {
        System.out.print("Test 49: Join single-line encoding... ");

        try {
            JoinMessage original = new JoinMessage("Bob", LocalDateTime.of(2026, 10, 3, 14, 32, 10));
            String encoded = MessageCodec.encodeJoin(original);

            if (encoded.contains("\n") || encoded.contains("\r")) {
                System.out.println("FAIL - Encoded output contains newline");
                testsFailed++;
                return;
            }
            String[] fields = encoded.split("\\|", -1);
            if (fields.length != 3 || !fields[0].equals("JOIN")) {
                System.out.println("FAIL - Expected 3 JOIN fields, got " + fields.length);
                testsFailed++;
                return;
            }

            System.out.println("PASS");
            testsPassed++;

        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        }
    }

    private static void testJoinMissingFieldRejection() {
        System.out.print("Test 50: Join missing field rejection... ");

        try {
            String encodedSender = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("Bob".getBytes(StandardCharsets.UTF_8));
            MessageCodec.decodeJoin("JOIN|" + encodedSender);
            System.out.println("FAIL - Missing field was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        } catch (Exception e) {
            System.out.println("FAIL - Wrong exception: " + e.getClass().getSimpleName());
            testsFailed++;
        }
    }

    private static void testJoinExtraFieldRejection() {
        System.out.print("Test 51: Join extra field rejection... ");

        try {
            String encodedSender = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("Bob".getBytes(StandardCharsets.UTF_8));
            MessageCodec.decodeJoin("JOIN|" + encodedSender + "|2026-10-03T14:32:10|EXTRA");
            System.out.println("FAIL - Extra field was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        } catch (Exception e) {
            System.out.println("FAIL - Wrong exception: " + e.getClass().getSimpleName());
            testsFailed++;
        }
    }

    private static void testJoinInvalidBase64Rejection() {
        System.out.print("Test 52: Join invalid Base64 rejection... ");

        try {
            MessageCodec.decodeJoin("JOIN|!!!not-base64!!!|2026-10-03T14:32:10");
            System.out.println("FAIL - Invalid Base64 was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        } catch (Exception e) {
            System.out.println("FAIL - Wrong exception: " + e.getClass().getSimpleName());
            testsFailed++;
        }
    }

    private static void testJoinMalformedUtf8Rejection() {
        System.out.print("Test 53: Join malformed UTF-8 rejection... ");

        try {
            String bad = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(new byte[]{(byte) 0xFF, (byte) 0xFE});
            MessageCodec.decodeJoin("JOIN|" + bad + "|2026-10-03T14:32:10");
            System.out.println("FAIL - Malformed UTF-8 was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        } catch (Exception e) {
            System.out.println("FAIL - Wrong exception: " + e.getClass().getSimpleName());
            testsFailed++;
        }
    }

    private static void testJoinInvalidTimestampRejection() {
        System.out.print("Test 54: Join invalid timestamp rejection... ");

        try {
            String encodedSender = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("Bob".getBytes(StandardCharsets.UTF_8));
            MessageCodec.decodeJoin("JOIN|" + encodedSender + "|not-a-timestamp");
            System.out.println("FAIL - Invalid timestamp was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        } catch (Exception e) {
            System.out.println("FAIL - Wrong exception: " + e.getClass().getSimpleName());
            testsFailed++;
        }
    }

    private static void testJoinInvalidUsernameRejection() {
        System.out.print("Test 55: Join invalid username rejection... ");

        try {
            // Base64 of "Bo\nb" is structurally valid but semantically invalid.
            String encodedSender = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("Bo\nb".getBytes(StandardCharsets.UTF_8));
            MessageCodec.decodeJoin("JOIN|" + encodedSender + "|2026-10-03T14:32:10");
            System.out.println("FAIL - Invalid username was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        } catch (Exception e) {
            System.out.println("FAIL - Wrong exception: " + e.getClass().getSimpleName());
            testsFailed++;
        }
    }

    private static void testDetectTypeJoin() {
        System.out.print("Test 56: Detect type JOIN... ");

        try {
            String line = "JOIN|Qm9i|2026-10-03T14:32:10";
            MessageCodec.ProtocolType type = MessageCodec.detectType(line);

            if (type != MessageCodec.ProtocolType.JOIN) {
                System.out.println("FAIL - Expected JOIN, got " + type);
                testsFailed++;
                return;
            }

            System.out.println("PASS");
            testsPassed++;

        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        }
    }

    // ---------------- SYSTEM protocol tests (Step 9B) ----------------

    private static void testSystemRoundTripAllEventTypes() {
        System.out.print("Test 57: System round trip for every EventType... ");

        try {
            LocalDateTime timestamp = LocalDateTime.of(2026, 10, 3, 14, 32, 10);
            for (SystemMessage.EventType eventType : SystemMessage.EventType.values()) {
                SystemMessage original = new SystemMessage(eventType, "Sample event text", timestamp);
                String encoded = MessageCodec.encodeSystem(original);
                SystemMessage decoded = MessageCodec.decodeSystem(encoded);

                if (decoded.getEventType() != eventType) {
                    System.out.println("FAIL - EventType mismatch for " + eventType);
                    testsFailed++;
                    return;
                }
                if (!decoded.getText().equals("Sample event text")) {
                    System.out.println("FAIL - Text mismatch for " + eventType);
                    testsFailed++;
                    return;
                }
                if (!decoded.getTimestamp().equals(timestamp)) {
                    System.out.println("FAIL - Timestamp mismatch for " + eventType);
                    testsFailed++;
                    return;
                }
            }

            System.out.println("PASS");
            testsPassed++;

        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        }
    }

    private static void testUnicodeSystemText() {
        System.out.print("Test 58: Unicode system text... ");

        try {
            LocalDateTime timestamp = LocalDateTime.of(2026, 10, 3, 14, 32, 10);
            String text = "B\u00F8b a rejoint \uD83D\uDC4B";
            SystemMessage original = new SystemMessage(SystemMessage.EventType.USER_JOINED, text, timestamp);

            String encoded = MessageCodec.encodeSystem(original);
            SystemMessage decoded = MessageCodec.decodeSystem(encoded);

            if (!decoded.getText().equals(text)) {
                System.out.println("FAIL - Text mismatch");
                testsFailed++;
                return;
            }

            System.out.println("PASS");
            testsPassed++;

        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        }
    }

    private static void testSystemTimestampPreservation() {
        System.out.print("Test 59: System timestamp preservation... ");

        try {
            LocalDateTime timestamp = LocalDateTime.of(2026, 10, 3, 14, 32, 10, 123456789);
            SystemMessage original = new SystemMessage(SystemMessage.EventType.USER_LEFT, "Bob left the chat.", timestamp);

            String encoded = MessageCodec.encodeSystem(original);
            SystemMessage decoded = MessageCodec.decodeSystem(encoded);

            if (!decoded.getTimestamp().equals(original.getTimestamp())) {
                System.out.println("FAIL - Timestamp not preserved");
                testsFailed++;
                return;
            }

            System.out.println("PASS");
            testsPassed++;

        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        }
    }

    private static void testSystemSingleLineEncoding() {
        System.out.print("Test 60: System single-line encoding... ");

        try {
            SystemMessage original = new SystemMessage(
                SystemMessage.EventType.USER_JOINED, "Bob joined the chat.",
                LocalDateTime.of(2026, 10, 3, 14, 32, 10));
            String encoded = MessageCodec.encodeSystem(original);

            if (encoded.contains("\n") || encoded.contains("\r")) {
                System.out.println("FAIL - Encoded output contains newline");
                testsFailed++;
                return;
            }
            String[] fields = encoded.split("\\|", -1);
            if (fields.length != 4 || !fields[0].equals("SYSTEM")) {
                System.out.println("FAIL - Expected 4 SYSTEM fields, got " + fields.length);
                testsFailed++;
                return;
            }

            System.out.println("PASS");
            testsPassed++;

        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        }
    }

    private static void testSystemMissingFieldRejection() {
        System.out.print("Test 61: System missing field rejection... ");

        try {
            String encodedText = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("Bob joined the chat.".getBytes(StandardCharsets.UTF_8));
            MessageCodec.decodeSystem("SYSTEM|USER_JOINED|" + encodedText);
            System.out.println("FAIL - Missing field was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        } catch (Exception e) {
            System.out.println("FAIL - Wrong exception: " + e.getClass().getSimpleName());
            testsFailed++;
        }
    }

    private static void testSystemExtraFieldRejection() {
        System.out.print("Test 62: System extra field rejection... ");

        try {
            String encodedText = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("Bob joined the chat.".getBytes(StandardCharsets.UTF_8));
            MessageCodec.decodeSystem("SYSTEM|USER_JOINED|" + encodedText + "|2026-10-03T14:32:10|EXTRA");
            System.out.println("FAIL - Extra field was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        } catch (Exception e) {
            System.out.println("FAIL - Wrong exception: " + e.getClass().getSimpleName());
            testsFailed++;
        }
    }

    private static void testSystemInvalidEventTypeRejection() {
        System.out.print("Test 63: System invalid event type rejection... ");

        try {
            String encodedText = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("Some text".getBytes(StandardCharsets.UTF_8));
            MessageCodec.decodeSystem("SYSTEM|USER_EXPLODED|" + encodedText + "|2026-10-03T14:32:10");
            System.out.println("FAIL - Invalid event type was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        } catch (Exception e) {
            System.out.println("FAIL - Wrong exception: " + e.getClass().getSimpleName());
            testsFailed++;
        }
    }

    private static void testSystemInvalidBase64Rejection() {
        System.out.print("Test 64: System invalid Base64 rejection... ");

        try {
            MessageCodec.decodeSystem("SYSTEM|USER_JOINED|!!!not-base64!!!|2026-10-03T14:32:10");
            System.out.println("FAIL - Invalid Base64 was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        } catch (Exception e) {
            System.out.println("FAIL - Wrong exception: " + e.getClass().getSimpleName());
            testsFailed++;
        }
    }

    private static void testSystemMalformedUtf8Rejection() {
        System.out.print("Test 65: System malformed UTF-8 rejection... ");

        try {
            String bad = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(new byte[]{(byte) 0xFF, (byte) 0xFE});
            MessageCodec.decodeSystem("SYSTEM|USER_JOINED|" + bad + "|2026-10-03T14:32:10");
            System.out.println("FAIL - Malformed UTF-8 was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        } catch (Exception e) {
            System.out.println("FAIL - Wrong exception: " + e.getClass().getSimpleName());
            testsFailed++;
        }
    }

    private static void testSystemInvalidTimestampRejection() {
        System.out.print("Test 66: System invalid timestamp rejection... ");

        try {
            String encodedText = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("Some text".getBytes(StandardCharsets.UTF_8));
            MessageCodec.decodeSystem("SYSTEM|USER_JOINED|" + encodedText + "|not-a-timestamp");
            System.out.println("FAIL - Invalid timestamp was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        } catch (Exception e) {
            System.out.println("FAIL - Wrong exception: " + e.getClass().getSimpleName());
            testsFailed++;
        }
    }

    private static void testSystemInvalidSemanticRejection() {
        System.out.print("Test 67: System invalid semantic content rejection... ");

        try {
            // Base64 of blank text is structurally valid but semantically invalid.
            String encodedText = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("   ".getBytes(StandardCharsets.UTF_8));
            MessageCodec.decodeSystem("SYSTEM|USER_JOINED|" + encodedText + "|2026-10-03T14:32:10");
            System.out.println("FAIL - Blank text was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        } catch (Exception e) {
            System.out.println("FAIL - Wrong exception: " + e.getClass().getSimpleName());
            testsFailed++;
        }
    }

    private static void testDetectTypeSystem() {
        System.out.print("Test 68: Detect type SYSTEM... ");

        try {
            String line = "SYSTEM|USER_JOINED|Qm9iIGpvaW5lZA|2026-10-03T14:32:10";
            MessageCodec.ProtocolType type = MessageCodec.detectType(line);

            if (type != MessageCodec.ProtocolType.SYSTEM) {
                System.out.println("FAIL - Expected SYSTEM, got " + type);
                testsFailed++;
                return;
            }

            System.out.println("PASS");
            testsPassed++;

        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        }
    }

    // ---------------- USER_LIST protocol tests (Step 9C-3B) ----------------

    private static String userListPayload(int count, String... entries) {
        StringBuilder sb = new StringBuilder();
        sb.append(count).append(':');
        for (String entry : entries) {
            sb.append(entry);
        }
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String userListEntry(String name) {
        byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
        return bytes.length + ":" + name;
    }

    private static void testUserListRoundTrip() {
        System.out.print("Test 69: UserList round trip... ");

        try {
            LocalDateTime timestamp = LocalDateTime.of(2026, 10, 3, 14, 32, 10);
            UserListMessage original = new UserListMessage(
                Arrays.asList("Alice", "Bob", "Charlie"), timestamp);

            String encoded = MessageCodec.encodeUserList(original);
            UserListMessage decoded = MessageCodec.decodeUserList(encoded);

            if (!decoded.getUsernames().equals(original.getUsernames())) {
                System.out.println("FAIL - Usernames mismatch: " + decoded.getUsernames());
                testsFailed++;
                return;
            }
            if (!decoded.getTimestamp().equals(timestamp)) {
                System.out.println("FAIL - Timestamp mismatch");
                testsFailed++;
                return;
            }

            System.out.println("PASS");
            testsPassed++;

        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        }
    }

    private static void testUserListHostFirstOrdering() {
        System.out.print("Test 70: UserList host-first ordering... ");

        try {
            LocalDateTime timestamp = LocalDateTime.of(2026, 10, 3, 14, 32, 10);
            List<String> ordered = Arrays.asList("Alice", "Bob", "Charlie", "Dana");
            UserListMessage original = new UserListMessage(ordered, timestamp);

            String encoded = MessageCodec.encodeUserList(original);
            UserListMessage decoded = MessageCodec.decodeUserList(encoded);

            if (!decoded.getUsernames().equals(ordered)) {
                System.out.println("FAIL - Order not preserved: " + decoded.getUsernames());
                testsFailed++;
                return;
            }
            if (!decoded.getUsernames().get(0).equals("Alice")) {
                System.out.println("FAIL - Host not first");
                testsFailed++;
                return;
            }

            System.out.println("PASS");
            testsPassed++;

        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        }
    }

    private static void testUserListUnicodeUsernames() {
        System.out.print("Test 71: UserList unicode usernames... ");

        try {
            LocalDateTime timestamp = LocalDateTime.of(2026, 10, 3, 14, 32, 10);
            List<String> names = Arrays.asList("B\u00F8b", "\u4E2D\u6587", "Zo\u00EB");
            UserListMessage original = new UserListMessage(names, timestamp);

            String encoded = MessageCodec.encodeUserList(original);
            UserListMessage decoded = MessageCodec.decodeUserList(encoded);

            if (!decoded.getUsernames().equals(names)) {
                System.out.println("FAIL - Unicode mismatch: " + decoded.getUsernames());
                testsFailed++;
                return;
            }

            System.out.println("PASS");
            testsPassed++;

        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        }
    }

    private static void testUserListPunctuationUsernames() {
        System.out.print("Test 72: UserList punctuation usernames... ");

        try {
            LocalDateTime timestamp = LocalDateTime.of(2026, 10, 3, 14, 32, 10);
            List<String> names = Arrays.asList("Bob Jr.", "O'Brien", "User-1_2", "A:B,C|D");
            UserListMessage original = new UserListMessage(names, timestamp);

            String encoded = MessageCodec.encodeUserList(original);
            UserListMessage decoded = MessageCodec.decodeUserList(encoded);

            if (!decoded.getUsernames().equals(names)) {
                System.out.println("FAIL - Punctuation mismatch: " + decoded.getUsernames());
                testsFailed++;
                return;
            }

            System.out.println("PASS");
            testsPassed++;

        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        }
    }

    private static void testUserListTimestampPreservation() {
        System.out.print("Test 73: UserList timestamp preservation... ");

        try {
            LocalDateTime timestamp = LocalDateTime.of(2026, 10, 3, 14, 32, 10, 123456789);
            UserListMessage original = new UserListMessage(Arrays.asList("Alice"), timestamp);

            String encoded = MessageCodec.encodeUserList(original);
            UserListMessage decoded = MessageCodec.decodeUserList(encoded);

            if (!decoded.getTimestamp().equals(timestamp)) {
                System.out.println("FAIL - Timestamp not preserved");
                testsFailed++;
                return;
            }

            System.out.println("PASS");
            testsPassed++;

        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        }
    }

    private static void testUserListSingleLineEncoding() {
        System.out.print("Test 74: UserList single-line encoding... ");

        try {
            UserListMessage original = new UserListMessage(
                Arrays.asList("Alice", "Bob"), LocalDateTime.of(2026, 10, 3, 14, 32, 10));
            String encoded = MessageCodec.encodeUserList(original);

            if (encoded.contains("\n") || encoded.contains("\r")) {
                System.out.println("FAIL - Encoded output contains newline");
                testsFailed++;
                return;
            }

            System.out.println("PASS");
            testsPassed++;

        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        }
    }

    private static void testUserListThreeFields() {
        System.out.print("Test 75: UserList three fields... ");

        try {
            UserListMessage original = new UserListMessage(
                Arrays.asList("Alice", "Bob"), LocalDateTime.of(2026, 10, 3, 14, 32, 10));
            String encoded = MessageCodec.encodeUserList(original);
            String[] fields = encoded.split("\\|", -1);

            if (fields.length != 3 || !fields[0].equals("USER_LIST")) {
                System.out.println("FAIL - Expected 3 USER_LIST fields, got " + fields.length);
                testsFailed++;
                return;
            }

            System.out.println("PASS");
            testsPassed++;

        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        }
    }

    private static void testUserListNullEncodeRejection() {
        System.out.print("Test 76: UserList null encode rejection... ");

        try {
            MessageCodec.encodeUserList(null);
            System.out.println("FAIL - Null message was accepted");
            testsFailed++;
        } catch (IllegalArgumentException e) {
            System.out.println("PASS");
            testsPassed++;
        } catch (Exception e) {
            System.out.println("FAIL - Wrong exception: " + e.getClass().getSimpleName());
            testsFailed++;
        }
    }

    private static void testUserListNullDecodeRejection() {
        System.out.print("Test 77: UserList null decode rejection... ");

        try {
            MessageCodec.decodeUserList(null);
            System.out.println("FAIL - Null line was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        } catch (Exception e) {
            System.out.println("FAIL - Wrong exception: " + e.getClass().getSimpleName());
            testsFailed++;
        }
    }

    private static void testUserListBlankDecodeRejection() {
        System.out.print("Test 78: UserList blank decode rejection... ");

        try {
            MessageCodec.decodeUserList("   ");
            System.out.println("FAIL - Blank line was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        } catch (Exception e) {
            System.out.println("FAIL - Wrong exception: " + e.getClass().getSimpleName());
            testsFailed++;
        }
    }

    private static void testUserListWrongPrefixRejection() {
        System.out.print("Test 79: UserList wrong prefix rejection... ");

        try {
            MessageCodec.decodeUserList("CHAT|Qm9i|SGVsbG8|2026-10-03T14:32:10");
            System.out.println("FAIL - Wrong prefix was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        } catch (Exception e) {
            System.out.println("FAIL - Wrong exception: " + e.getClass().getSimpleName());
            testsFailed++;
        }
    }

    private static void testUserListMissingFieldRejection() {
        System.out.print("Test 80: UserList missing field rejection... ");

        try {
            MessageCodec.decodeUserList("USER_LIST|QUJD");
            System.out.println("FAIL - Missing field was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        } catch (Exception e) {
            System.out.println("FAIL - Wrong exception: " + e.getClass().getSimpleName());
            testsFailed++;
        }
    }

    private static void testUserListExtraFieldRejection() {
        System.out.print("Test 81: UserList extra field rejection... ");

        try {
            String payload = userListPayload(1, userListEntry("Alice"));
            MessageCodec.decodeUserList(
                "USER_LIST|" + payload + "|2026-10-03T14:32:10|EXTRA");
            System.out.println("FAIL - Extra field was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        } catch (Exception e) {
            System.out.println("FAIL - Wrong exception: " + e.getClass().getSimpleName());
            testsFailed++;
        }
    }

    private static void testUserListInvalidBase64Rejection() {
        System.out.print("Test 82: UserList invalid Base64 rejection... ");

        try {
            MessageCodec.decodeUserList("USER_LIST|!!!not-base64!!!|2026-10-03T14:32:10");
            System.out.println("FAIL - Invalid Base64 was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        } catch (Exception e) {
            System.out.println("FAIL - Wrong exception: " + e.getClass().getSimpleName());
            testsFailed++;
        }
    }

    private static void testUserListMalformedUtf8Rejection() {
        System.out.print("Test 83: UserList malformed UTF-8 rejection... ");

        try {
            String bad = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(new byte[]{(byte) 0xFF, (byte) 0xFE});
            MessageCodec.decodeUserList("USER_LIST|" + bad + "|2026-10-03T14:32:10");
            System.out.println("FAIL - Malformed UTF-8 was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        } catch (Exception e) {
            System.out.println("FAIL - Wrong exception: " + e.getClass().getSimpleName());
            testsFailed++;
        }
    }

    private static void testUserListInvalidTimestampRejection() {
        System.out.print("Test 84: UserList invalid timestamp rejection... ");

        try {
            String payload = userListPayload(1, userListEntry("Alice"));
            MessageCodec.decodeUserList("USER_LIST|" + payload + "|not-a-timestamp");
            System.out.println("FAIL - Invalid timestamp was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        } catch (Exception e) {
            System.out.println("FAIL - Wrong exception: " + e.getClass().getSimpleName());
            testsFailed++;
        }
    }

    private static void testUserListEmptyPayloadRejection() {
        System.out.print("Test 85: UserList empty payload rejection... ");

        try {
            MessageCodec.decodeUserList("USER_LIST||2026-10-03T14:32:10");
            System.out.println("FAIL - Empty payload was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        } catch (Exception e) {
            System.out.println("FAIL - Wrong exception: " + e.getClass().getSimpleName());
            testsFailed++;
        }
    }

    private static void testUserListZeroUserRejection() {
        System.out.print("Test 86: UserList zero-user rejection... ");

        try {
            MessageCodec.decodeUserList(
                "USER_LIST|" + userListPayload(0) + "|2026-10-03T14:32:10");
            System.out.println("FAIL - Zero users were accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        } catch (Exception e) {
            System.out.println("FAIL - Wrong exception: " + e.getClass().getSimpleName());
            testsFailed++;
        }
    }

    private static void testUserListNegativeCountRejection() {
        System.out.print("Test 87: UserList negative-count rejection... ");

        try {
            String raw = "-1:5:Alice";
            String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
            MessageCodec.decodeUserList("USER_LIST|" + payload + "|2026-10-03T14:32:10");
            System.out.println("FAIL - Negative count was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        } catch (Exception e) {
            System.out.println("FAIL - Wrong exception: " + e.getClass().getSimpleName());
            testsFailed++;
        }
    }

    private static void testUserListCountMismatchRejection() {
        System.out.print("Test 88: UserList count-mismatch rejection... ");

        try {
            MessageCodec.decodeUserList("USER_LIST|"
                + userListPayload(2, userListEntry("Alice")) + "|2026-10-03T14:32:10");
            System.out.println("FAIL - Count mismatch was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        } catch (Exception e) {
            System.out.println("FAIL - Wrong exception: " + e.getClass().getSimpleName());
            testsFailed++;
        }
    }

    private static void testUserListInvalidLengthRejection() {
        System.out.print("Test 89: UserList invalid-length rejection... ");

        try {
            // Declared length exceeds the actual name bytes.
            String raw = "1:10:Bob";
            String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
            MessageCodec.decodeUserList("USER_LIST|" + payload + "|2026-10-03T14:32:10");
            System.out.println("FAIL - Invalid length was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        } catch (Exception e) {
            System.out.println("FAIL - Wrong exception: " + e.getClass().getSimpleName());
            testsFailed++;
        }
    }

    private static void testUserListTruncatedRejection() {
        System.out.print("Test 90: UserList truncated rejection... ");

        try {
            // Second user announced but cut off mid-name.
            String raw = "2:5:Alice3:Bo";
            String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
            MessageCodec.decodeUserList("USER_LIST|" + payload + "|2026-10-03T14:32:10");
            System.out.println("FAIL - Truncated payload was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        } catch (Exception e) {
            System.out.println("FAIL - Wrong exception: " + e.getClass().getSimpleName());
            testsFailed++;
        }
    }

    private static void testUserListTrailingPayloadRejection() {
        System.out.print("Test 91: UserList trailing-payload rejection... ");

        try {
            String raw = "1:5:AliceEXTRA";
            String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
            MessageCodec.decodeUserList("USER_LIST|" + payload + "|2026-10-03T14:32:10");
            System.out.println("FAIL - Trailing payload was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        } catch (Exception e) {
            System.out.println("FAIL - Wrong exception: " + e.getClass().getSimpleName());
            testsFailed++;
        }
    }

    private static void testUserListInvalidSemanticRejection() {
        System.out.print("Test 92: UserList invalid semantic rejection... ");

        try {
            // Structurally valid payload with a blank username.
            String raw = "1:3:   ";
            String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
            MessageCodec.decodeUserList("USER_LIST|" + payload + "|2026-10-03T14:32:10");
            System.out.println("FAIL - Blank username was accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        } catch (Exception e) {
            System.out.println("FAIL - Wrong exception: " + e.getClass().getSimpleName());
            testsFailed++;
        }
    }

    private static void testUserListOverMaximumRejection() {
        System.out.print("Test 93: UserList over-maximum rejection... ");

        try {
            List<String> names = new ArrayList<>();
            for (int i = 0; i < UserListMessage.MAX_USERS + 1; i++) {
                names.add("User" + i);
            }
            StringBuilder raw = new StringBuilder();
            raw.append(names.size()).append(':');
            for (String name : names) {
                raw.append(userListEntry(name));
            }
            String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.toString().getBytes(StandardCharsets.UTF_8));
            MessageCodec.decodeUserList("USER_LIST|" + payload + "|2026-10-03T14:32:10");
            System.out.println("FAIL - Over-maximum users were accepted");
            testsFailed++;
        } catch (MessageFormatException e) {
            System.out.println("PASS");
            testsPassed++;
        } catch (Exception e) {
            System.out.println("FAIL - Wrong exception: " + e.getClass().getSimpleName());
            testsFailed++;
        }
    }

    private static void testDetectTypeUserList() {
        System.out.print("Test 94: Detect type USER_LIST... ");

        try {
            String line = "USER_LIST|QWxpY2U|2026-10-03T14:32:10";
            MessageCodec.ProtocolType type = MessageCodec.detectType(line);

            if (type != MessageCodec.ProtocolType.USER_LIST) {
                System.out.println("FAIL - Expected USER_LIST, got " + type);
                testsFailed++;
                return;
            }

            System.out.println("PASS");
            testsPassed++;

        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        }
    }
}
