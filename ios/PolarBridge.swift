import CoreBluetooth
import PolarBleSdk
import React
import RxSwift

enum PolarEvent: String, CaseIterable {
    case onDeviceFound
    case onScanError
    case onScanComplete
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

    private var scanDisposable: Disposable?
    private let disposeBag = DisposeBag()

    @objc
    func multiply(_ a: NSNumber,withB b: NSNumber) -> NSNumber {
        let result = a.doubleValue * b.doubleValue
        NSLog("Hello from Swift! Result: \(result)")
        return NSNumber(value: result)
    }

    override init() {
        super.init()
        api = PolarBleApiDefaultImpl.polarImplementation(DispatchQueue.main, features: [PolarBleSdkFeature.feature_hr,
                                                                                          PolarBleSdkFeature.feature_polar_sdk_mode,
                                                                                          PolarBleSdkFeature.feature_battery_info,
                                                                                          PolarBleSdkFeature.feature_device_info,
                                                                                          PolarBleSdkFeature.feature_polar_online_streaming,
                                                                                          PolarBleSdkFeature.feature_polar_offline_recording,
                                                                                          PolarBleSdkFeature.feature_polar_device_time_setup,
                                                                                          PolarBleSdkFeature.feature_polar_h10_exercise_recording])

        setObservers()
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
            NSLog("Polar: Connecting to device \(identifier)")
        } catch {
            NSLog("Polar: Failed to connect \(error.localizedDescription)")
            reject("INVALID_ARGUMENT", "Invalid device ID", error)
        }
    }

    @objc(disconnectFromDevice:)
    func disconnectFromDevice(_ identifier: String) {
        guard let api = api else { return }
        do {
            try api.disconnectFromDevice(identifier)
            NSLog("Polar: Disconnected from \(identifier)")
        } catch {
            NSLog("Polar: Failed to disconnect \(error.localizedDescription)")
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

    @objc func scanDevices() {
        NSLog("PolarBridge: Scan Devices triggered")

        // If already scanning, stop and complete
        if let disposable = scanDisposable {
            disposable.dispose()
            scanDisposable = nil
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

        // Start scanning
        scanDisposable = api.searchForDevice()
            .observe(on: MainScheduler.instance)
            .subscribe(
                onNext: { [weak self] polarDeviceInfo in
                    guard let self = self else { return }
                    NSLog("PolarBridge: Device found \(polarDeviceInfo.deviceId)")

                    let device: [String: Any] = [
                        "deviceId": polarDeviceInfo.deviceId,
                        "address": polarDeviceInfo.address ?? "",
                        "rssi": polarDeviceInfo.rssi,
                        "name": polarDeviceInfo.name ?? "",
                        "isConnectable": polarDeviceInfo.connectable
                    ]

                    self.sendEvent(withName: PolarEvent.onDeviceFound.rawValue, body: device)
                },
                onError: { [weak self] error in
                    guard let self = self else { return }
                    NSLog("PolarBridge: Scan failed \(error.localizedDescription)")
                    self.sendEvent(withName: PolarEvent.onScanError.rawValue, body: ["message": error.localizedDescription])
                },
                onCompleted: { [weak self] in
                    guard let self = self else { return }
                    NSLog("PolarBridge: Scan complete")
                    self.sendEvent(withName: PolarEvent.onScanComplete.rawValue, body: ["message": "Scan complete"])
                }
            )

        scanDisposable?.disposed(by: disposeBag)
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
}
