import Foundation
import PolarBleSdk
import RxSwift
import React

class PpgStreamManager {

    private weak var bridge: PolarBridge?
    private var api: PolarBleApi?

    private var ppgDisposable: Disposable?
    private var isPpgStreaming = false

    private var ppgBuffer: [[String: Any]] = []
    private let ppgBufferQueue = DispatchQueue(label: "com.polarbridge.ppgBufferQueue")
    private var ppgFlushTimer: Timer?

    private let SENSOR_BUFFER_MS: TimeInterval = 10_000
    private let disposeBag = DisposeBag()

    init(api: PolarBleApi?, bridge: PolarBridge) {
        self.api = api
        self.bridge = bridge
    }

    // MARK: - Public

    func fetchPpgData(_ deviceId: String, bufferMs: NSNumber?) {
        NSLog("PpgStreamManager: Fetch PPG Data called on: \(deviceId) bufferMs: \(String(describing: bufferMs))")

        let resolvedBufferMs: TimeInterval
        if let number = bufferMs, !(number is NSNull) {
            resolvedBufferMs = number.doubleValue >= 0 ? number.doubleValue : SENSOR_BUFFER_MS
        } else {
            resolvedBufferMs = SENSOR_BUFFER_MS
        }

        guard let api = api else {
            NSLog("PpgStreamManager: Polar API not initialized")
            return
        }

        guard let bridge = bridge else {
            NSLog("PpgStreamManager: bridge is nil")
            return
        }

        if isPpgStreaming {
            disposePpgStream()
            stopPpgFlushTimer()
            flushPpgBuffer()
            isPpgStreaming = false

            bridge.sendEvent(
                withName: PolarEvent.PolarPpgComplete.rawValue,
                body: ["message": "PPG Stream stopped"]
            )
            return
        }

        isPpgStreaming = true
        startPpgFlushTimer(bufferMs: resolvedBufferMs)

        ppgDisposable = SensorSettings.requestStreamSettings(
                api: api,
                identifier: deviceId,
                feature: .ppg
            )
            .flatMap { settings in
                api.startPpgStreaming(deviceId, settings: settings).asObservable()
            }
            .observe(on: MainScheduler.instance)
            .subscribe(
                onNext: { [weak self] ppgData in
                    guard let self = self else { return }

                    if ppgData.type == PpgDataType.ppg3_ambient1 {
                        self.ppgBufferQueue.async {
                            for sample in ppgData.samples {

                                var event: [String: Any] = [:]

                                event["ppg0"] = "\(sample.channelSamples[0])"
                                event["ppg1"] = "\(sample.channelSamples[1])"
                                event["ppg2"] = "\(sample.channelSamples[2])"
                                event["ambient"] = "\(sample.channelSamples[3])"

                                event["ppgTimestamp"] = Double(sample.timeStamp)

                                self.ppgBuffer.append(event)
                            }
                        }
                    }
                },
                onError: { [weak self] error in
                    guard let self = self else { return }

                    self.stopPpgFlushTimer()
                    self.flushPpgBuffer()
                    self.isPpgStreaming = false

                    self.bridge?.sendEvent(
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

                    self.bridge?.sendEvent(
                        withName: PolarEvent.PolarPpgComplete.rawValue,
                        body: ["message": "PPG stream complete"]
                    )

                    self.ppgDisposable = nil
                }
            )

        ppgDisposable?.disposed(by: disposeBag)
    }

    func disposePpgStream() {
        isPpgStreaming = false
        ppgDisposable?.dispose()
    }

    // MARK: - Buffer

    private func startPpgFlushTimer(bufferMs: TimeInterval) {
        stopPpgFlushTimer()

        let intervalSeconds = bufferMs / 1000.0

        DispatchQueue.main.async {
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
        DispatchQueue.main.async {
            self.ppgFlushTimer?.invalidate()
            self.ppgFlushTimer = nil
        }
    }

    private func flushPpgBuffer() {
        ppgBufferQueue.async {
            guard !self.ppgBuffer.isEmpty else { return }

            let events = self.ppgBuffer
            self.ppgBuffer.removeAll()

            DispatchQueue.main.async {
                for event in events {
                    self.bridge?.sendEvent(
                        withName: PolarEvent.PolarPpgData.rawValue,
                        body: event
                    )
                }
            }
        }
    }
}
