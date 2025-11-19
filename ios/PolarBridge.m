// #import "PolarBridge.h"
//
// @implementation PolarBridge
// RCT_EXPORT_MODULE()

#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>

@interface RCT_EXTERN_MODULE(PolarBridge, RCTEventEmitter)

RCT_EXTERN_METHOD(multiply:(nonnull NSNumber *)a withB:(nonnull NSNumber *)b)

RCT_EXTERN_METHOD(connectToDevice:(NSString *)deviceId
                 resolver:(RCTPromiseResolveBlock)resolve
                 rejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(disconnectFromDevice:(NSString *)deviceId)
RCT_EXTERN_METHOD(scanDevices)
RCT_EXTERN_METHOD(fetchHrData:(NSString *)deviceId)
RCT_EXTERN_METHOD(disposeHrStream)
RCT_EXTERN_METHOD(fetchAccData:(NSString *)deviceId)
RCT_EXTERN_METHOD(disposeAccStream)

@end
