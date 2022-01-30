package studio.core.v1.utils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.SecureRandom;

import org.xiph.libogg.ogg_packet;
import org.xiph.libogg.ogg_page;
import org.xiph.libogg.ogg_stream_state;
import org.xiph.libvorbis.vorbis_block;
import org.xiph.libvorbis.vorbis_comment;
import org.xiph.libvorbis.vorbis_dsp_state;
import org.xiph.libvorbis.vorbis_info;
import org.xiph.libvorbis.vorbisenc;

/**
 * This class is *heavily* inspired by the OggVorbis software codec source code, which is
 * governed by the following license:
 *
 *
 *
 * Copyright (c) 2002-2004 Xiph.org Foundation
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * - Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * - Neither the name of the Xiph.org Foundation nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE FOUNDATION
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */
public class VorbisEncoder {

    private static final int READ = 1024;

    // need to randomize seed
    private static final SecureRandom prng = new SecureRandom();

    private VorbisEncoder() {
        throw new IllegalArgumentException("Utility class");
    }

    public static byte[] encode(InputStream pcmInputStream) throws VorbisEncodingException {
        byte[] buffer = new byte[READ*4+44];
        boolean eos = false;

        // struct that stores all the static vorbis bitstream settings
        vorbis_info vi = new vorbis_info();

        vorbisenc encoder = new vorbisenc();

        if ( !encoder.vorbis_encode_init_vbr( vi, 2, 44100, .3f ) ) {
            throw new VorbisEncodingException("Failed to Initialize vorbisenc");
        }

        // struct that stores all the user comments
        vorbis_comment vc = new vorbis_comment();
        vc.vorbis_comment_add_tag( "ENCODER", "Java Vorbis Encoder" );

        // central working state for the packet->PCM decoder
        vorbis_dsp_state vd = new vorbis_dsp_state();

        if ( !vd.vorbis_analysis_init( vi ) ) {
            throw new VorbisEncodingException("Failed to Initialize vorbis_dsp_state");
        }

        // local working space for packet->PCM decode
        vorbis_block vb = new vorbis_block( vd );

        // take physical pages, weld into a logical stream of packets
        ogg_stream_state os = new ogg_stream_state( prng.nextInt(256) );

        // Writing header
        ogg_packet header = new ogg_packet();
        ogg_packet headerComm = new ogg_packet();
        ogg_packet headerCode = new ogg_packet();

        vd.vorbis_analysis_headerout( vc, header, headerComm, headerCode );

        os.ogg_stream_packetin( header); // automatically placed in its own page
        os.ogg_stream_packetin( headerComm );
        os.ogg_stream_packetin( headerCode );

        // one Ogg bitstream page.  Vorbis packets are inside
        ogg_page og = new ogg_page();
        // one raw packet of data for decode
        ogg_packet op = new ogg_packet();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            while( !eos ) {
                if ( !os.ogg_stream_flush( og ) )
                    break;
                baos.write( og.header, 0, og.header_len );
                baos.write( og.body, 0, og.body_len );
            }

            // Encoding
            while ( !eos ) {
                int i;
                int bytes = pcmInputStream.read(buffer, 0, READ*4 );

                int breakCount = 0;

                if ( bytes==0 ) {
                    // end of file.  this can be done implicitly in the mainline,
                    // but it's easier to see here in non-clever fashion.
                    // Tell the library we're at end of stream so that it can handle
                    // the last frame and mark end of stream in the output properly
                    vd.vorbis_analysis_wrote( 0 );

                } else {
                    // data to encode
                    // expose the buffer to submit data
                    float[][] floatBuffer = vd.vorbis_analysis_buffer( READ );
                    float fb;
                    // duplicate mono channel
                    for (i = 0; i < bytes / 2; i++) {
                        fb = ((buffer[i * 2 + 1] << 8) | (0x00ff & buffer[i * 2])) / 32768.f;
                        floatBuffer[0][vd.pcm_current + i] = fb;
                        floatBuffer[1][vd.pcm_current + i] = fb;
                    }

                    // tell the library how much we actually submitted
                    vd.vorbis_analysis_wrote( i );
                }

                // vorbis does some data preanalysis, then divvies up blocks for more involved
                // (potentially parallel) processing.  Get a single block for encoding now

                while ( vb.vorbis_analysis_blockout( vd ) ) {

                    // analysis, assume we want to use bitrate management
                    vb.vorbis_analysis( null );
                    vb.vorbis_bitrate_addblock();

                    while ( vd.vorbis_bitrate_flushpacket( op ) ) {

                        // weld the packet into the bitstream
                        os.ogg_stream_packetin( op );

                        // write out pages (if any)
                        while ( !eos ) {

                            if ( !os.ogg_stream_pageout( og ) ) {
                                breakCount++;
                                break;
                            }

                            baos.write( og.header, 0, og.header_len );
                            baos.write( og.body, 0, og.body_len );

                            // this could be set above, but for illustrative purposes, I do
                            // it here (to show that vorbis does know where the stream ends)
                            if ( og.ogg_page_eos() > 0 )
                                eos = true;
                        }
                    }
                }
                // TODO notify progress (one more block of 1024 samples has been processed)
            }

            // TODO notify progress (done)

            return baos.toByteArray();

        } catch (Exception e) {
            throw new VorbisEncodingException(e);
        }
    }
}