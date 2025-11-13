// #import "PolarBridge.h"
//
// @implementation PolarBridge
// RCT_EXPORT_MODULE()

#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>

@interface RCT_EXTERN_MODULE(PolarBridge, RCTEventEmitter)

RCT_EXTERN_METHOD(multiply:(nonnull NSNumber *)a withB:(nonnull NSNumber *)b)
// RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(multiply:(nonnull NSNumber *)a b:(nonnull NSNumber *)b)

// - (NSNumber *)multiply:(double)a b:(double)b {
//     NSNumber *result = @(a * b);
//
//     return result;
// }
//
// - (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
//     (const facebook::react::ObjCTurboModule::InitParams &)params
// {
//     return std::make_shared<facebook::react::NativePolarBridgeSpecJSI>(params);
// }

// Sample demonstrating exporting Objective-C code to be accessible in React Native
// RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(multiply:(double)a b:(double)b)
// {
//   return @(a * b);
// }

RCT_EXTERN_METHOD(connectToDevice:(NSString *)deviceId
                 resolver:(RCTPromiseResolveBlock)resolve
                 rejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(disconnectFromDevice:(NSString *)deviceId)
RCT_EXTERN_METHOD(scanDevices)

@end
