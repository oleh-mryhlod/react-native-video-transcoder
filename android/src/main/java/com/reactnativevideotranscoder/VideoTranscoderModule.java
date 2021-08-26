package com.reactnativevideotranscoder;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.linkedin.android.litr.MediaTransformer;
import com.linkedin.android.litr.TransformationListener;
import com.linkedin.android.litr.analytics.TrackTransformationInfo;
import com.linkedin.android.litr.utils.CodecUtils;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ReactModule(name = VideoTranscoderModule.NAME)
public class VideoTranscoderModule extends ReactContextBaseJavaModule {
    public static final String NAME = "VideoTranscoder";

    private final ReactApplicationContext reactContext;
    private final Context appContext;

    private MediaTransformer mMediaTransformer = null;

    private static final String KEY_ROTATION = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
        ? MediaFormat.KEY_ROTATION
        : "rotation-degrees";

    public VideoTranscoderModule(ReactApplicationContext reactContext) {
        super(reactContext);

        this.reactContext = reactContext;
        this.appContext = reactContext.getApplicationContext();

        mMediaTransformer = new MediaTransformer(this.appContext);
    }

    @Override
    @NonNull
    public String getName() {
        return NAME;
    }

    @ReactMethod
    public void cancelCompress(String requestId) {
        mMediaTransformer.cancel(requestId);
    }

    @ReactMethod
    public void compress(String requestId, String sourcePath, ReadableMap options, final Promise promise) {
        try {
            final String quality = options.hasKey("quality") ? options.getString("quality") : "";
            final String targetPath = options.hasKey("targetPath") ? options.getString("targetPath") : "";
            final boolean keepOriginalResolution = options.hasKey("keepOriginalResolution") && options.getBoolean("keepOriginalResolution");

            Uri sourceUri = Uri.parse(sourcePath);
            final File outputDir = reactContext.getCacheDir();

            File targetFile = !targetPath.isEmpty()
                    ? new File(targetPath)
                    : new File(outputDir.getPath(),
                    String.format("transcoded_%s.mp4", UUID.randomUUID().toString()));

            final String outputPath = targetFile.getPath();

            MediaExtractor mediaExtractor = new MediaExtractor();
            mediaExtractor.setDataSource(this.appContext, sourceUri, null);

            MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
            mediaMetadataRetriever.setDataSource(this.appContext, sourceUri);

            MediaFormat sourceAudioFormat = getSourceAudioMediaFormat(mediaExtractor);
            MediaFormat sourceVideoFormat = getSourceVideoMediaFormat(mediaExtractor);

            MediaFormat targetAudioFormat = getTargetAudioMediaFormat(sourceAudioFormat);
            MediaFormat targetVideoFormat = getTargetVideoMediaFormat(sourceVideoFormat, mediaMetadataRetriever, quality, keepOriginalResolution);

            mMediaTransformer
                    .transform(
                            requestId,
                            sourceUri,
                            outputPath,
                            targetVideoFormat,
                            targetAudioFormat,
                            createListener(requestId, outputPath),
                            null
                    );

            promise.resolve(requestId);
        } catch (Throwable e) {
            Log.e(NAME, e.getMessage(), e);
            promise.reject("error", e.getMessage());
        }
    }

    private MediaFormat getSourceAudioMediaFormat(final MediaExtractor mediaExtractor) {
        MediaFormat audioFormat = null;

        for (int track = 0; track < mediaExtractor.getTrackCount(); track++) {
            MediaFormat mediaFormat = mediaExtractor.getTrackFormat(track);
            String mimeType = mediaFormat.getString(MediaFormat.KEY_MIME);

            if (mimeType.startsWith("audio")) {
                audioFormat = mediaFormat;
                break;
            }
        }

        return audioFormat;
    }

    private MediaFormat getSourceVideoMediaFormat(final MediaExtractor mediaExtractor) {
        MediaFormat videoFormat = null;

        for (int track = 0; track < mediaExtractor.getTrackCount(); track++) {
            MediaFormat mediaFormat = mediaExtractor.getTrackFormat(track);
            String mimeType = mediaFormat.getString(MediaFormat.KEY_MIME);

            if (mimeType.startsWith("video")) {
                videoFormat = mediaFormat;
                break;
            }
        }

        return videoFormat;
    }

    private MediaFormat getTargetAudioMediaFormat(final MediaFormat sourceFormat) {
        MediaFormat targetFormat = new MediaFormat();

        targetFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, getInt(sourceFormat, MediaFormat.KEY_CHANNEL_COUNT));
        targetFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, getInt(sourceFormat, MediaFormat.KEY_SAMPLE_RATE));
        targetFormat.setLong(MediaFormat.KEY_DURATION, getLong(sourceFormat, MediaFormat.KEY_DURATION));
        targetFormat.setInteger(MediaFormat.KEY_BIT_RATE, getInt(sourceFormat, MediaFormat.KEY_BIT_RATE));
        targetFormat.setString(MediaFormat.KEY_MIME, sourceFormat.getString(MediaFormat.KEY_MIME));

        return targetFormat;
    }

    private MediaFormat getTargetVideoMediaFormat(
            final MediaFormat sourceFormat,
            final MediaMetadataRetriever mediaMetadataRetriever,
            String quality, boolean keepOriginalResolution
    ) {
        MediaFormat targetFormat = new MediaFormat();

        targetFormat.setLong(MediaFormat.KEY_DURATION, getLong(sourceFormat, MediaFormat.KEY_DURATION));
        targetFormat.setInteger(MediaFormat.KEY_FRAME_RATE, getInt(sourceFormat, MediaFormat.KEY_FRAME_RATE));
        targetFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, getInt(sourceFormat, MediaFormat.KEY_I_FRAME_INTERVAL));
        targetFormat.setInteger(KEY_ROTATION, getInt(sourceFormat, KEY_ROTATION, 0));

        int bitrateData =
                Integer.parseInt(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE));
        int bitrate = getBitrate(bitrateData, quality);

        Map<String, Integer> size = generateWidthAndHeight(
                getInt(sourceFormat, MediaFormat.KEY_WIDTH),
                getInt(sourceFormat, MediaFormat.KEY_HEIGHT),
                keepOriginalResolution
        );

        targetFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        targetFormat.setInteger(MediaFormat.KEY_WIDTH, size.get("width"));
        targetFormat.setInteger(MediaFormat.KEY_HEIGHT, size.get("height"));

        targetFormat.setString(MediaFormat.KEY_MIME, CodecUtils.MIME_TYPE_VIDEO_AVC);

        return targetFormat;
    }


    private int getBitrate(int bitrate, String quality) {
        int resultBitrate;

        switch (quality) {
            case "VERY_LOW":
                resultBitrate = bitrate > 0 ? (int) (bitrate * 0.08) : 700000;
                break;

            case "MEDIUM":
                resultBitrate = bitrate > 0 ? (int) (bitrate * 0.2) : 2000000;
                break;

            case "HIGH":
                resultBitrate = bitrate > 0 ? (int) (bitrate * 0.3) : 3000000;
                break;

            case "VERY_HIGH":
                resultBitrate = bitrate > 0 ? (int) (bitrate * 0.5) : 5000000;
                break;

            case "LOW":
            default:
                resultBitrate = bitrate > 0 ? (int) (bitrate * 0.1) : 1000000;
                break;

        }
        Log.i(NAME, String.format("bitrate - original: %d, result: %d", bitrate, resultBitrate));

        return resultBitrate;
    }

    private Map<String, Integer> generateWidthAndHeight(int width, int height, boolean keepOriginalResolution) {
        Map<String, Integer> size = new HashMap<>();

        int newWidth;
        int newHeight;

        if (keepOriginalResolution) {
            newWidth = width;
            newHeight = height;
        } else if (width >= 1920 || height >= 1920) {
            newWidth = generateWidthHeightValue(width, 0.5);
            newHeight = generateWidthHeightValue(height, 0.5);
        } else if (width >= 1280 || height >= 1280) {
            newWidth = generateWidthHeightValue(width, 0.75);
            newHeight = generateWidthHeightValue(height, 0.75);
        } else if (width >= 960 || height >= 960) {
            newWidth = generateWidthHeightValue(width, 0.95);
            newHeight = generateWidthHeightValue(height, 0.95);
        } else {
            newWidth = generateWidthHeightValue(width, 0.9);
            newHeight = generateWidthHeightValue(height, 0.9);
        }

        size.put("width", newWidth);
        size.put("height", newHeight);

        return size;
    }

    private int roundEven(int value) {
        return value + 1 & ~1;
    }

    private int generateWidthHeightValue(double value, double factor) {
        return this.roundEven((int) (Math.round(value * factor / (double) 16) * 16));
    }

    private void emitEvent(String eventName, @Nullable WritableMap params) {
        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }

    private void sendOnProgress(String requestId, float progress) {
        WritableMap params = Arguments.createMap();
        params.putString("requestId", requestId);
        params.putDouble("progress", progress);

        emitEvent("onProgress", params);
    }

    private void sendOnStart(String requestId) {
        WritableMap params = Arguments.createMap();
        params.putString("requestId", requestId);

        emitEvent("onStart", params);
    }

    private void sendOnSuccess(String requestId, String outputPath) {
        WritableMap params = Arguments.createMap();
        params.putString("requestId", requestId);
        params.putString("outputPath", outputPath);

        emitEvent("onSuccess", params);
    }

    private void sendOnCancelled(String requestId) {
        WritableMap params = Arguments.createMap();
        params.putString("requestId", requestId);

        emitEvent("onCancelled", params);
    }

    private void sendOnFailure(String requestId, @Nullable Throwable cause) {
        WritableMap params = Arguments.createMap();
        params.putString("requestId", requestId);
        params.putString("error", cause != null ? cause.getMessage() : null);

        emitEvent("onFailure", params);
    }

    private TransformationListener createListener(@NonNull final String requestId, final String outputPath) {
        return new TransformationListener() {
            @Override
            public void onStarted(@NonNull String id) {
                if (TextUtils.equals(requestId, id)) {
                    sendOnStart(requestId);
                }
            }

            @Override
            public void onProgress(@NonNull String id, float progress) {
                if (TextUtils.equals(requestId, id)) {
                    sendOnProgress(requestId, progress * 100);
                }
            }

            @Override
            public void onCompleted(@NonNull String id, @Nullable List<TrackTransformationInfo> trackTransformationInfos) {
                if (TextUtils.equals(requestId, id)) {
                    sendOnSuccess(requestId, outputPath);
                }
            }

            @Override
            public void onCancelled(@NonNull String id, @Nullable List<TrackTransformationInfo> trackTransformationInfos) {
                if (TextUtils.equals(requestId, id)) {
                    sendOnCancelled(requestId);
                }
            }

            @Override
            public void onError(@NonNull String id, @Nullable Throwable cause, @Nullable List<TrackTransformationInfo> trackTransformationInfos) {
                if (TextUtils.equals(requestId, id)) {
                    sendOnFailure(requestId, cause);
                }
            }
        };
    }

    private int getInt(@NonNull MediaFormat mediaFormat, @NonNull String key, int defaultValue) {
        if (mediaFormat.containsKey(key)) {
            return mediaFormat.getInteger(key);
        }
        return defaultValue;
    }

    private int getInt(@NonNull MediaFormat mediaFormat, @NonNull String key) {
        return getInt(mediaFormat, key, -1);
    }

    private long getLong(@NonNull MediaFormat mediaFormat, @NonNull String key) {
        if (mediaFormat.containsKey(key)) {
            return mediaFormat.getLong(key);
        }
        return -1;
    }
}
