package model;

import java.time.LocalDateTime;

/**
 * Unit tests for DisconnectMessage class.
 */
public class DisconnectMessageTest {
    
    private static int testsPassed = 0;
    private static int testsFailed = 0;
    private static int testsSkipped = 0;
    
    public static void main(String[] args) {
        System.out.println("=== DisconnectMessage Tests ===");
        
        testValidFullConstructor();
        testConvenienceConstructor();
        testNullSenderRejection();
        testBlankSenderRejection();
        testSenderNewlineRejection();
        testSenderCarriageReturnRejection();
        testMaximumSenderLengthAcceptance();
        testOverMaximumSenderRejection();
        testNullTimestampRejection();
        testEqualsAndHashCode();
        
        System.out.println("\n=== Test Summary ===");
        System.out.println("Passed: " + testsPassed);
        System.out.println("Failed: " + testsFailed);
        System.out.println("Skipped: " + testsSkipped);
        
        if (testsFailed > 0) {
            System.exit(1);
        }
    }
    
    private static void testValidFullConstructor() {
        System.out.print("Test 1: Valid full constructor... ");
        
        try {
            LocalDateTime timestamp = LocalDateTime.of(2026, 10, 3, 14, 32, 10);
            DisconnectMessage message = new DisconnectMessage("Alice", timestamp);
            
            if (!message.getSender().equals("Alice")) {
                System.out.println("FAIL - Sender mismatch");
                testsFailed++;
                return;
            }
            if (!message.getTimestamp().equals(timestamp)) {
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
    
    private static void testConvenienceConstructor() {
        System.out.print("Test 2: Convenience constructor... ");
        
        try {
            DisconnectMessage message = new DisconnectMessage("Alice");
            
            if (message.getTimestamp() == null) {
                System.out.println("FAIL - Timestamp is null");
                testsFailed++;
                return;
            }
            if (!message.getSender().equals("Alice")) {
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
    
    private static void testNullSenderRejection() {
        System.out.print("Test 3: Null sender rejection... ");
        
        try {
            LocalDateTime timestamp = LocalDateTime.of(2026, 10, 3, 14, 32, 10);
            new DisconnectMessage(null, timestamp);
            System.out.println("FAIL - Null sender was accepted");
            testsFailed++;
        } catch (IllegalArgumentException e) {
            System.out.println("PASS");
            testsPassed++;
        }
    }
    
    private static void testBlankSenderRejection() {
        System.out.print("Test 4: Blank sender rejection... ");
        
        try {
            LocalDateTime timestamp = LocalDateTime.of(2026, 10, 3, 14, 32, 10);
            new DisconnectMessage("   ", timestamp);
            System.out.println("FAIL - Blank sender was accepted");
            testsFailed++;
        } catch (IllegalArgumentException e) {
            System.out.println("PASS");
            testsPassed++;
        }
    }
    
    private static void testSenderNewlineRejection() {
        System.out.print("Test 5: Sender newline rejection... ");
        
        try {
            LocalDateTime timestamp = LocalDateTime.of(2026, 10, 3, 14, 32, 10);
            new DisconnectMessage("Alice\nBob", timestamp);
            System.out.println("FAIL - Sender newline was accepted");
            testsFailed++;
        } catch (IllegalArgumentException e) {
            System.out.println("PASS");
            testsPassed++;
        }
    }
    
    private static void testSenderCarriageReturnRejection() {
        System.out.print("Test 6: Sender carriage return rejection... ");
        
        try {
            LocalDateTime timestamp = LocalDateTime.of(2026, 10, 3, 14, 32, 10);
            new DisconnectMessage("Alice\rBob", timestamp);
            System.out.println("FAIL - Sender carriage return was accepted");
            testsFailed++;
        } catch (IllegalArgumentException e) {
            System.out.println("PASS");
            testsPassed++;
        }
    }
    
    private static void testMaximumSenderLengthAcceptance() {
        System.out.print("Test 7: Maximum sender length acceptance... ");
        
        try {
            LocalDateTime timestamp = LocalDateTime.of(2026, 10, 3, 14, 32, 10);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Message.MAX_SENDER_LENGTH; i++) {
                sb.append("a");
            }
            DisconnectMessage message = new DisconnectMessage(sb.toString(), timestamp);
            
            if (message.getSender().length() != Message.MAX_SENDER_LENGTH) {
                System.out.println("FAIL - Sender length mismatch");
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
    
    private static void testOverMaximumSenderRejection() {
        System.out.print("Test 8: Over-maximum sender rejection... ");
        
        try {
            LocalDateTime timestamp = LocalDateTime.of(2026, 10, 3, 14, 32, 10);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Message.MAX_SENDER_LENGTH + 1; i++) {
                sb.append("a");
            }
            new DisconnectMessage(sb.toString(), timestamp);
            System.out.println("FAIL - Over-maximum sender was accepted");
            testsFailed++;
        } catch (IllegalArgumentException e) {
            System.out.println("PASS");
            testsPassed++;
        }
    }
    
    private static void testNullTimestampRejection() {
        System.out.print("Test 9: Null timestamp rejection... ");
        
        try {
            new DisconnectMessage("Alice", null);
            System.out.println("FAIL - Null timestamp was accepted");
            testsFailed++;
        } catch (IllegalArgumentException e) {
            System.out.println("PASS");
            testsPassed++;
        }
    }
    
    private static void testEqualsAndHashCode() {
        System.out.print("Test 10: Equals and hashCode... ");
        
        try {
            LocalDateTime timestamp = LocalDateTime.of(2026, 10, 3, 14, 32, 10);
            DisconnectMessage message1 = new DisconnectMessage("Alice", timestamp);
            DisconnectMessage message2 = new DisconnectMessage("Alice", timestamp);
            DisconnectMessage message3 = new DisconnectMessage("Bob", timestamp);
            
            if (!message1.equals(message2)) {
                System.out.println("FAIL - Equal messages not equal");
                testsFailed++;
                return;
            }
            if (message1.equals(message3)) {
                System.out.println("FAIL - Different messages are equal");
                testsFailed++;
                return;
            }
            if (message1.hashCode() != message2.hashCode()) {
                System.out.println("FAIL - Equal messages have different hash codes");
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
