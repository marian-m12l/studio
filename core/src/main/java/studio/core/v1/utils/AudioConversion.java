/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.utils;

import de.sciss.jump3r.lowlevel.LameEncoder;
import de.sciss.jump3r.mp3.MPEGMode;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class AudioConversion {

    public static final float WAVE_SAMPLE_RATE = 32000.0f;
    public static final float OGG_SAMPLE_RATE = 44100.0f;
    public static final float MP3_SAMPLE_RATE = 44100.0f;
    public static final int BITSIZE = 16;
    public static final int MP3_BITSIZE = 32;
    public static final int CHANNELS = 1;


    public static byte[] oggToWave(byte[] oggData) throws Exception {
        return anyToWave(oggData);
    }

    public static byte[] mp3ToWave(byte[] mp3Data) throws Exception {
        return anyToWave(mp3Data);
    }

    public static byte[] anyToWave(byte[] data) throws Exception {
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
    }

    public static byte[] waveToOgg(byte[] waveData) throws Exception {
        AudioInputStream inputAudio = AudioSystem.getAudioInputStream(new ByteArrayInputStream(waveData));

        // First, convert sample rate to 44100Hz (the only rate that is supported by the vorbis encoding library)
        AudioFormat pcm44100Format = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                OGG_SAMPLE_RATE,
                BITSIZE,
                CHANNELS,
                CHANNELS * 2,
                OGG_SAMPLE_RATE,
                false
        );
        AudioInputStream pcm44100 = AudioSystem.getAudioInputStream(pcm44100Format, inputAudio);

        return VorbisEncoder.encode(pcm44100);
    }

    public static byte[] anyToMp3(byte[] data) throws Exception {
            AudioInputStream inputAudio = AudioSystem.getAudioInputStream(new ByteArrayInputStream(data));

        // First, convert to PCM
        AudioFormat pcmFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                inputAudio.getFormat().getSampleRate(),
                BITSIZE,
                inputAudio.getFormat().getChannels(),
                inputAudio.getFormat().getChannels() * 2,
                inputAudio.getFormat().getSampleRate(),
                false
        );
        AudioInputStream pcm = AudioSystem.getAudioInputStream(pcmFormat, inputAudio);

        // Then, convert to mono **and oversample** (apparently the input stream in always empty unless the sample rate changes)
        AudioFormat pcmOverSampledFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                inputAudio.getFormat().getSampleRate() * 2,
                BITSIZE,
                CHANNELS,
                CHANNELS * 2,
                inputAudio.getFormat().getSampleRate() * 2,
                false
        );
        AudioInputStream pcmOverSampled = AudioSystem.getAudioInputStream(pcmOverSampledFormat, pcm);

        // Finally, convert sample rate to 44100Hz and sample bitsize to 32 bits
        AudioFormat pcm44100Format = new AudioFormat(
                AudioFormat.Encoding.PCM_FLOAT,
                MP3_SAMPLE_RATE,
                MP3_BITSIZE,
                CHANNELS,
                CHANNELS * 4,
                MP3_SAMPLE_RATE,
                false
        );
        AudioInputStream pcm44100 = AudioSystem.getAudioInputStream(pcm44100Format, pcmOverSampled);

        LameEncoder encoder = new LameEncoder(pcm44100.getFormat(), LameEncoder.BITRATE_AUTO, MPEGMode.MONO.ordinal(), 4, true);

        ByteArrayOutputStream mp3 = new ByteArrayOutputStream();
        byte[] inputBuffer = new byte[encoder.getPCMBufferSize()];
        byte[] outputBuffer = new byte[encoder.getPCMBufferSize()];

        int bytesRead;
        int bytesWritten;

        while (0 < (bytesRead = pcm44100.read(inputBuffer))) {
            bytesWritten = encoder.encodeBuffer(inputBuffer, 0, bytesRead, outputBuffer);
            mp3.write(outputBuffer, 0, bytesWritten);
        }
        bytesWritten = encoder.encodeFinish(outputBuffer);
        mp3.write(outputBuffer, 0, bytesWritten);

        encoder.close();
        return mp3.toByteArray();
    }
}
