import CoreBluetooth
import PolarBleSdk
import React
import RxSwift

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

    private var scanDisposable: Disposable?
    private var hrDisposable: Disposable?
    private var isHrStreaming = false
    private var accDisposable: Disposable?
    private var isAccStreaming = false
    private var gyrDisposable: Disposable?
    private var isGyrStreaming = false
    private var ppgDisposable: Disposable?
    private var isPpgStreaming = false
    private let disposeBag = DisposeBag()

    /// Flush interval for all sensor buffers (milliseconds)
    private let SENSOR_BUFFER_MS: TimeInterval = 10_000

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

    func requestStreamSettings(_ identifier: String,
    feature: PolarDeviceDataType
    ) -> Observable<PolarSensorSetting> {

        guard let api = api else {
            return Observable.error(NSError(domain: "PolarBridge", code: -1, userInfo: [
                NSLocalizedDescriptionKey: "Polar API not initialized"
            ]))
        }

        let availableSettings = api.requestStreamSettings(identifier, feature: feature)

        let allSettings = api.requestFullStreamSettings(identifier, feature: feature)
            .catch { error in
                NSLog("Full stream settings NOT available for \(feature). Reason: \(error.localizedDescription)")
                return Single.just(PolarSensorSetting([:]))
            }

        return Single.zip(availableSettings, allSettings)
            .flatMap { available, all -> Single<PolarSensorSetting> in

                if available.settings.isEmpty {
                    return Single.error(NSError(domain: "PolarBridge", code: -2, userInfo: [
                        NSLocalizedDescriptionKey: "Settings are not available"
                    ]))
                }

                NSLog("Feature \(feature) available settings: \(available.settings)")
                NSLog("Feature \(feature) all settings: \(all.settings)")

                for setting in available.settings {
                    NSLog("Available Setting: \(setting)")
                }

                for setting in all.settings {
                    NSLog("All Setting: \(setting)")
                }

                // Return default settings (same as Kotlin: sensorSettings.first)
                return Single.just(available)
            }
            .asObservable()
    }

    private var hrBuffer: [[String: Any]] = []
    private let hrBufferQueue = DispatchQueue(label: "com.polarbridge.hrBufferQueue")
    private var hrFlushTimer: Timer?

    private func startHrFlushTimer(bufferMs: TimeInterval) {
        stopHrFlushTimer()
        let intervalSeconds = bufferMs / 1000.0   // Timer always uses seconds,
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            self.hrFlushTimer = Timer(
                timeInterval: intervalSeconds,
                repeats: true
            ) { [weak self] _ in
                self?.flushHrBuffer()
            }

            RunLoop.main.add(self.hrFlushTimer!, forMode: .common)
        }
    }

    private func stopHrFlushTimer() {
        DispatchQueue.main.async { [weak self] in
            self?.hrFlushTimer?.invalidate()
            self?.hrFlushTimer = nil
        }
    }

    private func flushHrBuffer() {
        hrBufferQueue.async { [weak self] in
            guard let self = self else { return }
            guard !self.hrBuffer.isEmpty else { return }

            let events = self.hrBuffer
            self.hrBuffer.removeAll()

            DispatchQueue.main.async {
                for event in events {
                    self.sendEvent(
                        withName: PolarEvent.PolarHrData.rawValue,
                        body: event
                    )
                }
            }
        }
    }

    @objc(fetchHrData:bufferMs:)
    func fetchHrData(_ deviceId: String, bufferMs: NSNumber?) {
        NSLog("PolarBridge: Fetch HR Data called on: \(deviceId) bufferMs: \(bufferMs)")
        let resolvedBufferMs: TimeInterval

        if let number = bufferMs, !(number is NSNull) {
           resolvedBufferMs = number.doubleValue >= 0 ? number.doubleValue : SENSOR_BUFFER_MS
        } else {
           resolvedBufferMs = SENSOR_BUFFER_MS
        }
        guard let api = api else {
            NSLog("PolarBridge: Polar API not initialized")
            return
        }

        // Dispose previous subscription if running
        if isHrStreaming {
            disposeHrStream()
            stopHrFlushTimer()
            flushHrBuffer()
            isHrStreaming = false

            NSLog("PolarBridge: HR Stream stopped")
            sendEvent(withName: PolarEvent.PolarHrComplete.rawValue, body: ["message": "HR Stream stopped"])
            return
        }

        isHrStreaming = true
        startHrFlushTimer(bufferMs: resolvedBufferMs)

        // Start HR streaming
        hrDisposable = api.startHrStreaming(deviceId)
            .observe(on: MainScheduler.instance)
            .subscribe(
                onNext: { [weak self] hrData in
                    guard let self = self else { return }
                    NSLog("PolarBridge: HR data samples count: \(hrData.count)")

                    self.hrBufferQueue.async {
                        for sample in hrData {
                            NSLog("HR bpm: \(sample.hr), rrs: \(sample.rrsMs), rrAvailable: \(sample.rrAvailable), contactStatus: \(sample.contactStatus), contactStatusSupported: \(sample.contactStatusSupported)")

                            // Create dictionary to send as event
                            var event: [String: Any] = [:]
                            event["hr"] = sample.hr
                            event["rrsMs"] = sample.rrsMs
                            event["rrAvailable"] = sample.rrAvailable
                            event["contactStatus"] = sample.contactStatus
                            event["contactStatusSupported"] = sample.contactStatusSupported
                            event["timestamp"] = Date().timeIntervalSince1970 * 1000

//                             self.sendEvent(withName: PolarEvent.PolarHrData.rawValue, body: event)
                            self.hrBuffer.append(event)
                        }
                    }
                },
                onError: { [weak self] error in
                    guard let self = self else { return }
                    NSLog("PolarBridge: HR stream failed: \(error.localizedDescription)")
                    self.stopHrFlushTimer()
                    self.flushHrBuffer()
                    self.isHrStreaming = false

                    self.sendEvent(withName: PolarEvent.PolarHrError.rawValue, body: ["error": error.localizedDescription])
                },
                onCompleted: { [weak self] in
                    guard let self = self else { return }
                    NSLog("PolarBridge: HR stream complete")
                    self.stopHrFlushTimer()
                    self.flushHrBuffer()
                    self.isHrStreaming = false
                    self.sendEvent(withName: PolarEvent.PolarHrComplete.rawValue, body: ["message": "HR stream complete"])
                }
            )

        hrDisposable?.disposed(by: disposeBag)

    }

    // ACC
    private var accBuffer: [[String: Any]] = []
    private let accBufferQueue = DispatchQueue(label: "com.polarbridge.accBufferQueue")
    private var accFlushTimer: Timer?

    // GYR
    private var gyrBuffer: [[String: Any]] = []
    private let gyrBufferQueue = DispatchQueue(label: "com.polarbridge.gyrBufferQueue")
    private var gyrFlushTimer: Timer?

    // PPG
    private var ppgBuffer: [[String: Any]] = []
    private let ppgBufferQueue = DispatchQueue(label: "com.polarbridge.ppgBufferQueue")
    private var ppgFlushTimer: Timer?

    private func startAccFlushTimer(bufferMs: TimeInterval) {
        stopAccFlushTimer()
        let intervalSeconds = bufferMs / 1000.0
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }

            self.accFlushTimer = Timer(
                timeInterval: intervalSeconds,
                repeats: true
            ) { [weak self] _ in
                self?.flushAccBuffer()
            }

            RunLoop.main.add(self.accFlushTimer!, forMode: .common)
        }
    }

    private func stopAccFlushTimer() {
        DispatchQueue.main.async { [weak self] in
            self?.accFlushTimer?.invalidate()
            self?.accFlushTimer = nil
        }
    }

    private func flushAccBuffer() {
        accBufferQueue.async { [weak self] in
            guard let self = self else { return }
            guard !self.accBuffer.isEmpty else { return }

            let events = self.accBuffer
            self.accBuffer.removeAll()

            DispatchQueue.main.async {
                for event in events {
                    self.sendEvent(
                        withName: PolarEvent.PolarAccData.rawValue,
                        body: event
                    )
                }
            }
        }
    }

    @objc(fetchAccData:bufferMs:)
    func fetchAccData(_ deviceId: String, bufferMs: NSNumber?) {
        NSLog("PolarBridge: Fetch ACC Data called on: \(deviceId) bufferMs: \(bufferMs)")
        let resolvedBufferMs: TimeInterval

        if let number = bufferMs, !(number is NSNull) {
          resolvedBufferMs = number.doubleValue >= 0 ? number.doubleValue : SENSOR_BUFFER_MS
        } else {
          resolvedBufferMs = SENSOR_BUFFER_MS
        }
        guard let api = api else {
            NSLog("PolarBridge: Polar API not initialized")
            return
        }

        // Stop existing ACC stream if running
        if isAccStreaming {
            disposeAccStream()
            stopAccFlushTimer()
            flushAccBuffer()
            isAccStreaming = false

            NSLog("PolarBridge: ACC Stream stopped")
            sendEvent(withName: PolarEvent.PolarAccComplete.rawValue, body: ["message": "ACC Stream stopped"])
            return
        }

        isAccStreaming = true
        startAccFlushTimer(bufferMs: resolvedBufferMs)

        accDisposable = requestStreamSettings(deviceId, feature: .acc)
            .flatMap { settings in
                api.startAccStreaming(deviceId, settings: settings).asObservable()
            }
            .observe(on: MainScheduler.instance)
            .subscribe(
                onNext: { [weak self] accData in
                    guard let self = self else { return }

                    self.accBufferQueue.async {
                        for sample in accData {
                            NSLog("ACC x: \(sample.x) y: \(sample.y) z: \(sample.z) timestamp: \(sample.timeStamp)")

                            var event: [String: Any] = [:]
                            event["accX"] = sample.x
                            event["accY"] = sample.y
                            event["accZ"] = sample.z
                            event["accTimestamp"] = Double(sample.timeStamp)  // RN doesn’t support int64

                            self.accBuffer.append(event)
//                             self.sendEvent(withName: PolarEvent.PolarAccData.rawValue, body: event)
                        }
                    }
                },
                onError: { [weak self] error in
                    guard let self = self else { return }

                    self.stopAccFlushTimer()
                    self.flushAccBuffer()
                    self.isAccStreaming = false

                    NSLog("PolarBridge: ACC stream failed: \(error.localizedDescription)")

                    self.sendEvent(
                        withName: PolarEvent.PolarAccError.rawValue,
                        body: ["error": error.localizedDescription]
                    )

                    self.accDisposable = nil
                },
                onCompleted: { [weak self] in
                    guard let self = self else { return }

                    self.stopAccFlushTimer()
                    self.flushAccBuffer()
                    self.isAccStreaming = false

                    NSLog("PolarBridge: ACC stream complete")

                    self.sendEvent(
                        withName: PolarEvent.PolarAccComplete.rawValue,
                        body: ["message": "ACC stream complete"]
                    )

                    self.accDisposable = nil
                }
            )

        accDisposable?.disposed(by: disposeBag)
    }

    private func startGyrFlushTimer(bufferMs: TimeInterval) {
        stopGyrFlushTimer()
        let intervalSeconds = bufferMs / 1000.0
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }

            self.gyrFlushTimer = Timer(
                timeInterval: intervalSeconds,
                repeats: true
            ) { [weak self] _ in
                self?.flushGyrBuffer()
            }

            RunLoop.main.add(self.gyrFlushTimer!, forMode: .common)
        }
    }

    private func stopGyrFlushTimer() {
        DispatchQueue.main.async { [weak self] in
            self?.gyrFlushTimer?.invalidate()
            self?.gyrFlushTimer = nil
        }
    }

    private func flushGyrBuffer() {
        gyrBufferQueue.async { [weak self] in
            guard let self = self else { return }
            guard !self.gyrBuffer.isEmpty else { return }

            let events = self.gyrBuffer
            self.gyrBuffer.removeAll()

            DispatchQueue.main.async {
                for event in events {
                    self.sendEvent(
                        withName: PolarEvent.PolarGyrData.rawValue,
                        body: event
                    )
                }
            }
        }
    }

    @objc(fetchGyrData:bufferMs:)
    func fetchGyrData(_ deviceId: String, bufferMs: NSNumber?) {
        NSLog("PolarBridge: Fetch Gyroscope Data called on: \(deviceId) bufferMs: \(bufferMs)")
        let resolvedBufferMs: TimeInterval

        if let number = bufferMs, !(number is NSNull) {
          resolvedBufferMs = number.doubleValue >= 0 ? number.doubleValue : SENSOR_BUFFER_MS
        } else {
          resolvedBufferMs = SENSOR_BUFFER_MS
        }
        guard let api = api else {
            NSLog("PolarBridge: Polar API not initialized")
            return
        }

        if isGyrStreaming {
            disposeGyrStream()
            stopGyrFlushTimer()
            flushGyrBuffer()
            isGyrStreaming = false

            NSLog("PolarBridge: GYR Stream stopped")
            sendEvent(withName: PolarEvent.PolarAccComplete.rawValue, body: ["message": "GYR Stream stopped"])
            return
        }

        isGyrStreaming = true
        startGyrFlushTimer(bufferMs: resolvedBufferMs)

        gyrDisposable = requestStreamSettings(deviceId, feature: .gyro)
            .flatMap { settings in
                api.startGyroStreaming(deviceId, settings: settings).asObservable()
            }
            .observe(on: MainScheduler.instance)
            .subscribe(
                onNext: { [weak self] gyrData in
                    guard let self = self else { return }

                    self.gyrBufferQueue.async {
                        for sample in gyrData {
                            NSLog("GYR x: \(sample.x) y: \(sample.y) z: \(sample.z) timestamp: \(sample.timeStamp)")

                            var event: [String: Any] = [:]

                            // JS does NOT support Float — convert to String
                            event["gyrX"] = "\(sample.x)"
                            event["gyrY"] = "\(sample.y)"
                            event["gyrZ"] = "\(sample.z)"

                            // JS does NOT support Int64 — convert to Double
                            event["gyrTimestamp"] = Double(sample.timeStamp)

                            self.gyrBuffer.append(event)
//                             self.sendEvent(
//                                 withName: PolarEvent.PolarGyrData.rawValue,
//                                 body: event
//                             )
                        }
                    }
                },
                onError: { [weak self] error in
                    guard let self = self else { return }

                    stopGyrFlushTimer()
                    flushGyrBuffer()
                    isGyrStreaming = false

                    NSLog("PolarBridge: GYR stream failed: \(error.localizedDescription)")

                    self.sendEvent(
                        withName: PolarEvent.PolarGyrError.rawValue,
                        body: ["error": error.localizedDescription]
                    )

                    self.gyrDisposable = nil
                },
                onCompleted: { [weak self] in
                    guard let self = self else { return }

                    stopGyrFlushTimer()
                    flushGyrBuffer()
                    isGyrStreaming = false

                    NSLog("PolarBridge: GYR stream complete")

                    self.sendEvent(
                        withName: PolarEvent.PolarGyrComplete.rawValue,
                        body: ["message": "GYR stream complete"]
                    )

                    self.gyrDisposable = nil
                }
            )

        gyrDisposable?.disposed(by: disposeBag)
    }

    private func startPpgFlushTimer(bufferMs: TimeInterval) {
        stopPpgFlushTimer()
        let intervalSeconds = bufferMs / 1000.0
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }

            self.ppgFlushTimer = Timer(
                timeInterval: intervalSeconds,
                repeats: true
            ) { [weak self] _ in
                self?.flushPpgBuffer()
            }

            RunLoop.main.add(self.ppgFlushTimer!, forMode: .common)
        }
    }

    private func stopPpgFlushTimer() {
        DispatchQueue.main.async { [weak self] in
            self?.ppgFlushTimer?.invalidate()
            self?.ppgFlushTimer = nil
        }
    }

    private func flushPpgBuffer() {
        ppgBufferQueue.async { [weak self] in
            guard let self = self else { return }
            guard !self.ppgBuffer.isEmpty else { return }

            let events = self.ppgBuffer
            self.ppgBuffer.removeAll()

            DispatchQueue.main.async {
                for event in events {
                    self.sendEvent(
                        withName: PolarEvent.PolarPpgData.rawValue,
                        body: event
                    )
                }
            }
        }
    }

    @objc(fetchPpgData:bufferMs:)
    func fetchPpgData(_ deviceId: String, bufferMs: NSNumber?) {
        NSLog("PolarBridge: Fetch PPG Data called on: \(deviceId) bufferMs: \(bufferMs)")
        let resolvedBufferMs: TimeInterval

        if let number = bufferMs, !(number is NSNull) {
          resolvedBufferMs = number.doubleValue >= 0 ? number.doubleValue : SENSOR_BUFFER_MS
        } else {
          resolvedBufferMs = SENSOR_BUFFER_MS
        }
        guard let api = api else {
            NSLog("PolarBridge: Polar API not initialized")
            return
        }

        if isPpgStreaming {
            disposePpgStream()
            stopPpgFlushTimer()
            flushPpgBuffer()
            isPpgStreaming = false

            NSLog("PolarBridge: PPG Stream stopped")
            sendEvent(withName: PolarEvent.PolarAccComplete.rawValue, body: ["message": "PPG Stream stopped"])
            return
        }

        isPpgStreaming = true
        startPpgFlushTimer(bufferMs: resolvedBufferMs)

        ppgDisposable = requestStreamSettings(deviceId, feature: .ppg)
            .flatMap { settings in
                api.startPpgStreaming(deviceId, settings: settings).asObservable()
            }
            .observe(on: MainScheduler.instance)
            .subscribe(
                onNext: { [weak self] ppgData in
                    guard let self = self else { return }

                    // Only handle PPG3_AMBIENT1 type (just like Kotlin)
                    if ppgData.type == PpgDataType.ppg3_ambient1 {
                        self.ppgBufferQueue.async {
                            for sample in ppgData.samples {

                                NSLog("PPG ppg0: \(sample.channelSamples[0]) ppg1: \(sample.channelSamples[1]) ppg2: \(sample.channelSamples[2]) ambient: \(sample.channelSamples[3]) ts: \(sample.timeStamp)")

                                var event: [String: Any] = [:]

                                // Float → String (React Native cannot handle Float)
                                event["ppg0"] = "\(sample.channelSamples[0])"
                                event["ppg1"] = "\(sample.channelSamples[1])"
                                event["ppg2"] = "\(sample.channelSamples[2])"
                                event["ambient"] = "\(sample.channelSamples[3])"

                                // Int64 timestamp → Double
                                event["ppgTimestamp"] = Double(sample.timeStamp)

                                self.ppgBuffer.append(event)
//                                 self.sendEvent(
//                                     withName: PolarEvent.PolarPpgData.rawValue,
//                                     body: event
//                                 )
                            }
                        }
                    }
                },
                onError: { [weak self] error in
                    guard let self = self else { return }

                    self.stopPpgFlushTimer()
                    self.flushPpgBuffer()
                    self.isPpgStreaming = false

                    NSLog("PolarBridge: PPG stream failed: \(error.localizedDescription)")

                    self.sendEvent(
                        withName: PolarEvent.PolarPpgError.rawValue,
                        body: ["error": error.localizedDescription]
                    )

                    self.ppgDisposable = nil
                },
                onCompleted: { [weak self] in
                    guard let self = self else { return }

                    self.stopPpgFlushTimer()
                    self.flushPpgBuffer()
                    self.isPpgStreaming = false

                    NSLog("PolarBridge: PPG stream complete")

                    self.sendEvent(
                        withName: PolarEvent.PolarPpgComplete.rawValue,
                        body: ["message": "PPG stream complete"]
                    )

                    self.ppgDisposable = nil
                }
            )

        ppgDisposable?.disposed(by: disposeBag)
    }

    @objc func disposeHrStream(){
        isHrStreaming = false
        hrDisposable?.dispose()
    }

    @objc func disposeAccStream(){
        isAccStreaming = false
        accDisposable?.dispose()
    }

    @objc func disposeGyrStream(){
        isGyrStreaming = false
        gyrDisposable?.dispose()
    }

    @objc func disposePpgStream(){
        isPpgStreaming = false
        ppgDisposable?.dispose()
    }

    private func disposeAllStreams() {
        disposeHrStream()
        disposeAccStream()
        disposeGyrStream()
        disposeAccStream()
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

        api.setLocalTime(deviceId, time: now, zone: timeZone)
            .observe(on: MainScheduler.instance)
            .subscribe(
                onCompleted: {
                    let timeSetString = "Time \(now) set to device"
                    NSLog("PolarBridge: \(timeSetString)")
                },
                onError: { error in
                    NSLog("PolarBridge: Set time failed: \(error.localizedDescription)")
                }
            )
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

        api.getLocalTime(deviceId)
            .observe(on: MainScheduler.instance)
            .subscribe(
                onSuccess: { date in
                    let timeGetString = "\(date) read from the device"
                    NSLog("PolarBridge: \(timeGetString)")

                    let result: [String: Any] = [
                        "time": "\(date)",
                        "timeMs": Double(date.timeIntervalSince1970 * 1000)
                    ]

                    resolve(result)
                },
                onFailure: { error in
                    NSLog("PolarBridge: Get time failed: \(error.localizedDescription)")
                    reject("GET_DEVICE_TIME_ERROR", "Failed to get device time", error)
                }
            )
            .disposed(by: disposeBag)
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

        api.getDiskSpace(deviceId)
            .observe(on: MainScheduler.instance)
            .subscribe(
                onSuccess: { diskSpace in
                    NSLog("Disk space left: \(diskSpace.freeSpace)/\(diskSpace.totalSpace) Bytes")

                    // React Native doesn't support Int64: convert to Double
                    let result: [String: Any] = [
                        "freeSpace": Double(diskSpace.freeSpace),
                        "totalSpace": Double(diskSpace.totalSpace)
                    ]

                    resolve(result)
                },
                onFailure: { error in
                    NSLog("Get disk space failed: \(error.localizedDescription)")
                    reject("GET_DISK_SPACE_ERROR", "Failed to get device disk space", error)
                }
            )
    }

    @objc(doFactoryReset:)
    func doFactoryReset(_ deviceId: String) {
        NSLog("PolarBridge: do factory reset for: \(deviceId)")

        guard let api = api else {
            NSLog("PolarBridge: Polar API not initialized")
            return
        }

        api.doFactoryReset(deviceId, preservePairingInformation: true)
            .observe(on: MainScheduler.instance)
            .subscribe(
                onCompleted: {
                    NSLog("PolarBridge: send do factory reset to device")
                },
                onError: { error in
                    NSLog("PolarBridge: do factory reset failed: \(error.localizedDescription)")
                }
            )
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

    func batteryChargingStatusReceived(_ identifier: String, chargingStatus: BleBasClient.ChargeState) {
        NSLog("Polar: Battery Charging status for \(identifier): \(chargingStatus)")
    }
}
