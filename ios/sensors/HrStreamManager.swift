import Foundation
import PolarBleSdk
import React

class HrStreamManager {

    private weak var bridge: PolarBridge?
    private var api: PolarBleApi?

    private var hrTask: Task<Void, Never>?
    private var isHrStreaming = false

    private var hrBuffer: [[String: Any]] = []
    private let hrBufferQueue = DispatchQueue(label: "com.polarbridge.hrBufferQueue")
    private var hrFlushTimer: Timer?

    private let SENSOR_BUFFER_MS: TimeInterval = 10_000

    init(api: PolarBleApi?, bridge: PolarBridge) {
        self.api = api
        self.bridge = bridge
    }

    // MARK: - Public API

    func fetchHrData(_ deviceId: String, bufferMs: NSNumber?) {
        NSLog("HrStreamManager: Fetch HR Data called on: \(deviceId) bufferMs: \(String(describing: bufferMs))")

        let resolvedBufferMs: TimeInterval
        if let number = bufferMs, !(number is NSNull) {
            resolvedBufferMs = number.doubleValue >= 0 ? number.doubleValue : SENSOR_BUFFER_MS
        } else {
            resolvedBufferMs = SENSOR_BUFFER_MS
        }

        guard let api = api else {
            NSLog("HrStreamManager: Polar API not initialized")
            return
        }

        // Stop if already running
        if isHrStreaming {
            disposeHrStream()
            stopHrFlushTimer()
            flushHrBuffer()
            isHrStreaming = false

            bridge?.sendEvent(
                withName: PolarEvent.PolarHrComplete.rawValue,
                body: ["message": "HR Stream stopped"]
            )
            return
        }

        isHrStreaming = true
        startHrFlushTimer(bufferMs: resolvedBufferMs)

        hrTask = Task { @MainActor [weak self] in
            guard let self = self else { return }

            do {
                let stream = api.startHrStreaming(deviceId)

                for try await hrData in stream {
                    if Task.isCancelled { break }

                    self.hrBufferQueue.async {
                        for sample in hrData {
                            var event: [String: Any] = [:]
                            event["hr"] = sample.hr
                            event["rrsMs"] = sample.rrsMs
                            event["rrAvailable"] = sample.rrAvailable
                            event["contactStatus"] = sample.contactStatus
                            event["contactStatusSupported"] = sample.contactStatusSupported
                            event["timestamp"] = Date().timeIntervalSince1970 * 1000

                            self.hrBuffer.append(event)
                        }
                    }
                }

                self.stopHrFlushTimer()
                self.flushHrBuffer()
                self.isHrStreaming = false

                self.bridge?.sendEvent(
                    withName: PolarEvent.PolarHrComplete.rawValue,
                    body: ["message": "HR stream complete"]
                )

                self.hrTask = nil
            } catch {
                self.stopHrFlushTimer()
                self.flushHrBuffer()
                self.isHrStreaming = false

                self.bridge?.sendEvent(
                    withName: PolarEvent.PolarHrError.rawValue,
                    body: ["error": error.localizedDescription]
                )

                self.hrTask = nil
            }
        }
    }

    func disposeHrStream() {
        isHrStreaming = false
        hrTask?.cancel()
        hrTask = nil
    }

    // MARK: - Buffer

    private func startHrFlushTimer(bufferMs: TimeInterval) {
        stopHrFlushTimer()

        let intervalSeconds = bufferMs / 1000.0

        DispatchQueue.main.async {
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
        DispatchQueue.main.async {
            self.hrFlushTimer?.invalidate()
            self.hrFlushTimer = nil
        }
    }

    private func flushHrBuffer() {
        hrBufferQueue.async {
            guard !self.hrBuffer.isEmpty else { return }

            let events = self.hrBuffer
            self.hrBuffer.removeAll()

            DispatchQueue.main.async {
                for event in events {
                    self.bridge?.sendEvent(
                        withName: PolarEvent.PolarHrData.rawValue,
                        body: event
                    )
                }
            }
        }
    }
}
