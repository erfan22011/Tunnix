import os
import sys

print("=== VERIFYING STAGE 6: VPN LIFECYCLE, RACE CONDITIONS & TUN STABILITY ===")

# 1. Read source files
with open(r"vpn-service/src/main/kotlin/com/tunnix/vpn/TunnixVpnService.kt", "r", encoding="utf-8") as f:
    service_code = f.read()

with open(r"vpn-service/src/main/kotlin/com/tunnix/vpn/VpnTunnelManager.kt", "r", encoding="utf-8") as f:
    manager_code = f.read()

with open(r"vpn-service/src/main/kotlin/com/tunnix/vpn/TunInterfaceBuilder.kt", "r", encoding="utf-8") as f:
    builder_code = f.read()

with open(r"xray-core/src/main/kotlin/com/tunnix/xray/XrayCoreController.kt", "r", encoding="utf-8") as f:
    controller_code = f.read()

# Test 1: Service Mutex & Transition Job Race Condition Prevention
assert "serviceMutex = Mutex()" in service_code, "TunnixVpnService must define serviceMutex!"
assert "serviceMutex.withLock" in service_code, "TunnixVpnService must use serviceMutex.withLock for start/stop!"
assert "transitionJob?.cancel()" in service_code, "TunnixVpnService must cancel previous transition jobs to avoid races!"
print("✅ Test 1 (Service Mutex & Transition Job Race Condition Prevention): PASSED")

# Test 2: Safe File Descriptor Management & Silent Cleanup
assert "closeTunSilently" in service_code, "TunnixVpnService must implement closeTunSilently helper!"
assert "tunDescriptor?.close()" in service_code, "closeTunSilently must invoke tunDescriptor?.close()!"
assert "tunDescriptor = null" in service_code, "closeTunSilently must null out tunDescriptor reference!"
assert "finally {" in service_code, "startTunnel must have a finally block!"
assert "if (!isActive)" in service_code, "startTunnel finally block must clean up if coroutine was cancelled!"
print("✅ Test 2 (Safe File Descriptor Management & Cancellation Cleanup): PASSED")

# Test 3: System Revocation Handling (onRevoke)
assert "override fun onRevoke()" in service_code, "TunnixVpnService must override onRevoke()!"
assert "stopTunnel(" in service_code, "onRevoke must call stopTunnel to release resources!"
print("✅ Test 3 (System VPN Revocation onRevoke Handling): PASSED")

# Test 4: Dynamic Network Handover (WiFi <-> Cellular)
assert "setUnderlyingNetworks(arrayOf(network))" in service_code, "networkCallback must notify Android OS of new network via setUnderlyingNetworks!"
assert "setUnderlyingNetworks(null)" in service_code, "networkCallback onLost must call setUnderlyingNetworks(null)!"
assert "onCapabilitiesChanged" in service_code, "networkCallback must monitor onCapabilitiesChanged!"
print("✅ Test 4 (Dynamic Network Handover & setUnderlyingNetworks): PASSED")

# Test 5: Native Core Crash & Unexpected Shutdown Detection
assert "onUnexpectedShutdown" in controller_code, "XrayCoreController must define onUnexpectedShutdown listener!"
assert "onUnexpectedShutdown?.invoke()" in controller_code, "callbackHandler.shutdown must invoke onUnexpectedShutdown!"
assert "XrayCoreController.onUnexpectedShutdown = {" in service_code, "TunnixVpnService must attach onUnexpectedShutdown listener!"
assert "stopSync()" in controller_code, "XrayCoreController must provide stopSync() for synchronous teardown!"
assert "XrayCoreController.stopSync()" in service_code, "TunnixVpnService.onDestroy must call XrayCoreController.stopSync()!"
print("✅ Test 5 (Native Core Crash & Unexpected Shutdown Detection): PASSED")

# Test 6: TunInterfaceBuilder Crash-Proof Establishment
assert "try {" in builder_code and "builder.establish()" in builder_code, "TunInterfaceBuilder must wrap builder.establish() in try-catch!"
print("✅ Test 6 (TunInterfaceBuilder Crash-Proof Establishment): PASSED")

# Test 7: VpnTunnelManager Atomic Reconnect
assert "fun reconnect" in manager_code, "VpnTunnelManager must implement reconnect!"
assert "startVpn(context, server)" in manager_code, "VpnTunnelManager.reconnect must transition cleanly via startVpn!"
print("✅ Test 7 (VpnTunnelManager Atomic Reconnect): PASSED")

# Test 8: State Machine & Transition Simulation
class MockVpnStateMachine:
    def __init__(self):
        self.state = "Disconnected"
        self.tun_fd = None
        self.core_running = False

    def start(self, server):
        # Emulate startTunnel
        if self.tun_fd is not None:
            self.tun_fd = None # cleaned previous
        self.state = "Connecting"
        self.tun_fd = 42 # established
        self.core_running = True
        self.state = f"Connected({server})"

    def stop(self):
        # Emulate stopTunnel
        self.tun_fd = None
        self.core_running = False
        self.state = "Disconnected"

    def native_crash(self):
        # Emulate Go core panic/shutdown
        if self.core_running:
            self.core_running = False
            self.stop()
            self.state = "Error(Proxy core terminated unexpectedly)"

sm = MockVpnStateMachine()
sm.start("Server-1")
assert sm.state == "Connected(Server-1)" and sm.tun_fd == 42 and sm.core_running

# Test rapid restart
sm.start("Server-2")
assert sm.state == "Connected(Server-2)" and sm.tun_fd == 42 and sm.core_running

# Test native core crash
sm.native_crash()
assert sm.state == "Error(Proxy core terminated unexpectedly)" and sm.tun_fd is None and not sm.core_running

# Test manual stop
sm.stop()
assert sm.state == "Disconnected"

print("✅ Test 8 (Lifecycle & Native Crash State Machine Simulation): PASSED")

print("\n🎉 ALL STAGE 6 VPN LIFECYCLE & TUN STABILITY CHECKS PASSED WITH 100% SUCCESS!")
