package studio.core.v1.utils;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.axet.libogg.Jogg_packet;
import com.github.axet.libogg.Jogg_page;
import com.github.axet.libogg.Jogg_stream_state;
import com.github.axet.libvorbis.Jvorbis_block;
import com.github.axet.libvorbis.Jvorbis_comment;
import com.github.axet.libvorbis.Jvorbis_dsp_state;
import com.github.axet.libvorbis.Jvorbis_info;
import com.github.axet.libvorbis.Jvorbis_pcm;

/**
 * This class is *heavily* inspired by the OggVorbis software codec source code,
 * which is governed by the following license:
 *
 *
 *
 * Copyright (c) 2002-2004 Xiph.org Foundation
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * - Neither the name of the Xiph.org Foundation nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS ``AS IS''
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE FOUNDATION OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * @see https://gitlab.com/axet/jvorbis/
 * @see https://gitlab.com/axet/jvorbis/-/blob/master/src/test/java/com/github/axet/jvorbis/examples/Jencoder_example.java
 */
public class VorbisEncoder {

    private static final Logger LOGGER = LogManager.getLogger(VorbisEncoder.class);

    private static final int CHANNELS = 2;
    private static final int RATE = 44100;
    private static final float QUALITY = .3f;

    private static final int READ_BLOCK_SIZE = 4096;
    // need to randomize seed
    private static final SecureRandom prng = new SecureRandom();

    private VorbisEncoder() {
        throw new IllegalArgumentException("Utility class");
    }

    public static byte[] encode(InputStream is) throws VorbisEncodingException {
        LOGGER.debug("Start encoding with {} channels, {}Hz, quality {}", CHANNELS, RATE, QUALITY);

        // struct that stores all the static vorbis bitstream settings
        Jvorbis_info vi = new Jvorbis_info();
        vi.vorbis_info_init();
        if (vi.vorbis_encode_init_vbr(CHANNELS, RATE, QUALITY) != 0) {
            throw new VorbisEncodingException("Failed to Initialize vorbisenc");
        }

        // struct that stores all the user comments
        Jvorbis_comment vc = new Jvorbis_comment();
        vc.vorbis_comment_init();
        vc.vorbis_comment_add_tag("ENCODER", "Java Vorbis Encoder");

        // central working state for the packet->PCM decoder
        Jvorbis_dsp_state vd = new Jvorbis_dsp_state();
        if (vd.vorbis_analysis_init(vi)) {
            throw new VorbisEncodingException("Failed to Initialize vorbis_dsp_state");
        }

        // local working space for packet->PCM decode
        Jvorbis_block vb = new Jvorbis_block();
        vd.vorbis_block_init(vb);

        // take physical pages, weld into a logical stream of packets
        Jogg_stream_state os = new Jogg_stream_state();
        os.ogg_stream_init(prng.nextInt(256));

        LOGGER.trace("Writing header.");
        Jogg_packet header = new Jogg_packet();
        Jogg_packet headerComm = new Jogg_packet();
        Jogg_packet headerCode = new Jogg_packet();

        vd.vorbis_analysis_headerout(vc, header, headerComm, headerCode);
        os.ogg_stream_packetin(header); // automatically placed in its own page
        os.ogg_stream_packetin(headerComm);
        os.ogg_stream_packetin(headerCode);

        // one Ogg bitstream page. Vorbis packets are inside
        Jogg_page og = new Jogg_page();

        try (DataInputStream dis = new DataInputStream(is); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            // skip first 44 bytes (simplest WAV header is 44 bytes) and
            // assume that the data is 44.1khz, stereo, 16 bit little endian pcm samples.
            dis.skipBytes(44);

            // headers
            while (os.ogg_stream_flush(og) != 0) {
                writeOggPage(baos, og, 0);
            }

            LOGGER.trace("Writing header done.\nEncoding");
            int page = 0;
            byte[] readbuffer = new byte[READ_BLOCK_SIZE];
            while (!og.ogg_page_eos()) {
                // stereo hardwired here
                int readBytes = dis.readNBytes(readbuffer, 0, READ_BLOCK_SIZE);

                if (readBytes <= 0) {
                    // end of file. this can be done implicitly in the mainline,
                    // but it's easier to see here in non-clever fashion.
                    // Tell the library we're at end of stream so that it can handle
                    // the last frame and mark end of stream in the output properly
                    vd.vorbis_analysis_wrote(0);
                } else {
                    // data to encode
                    // expose the buffer to submit data
                    Jvorbis_pcm data = vd.vorbis_analysis_buffer(READ_BLOCK_SIZE / 4);
                    int i;
                    // duplicate mono channel
                    for (i = 0; i < readBytes / 2; i++) {
                        float fb = ((readbuffer[i * 2 + 1] << 8) | (0x00ff & readbuffer[i * 2])) / 32768f;
                        data.pcm[0][data.pcmret + i] = fb;
                        data.pcm[1][data.pcmret + i] = fb;
                    }

                    // tell the library how much we actually submitted
                    vd.vorbis_analysis_wrote(i);
                }

                // vorbis does some data preanalysis, then divvies up blocks for more involved
                // (potentially parallel) processing. Get a single block for encoding now
                while (vd.vorbis_analysis_blockout(vb)) {
                    // analysis, assume we want to use bitrate management
                    vb.vorbis_analysis(null);
                    vb.vorbis_bitrate_addblock();

                    // one raw packet of data for decode
                    Jogg_packet op = new Jogg_packet();
                    while (vd.vorbis_bitrate_flushpacket(op)) {
                        // weld the packet into the bitstream
                        os.ogg_stream_packetin(op);
                        // write out pages (if any)
                        while (os.ogg_stream_pageout(og) != 0) {
                            writeOggPage(baos, og, ++page);
                        }
                    }
                }
                // notify progress (one more block of samples has been processed)
                LOGGER.trace("{} block read", readBytes);
            }

            LOGGER.debug("Encoding done (pageCount={}, size={})", page, baos.size());
            return baos.toByteArray();
        } catch (IOException e) {
            throw new VorbisEncodingException(e);
        } finally {
            os.ogg_stream_clear();
            vb.vorbis_block_clear();
            vd.vorbis_dsp_clear();
            vc.vorbis_comment_clear();
            vi.vorbis_info_clear();
        }
    }

    private static void writeOggPage(OutputStream os, Jogg_page og, int page) throws IOException {
        os.write(og.header_base, og.header, og.header_len);
        os.write(og.body_base, og.body, og.body_len);
        LOGGER.debug("Writing page {}: head ({}) and body ({})", page, og.header_len, og.body_len);
    }
}
