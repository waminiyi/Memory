# Architecture Audit: DDD + SOLID + Production Readiness

Date: 2026-02-17
Project: `/Users/patrick.soto/AndroidStudioProjects/Memory`

## Executive Summary
- DDD maturity: **6/10**
- SOLID maturity: **5/10**
- Production readiness: **5/10**
- Conclusion: strong foundations, but not state-of-the-art yet due to state orchestration and boundary issues.

## What Is Strong
- Rich domain model with value objects and sealed message hierarchy.
  - `/Users/patrick.soto/AndroidStudioProjects/Memory/app/src/main/java/com/memory/sotopatrick/domain/events/MemoMessage.kt`
- Reducer + validator split is clear.
  - `/Users/patrick.soto/AndroidStudioProjects/Memory/app/src/main/java/com/memory/sotopatrick/domain/game/GameEventHandler.kt`
  - `/Users/patrick.soto/AndroidStudioProjects/Memory/app/src/main/java/com/memory/sotopatrick/domain/validation/GameValidator.kt`
- Typed cross-layer error and result contracts exist.
  - `/Users/patrick.soto/AndroidStudioProjects/Memory/app/src/main/java/com/memory/sotopatrick/domain/error/AppError.kt`
  - `/Users/patrick.soto/AndroidStudioProjects/Memory/app/src/main/java/com/memory/sotopatrick/domain/result/OperationResult.kt`
- BLE transport internals improved (fragment handling, ack race hardening previously implemented).

## Critical Gaps (Priority Ordered)

### 1) Connection state races and lossy mapping
Severity: **Critical**

- `BleState.Ready` is collapsed into `MatchConnectionStatus.Connected`, losing handshake distinction.
  - `/Users/patrick.soto/AndroidStudioProjects/Memory/app/src/main/java/com/memory/sotopatrick/data/network/ble/connection/BlePlayerMatchingService.kt:48`
- `BleState.Scanning` and `BleState.Advertising` are mapped to `Idle`, losing actual discovery mode.
  - `/Users/patrick.soto/AndroidStudioProjects/Memory/app/src/main/java/com/memory/sotopatrick/data/network/ble/connection/BlePlayerMatchingService.kt:58`

Impact:
- Contributes to route/UI jitter and stale state after failure/quit/rejoin.

### 2) UI state mapping masks failures/disconnects
Severity: **Critical**

- In `DiscoveryViewModel`, on `Failed`/`Disconnected`, UI may still render `Advertising`/`Searching` based on discovery state.
  - `/Users/patrick.soto/AndroidStudioProjects/Memory/app/src/main/java/com/memory/sotopatrick/ui/presentation/discovery/DiscoveryViewModel.kt:174`
  - `/Users/patrick.soto/AndroidStudioProjects/Memory/app/src/main/java/com/memory/sotopatrick/ui/presentation/discovery/DiscoveryViewModel.kt:220`

Impact:
- User sees active discovery while transport is broken.
- Recovery actions become non-deterministic.

### 3) Domain aggregate contains infrastructure concerns
Severity: **High**

- `P2PGameManager` mixes domain transitions with transport retry/backoff, ACK waits, and Android logging.
  - `/Users/patrick.soto/AndroidStudioProjects/Memory/app/src/main/java/com/memory/sotopatrick/domain/game/P2PGameManager.kt:3`
  - `/Users/patrick.soto/AndroidStudioProjects/Memory/app/src/main/java/com/memory/sotopatrick/domain/game/P2PGameManager.kt:95`

Impact:
- Weakens DDD aggregate purity and SRP.
- Harder to test deterministically.

### 4) Invariant validation is incomplete
Severity: **High**

- `PairResolved` validation only checks count of revealed cards.
  - `/Users/patrick.soto/AndroidStudioProjects/Memory/app/src/main/java/com/memory/sotopatrick/domain/validation/GameValidator.kt:46`

Missing checks:
- `card1/card2` must match currently revealed cards.
- sender authority for control events.
- card existence and duplicate-card defense.

### 5) Protocol/version checks are incomplete
Severity: **High**

- Serializer accepts payload without protocol compatibility checks.
  - `/Users/patrick.soto/AndroidStudioProjects/Memory/app/src/main/java/com/memory/sotopatrick/data/network/ble/utils/BleSerializer.kt:26`
- ACK `newVersion` is not validated against expected transition before local apply.
  - `/Users/patrick.soto/AndroidStudioProjects/Memory/app/src/main/java/com/memory/sotopatrick/domain/game/P2PGameManager.kt:108`

Impact:
- Vulnerable to silent desync after malformed or out-of-order packets.

### 6) Dependency inversion not fully enforced at composition root
Severity: **Medium-High**

- `Navigation` directly constructs data/domain concretes (`GameSessionOrchestrator`, `P2PGameManager`).
  - `/Users/patrick.soto/AndroidStudioProjects/Memory/app/src/main/java/com/memory/sotopatrick/ui/presentation/Navigation.kt:3`
  - `/Users/patrick.soto/AndroidStudioProjects/Memory/app/src/main/java/com/memory/sotopatrick/ui/presentation/Navigation.kt:175`

Impact:
- UI owns orchestration wiring details.
- Harder replacement/testing and weaker DIP.

### 7) Runtime state-machine contracts exist but are not authoritative
Severity: **Medium**

- `ConnectionState` and `SessionState` are defined, but not driving app-wide runtime transitions.
  - `/Users/patrick.soto/AndroidStudioProjects/Memory/app/src/main/java/com/memory/sotopatrick/domain/connection/ConnectionState.kt`
  - `/Users/patrick.soto/AndroidStudioProjects/Memory/app/src/main/java/com/memory/sotopatrick/domain/session/SessionState.kt`
- `ConnectionStateInterpreter` appears mostly test-facing and not central to navigation/session flow.
  - `/Users/patrick.soto/AndroidStudioProjects/Memory/app/src/main/java/com/memory/sotopatrick/data/connection/ConnectionStateInterpreter.kt:13`

### 8) Dead/duplicate presentation code
Severity: **Medium**

- Unused overlay/component:
  - `/Users/patrick.soto/AndroidStudioProjects/Memory/app/src/main/java/com/memory/sotopatrick/ui/presentation/discovery/ConnectionStatusOverlay.kt:31`
- Unused permission composable:
  - `/Users/patrick.soto/AndroidStudioProjects/Memory/app/src/main/java/com/memory/sotopatrick/ui/presentation/discovery/PermissionChecker.kt:10`
- Duplicate Bluetooth checker (commented file + active function in `DiscoveryScreen`).
  - `/Users/patrick.soto/AndroidStudioProjects/Memory/app/src/main/java/com/memory/sotopatrick/ui/presentation/discovery/BluetoothStateChecker.kt:1`
  - `/Users/patrick.soto/AndroidStudioProjects/Memory/app/src/main/java/com/memory/sotopatrick/ui/presentation/discovery/DiscoveryScreen.kt:644`

Impact:
- Architectural noise and maintenance risk.

### 9) Test coverage is below production threshold
Severity: **Medium**

- `GameSessionViewModel` has only a default-state test.
  - `/Users/patrick.soto/AndroidStudioProjects/Memory/app/src/test/java/com/memory/sotopatrick/ui/presentation/game/GameSessionViewModelTest.kt:7`
- No integration tests for replay/leave propagation and no robust BLE failure simulation suite.

## SOLID Review

### Single Responsibility (SRP)
- Improved in parts (`GameEventHandler`, `GameValidator` split).
- Violated in `P2PGameManager` and partially in `DiscoveryViewModel` (UI mapping + side-effect coordination + session holder logic).

### Open/Closed (OCP)
- Message model is extensible (sealed hierarchy with new control messages).
- Validation paths still require direct edits in central validators; acceptable but needs stronger invariants.

### Liskov (LSP)
- No major violations detected in interface implementations.

### Interface Segregation (ISP)
- Domain ports are reasonably small (`PlayerMatchingService`, `PlayerDiscoveryService`, `MemoMessenger`, `UserProvider`).
- `MemoMessenger` is broad but still cohesive for current scope.

### Dependency Inversion (DIP)
- Core direction mostly correct (`data` implements domain contracts).
- Composition root leaks concrete constructions into UI route wiring.

## DDD Review

### Aggregates and invariants
- Positive: event-sourced style and reducer pattern.
- Gap: aggregate boundary includes transport concerns; invariants should be fully enforced in domain transition layer.

### Ubiquitous language and bounded contexts
- Names are mostly coherent for game/discovery/connection.
- Connection semantics are overloaded (`Connected` often includes not-yet-ready states).

### Domain events
- Rich and practical event schema; includes game and system control messages.
- Need stricter protocol-version and sequence validation for production reliability.

## Production Readiness

### Ready-ish
- Build compiles, structure is understandable, BLE stack has core pieces.

### Not ready yet
- Deterministic session state authority.
- Full typed failure propagation to UX actions.
- Hardening against malformed payload/version drift.
- CI-level test depth (domain/application/integration).

## Recommended Next Refactor Sequence
1. Make a single authoritative connection/session FSM and drive navigation from it only.
2. Refactor `P2PGameManager` so transport retry/ack orchestration moves to data service layer.
3. Strengthen domain invariant checks (`PairResolved`, `TurnChanged`, `GameFinished` authority).
4. Add protocol guards (version checks, ACK version checks, sequence guards).
5. Remove dead/duplicate UI utilities and consolidate permission/bluetooth observers.
6. Expand tests: replay/leave race, disconnect/reconnect, malformed packet handling, turn authority.

## Notes
- This audit intentionally excludes complex process recreation/persistence (as requested).
- Overall direction is good, but key reliability/architecture gates are still open before “state-of-the-art” status.
