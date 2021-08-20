import { NativeModules } from 'react-native';

type VideoTranscoderType = {
  multiply(a: number, b: number): Promise<number>;
};

const { VideoTranscoder } = NativeModules;

export default VideoTranscoder as VideoTranscoderType;
