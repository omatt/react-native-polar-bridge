import CoreBluetooth
import PolarBleSdk
import React

enum PolarEvent: String, CaseIterable {
    case onDeviceFound
    case onScanError
    case onScanComplete
    case PolarHrData
    case PolarHrError
    case PolarHrComplete
    case PolarAccData
    case PolarAccError
    case PolarAccComplete
    case PolarGyrData
    case PolarGyrError
    case PolarGyrComplete
    case PolarPpgData
    case PolarPpgError
    case PolarPpgComplete
}

enum PolarBleError: Error {
    case unconfigured
}

@objc(PolarBridge)
class PolarBridge: RCTEventEmitter, ObservableObject
{
    private var api: PolarBleApi?
    private var deviceConnected = false
    private var batteryReceived = false
    private var connectionData: [String: Any] = [:]
    private var pendingResolver: RCTPromiseResolveBlock?
    private var pendingRejecter: RCTPromiseRejectBlock?

    private var scanTask: Task<Void, Never>?
    private var hrManager: HrStreamManager?
    private var accManager: AccStreamManager?
    private var gyrManager: GyrStreamManager?
    private var ppgManager: PpgStreamManager?

    /// Flush interval for all sensor buffers (milliseconds)
    private let SENSOR_BUFFER_MS: TimeInterval = 10_000

    override init() {
        super.init()
        api = PolarBleApiDefaultImpl.polarImplementation(
            DispatchQueue.main,
            features: [
                .feature_hr,
                .feature_polar_sdk_mode,
                .feature_battery_info,
                .feature_device_info,
                .feature_polar_online_streaming,
                .feature_polar_offline_recording,
                .feature_polar_device_time_setup,
                .feature_polar_h10_exercise_recording
            ],
            restoreIdentifier: "PolarBleSdkRestoreId"
        )

        setObservers()
        // Initialize stream managers
        hrManager = HrStreamManager(api: api, bridge: self)
        accManager = AccStreamManager(api: api, bridge: self)
        gyrManager = GyrStreamManager(api: api, bridge: self)
        ppgManager = PpgStreamManager(api: api, bridge: self)
    }

    private func setObservers() {
        api?.observer = self
        api?.deviceInfoObserver = self
//         api?.powerStateObserver = self
//         api?.deviceHrObserver = self
//         api?.deviceFeaturesObserver = self
    }

    @objc(connectToDevice:resolver:rejecter:)
    func connectToDevice(
        _ identifier: String,
        resolver resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
    ) {
        do {
            guard let api = api else {
//                 throw PolarBleError.unconfigured
                reject("UNCONFIGURED", "Polar API not initialized", nil)
                return
            }

            pendingResolver = resolve
            pendingRejecter = reject
            deviceConnected = false
            batteryReceived = false
            connectionData = [:]


            try api.connectToDevice(identifier)
            NSLog("PolarBridge: Connecting to device \(identifier)")
        } catch {
            NSLog("PolarBridge: Failed to connect \(error.localizedDescription)")
            reject("INVALID_ARGUMENT", "Invalid device ID", error)
        }
    }

    @objc(disconnectFromDevice:)
    func disconnectFromDevice(_ identifier: String) {
        guard let api = api else { return }
        do {
            try api.disconnectFromDevice(identifier)
            NSLog("PolarBridge: Disconnected from \(identifier)")
        } catch {
            NSLog("PolarBridge: Failed to disconnect \(error.localizedDescription)")
        }
    }

    @objc(fetchHrData:bufferMs:)
    func fetchHrData(_ deviceId: String, bufferMs: NSNumber?) {
        hrManager?.fetchHrData(deviceId, bufferMs: bufferMs)
    }

    @objc(fetchAccData:bufferMs:)
    func fetchAccData(_ deviceId: String, bufferMs: NSNumber?) {
        accManager?.fetchAccData(deviceId, bufferMs: bufferMs)
    }

    @objc(fetchGyrData:bufferMs:)
    func fetchGyrData(_ deviceId: String, bufferMs: NSNumber?) {
        gyrManager?.fetchGyrData(deviceId, bufferMs: bufferMs)
    }

    @objc(fetchPpgData:bufferMs:)
    func fetchPpgData(_ deviceId: String, bufferMs: NSNumber?) {
        ppgManager?.fetchPpgData(deviceId, bufferMs: bufferMs)
    }

    @objc func disposeHrStream(){
        hrManager?.disposeHrStream()
    }

    @objc func disposeAccStream(){
        accManager?.disposeAccStream()
    }

    @objc func disposeGyrStream(){
        gyrManager?.disposeGyrStream()
    }

    @objc func disposePpgStream(){
        ppgManager?.disposePpgStream()
    }

    private func disposeAllStreams() {
        disposeHrStream()
        disposeAccStream()
        disposeGyrStream()
        disposeAccStream()
    }

    @objc(getBatteryLevel:resolver:rejecter:)
    func getBatteryLevel(_ deviceId: String,
        resolver resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
    ) {
        guard let api = api else {
            reject("UNCONFIGURED", "Polar API not initialized", nil)
            return
        }
        do {
            let batteryLevel = try api.getBatteryLevel(identifier: deviceId)
            NSLog("PolarBridge Battery: \(batteryLevel)%")
            let result: [String: Any] = ["batteryLevel": batteryLevel]
            resolve(result)
        } catch {
            NSLog("PolarBridge Error getting battery level: \(error.localizedDescription)")
        }

    }

    @objc(getChargerState:resolver:rejecter:)
    func getChargerState(_ deviceId: String,
        resolver resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
    ) {
        guard let api = api else {
            reject("UNCONFIGURED", "Polar API not initialized", nil)
            return
        }
        do {
            let chargerState = try api.getChargerState(identifier: deviceId)
            NSLog("PolarBridge Charger State: \(chargerState)")
            let result: [String: Any] = ["chargerState": "\(chargerState)"]
            resolve(result)
        } catch {
            NSLog("PolarBridge Error getting charger state: \(error.localizedDescription)")
        }
    }

    @objc(setDeviceTime:)
    func setDeviceTime(_ deviceId: String) {
        NSLog("PolarBridge: Set device time for: \(deviceId)")

        guard let api = api else {
            NSLog("PolarBridge: Polar API not initialized")
            return
        }

        let now = Date()
        let timeZone = TimeZone.current
        NSLog("PolarBridge: Set device: \(deviceId) time to \(now)")

        Task { @MainActor in
            do {
                try await api.setLocalTime(deviceId, time: now, zone: timeZone)
                let timeSetString = "Time \(now) set to device"
                NSLog("PolarBridge: \(timeSetString)")
            } catch {
                NSLog("PolarBridge: Set time failed: \(error.localizedDescription)")
            }
        }
    }

    @objc(getDeviceTime:resolver:rejecter:)
    func getDeviceTime(
        _ deviceId: String,
        resolver resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
    ) {
        NSLog("PolarBridge: Get device time for: \(deviceId)")

        guard let api = api else {
            reject("UNCONFIGURED", "Polar API not initialized", nil)
            return
        }

        Task { @MainActor in
            do {
                let date = try await api.getLocalTime(deviceId)
                let timeGetString = "\(date) read from the device"
                NSLog("PolarBridge: \(timeGetString)")

                let result: [String: Any] = [
                    "time": "\(date)",
                    "timeMs": Double(date.timeIntervalSince1970 * 1000)
                ]

                resolve(result)
            } catch {
                NSLog("PolarBridge: Get time failed: \(error.localizedDescription)")
                reject("GET_DEVICE_TIME_ERROR", "Failed to get device time", error)
            }
        }
    }

    @objc(getDiskSpace:resolver:rejecter:)
    func getDiskSpace(
        _ deviceId: String,
        resolver resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
    ) {
        NSLog("PolarBridge: Get Disk Space")

        guard let api = api else {
            reject("UNCONFIGURED", "Polar API not initialized", nil)
            return
        }

        Task { @MainActor in
            do {
                let diskSpace = try await api.getDiskSpace(deviceId)
                NSLog("Disk space left: \(diskSpace.freeSpace)/\(diskSpace.totalSpace) Bytes")

                // React Native doesn't support Int64: convert to Double
                let result: [String: Any] = [
                    "freeSpace": Double(diskSpace.freeSpace),
                    "totalSpace": Double(diskSpace.totalSpace)
                ]

                resolve(result)
            } catch {
                NSLog("Get disk space failed: \(error.localizedDescription)")
                reject("GET_DISK_SPACE_ERROR", "Failed to get device disk space", error)
            }
        }
    }

    @objc(doFactoryReset:)
    func doFactoryReset(_ deviceId: String) {
        NSLog("PolarBridge: do factory reset for: \(deviceId)")

        guard let api = api else {
            NSLog("PolarBridge: Polar API not initialized")
            return
        }

        Task { @MainActor in
            do {
                try await api.doFactoryReset(deviceId, preservePairingInformation: true)
                NSLog("PolarBridge: send do factory reset to device")
            } catch {
                NSLog("PolarBridge: do factory reset failed: \(error.localizedDescription)")
            }
        }
    }

    // Returns promise for connecting to the device
    private func maybeResolve() {
        if deviceConnected && batteryReceived {
            pendingResolver?(connectionData)
            pendingResolver = nil
            pendingRejecter = nil
        }
    }

    // Emit events
    override func sendEvent(withName name: String!, body: Any!) {
        super.sendEvent(withName: name, body: body)
    }

    override func supportedEvents() -> [String]! {
        return PolarEvent.allCases.map { $0.rawValue }
    }

    override static func requiresMainQueueSetup() -> Bool {
        return true
    }

    @objc func scanDevices() {
        NSLog("PolarBridge: Scan Devices triggered")

        // If already scanning, stop and complete
        if scanTask != nil {
            scanTask?.cancel()
            scanTask = nil
            NSLog("PolarBridge: Scan stopped")
            sendEvent(withName: PolarEvent.onScanComplete.rawValue, body: ["message": "Scan stopped"])
            return
        } else {
            // scanDisposable is nil
            NSLog("PolarBridge: No active scan")
        }

        guard let api = api else {
            NSLog("PolarBridge API not initialized")
            return
        }

        // Start scanning using Swift Concurrency Task
        scanTask = Task { @MainActor [weak self] in
            guard let self = self else { return }
            do {
                for try await polarDeviceInfo in api.searchForDevice() {
                    if Task.isCancelled { break }
                    NSLog("PolarBridge: Device found \(polarDeviceInfo.deviceId)")

                    let device: [String: Any] = [
                        "deviceId": polarDeviceInfo.deviceId,
                        "address": polarDeviceInfo.address,
                        "rssi": polarDeviceInfo.rssi,
                        "name": polarDeviceInfo.name,
                        "isConnectable": polarDeviceInfo.connectable
                    ]

                    self.sendEvent(withName: PolarEvent.onDeviceFound.rawValue, body: device)
                }
                NSLog("PolarBridge: Scan complete")
                self.sendEvent(withName: PolarEvent.onScanComplete.rawValue, body: ["message": "Scan complete"])
            } catch {
                NSLog("PolarBridge: Scan failed \(error.localizedDescription)")
                self.sendEvent(withName: PolarEvent.onScanError.rawValue, body: ["message": error.localizedDescription])
            }
            self.scanTask = nil
        }
    }
}

// MARK: - PolarBleApiObserver
extension PolarBridge : PolarBleApiObserver {
    func deviceConnecting(_ polarDeviceInfo: PolarDeviceInfo) {
        NSLog("Polar: Device connecting: \(polarDeviceInfo)")
    }

    func deviceConnected(_ polarDeviceInfo: PolarDeviceInfo) {
        NSLog("Polar: Device connected: \(polarDeviceInfo.deviceId)")
    }

    func deviceDisconnected(_ polarDeviceInfo: PolarDeviceInfo, pairingError: Bool) {
        NSLog("Polar: Device disconnected: \(polarDeviceInfo.deviceId) Pairing error: \(pairingError)")
    }
}

// MARK: - PolarBleApiDeviceInfoObserver
extension PolarBridge: PolarBleApiDeviceInfoObserver {
    func disInformationReceivedWithKeysAsStrings(_ identifier: String, key: String, value: String) {
//         NSLog("Polar: disInfoKeys: \(identifier) value: \(key)")
    }

    func disInformationReceived(_ identifier: String, uuid: CBUUID, value: String) {
//         NSLog("Polar: dis info: \(uuid.uuidString) value: \(value)")
    }

    func batteryLevelReceived(_ identifier: String, batteryLevel: UInt) {
        NSLog("Polar: Battery for \(identifier): \(batteryLevel)%")
        connectionData["batteryLevel"] = Int(batteryLevel)
        connectionData["connectedDeviceId"] = identifier
        deviceConnected = true
        batteryReceived = true
        maybeResolve()
    }

    func batteryChargingStatusReceived(_ identifier: String, chargingStatus: BleBasClient.ChargeState) {
        NSLog("Polar: Battery Charging status for \(identifier): \(chargingStatus)")
    }
}
