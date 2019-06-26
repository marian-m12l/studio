/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.utils;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class AudioConversion {

    private static final float WAVE_SAMPLE_RATE = 32000.0f;
    private static final float OGG_SAMPLE_RATE = 44100.0f;
    private static final int BITSIZE = 16;
    private static final int CHANNELS = 1;


    public static byte[] oggToWave(byte[] oggData) throws IOException {
        return anyToWave(oggData);
    }

    public static byte[] mp3ToWave(byte[] mp3Data) throws IOException {
        return anyToWave(mp3Data);
    }

    public static byte[] anyToWave(byte[] data) throws IOException {
        try {
            AudioInputStream inputAudio = AudioSystem.getAudioInputStream(new ByteArrayInputStream(data));

            // First, convert to PCM
            AudioFormat pcmFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    inputAudio.getFormat().getSampleRate(),
                    BITSIZE,
                    inputAudio.getFormat().getChannels(),
                    inputAudio.getFormat().getChannels()*2,
                    inputAudio.getFormat().getSampleRate(),
                    false
            );
            AudioInputStream pcm = AudioSystem.getAudioInputStream(pcmFormat, inputAudio);

            // Then, convert sample rate to 32000Hz, and to mono channel (the only format that is supported by the story teller device)
            AudioFormat pcm32000Format = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    WAVE_SAMPLE_RATE,
                    BITSIZE,
                    CHANNELS,
                    CHANNELS *2,
                    WAVE_SAMPLE_RATE,
                    false
            );
            AudioInputStream pcm32000 = AudioSystem.getAudioInputStream(pcm32000Format, pcm);

            // Read the whole stream in a byte array because length must be known
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.writeBytes(pcm32000.readAllBytes());
            AudioInputStream waveStream = new AudioInputStream(new ByteArrayInputStream(baos.toByteArray()), pcm32000Format, baos.toByteArray().length);

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            AudioSystem.write(waveStream, AudioFileFormat.Type.WAVE, output);
            return output.toByteArray();
        } catch (UnsupportedAudioFileException e) {
            e.printStackTrace();
            throw new IOException("Unsupported audio format", e);
        }
    }

    public static byte[] waveToOgg(byte[] waveData) throws IOException {
        try {
            AudioInputStream inputAudio = AudioSystem.getAudioInputStream(new ByteArrayInputStream(waveData));

            // First, convert sample rate to 44100Hz (the only rate that is supported by the vorbis encoding library)
            AudioFormat pcm44100Format = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    OGG_SAMPLE_RATE,
                    BITSIZE,
                    CHANNELS,
                    CHANNELS*2,
                    OGG_SAMPLE_RATE,
                    false
            );
            AudioInputStream pcm44100 = AudioSystem.getAudioInputStream(pcm44100Format, inputAudio);

            return VorbisEncoder.encode(pcm44100);
        } catch (UnsupportedAudioFileException e) {
            e.printStackTrace();
            throw new IOException("Unsupported audio format", e);
        } catch (VorbisEncodingException e) {
            e.printStackTrace();
            throw new IOException("Audio compression to ogg format failed", e);
        }
    }
}
