import Foundation
import PolarBleSdk
import RxSwift
import React

class GyrStreamManager {

    private weak var bridge: PolarBridge?
    private var api: PolarBleApi?

    private var gyrDisposable: Disposable?
    private var isGyrStreaming = false

    private var gyrBuffer: [[String: Any]] = []
    private let gyrBufferQueue = DispatchQueue(label: "com.polarbridge.gyrBufferQueue")
    private var gyrFlushTimer: Timer?

    private let SENSOR_BUFFER_MS: TimeInterval = 10_000
    private let disposeBag = DisposeBag()

    init(api: PolarBleApi?, bridge: PolarBridge) {
        self.api = api
        self.bridge = bridge
    }

    // MARK: - Public

    func fetchGyrData(_ deviceId: String, bufferMs: NSNumber?) {
        NSLog("GyrStreamManager: Fetch GYR Data called on: \(deviceId) bufferMs: \(String(describing: bufferMs))")

        let resolvedBufferMs: TimeInterval
        if let number = bufferMs, !(number is NSNull) {
            resolvedBufferMs = number.doubleValue >= 0 ? number.doubleValue : SENSOR_BUFFER_MS
        } else {
            resolvedBufferMs = SENSOR_BUFFER_MS
        }

        guard let api = api else {
            NSLog("GyrStreamManager: Polar API not initialized")
            return
        }

        if isGyrStreaming {
            disposeGyrStream()
            stopGyrFlushTimer()
            flushGyrBuffer()
            isGyrStreaming = false

            bridge?.sendEvent(
                withName: PolarEvent.PolarGyrComplete.rawValue,
                body: ["message": "GYR Stream stopped"]
            )
            return
        }

        isGyrStreaming = true
        startGyrFlushTimer(bufferMs: resolvedBufferMs)

        gyrDisposable = SensorSettings.requestStreamSettings(
                api: api,
                identifier: deviceId,
                feature: .gyro
            )
            .flatMap { settings in
                api.startGyroStreaming(deviceId, settings: settings).asObservable()
            }
            .observe(on: MainScheduler.instance)
            .subscribe(
                onNext: { [weak self] gyrData in
                    guard let self = self else { return }

                    self.gyrBufferQueue.async {
                        for sample in gyrData {
                            var event: [String: Any] = [:]
                            event["gyrX"] = "\(sample.x)"
                            event["gyrY"] = "\(sample.y)"
                            event["gyrZ"] = "\(sample.z)"
                            event["gyrTimestamp"] = Double(sample.timeStamp)

                            self.gyrBuffer.append(event)
                        }
                    }
                },
                onError: { [weak self] error in
                    guard let self = self else { return }

                    self.stopGyrFlushTimer()
                    self.flushGyrBuffer()
                    self.isGyrStreaming = false

                    self.bridge?.sendEvent(
                        withName: PolarEvent.PolarGyrError.rawValue,
                        body: ["error": error.localizedDescription]
                    )

                    self.gyrDisposable = nil
                },
                onCompleted: { [weak self] in
                    guard let self = self else { return }

                    self.stopGyrFlushTimer()
                    self.flushGyrBuffer()
                    self.isGyrStreaming = false

                    self.bridge?.sendEvent(
                        withName: PolarEvent.PolarGyrComplete.rawValue,
                        body: ["message": "GYR stream complete"]
                    )

                    self.gyrDisposable = nil
                }
            )

        gyrDisposable?.disposed(by: disposeBag)
    }

    func disposeGyrStream() {
        isGyrStreaming = false
        gyrDisposable?.dispose()
    }

    // MARK: - Buffer

    private func startGyrFlushTimer(bufferMs: TimeInterval) {
        stopGyrFlushTimer()

        let intervalSeconds = bufferMs / 1000.0

        DispatchQueue.main.async {
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
        DispatchQueue.main.async {
            self.gyrFlushTimer?.invalidate()
            self.gyrFlushTimer = nil
        }
    }

    private func flushGyrBuffer() {
        gyrBufferQueue.async {
            guard !self.gyrBuffer.isEmpty else { return }

            let events = self.gyrBuffer
            self.gyrBuffer.removeAll()

            DispatchQueue.main.async {
                for event in events {
                    self.bridge?.sendEvent(
                        withName: PolarEvent.PolarGyrData.rawValue,
                        body: event
                    )
                }
            }
        }
    }
}
