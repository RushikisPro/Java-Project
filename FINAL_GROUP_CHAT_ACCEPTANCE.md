# Java LAN Group Chat Final Acceptance

Date: 2026-10-03 (UTC). Environment: Windows 10/11, Temurin OpenJDK 21.0.12.1,
graphical desktop (headless checks negative: Swing suites execute, none skip).

## Build

- Source clean: `Get-ChildItem -Path src,test -Filter *.class -Recurse`
  returns nothing → **PASS: Source directories are clean**.
- Production compilation (`javac -encoding UTF-8 -d out-production
  -sourcepath src src\Main.java src\model\*.java src\network\*.java
  src\controller\*.java src\ui\*.java`) → exit code **0**.
- Test compilation (production classes copied to `out-test`, then all
  `test\...` compiled with `-cp out-test`) → exit code **0**.
- `manifest.mf` (`Manifest-Version: 1.0`, `Main-Class: Main`, trailing blank
  line) → `jar cfm LanChat.jar manifest.mf -C out-production .` → exit **0**.
- `jar tf LanChat.jar` contains all required production classes
  (`Main`, `ui/StartFrame`, `ui/ChatFrame`, `ui/ChatView`,
  `controller/HostGroupChatController`, `controller/ClientGroupChatController`,
  `network/GroupChatServer`, `network/GroupChatClientSession`,
  `model/Message`, `model/JoinMessage`, `model/SystemMessage`,
  `model/UserListMessage`) and **zero** entries matching
  `Test.class|FakeChatView.class|RawFakeChatView.class` →
  **PASS: Release JAR contains production classes only**.

## Automated Tests

Exit code for every suite below: **0**.

| Suite | PASS | FAIL | SKIP |
|---|---|---|---|
| MessageTest | 16 | 0 | 0 |
| DisconnectMessageTest | 10 | 0 | 0 |
| JoinMessageTest | 10 | 0 | 0 |
| SystemMessageTest | 11 | 0 | 0 |
| UserListMessageTest | 18 | 0 | 0 |
| MessageCodecTest | 94 | 0 | 0 |
| MessageHandlerIntegrationTest | 12 | 0 | 0 |
| MessageProtocolIntegrationTest | 5 | 0 | 0 |
| DisconnectProtocolIntegrationTest | 5 | 0 | 0 |
| ChatClientLifecycleTest | 5 | 0 | 1 |
| ChatServerLifecycleTest | 6 | 0 | 0 |
| ChatServerMultiClientTest | 6 | 0 | 0 |
| GroupChatServerIntegrationTest | 17 | 0 | 0 |
| GroupChatClientSessionTest | 17 | 0 | 0 |
| ChatControllerDisconnectTest | 10 | 0 | 0 |
| ClientGroupChatControllerTest | 22 | 0 | 0 |
| HostGroupChatControllerTest | 14 | 0 | 0 |
| ChatViewContractTest | 6 | 0 | 0 |
| ChatFrameTest | 19 | 0 | 0 |
| StartFrameGroupIntegrationTest | 9 | 0 | 0 |
| **Total** | **312** | **0** | **1** |

The single SKIP is the accepted deterministic disconnect-during-connect test
in ChatClientLifecycleTest (reported as skipped, not passed).

### Stress-run results (5 consecutive clean runs each, exit 0 every run)

- GroupChatServerIntegrationTest: 5/5 clean.
- GroupChatClientSessionTest: 5/5 clean (after fixing one observed flake:
  `testObservesLeave` baselined a system-message count before a late
  legitimate USER_JOINED delivery; the test now waits for the specific
  USER_LEFT event — test-only change, no production change).
- ChatServerMultiClientTest: 5/5 clean.
- ClientGroupChatControllerTest: 5/5 clean.
- HostGroupChatControllerTest: 5/5 clean.
- StartFrameGroupIntegrationTest: 5/5 clean (after fixing one observed
  teardown race: closing the Host before Clients let ROOM_CLOSED resurrect
  disposed StartFrames and linger the JVM; teardown now closes Clients
  first and re-verifies disposal — test-only change).
- A single transient 16/1 in GroupChatServerIntegrationTest during the
  release run was root-caused to a similar baseline race in
  `testUserListForgeryRejected` (a client's own snapshot landing after the
  baseline) and fixed the same way; the suite then passed repeatedly.
- No failure was accepted on rerun without analysis; every flake was
  diagnosed to a test-side baseline/teardown race and hardened.

## Three-Window Test

All flows run with real visible Swing windows (graphical environment;
`isVisible()` asserted programmatically) in `StartFrameGroupIntegrationTest`
(9/9), plus a release-JAR rehearsal driver on the production port 5000
(real `StartFrame`/`ChatFrame`/controllers from `LanChat.jar`, 25/25 checks):

- Host launch (Alice, immediate room, `Waiting for users at <ip>:5000`,
  `Alice (You, Host)`, controls enabled, zero-client send works): PASS.
- Bob join, Charlie join, host online list `Alice/Bob/Charlie`: PASS.
- User-list correctness per view (`(You, Host)`/`(You)`/`(Host)` labels): PASS.
- Bob broadcast → exactly one copy in every window (`You:` for Bob): PASS.
- Alice broadcast → one copy everywhere: PASS.
- Charlie Unicode broadcast → exact text once everywhere: PASS.
- Bob leave → Bob back at StartFrame (`You left the chat.`), process alive;
  Alice and Charlie stay, see `[System] Bob left the chat.`, Bob delisted,
  controls enabled: PASS (main acceptance test for the original problem).
- Remaining-user messaging both directions (Charlie→Alice, Alice→Charlie): PASS.
- Dana late join with authoritative `Alice (Host) / Charlie / Dana (You)`
  snapshot; host sees Dana-join line; all exchange messages: PASS.
- Duplicate `alice` rejected case-insensitively, back to StartFrame
  (`Username is already in use.`), room and users unaffected: PASS.
- Close Room cancel: room, clients, messaging unaffected: PASS (covered in
  controller suites; StartFrame path uses the same controller API).
- Close Room confirm: Alice back (`You closed the chat room.`), Charlie/Dana
  back (`The Host closed the chat room.`, no unexpected-EOF duplicate),
  port 5000 refused afterwards: PASS.
- Port release (`Get-NetTCPConnection -LocalPort 5000` empty) and immediate
  second room on the same port with rejoins and messaging: PASS.
- Three `java -jar LanChat.jar` processes launch cleanly with no console
  errors (taskbar/process evidence collected).

## Two-Laptop Test

NOT RUN: second laptop unavailable.

## Final Decision

**PASS: Ready for mini-project submission**
