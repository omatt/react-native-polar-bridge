import Foundation
import PolarBleSdk
import RxSwift

class SensorSettings {

    static func requestStreamSettings(
        api: PolarBleApi?,
        identifier: String,
        feature: PolarDeviceDataType
    ) -> Observable<PolarSensorSetting> {

        guard let api = api else {
            return Observable.error(NSError(
                domain: "SensorSettings",
                code: -1,
                userInfo: [NSLocalizedDescriptionKey: "Polar API not initialized"]
            ))
        }

        let availableSettings = api.requestStreamSettings(identifier, feature: feature)

        let allSettings = api.requestFullStreamSettings(identifier, feature: feature)
            .catch { error in
                NSLog("Full stream settings NOT available for \(feature). Reason: \(error.localizedDescription)")
                return Single.just(try PolarSensorSetting([:]))
            }

        return Single.zip(availableSettings, allSettings)
            .flatMap { available, all -> Single<PolarSensorSetting> in

                if available.settings.isEmpty {
                    return Single.error(NSError(
                        domain: "SensorSettings",
                        code: -2,
                        userInfo: [NSLocalizedDescriptionKey: "Settings are not available"]
                    ))
                }

                NSLog("Feature \(feature) available settings: \(available.settings)")
                NSLog("Feature \(feature) all settings: \(all.settings)")

                return Single.just(available)
            }
            .asObservable()
    }
}
