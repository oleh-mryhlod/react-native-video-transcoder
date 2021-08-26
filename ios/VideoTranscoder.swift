//
//  VideoTranscoder.swift
//

import Foundation
import LightCompressor

@objc(VideoTranscoder)
class VideoTranscoder: RCTEventEmitter {
  override func supportedEvents() -> [String]! {
    return ["onStart", "onProgress", "onSuccess", "onCancelled", "onFailure"]
  }
  
  override static func requiresMainQueueSetup() -> Bool {
      return true
  }
  
  private var activeCompressions: [String: Compression] = [:]
  
  @objc(compress:withSourceUrl:withOptions:withResolver:withRejecter:)
  func compress(
    requestId: String,
    sourcePath: String,
    options: NSDictionary,
    resolve: RCTPromiseResolveBlock,
    reject: RCTPromiseRejectBlock
  ) {
    do {
      let source = URL(string: sourcePath)!
      let keepOriginalResolution = options["keepOriginalResolution"] as? Bool ?? false
      
      let destination: URL!
      if let targetPath = options["targetPath"] as? String {
        destination = URL(string: targetPath)
      } else {
        destination = URL(fileURLWithPath: NSTemporaryDirectory()).appendingPathComponent("transcoded_\(UUID().uuidString).mp4")
      }
      
      let quality: VideoQuality!
      switch options["quality"] as? String {
      case "VERY_LOW":
        quality = VideoQuality.very_low
      case "LOW":
        quality = VideoQuality.low
      case "MEDIUM":
        quality = VideoQuality.medium
      case "HIGH":
        quality = VideoQuality.high
      case "VERY_HIGH":
        quality = VideoQuality.very_high
      default:
        quality = VideoQuality.very_low
      }
      
      let videoCompressor = LightCompressor()

      let compression = try videoCompressor.compressVideo(
        source: source,
        destination: destination,
        quality: quality,
        keepOriginalResolution: keepOriginalResolution,
        progressQueue: .main,
        progressHandler: { [weak self] progress in
          guard let `self` = self else { return }
          
          // progress
          self.sendEvent(withName: "onProgress", body: ["requestId": requestId, "progress": progress.fractionCompleted * 100])
        },
        completion: {[weak self] result in
          guard let `self` = self else { return }
          
          switch result {
          case .onSuccess(let path):
            // success
            self.sendEvent(withName: "onSuccess", body: ["requestId": requestId, "outputPath": path.absoluteString])
            self.activeCompressions[requestId] = nil
            
          case .onStart:
            // when compression starts
            self.sendEvent(withName: "onStart", body: ["requestId": requestId])
            
          case .onFailure(let error):
            // failure error
            self.sendEvent(withName: "onFailure", body: ["requestId": requestId, "error": error.title])
            self.activeCompressions[requestId] = nil
            
          case .onCancelled:
            // if cancelled
            self.sendEvent(withName: "onCancelled", body: ["requestId": requestId])
            self.activeCompressions[requestId] = nil
          }
        }
      )
      
      activeCompressions[requestId] = compression
      
      resolve(requestId)
    } catch {
      print("Compression not started, error -  \(error)")
      reject("Compression not started, error", error.localizedDescription, error)
    }
  }
  
  @objc(cancelCompress:)
  func cancelCompress(requestId: String) {
    activeCompressions[requestId]?.cancel = true;
  }
}
