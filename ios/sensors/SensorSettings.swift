import Foundation
import PolarBleSdk

class SensorSettings {

    static func requestStreamSettings(
        api: PolarBleApi?,
        identifier: String,
        feature: PolarDeviceDataType
    ) async throws -> PolarSensorSetting {

        guard let api = api else {
            throw NSError(
                domain: "SensorSettings",
                code: -1,
                userInfo: [NSLocalizedDescriptionKey: "Polar API not initialized"]
            )
        }

        let availableSettings = try await api.requestStreamSettings(
            identifier,
            feature: feature
        )

        let allSettings: PolarSensorSetting

        do {
            allSettings = try await api.requestFullStreamSettings(
                identifier,
                feature: feature
            )
        } catch {
            NSLog("Full stream settings NOT available for \(feature). Reason: \(error.localizedDescription)")
            allSettings = try PolarSensorSetting([:])
        }

        if availableSettings.settings.isEmpty {
            throw NSError(
                domain: "SensorSettings",
                code: -2,
                userInfo: [NSLocalizedDescriptionKey: "Settings are not available"]
            )
        }

        NSLog("Feature \(feature) available settings: \(availableSettings.settings)")
        NSLog("Feature \(feature) all settings: \(allSettings.settings)")

        return availableSettings
    }
}
