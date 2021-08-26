import { NativeEventEmitter, NativeModules } from 'react-native';
const { VideoTranscoder: NativeVideoTranscoder } = NativeModules;

const VideoTranscoderEmitter = new NativeEventEmitter(NativeVideoTranscoder);

export const Quality = {
  VeryLow: 'VERY_LOW',
  Low: 'LOW',
  Medium: 'MEDIUM',
  High: 'HIGH',
  VeryHigh: 'VERY_HIGH',
};

let _requestId = -1;
function getId() {
  return `${++_requestId}${Date.now()}`;
}

class VideoTranscoder {
  constructor() {
    this._requestsListeners = new Map();

    this._onStart = this._onStart.bind(this);
    this._onProgress = this._onProgress.bind(this);
    this._onSuccess = this._onSuccess.bind(this);
    this._onCancelled = this._onCancelled.bind(this);
    this._onFailure = this._onFailure.bind(this);

    this._initialized = false;
  }

  initListeners() {
    if (this._initialized) {
      return;
    }

    VideoTranscoderEmitter.addListener('onStart', this._onStart);
    VideoTranscoderEmitter.addListener('onProgress', this._onProgress);
    VideoTranscoderEmitter.addListener('onSuccess', this._onSuccess);
    VideoTranscoderEmitter.addListener('onCancelled', this._onCancelled);
    VideoTranscoderEmitter.addListener('onFailure', this._onFailure);

    this._initialized = true;
  }

  async _onStart({ requestId }) {
    const listeners = this._requestsListeners.get(requestId);
    if (listeners) {
      listeners.onStart({ requestId });
    }
  }

  async _onProgress({ requestId, progress }) {
    const listeners = this._requestsListeners.get(requestId);
    if (listeners) {
      listeners.onProgress({ requestId, progress });
    }
  }

  async _onSuccess({ requestId, outputPath }) {
    const listeners = this._requestsListeners.get(requestId);
    if (listeners) {
      listeners.onSuccess({ requestId, outputPath });

      this._requestsListeners.delete(requestId);
    }
  }

  async _onCancelled({ requestId }) {
    const listeners = this._requestsListeners.get(requestId);
    if (listeners) {
      listeners.onCancelled({ requestId });

      this._requestsListeners.delete(requestId);
    }
  }

  async _onFailure({ requestId, error }) {
    const listeners = this._requestsListeners.get(requestId);
    if (listeners) {
      listeners.onFailure({ requestId, error });

      this._requestsListeners.delete(requestId);
    }
  }

  addListeners({
    requestId,
    onStart = () => {},
    onProgress = () => {},
    onSuccess = () => {},
    onCancelled = () => {},
    onFailure = () => {},
  }) {
    if (!this._initialized) {
      this.initListeners();
    }

    this._requestsListeners.set(requestId, {
      onStart,
      onProgress,
      onSuccess,
      onCancelled,
      onFailure,
    });
  }

  cancel(requestId) {
    NativeVideoTranscoder.cancelCompress(requestId);
  }

  async compress(
    sourcePath,
    { quality = Quality.Low, targetPath, keepOriginalResolution = false },
    { onStart, onProgress, onSuccess, onCancelled, onFailure }
  ) {
    const requestId = getId();

    this.addListeners({
      requestId,
      onStart,
      onProgress,
      onSuccess,
      onCancelled,
      onFailure,
    });

    try {
      await NativeVideoTranscoder.compress(requestId, sourcePath, {
        quality,
        targetPath,
        keepOriginalResolution,
      });

      return requestId;
    } catch (error) {
      this._onFailure({ requestId, error });
    }
  }
}

export default new VideoTranscoder();
