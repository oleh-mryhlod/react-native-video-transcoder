#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>

@interface RCT_EXTERN_MODULE(VideoTranscoder, RCTEventEmitter)

RCT_EXTERN_METHOD(compress:(NSString *)requestId withSourceUrl:(NSString *)sourceUrl withOptions:(NSDictionary *)options
                 withResolver:(RCTPromiseResolveBlock)resolve
                 withRejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(cancelCompress:(NSString *)requestId)

@end
