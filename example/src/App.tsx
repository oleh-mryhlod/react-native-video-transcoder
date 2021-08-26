/**
 * Sample React Native App
 * https://github.com/facebook/react-native
 *
 * @format
 * @flow strict-local
 */

import type { Node } from 'react';
import React, { useState } from 'react';
import {
  Button,
  SafeAreaView,
  ScrollView,
  StatusBar,
  View,
} from 'react-native';
import 'react-native-get-random-values';
import { Video } from 'expo-av';
import { launchCamera, launchImageLibrary } from 'react-native-image-picker';
import { Colors, Header } from 'react-native/Libraries/NewAppScreen';
import VIdeoTranscoder, { Quality } from 'react-native-video-transcoder';

const App: () => Node = () => {
  const backgroundStyle = {
    backgroundColor: Colors.lighter,
  };

  const [videoUri, setVideoUri] = useState();
  const [isCompressing, setIsCompressing] = useState(false);

  async function doCompression() {
    console.log('try to compress ', videoUri);

    const now = Date.now();

    setIsCompressing(true);

    try {
      const requestId = await VIdeoTranscoder.compress(
        videoUri,
        {
          quality: Quality.Medium,
        },
        {
          onStart: (data) => {
            console.log('started', data);
          },
          onProgress: (data) => {
            console.log('progress', data);
          },
          onSuccess: (data) => {
            console.log('complete', data);
            console.log(`It took ${(Date.now() - now) / 1000}`);
            setIsCompressing(false);
            setVideoUri(data.outputPath);
          },
          onCancelled: (data) => {
            console.log('cancel', data);
            setIsCompressing(false);
          },
          onFailure: (data) => {
            console.log('error', data);
            setIsCompressing(false);
          },
        }
      );
      console.log('compression started. requestId', requestId);
    } catch (error) {
      console.error('error starting compression', error);
    }
  }

  function onVideoPick(res) {
    if (res.didCancel || res.errorCode) {
      return;
    }

    const video = res.assets[0];

    console.log('video picked', video.uri);

    setVideoUri(video.uri);
  }

  async function chooseVideo() {
    launchImageLibrary(
      {
        mediaType: 'video',
      },
      onVideoPick
    );
  }

  async function recordVideo() {
    launchCamera(
      {
        mediaType: 'video',
      },
      onVideoPick
    );
  }

  return (
    <SafeAreaView style={backgroundStyle}>
      <StatusBar barStyle={'dark-content'} />
      <ScrollView
        contentInsetAdjustmentBehavior="automatic"
        style={backgroundStyle}
      >
        {videoUri ? (
          <Video
            isLooping
            shouldPlay
            useNativeControls
            source={{ uri: videoUri }}
            resizeMode={'contain'}
            style={{ width: '100%', height: 600 }}
          />
        ) : (
          <Header />
        )}
        <View
          style={{
            backgroundColor: Colors.white,
          }}
        />

        <Button title="Choose video from gallery" onPress={chooseVideo} />
        <Button title="Record video" onPress={recordVideo} />
        <Button
          title={isCompressing ? 'Compressing' : 'compress'}
          onPress={doCompression}
        />
      </ScrollView>
    </SafeAreaView>
  );
};

export default App;
