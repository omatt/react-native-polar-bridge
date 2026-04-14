import Foundation
import PolarBleSdk
import RxSwift
import React

class AccStreamManager {

    private weak var bridge: PolarBridge?
    private var api: PolarBleApi?

    private var accDisposable: Disposable?
    private var isAccStreaming = false

    private var accBuffer: [[String: Any]] = []
    private let accBufferQueue = DispatchQueue(label: "com.polarbridge.accBufferQueue")
    private var accFlushTimer: Timer?

    private let SENSOR_BUFFER_MS: TimeInterval = 10_000
    private let disposeBag = DisposeBag()

    init(api: PolarBleApi?, bridge: PolarBridge) {
        self.api = api
        self.bridge = bridge
    }

    // MARK: - Public

    func fetchAccData(_ deviceId: String, bufferMs: NSNumber?) {
        NSLog("AccStreamManager: Fetch ACC Data called on: \(deviceId) bufferMs: \(String(describing: bufferMs))")

        let resolvedBufferMs: TimeInterval
        if let number = bufferMs, !(number is NSNull) {
            resolvedBufferMs = number.doubleValue >= 0 ? number.doubleValue : SENSOR_BUFFER_MS
        } else {
            resolvedBufferMs = SENSOR_BUFFER_MS
        }

        guard let api = api else {
            NSLog("AccStreamManager: Polar API not initialized")
            return
        }

        if isAccStreaming {
            disposeAccStream()
            stopAccFlushTimer()
            flushAccBuffer()
            isAccStreaming = false

            bridge?.sendEvent(
                withName: PolarEvent.PolarAccComplete.rawValue,
                body: ["message": "ACC Stream stopped"]
            )
            return
        }

        isAccStreaming = true
        startAccFlushTimer(bufferMs: resolvedBufferMs)

        accDisposable = SensorSettings.requestStreamSettings(
                api: api,
                identifier: deviceId,
                feature: .acc
            )
            .flatMap { settings in
                api.startAccStreaming(deviceId, settings: settings).asObservable()
            }
            .observe(on: MainScheduler.instance)
            .subscribe(
                onNext: { [weak self] accData in
                    guard let self = self else { return }

                    self.accBufferQueue.async {
                        for sample in accData {
                            var event: [String: Any] = [:]
                            event["accX"] = sample.x
                            event["accY"] = sample.y
                            event["accZ"] = sample.z
                            event["accTimestamp"] = Double(sample.timeStamp)

                            self.accBuffer.append(event)
                        }
                    }
                },
                onError: { [weak self] error in
                    guard let self = self else { return }

                    self.stopAccFlushTimer()
                    self.flushAccBuffer()
                    self.isAccStreaming = false

                    self.bridge?.sendEvent(
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

                    self.bridge?.sendEvent(
                        withName: PolarEvent.PolarAccComplete.rawValue,
                        body: ["message": "ACC stream complete"]
                    )

                    self.accDisposable = nil
                }
            )

        accDisposable?.disposed(by: disposeBag)
    }

    func disposeAccStream() {
        isAccStreaming = false
        accDisposable?.dispose()
    }

    // MARK: - Buffer

    private func startAccFlushTimer(bufferMs: TimeInterval) {
        stopAccFlushTimer()

        let intervalSeconds = bufferMs / 1000.0

        DispatchQueue.main.async {
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
        DispatchQueue.main.async {
            self.accFlushTimer?.invalidate()
            self.accFlushTimer = nil
        }
    }

    private func flushAccBuffer() {
        accBufferQueue.async {
            guard !self.accBuffer.isEmpty else { return }

            let events = self.accBuffer
            self.accBuffer.removeAll()

            DispatchQueue.main.async {
                for event in events {
                    self.bridge?.sendEvent(
                        withName: PolarEvent.PolarAccData.rawValue,
                        body: event
                    )
                }
            }
        }
    }
}
