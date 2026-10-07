package model;

import java.time.LocalDateTime;

/**
 * Unit tests for Message class.
 */
public class MessageTest {
    
    private static int testsPassed = 0;
    private static int testsFailed = 0;
    private static int testsSkipped = 0;
    
    public static void main(String[] args) {
        System.out.println("=== Message Model Tests ===");
        
        testValidFullConstructor();
        testConvenienceConstructor();
        testNullSenderRejection();
        testBlankSenderRejection();
        testSenderNewlineRejection();
        testSenderCarriageReturnRejection();
        testSenderMaximumLengthAcceptance();
        testSenderOverMaximumRejection();
        testNullTextRejection();
        testBlankTextRejection();
        testNewlineRejection();
        testCarriageReturnRejection();
        testNullTimestampRejection();
        testMaximumLengthAcceptance();
        testOverMaximumRejection();
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
            Message message = new Message("Alice", "Hello Bob", timestamp);
            
            if (!message.getSender().equals("Alice")) {
                System.out.println("FAIL - Sender mismatch");
                testsFailed++;
                return;
            }
            if (!message.getText().equals("Hello Bob")) {
                System.out.println("FAIL - Text mismatch");
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
            Message message = new Message("Alice", "Hello Bob");
            
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
            if (!message.getText().equals("Hello Bob")) {
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
    
    private static void testNullSenderRejection() {
        System.out.print("Test 3: Null sender rejection... ");
        
        try {
            LocalDateTime timestamp = LocalDateTime.of(2026, 10, 3, 14, 32, 10);
            new Message(null, "Hello", timestamp);
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
            new Message("   ", "Hello", timestamp);
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
            new Message("Alice\nBob", "Hello", timestamp);
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
            new Message("Alice\rBob", "Hello", timestamp);
            System.out.println("FAIL - Sender carriage return was accepted");
            testsFailed++;
        } catch (IllegalArgumentException e) {
            System.out.println("PASS");
            testsPassed++;
        }
    }
    
    private static void testSenderMaximumLengthAcceptance() {
        System.out.print("Test 7: Sender maximum length acceptance... ");
        
        try {
            LocalDateTime timestamp = LocalDateTime.of(2026, 10, 3, 14, 32, 10);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Message.MAX_SENDER_LENGTH; i++) {
                sb.append("a");
            }
            Message message = new Message(sb.toString(), "Hello", timestamp);
            
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
    
    private static void testSenderOverMaximumRejection() {
        System.out.print("Test 8: Sender over-maximum rejection... ");
        
        try {
            LocalDateTime timestamp = LocalDateTime.of(2026, 10, 3, 14, 32, 10);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Message.MAX_SENDER_LENGTH + 1; i++) {
                sb.append("a");
            }
            new Message(sb.toString(), "Hello", timestamp);
            System.out.println("FAIL - Over-maximum sender was accepted");
            testsFailed++;
        } catch (IllegalArgumentException e) {
            System.out.println("PASS");
            testsPassed++;
        }
    }
    
    private static void testNullTextRejection() {
        System.out.print("Test 9: Null text rejection... ");
        
        try {
            LocalDateTime timestamp = LocalDateTime.of(2026, 10, 3, 14, 32, 10);
            new Message("Alice", null, timestamp);
            System.out.println("FAIL - Null text was accepted");
            testsFailed++;
        } catch (IllegalArgumentException e) {
            System.out.println("PASS");
            testsPassed++;
        }
    }
    
    private static void testBlankTextRejection() {
        System.out.print("Test 10: Blank text rejection... ");
        
        try {
            LocalDateTime timestamp = LocalDateTime.of(2026, 10, 3, 14, 32, 10);
            new Message("Alice", "   ", timestamp);
            System.out.println("FAIL - Blank text was accepted");
            testsFailed++;
        } catch (IllegalArgumentException e) {
            System.out.println("PASS");
            testsPassed++;
        }
    }
    
    private static void testNewlineRejection() {
        System.out.print("Test 11: Newline rejection... ");
        
        try {
            LocalDateTime timestamp = LocalDateTime.of(2026, 10, 3, 14, 32, 10);
            new Message("Alice", "Hello\nBob", timestamp);
            System.out.println("FAIL - Newline was accepted");
            testsFailed++;
        } catch (IllegalArgumentException e) {
            System.out.println("PASS");
            testsPassed++;
        }
    }
    
    private static void testCarriageReturnRejection() {
        System.out.print("Test 12: Carriage return rejection... ");
        
        try {
            LocalDateTime timestamp = LocalDateTime.of(2026, 10, 3, 14, 32, 10);
            new Message("Alice", "Hello\rBob", timestamp);
            System.out.println("FAIL - Carriage return was accepted");
            testsFailed++;
        } catch (IllegalArgumentException e) {
            System.out.println("PASS");
            testsPassed++;
        }
    }
    
    private static void testNullTimestampRejection() {
        System.out.print("Test 13: Null timestamp rejection... ");
        
        try {
            new Message("Alice", "Hello", null);
            System.out.println("FAIL - Null timestamp was accepted");
            testsFailed++;
        } catch (IllegalArgumentException e) {
            System.out.println("PASS");
            testsPassed++;
        }
    }
    
    private static void testMaximumLengthAcceptance() {
        System.out.print("Test 14: Maximum length acceptance... ");
        
        try {
            LocalDateTime timestamp = LocalDateTime.of(2026, 10, 3, 14, 32, 10);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Message.MAX_MESSAGE_LENGTH; i++) {
                sb.append("a");
            }
            Message message = new Message("Alice", sb.toString(), timestamp);
            
            if (message.getText().length() != Message.MAX_MESSAGE_LENGTH) {
                System.out.println("FAIL - Text length mismatch");
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
    
    private static void testOverMaximumRejection() {
        System.out.print("Test 15: Over-maximum rejection... ");
        
        try {
            LocalDateTime timestamp = LocalDateTime.of(2026, 10, 3, 14, 32, 10);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Message.MAX_MESSAGE_LENGTH + 1; i++) {
                sb.append("a");
            }
            new Message("Alice", sb.toString(), timestamp);
            System.out.println("FAIL - Over-maximum text was accepted");
            testsFailed++;
        } catch (IllegalArgumentException e) {
            System.out.println("PASS");
            testsPassed++;
        }
    }
    
    private static void testEqualsAndHashCode() {
        System.out.print("Test 16: Equals and hashCode... ");
        
        try {
            LocalDateTime timestamp = LocalDateTime.of(2026, 10, 3, 14, 32, 10);
            Message message1 = new Message("Alice", "Hello", timestamp);
            Message message2 = new Message("Alice", "Hello", timestamp);
            Message message3 = new Message("Bob", "Hello", timestamp);
            
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
