/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import de.sciss.jump3r.lowlevel.LameEncoder;
import de.sciss.jump3r.mp3.MPEGMode;

public class AudioConversion {

    public static final float WAVE_SAMPLE_RATE = 32000.0f;
    public static final float OGG_SAMPLE_RATE = 44100.0f;
    public static final float MP3_SAMPLE_RATE = 44100.0f;
    public static final int BITSIZE = 16;
    public static final int MP3_BITSIZE = 32;
    public static final int CHANNELS = 1;

    private AudioConversion() {
        throw new IllegalArgumentException("Utility class");
    }

    /** Make LameEncoder closeable. */
    private static class LameEncoderWrapper implements AutoCloseable {
        private final LameEncoder delegate;

        public LameEncoderWrapper(LameEncoder encoder) {
            delegate = encoder;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }

    public static byte[] oggToWave(byte[] oggData) throws IOException, UnsupportedAudioFileException {
        return anyToWave(oggData);
    }

    public static byte[] mp3ToWave(byte[] mp3Data) throws IOException, UnsupportedAudioFileException {
        return anyToWave(mp3Data);
    }

    public static byte[] anyToWave(byte[] data) throws IOException, UnsupportedAudioFileException {
        try (AudioInputStream inputAudio = AudioSystem.getAudioInputStream(new ByteArrayInputStream(data))) {

            float inputRate = inputAudio.getFormat().getSampleRate();
            int inputChannels = inputAudio.getFormat().getChannels();

            // First, convert to PCM
            AudioFormat pcmFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, inputRate, BITSIZE, //
                    inputChannels, inputChannels * 2, inputRate, false);

            // Then, convert sample rate to 32000Hz, and to mono channel (the only format
            // that is supported by the story teller device)
            AudioFormat pcm32000Format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, WAVE_SAMPLE_RATE, BITSIZE, //
                    CHANNELS, CHANNELS * 2, WAVE_SAMPLE_RATE, false);

            try (AudioInputStream pcm = AudioSystem.getAudioInputStream(pcmFormat, inputAudio);
                    AudioInputStream pcm32000 = AudioSystem.getAudioInputStream(pcm32000Format, pcm);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

                // Read the whole stream in a byte array because length must be known
                baos.writeBytes(pcm32000.readAllBytes());

                try (ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
                        AudioInputStream waveStream = new AudioInputStream(bais, pcm32000Format,
                                baos.toByteArray().length);
                        ByteArrayOutputStream output = new ByteArrayOutputStream();) {
                    AudioSystem.write(waveStream, AudioFileFormat.Type.WAVE, output);
                    return output.toByteArray();
                }
            }
        }
    }

    public static byte[] waveToOgg(byte[] waveData) throws IOException, VorbisEncodingException, UnsupportedAudioFileException {
        // Convert sample rate to 44100Hz (the only rate that is supported by the vorbis
        // encoding library)
        AudioFormat pcm44100Format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, OGG_SAMPLE_RATE, BITSIZE, //
                CHANNELS, CHANNELS * 2, OGG_SAMPLE_RATE, false);

        try (AudioInputStream inputAudio = AudioSystem.getAudioInputStream(new ByteArrayInputStream(waveData));
                AudioInputStream pcm44100 = AudioSystem.getAudioInputStream(pcm44100Format, inputAudio);) {
            return VorbisEncoder.encode(pcm44100);
        }
    }

    public static byte[] anyToMp3(byte[] data) throws IOException, UnsupportedAudioFileException {
        try (AudioInputStream inputAudio = AudioSystem.getAudioInputStream(new ByteArrayInputStream(data))) {

            float inputRate = inputAudio.getFormat().getSampleRate();
            int inputChannels = inputAudio.getFormat().getChannels();

            // First, convert to PCM
            AudioFormat pcmFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, inputRate, BITSIZE, //
                    inputChannels, inputChannels * 2, inputRate, false);

            // Then, convert to mono **and oversample** (apparently the input stream is
            // always empty unless the sample rate changes)
            AudioFormat pcmOverSampledFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, inputRate * 2, BITSIZE, //
                    CHANNELS, CHANNELS * 2, inputRate * 2, false);

            // Finally, convert sample rate to 44100Hz and sample bitsize to 32 bits
            AudioFormat pcm44100Format = new AudioFormat(AudioFormat.Encoding.PCM_FLOAT, MP3_SAMPLE_RATE, MP3_BITSIZE, //
                    CHANNELS, CHANNELS * 4, MP3_SAMPLE_RATE, false);

            try (AudioInputStream pcm = AudioSystem.getAudioInputStream(pcmFormat, inputAudio);
                    AudioInputStream pcmOverSampled = AudioSystem.getAudioInputStream(pcmOverSampledFormat, pcm);
                    AudioInputStream pcm44100 = AudioSystem.getAudioInputStream(pcm44100Format, pcmOverSampled);) {
                return encodeMP3(pcm44100);
            }
        }
    }

    /** Convert Wav to MP3 with LameEncoder. */
    private static byte[] encodeMP3(AudioInputStream pcm44100) throws IOException {
        LameEncoder encoder = new LameEncoder(pcm44100.getFormat(), LameEncoder.BITRATE_AUTO, MPEGMode.MONO.ordinal(),
                4, true);

        try (LameEncoderWrapper lameEncoderWrapper = new LameEncoderWrapper(encoder);
                ByteArrayOutputStream mp3 = new ByteArrayOutputStream()) {

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
            return mp3.toByteArray();
        }
    }

}
