package me.shengbin.corerecorder;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class RtmpFlv {
    private String url;
    //private HttpURLConnection conn;
    //private BufferedOutputStream bos;

    private Thread worker;
    private Looper looper;
    private Handler handler;
    private boolean started = false;

    private SrsFlv flv;
    private boolean sequenceHeaderOk;
    private SrsFlvFrame videoSequenceHeader;
    private SrsFlvFrame audioSequenceHeader;

    // use cache queue to ensure audio and video monotonically increase.
    private ArrayList<SrsFlvFrame> cache;
    private int nb_videos;
    private int nb_audios;

    private static final int VIDEO_TRACK = 100;
    private static final int AUDIO_TRACK = 101;
    private static final String TAG = "SrsMuxer";

    static{
        System.loadLibrary("rtmp");
        System.loadLibrary("transport");
    }

    public native boolean rtmpInit(String url);
    public native void rtmpClose();
    public native void rtmpSend(byte[] array, int type, int timestamp);

    /**
     * constructor.
     * @param path the http flv url to post to.
     * @param format the mux format, @see SrsHttpFlv.OutputFormat
     */
    public RtmpFlv(String path, int format) {
        nb_videos = 0;
        nb_audios = 0;
        sequenceHeaderOk = false;

        url = path;
        flv = new SrsFlv();
        cache = new ArrayList<SrsFlvFrame>();
    }

    /**
     * print the size of bytes in bb
     * @param bb the bytes to print.
     * @param size the total size of bytes to print.
     */
    public static void srs_print_bytes(String tag, ByteBuffer bb, int size) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        int bytes_in_line = 16;
        int max = bb.remaining();
        for (i = 0; i < size && i < max; i++) {
            sb.append(String.format("0x%s ", Integer.toHexString(bb.get(i) & 0xFF)));
            if (((i + 1) % bytes_in_line) == 0) {
                Log.i(tag, String.format("%03d-%03d: %s", i / bytes_in_line * bytes_in_line, i, sb.toString()));
                sb = new StringBuilder();
            }
        }
        if (sb.length() > 0) {
            Log.i(tag, String.format("%03d-%03d: %s", size / bytes_in_line * bytes_in_line, i - 1, sb.toString()));
        }
    }
    public static void srs_print_bytes(String tag, byte[] bb, int size) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        int bytes_in_line = 16;
        int max = bb.length;
        for (i = 0; i < size && i < max; i++) {
            sb.append(String.format("0x%s ", Integer.toHexString(bb[i] & 0xFF)));
            if (((i + 1) % bytes_in_line) == 0) {
                Log.i(tag, String.format("%03d-%03d: %s", i / bytes_in_line * bytes_in_line, i, sb.toString()));
                sb = new StringBuilder();
            }
        }
        if (sb.length() > 0) {
            Log.i(tag, String.format("%03d-%03d: %s", size / bytes_in_line * bytes_in_line, i - 1, sb.toString()));
        }
    }

    /**
     * start to the remote SRS for remux.
     */
    public void start() throws IOException {
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    cycle();
                } catch (InterruptedException ie) {
                } catch (Exception e) {
                    Log.i(TAG, "worker: thread exception.");
                    e.printStackTrace();
                }
            }
        });
        worker.start();
    }

    /**
     * Make sure you call this when you're done to free up any resources
     * instead of relying on the garbage collector to do this for you at
     * some point in the future.
     */
    public void release() {
        stop();
    }

    /**
     * stop the muxer, disconnect HTTP connection from SRS.
     */
    public void stop() {
        clearCache();

        if (worker == null) {
            return;
        }

        if (looper != null) {
            looper.quit();
        }

        if (worker != null) {
            worker.interrupt();
            try {
                worker.join();
            } catch (InterruptedException e) {
                Log.i(TAG, "worker: join thread failed.");
                e.printStackTrace();
                worker.stop();
            }
            worker = null;
        }

        rtmpClose();
        started = false;

        Log.i(TAG, String.format("worker: muxer closed, url=%s", url));
    }

    /**
     * send the annexb frame to SRS over HTTP FLV.
     * @param trackIndex The track index for this sample.
     * @param byteBuf The encoded sample.
     * @param bufferInfo The buffer information related to this sample.
     */
    public void writeSampleData(int type, ByteBuffer byteBuf, MediaCodec.BufferInfo bufferInfo) throws Exception {
        //Log.i(TAG, String.format("dumps the %s stream %dB, pts=%d", (trackIndex == VIDEO_TRACK) ? "Vdieo" : "Audio", bufferInfo.size, bufferInfo.presentationTimeUs / 1000));
        //SrsHttpFlv.srs_print_bytes(TAG, byteBuf, bufferInfo.size);

        if (bufferInfo.offset > 0) {
            Log.w(TAG, String.format("encoded frame %dB, offset=%d pts=%dms",
                    bufferInfo.size, bufferInfo.offset, bufferInfo.presentationTimeUs / 1000
            ));
        }

        if (type == 1) {
            flv.writeVideoSample(byteBuf, bufferInfo);
        } else if (type == 0){
            flv.writeAudioSample(byteBuf, bufferInfo);
        }
    }

    private void disconnect() {
        clearCache();
        rtmpClose();
        started = false;
        Log.i(TAG, "worker: disconnect SRS ok.");
    }

    private void clearCache() {
        nb_videos = 0;
        nb_audios = 0;
        cache.clear();
        sequenceHeaderOk = false;
    }

    private void reconnect() throws Exception {
        // when bos not null, already connected.
        if (started)
            return;
        disconnect();
        boolean suc = rtmpInit(url);
        if(!suc) {
            Log.v("rtmp","init failed");
        } else {
            Log.v("rtmp","init");
        }
        started = true;
        /*
        URL u = new URL(url);
        conn = (HttpURLConnection)u.openConnection();
        Log.i(TAG, String.format("worker: connect to SRS by url=%s", url));
        conn.setDoOutput(true);
        conn.setChunkedStreamingMode(0);
        conn.setRequestProperty("Content-Type", "application/octet-stream");
        bos = new BufferedOutputStream(conn.getOutputStream());
        */

        Log.i(TAG, String.format("worker: muxer opened, url=%s", url));

        // write 13B header
        // 9bytes header and 4bytes first previous-tag-size
        byte[] flv_header = new byte[]{
                'F', 'L', 'V', // Signatures "FLV"
                (byte) 0x01, // File version (for example, 0x01 for FLV version 1)
                (byte) 0x00, // 4, audio; 1, video; 5 audio+video.
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x09, // DataOffset UI32 The length of this header in bytes
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00
        };
        //rtmpSend(flv_header,18,0);
        Log.i(TAG, String.format("worker: flv header ok."));
        clearCache();
    }

    private void cycle() throws Exception {
        // create the handler.
        Looper.prepare();
        looper = Looper.myLooper();
        handler = new Handler(looper) {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what != SrsMessageType.FLV) {
                    Log.w(TAG, String.format("worker: drop unkown message, what=%d", msg.what));
                    return;
                }
                SrsFlvFrame frame = (SrsFlvFrame)msg.obj;
                try {
                    // only reconnect when got keyframe.
                    if (frame.is_keyframe()) {
                        reconnect();
                    }
                } catch (Exception e) {
                    Log.e(TAG, String.format("worker: reconnect failed. e=%s", e.getMessage()));
                    disconnect();
                }
                try {
                    // when sequence header required,
                    // adjust the dts by the current frame and sent it.
                    if (!sequenceHeaderOk) {
                        if (videoSequenceHeader != null) {
                            videoSequenceHeader.dts = frame.dts;
                        }
                        if (audioSequenceHeader != null) {
                            audioSequenceHeader.dts = frame.dts;
                        }
                        sendFlvTag(audioSequenceHeader);
                        sendFlvTag(videoSequenceHeader);
                        sequenceHeaderOk = true;
                    }

                    // try to send, igore when not connected.
                    if (sequenceHeaderOk) {
                        sendFlvTag(frame);
                    }

                    // cache the sequence header.
                    if (frame.type == SrsCodecFlvTag.Video && frame.avc_aac_type == SrsCodecVideoAVCType.SequenceHeader) {
                        videoSequenceHeader = frame;
                    } else if (frame.type == SrsCodecFlvTag.Audio && frame.avc_aac_type == 0) {
                        audioSequenceHeader = frame;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e(TAG, String.format("worker: send flv tag failed, e=%s", e.getMessage()));
                    disconnect();
                }
            }
        };
        flv.setHandler(handler);

        Looper.loop();
    }

    private void sendFlvTag(SrsFlvFrame frame) throws IOException {
        if (frame == null) {
            return;
        }

        if (frame.tag.size <= 0) {
            return;
        }

        if (frame.is_video()) {
            nb_videos++;
        } else if (frame.is_audio()) {
            nb_audios++;
        }
        cache.add(frame);

        // always keep one audio and one videos in cache.
        if (nb_videos > 1 && nb_audios > 1) {
            sendCachedFrames();
        }
    }

    private void sendCachedFrames() throws IOException {
        Collections.sort(cache, new Comparator<SrsFlvFrame>() {
            @Override
            public int compare(SrsFlvFrame lhs, SrsFlvFrame rhs) {
                return lhs.dts - rhs.dts;
            }
        });
        while (nb_videos > 1 && nb_audios > 1) {
            SrsFlvFrame frame = cache.remove(0);
            ByteBuffer buffer = ByteBuffer.allocate(15 + frame.tag.size);
            if (frame.is_video()) {
                nb_videos--;
            } else if (frame.is_audio()) {
                nb_audios--;
            }

            if (frame.is_keyframe()) {
                Log.i(TAG, String.format("worker: got key frame type=%d, dts=%d, size=%dB, videos=%d, audios=%d",
                        frame.type, frame.dts, frame.tag.size, nb_videos, nb_audios));
            } else {
                Log.i(TAG, String.format("worker: got frame type=%d, dts=%d, size=%dB, videos=%d, audios=%d",
                   frame.type, frame.dts, frame.tag.size, nb_videos, nb_audios));
            }

            // write the 11B flv tag header
            ByteBuffer th = ByteBuffer.allocate(11);
            // Reserved UB [2]
            // Filter UB [1]
            // TagType UB [5]
            // DataSize UI24
            int tag_size = (int) ((frame.tag.size & 0x00FFFFFF) | ((frame.type & 0x1F) << 24));
            th.putInt(tag_size);
            // Timestamp UI24
            // TimestampExtended UI8
            int time = (int) ((frame.dts << 8) & 0xFFFFFF00) | ((frame.dts >> 24) & 0x000000FF);
            th.putInt(time);
            // StreamID UI24 Always 0.
            th.put((byte) 0);
            th.put((byte) 0);
            th.put((byte) 0);
            buffer.put(th);

            // write the flv tag data.
            buffer.put(frame.tag.frame);

            // write the 4B previous tag size.
            // @remark, we append the tag size, this is different to SRS which write RTMP packet.
            ByteBuffer pps = ByteBuffer.allocate(4);
            pps.putInt((int) (frame.tag.size + 11));
            buffer.put(pps);

            try {
                rtmpSend(buffer.array(),frame.type,frame.dts);
            } catch (Exception e)
            {
                e.printStackTrace();;
            }


            if (frame.is_keyframe()) {
                Log.i(TAG, String.format("worker: send key frame type=%d, dts=%d, size=%dB, tag_size=%#x, time=%#x",
                        frame.type, frame.dts, frame.tag.size, tag_size, time
                ));
            } else {
                Log.i(TAG, String.format("worker: send frame type=%d, dts=%d, size=%dB, tag_size=%#x, time=%#x",
                        frame.type, frame.dts, frame.tag.size, tag_size, time
                ));
            }
        }
    }

    /**
     * the supported output format for muxer.
     */
    class OutputFormat {
        public final static int MUXER_OUTPUT_RTMP = 0;
    }

    // E.4.3.1 VIDEODATA
    // Frame Type UB [4]
    // Type of video frame. The following values are defined:
    //     1 = key frame (for AVC, a seekable frame)
    //     2 = inter frame (for AVC, a non-seekable frame)
    //     3 = disposable inter frame (H.263 only)
    //     4 = generated key frame (reserved for server use only)
    //     5 = video info/command frame
    class SrsCodecVideoAVCFrame
    {
        // set to the zero to reserved, for array map.
        public final static int Reserved = 0;
        public final static int Reserved1 = 6;
        public final static int KeyFrame                     = 1;
        public final static int InterFrame                 = 2;
        public final static int DisposableInterFrame         = 3;
        public final static int GeneratedKeyFrame            = 4;
        public final static int VideoInfoFrame                = 5;
    }

    // AVCPacketType IF CodecID == 7 UI8
    // The following values are defined:
    //     0 = AVC sequence header
    //     1 = AVC NALU
    //     2 = AVC end of sequence (lower level NALU sequence ender is
    //         not required or supported)
    class SrsCodecVideoAVCType
    {
        // set to the max value to reserved, for array map.
        public final static int Reserved                    = 3;

        public final static int SequenceHeader                 = 0;
        public final static int NALU                         = 1;
        public final static int SequenceHeaderEOF             = 2;
    }

    /**
     * E.4.1 FLV Tag, page 75
     */
    class SrsCodecFlvTag
    {
        // set to the zero to reserved, for array map.
        public final static int Reserved = 0;
        // 8 = audio
        public final static int Audio = 8;
        // 9 = video
        public final static int Video = 9;
        // 18 = script data
        public final static int Script = 18;
    };

    // E.4.3.1 VIDEODATA
    // CodecID UB [4]
    // Codec Identifier. The following values are defined:
    //     2 = Sorenson H.263
    //     3 = Screen video
    //     4 = On2 VP6
    //     5 = On2 VP6 with alpha channel
    //     6 = Screen video version 2
    //     7 = AVC
    class SrsCodecVideo
    {
        // set to the zero to reserved, for array map.
        public final static int Reserved                = 0;
        public final static int Reserved1                = 1;
        public final static int Reserved2                = 9;
        // for user to disable video, for example, use pure audio hls.
        public final static int Disabled                = 8;
        public final static int SorensonH263             = 2;
        public final static int ScreenVideo             = 3;
        public final static int On2VP6                 = 4;
        public final static int On2VP6WithAlphaChannel = 5;
        public final static int ScreenVideoVersion2     = 6;
        public final static int AVC                     = 7;
    }

    /**
     * the aac object type, for RTMP sequence header
     * for AudioSpecificConfig, @see aac-mp4a-format-ISO_IEC_14496-3+2001.pdf, page 33
     * for audioObjectType, @see aac-mp4a-format-ISO_IEC_14496-3+2001.pdf, page 23
     */
    class SrsAacObjectType
    {
        public final static int Reserved = 0;

        // Table 1.1 �C Audio Object Type definition
        // @see @see aac-mp4a-format-ISO_IEC_14496-3+2001.pdf, page 23
        public final static int AacMain = 1;
        public final static int AacLC = 2;
        public final static int AacSSR = 3;
        // AAC HE = LC+SBR
        public final static int AacHE = 5;
        // AAC HEv2 = LC+SBR+PS
        public final static int AacHEV2 = 29;
    }

    /**
     * the aac profile, for ADTS(HLS/TS)
     * @see
     */
    // https://github.com/simple-rtmp-server/srs/issues/310
    class SrsAacProfile
    {
        public final static int Reserved = 3;

        // @see 7.1 Profiles, aac-iso-13818-7.pdf, page 40
        public final static int Main = 0;
        public final static int LC = 1;
        public final static int SSR = 2;
    }

    /**
     * the FLV/RTMP supported audio sample rate.
     * Sampling rate. The following values are defined:
     * 0 = 5.5 kHz = 5512 Hz
     * 1 = 11 kHz = 11025 Hz
     * 2 = 22 kHz = 22050 Hz
     * 3 = 44 kHz = 44100 Hz
     */
    class SrsCodecAudioSampleRate
    {
        // set to the max value to reserved, for array map.
        public final static int Reserved                 = 4;
        public final static int R5512                     = 0;
        public final static int R11025                    = 1;
        public final static int R22050                    = 2;
        public final static int R44100                    = 3;
    }

    /**
     * the type of message to process.
     */
    class SrsMessageType {
        public final static int FLV = 0x100;
    }

    /**
     * Table 7-1 �C NAL unit type codes, syntax element categories, and NAL unit type classes
     * H.264-AVC-ISO_IEC_14496-10-2012.pdf, page 83.
     */
    class SrsAvcNaluType
    {
        // Unspecified
        public final static int Reserved = 0;

        // Coded slice of a non-IDR picture slice_layer_without_partitioning_rbsp( )
        public final static int NonIDR = 1;
        // Coded slice data partition A slice_data_partition_a_layer_rbsp( )
        public final static int DataPartitionA = 2;
        // Coded slice data partition B slice_data_partition_b_layer_rbsp( )
        public final static int DataPartitionB = 3;
        // Coded slice data partition C slice_data_partition_c_layer_rbsp( )
        public final static int DataPartitionC = 4;
        // Coded slice of an IDR picture slice_layer_without_partitioning_rbsp( )
        public final static int IDR = 5;
        // Supplemental enhancement information (SEI) sei_rbsp( )
        public final static int SEI = 6;
        // Sequence parameter set seq_parameter_set_rbsp( )
        public final static int SPS = 7;
        // Picture parameter set pic_parameter_set_rbsp( )
        public final static int PPS = 8;
        // Access unit delimiter access_unit_delimiter_rbsp( )
        public final static int AccessUnitDelimiter = 9;
        // End of sequence end_of_seq_rbsp( )
        public final static int EOSequence = 10;
        // End of stream end_of_stream_rbsp( )
        public final static int EOStream = 11;
        // Filler data filler_data_rbsp( )
        public final static int FilterData = 12;
        // Sequence parameter set extension seq_parameter_set_extension_rbsp( )
        public final static int SPSExt = 13;
        // Prefix NAL unit prefix_nal_unit_rbsp( )
        public final static int PrefixNALU = 14;
        // Subset sequence parameter set subset_seq_parameter_set_rbsp( )
        public final static int SubsetSPS = 15;
        // Coded slice of an auxiliary coded picture without partitioning slice_layer_without_partitioning_rbsp( )
        public final static int LayerWithoutPartition = 19;
        // Coded slice extension slice_layer_extension_rbsp( )
        public final static int CodedSliceExt = 20;
    }

    /**
     * utils functions from srs.
     */
    public class SrsUtils {
        private final static String TAG = "SrsMuxer";

        public boolean srs_bytes_equals(byte[] a, byte[]b) {
            if ((a == null || b == null) && (a != null || b != null)) {
                return false;
            }

            if (a.length != b.length) {
                return false;
            }

            for (int i = 0; i < a.length && i < b.length; i++) {
                if (a[i] != b[i]) {
                    return false;
                }
            }

            return true;
        }

        public SrsAnnexbSearch srs_avc_startswith_annexb(ByteBuffer bb, MediaCodec.BufferInfo bi) {
            SrsAnnexbSearch as = new SrsAnnexbSearch();
            as.match = false;

            int pos = bb.position();
            while (pos < bi.size - 3) {
                // not match.
                if (bb.get(pos) != 0x00 || bb.get(pos + 1) != 0x00) {
                    break;
                }

                // match N[00] 00 00 01, where N>=0
                if (bb.get(pos + 2) == 0x01) {
                    as.match = true;
                    as.nb_start_code = pos + 3 - bb.position();
                    break;
                }

                pos++;
            }

            return as;
        }

        public boolean srs_aac_startswith_adts(ByteBuffer bb, MediaCodec.BufferInfo bi)
        {
            int pos = bb.position();
            if (bi.size - pos < 2) {
                return false;
            }

            // matched 12bits 0xFFF,
            // @remark, we must cast the 0xff to char to compare.
            if (bb.get(pos) != (byte)0xff || (byte)(bb.get(pos + 1) & 0xf0) != (byte)0xf0) {
                return false;
            }

            return true;
        }

        public int srs_codec_aac_ts2rtmp(int profile)
        {
            switch (profile) {
                case SrsAacProfile.Main: return SrsAacObjectType.AacMain;
                case SrsAacProfile.LC: return SrsAacObjectType.AacLC;
                case SrsAacProfile.SSR: return SrsAacObjectType.AacSSR;
                default: return SrsAacObjectType.Reserved;
            }
        }

        public int srs_codec_aac_rtmp2ts(int object_type)
        {
            switch (object_type) {
                case SrsAacObjectType.AacMain: return SrsAacProfile.Main;
                case SrsAacObjectType.AacHE:
                case SrsAacObjectType.AacHEV2:
                case SrsAacObjectType.AacLC: return SrsAacProfile.LC;
                case SrsAacObjectType.AacSSR: return SrsAacProfile.SSR;
                default: return SrsAacProfile.Reserved;
            }
        }
    }

    /**
     * the search result for annexb.
     */
    class SrsAnnexbSearch {
        public int nb_start_code = 0;
        public boolean match = false;
    }

    /**
     * the demuxed tag frame.
     */
    class SrsFlvFrameBytes {
        public ByteBuffer frame;
        public int size;
    }

    /**
     * the muxed flv frame.
     */
    class SrsFlvFrame {
        // the tag bytes.
        public SrsFlvFrameBytes tag;
        // the codec type for audio/aac and video/avc for instance.
        public int avc_aac_type;
        // the frame type, keyframe or not.
        public int frame_type;
        // the tag type, audio, video or data.
        public int type;
        // the dts in ms, tbn is 1000.
        public int dts;

        public boolean is_keyframe() {
            return type == SrsCodecFlvTag.Video && frame_type == SrsCodecVideoAVCFrame.KeyFrame;
        }

        public boolean is_video() {
            return type == SrsCodecFlvTag.Video;
        }

        public boolean is_audio() {
            return type == SrsCodecFlvTag.Audio;
        }
    }

    /**
     * the raw h.264 stream, in annexb.
     */
    class SrsRawH264Stream {
        private SrsUtils utils;
        private final static String TAG = "SrsMuxer";

        public SrsRawH264Stream() {
            utils = new SrsUtils();
        }

        public boolean is_sps(SrsFlvFrameBytes frame) {
            if (frame.size < 1) {
                return false;
            }

            // 5bits, 7.3.1 NAL unit syntax,
            // H.264-AVC-ISO_IEC_14496-10.pdf, page 44.
            //  7: SPS, 8: PPS, 5: I Frame, 1: P Frame
            int nal_unit_type = (int)(frame.frame.get(0) & 0x1f);

            return nal_unit_type == SrsAvcNaluType.SPS;
        }

        public boolean is_pps(SrsFlvFrameBytes frame) {
            if (frame.size < 1) {
                return false;
            }

            // 5bits, 7.3.1 NAL unit syntax,
            // H.264-AVC-ISO_IEC_14496-10.pdf, page 44.
            //  7: SPS, 8: PPS, 5: I Frame, 1: P Frame
            int nal_unit_type = (int)(frame.frame.get(0) & 0x1f);

            return nal_unit_type == SrsAvcNaluType.PPS;
        }

        public SrsFlvFrameBytes mux_ibp_frame(SrsFlvFrameBytes frame) {
            SrsFlvFrameBytes nalu_header = new SrsFlvFrameBytes();
            nalu_header.size = 4;
            nalu_header.frame = ByteBuffer.allocate(nalu_header.size);

            // 5.3.4.2.1 Syntax, H.264-AVC-ISO_IEC_14496-15.pdf, page 16
            // lengthSizeMinusOne, or NAL_unit_length, always use 4bytes size
            int NAL_unit_length = frame.size;

            // mux the avc NALU in "ISO Base Media File Format"
            // from H.264-AVC-ISO_IEC_14496-15.pdf, page 20
            // NALUnitLength
            nalu_header.frame.putInt(NAL_unit_length);

            // reset the buffer.
            nalu_header.frame.rewind();

            //Log.i(TAG, String.format("mux ibp frame %dB", frame.size));
            //SrsHttpFlv.srs_print_bytes(TAG, nalu_header.frame, 16);

            return nalu_header;
        }

        public void mux_sequence_header(byte[] sps, byte[] pps, int dts, int pts, ArrayList<SrsFlvFrameBytes> frames) {
            // 5bytes sps/pps header:
            //      configurationVersion, AVCProfileIndication, profile_compatibility,
            //      AVCLevelIndication, lengthSizeMinusOne
            // 3bytes size of sps:
            //      numOfSequenceParameterSets, sequenceParameterSetLength(2B)
            // Nbytes of sps.
            //      sequenceParameterSetNALUnit
            // 3bytes size of pps:
            //      numOfPictureParameterSets, pictureParameterSetLength
            // Nbytes of pps:
            //      pictureParameterSetNALUnit

            // decode the SPS:
            // @see: 7.3.2.1.1, H.264-AVC-ISO_IEC_14496-10-2012.pdf, page 62
            if (true) {
                SrsFlvFrameBytes hdr = new SrsFlvFrameBytes();
                hdr.size = 5;
                hdr.frame = ByteBuffer.allocate(hdr.size);

                // @see: Annex A Profiles and levels, H.264-AVC-ISO_IEC_14496-10.pdf, page 205
                //      Baseline profile profile_idc is 66(0x42).
                //      Main profile profile_idc is 77(0x4d).
                //      Extended profile profile_idc is 88(0x58).
                byte profile_idc = sps[1];
                //u_int8_t constraint_set = frame[2];
                byte level_idc = sps[3];

                // generate the sps/pps header
                // 5.3.4.2.1 Syntax, H.264-AVC-ISO_IEC_14496-15.pdf, page 16
                // configurationVersion
                hdr.frame.put((byte)0x01);
                // AVCProfileIndication
                hdr.frame.put(profile_idc);
                // profile_compatibility
                hdr.frame.put((byte)0x00);
                // AVCLevelIndication
                hdr.frame.put(level_idc);
                // lengthSizeMinusOne, or NAL_unit_length, always use 4bytes size,
                // so we always set it to 0x03.
                hdr.frame.put((byte)0x03);

                // reset the buffer.
                hdr.frame.rewind();
                frames.add(hdr);
            }

            // sps
            if (true) {
                SrsFlvFrameBytes sps_hdr = new SrsFlvFrameBytes();
                sps_hdr.size = 3;
                sps_hdr.frame = ByteBuffer.allocate(sps_hdr.size);

                // 5.3.4.2.1 Syntax, H.264-AVC-ISO_IEC_14496-15.pdf, page 16
                // numOfSequenceParameterSets, always 1
                sps_hdr.frame.put((byte) 0x01);
                // sequenceParameterSetLength
                sps_hdr.frame.putShort((short) sps.length);

                sps_hdr.frame.rewind();
                frames.add(sps_hdr);

                // sequenceParameterSetNALUnit
                SrsFlvFrameBytes sps_bb = new SrsFlvFrameBytes();
                sps_bb.size = sps.length;
                sps_bb.frame = ByteBuffer.wrap(sps);
                frames.add(sps_bb);
            }

            // pps
            if (true) {
                SrsFlvFrameBytes pps_hdr = new SrsFlvFrameBytes();
                pps_hdr.size = 3;
                pps_hdr.frame = ByteBuffer.allocate(pps_hdr.size);

                // 5.3.4.2.1 Syntax, H.264-AVC-ISO_IEC_14496-15.pdf, page 16
                // numOfPictureParameterSets, always 1
                pps_hdr.frame.put((byte) 0x01);
                // pictureParameterSetLength
                pps_hdr.frame.putShort((short) pps.length);

                pps_hdr.frame.rewind();
                frames.add(pps_hdr);

                // pictureParameterSetNALUnit
                SrsFlvFrameBytes pps_bb = new SrsFlvFrameBytes();
                pps_bb.size = pps.length;
                pps_bb.frame = ByteBuffer.wrap(pps);
                frames.add(pps_bb);
            }
        }

        public SrsFlvFrameBytes mux_avc2flv(ArrayList<SrsFlvFrameBytes> frames, int frame_type, int avc_packet_type, int dts, int pts) {
            SrsFlvFrameBytes flv_tag = new SrsFlvFrameBytes();

            // for h264 in RTMP video payload, there is 5bytes header:
            //      1bytes, FrameType | CodecID
            //      1bytes, AVCPacketType
            //      3bytes, CompositionTime, the cts.
            // @see: E.4.3 Video Tags, video_file_format_spec_v10_1.pdf, page 78
            flv_tag.size = 5;
            for (int i = 0; i < frames.size(); i++) {
                SrsFlvFrameBytes frame = frames.get(i);
                flv_tag.size += frame.size;
            }

            flv_tag.frame = ByteBuffer.allocate(flv_tag.size);

            // @see: E.4.3 Video Tags, video_file_format_spec_v10_1.pdf, page 78
            // Frame Type, Type of video frame.
            // CodecID, Codec Identifier.
            // set the rtmp header
            flv_tag.frame.put((byte)((frame_type << 4) | SrsCodecVideo.AVC));

            // AVCPacketType
            flv_tag.frame.put((byte)avc_packet_type);

            // CompositionTime
            // pts = dts + cts, or
            // cts = pts - dts.
            // where cts is the header in rtmp video packet payload header.
            int cts = pts - dts;
            flv_tag.frame.put((byte)(cts >> 16));
            flv_tag.frame.put((byte)(cts >> 8));
            flv_tag.frame.put((byte)cts);

            // h.264 raw data.
            for (int i = 0; i < frames.size(); i++) {
                SrsFlvFrameBytes frame = frames.get(i);
                byte[] frame_bytes = new byte[frame.size];
                frame.frame.get(frame_bytes);
                flv_tag.frame.put(frame_bytes);
            }

            // reset the buffer.
            flv_tag.frame.rewind();

            //Log.i(TAG, String.format("flv tag muxed, %dB", flv_tag.size));
            //SrsHttpFlv.srs_print_bytes(TAG, flv_tag.frame, 128);

            return flv_tag;
        }

        public SrsFlvFrameBytes annexb_demux(ByteBuffer bb, MediaCodec.BufferInfo bi) throws Exception {
            SrsFlvFrameBytes tbb = new SrsFlvFrameBytes();

            while (bb.position() < bi.size) {
                // each frame must prefixed by annexb format.
                // about annexb, @see H.264-AVC-ISO_IEC_14496-10.pdf, page 211.
                SrsAnnexbSearch tbbsc = utils.srs_avc_startswith_annexb(bb, bi);
                if (!tbbsc.match || tbbsc.nb_start_code < 3) {
                    Log.e(TAG, "annexb not match.");
                    RtmpFlv.srs_print_bytes(TAG, bb, 16);
                    throw new Exception(String.format("annexb not match for %dB, pos=%d", bi.size, bb.position()));
                }

                // the start codes.
                ByteBuffer tbbs = bb.slice();
                for (int i = 0; i < tbbsc.nb_start_code; i++) {
                    bb.get();
                }

                // find out the frame size.
                tbb.frame = bb.slice();
                int pos = bb.position();
                while (bb.position() < bi.size) {
                    SrsAnnexbSearch bsc = utils.srs_avc_startswith_annexb(bb, bi);
                    if (bsc.match) {
                        break;
                    }
                    bb.get();
                }

                tbb.size = bb.position() - pos;
                if (bb.position() < bi.size) {
                    Log.i(TAG, String.format("annexb multiple match ok, pts=%d", bi.presentationTimeUs / 1000));
                    RtmpFlv.srs_print_bytes(TAG, tbbs, 16);
                    RtmpFlv.srs_print_bytes(TAG, bb.slice(), 16);
                }
                //Log.i(TAG, String.format("annexb match %d bytes", tbb.size));
                break;
            }

            return tbb;
        }
    }

    class SrsRawAacStreamCodec {
        public byte protection_absent;
        // SrsAacObjectType
        public int aac_object;
        public byte sampling_frequency_index;
        public byte channel_configuration;
        public short frame_length;

        public byte sound_format;
        public byte sound_rate;
        public byte sound_size;
        public byte sound_type;
        // 0 for sh; 1 for raw data.
        public byte aac_packet_type;

        public byte[] frame;
    }

    /**
     * remux the annexb to flv tags.
     */
    class SrsFlv {
        //private MediaFormat videoTrack;
        //private MediaFormat audioTrack;
        private int achannel = 2;
        private int asample_rate = 44100;

        private SrsUtils utils;
        private Handler handler;

        private SrsRawH264Stream avc;
        private byte[] h264_sps;
        private boolean h264_sps_changed;
        private byte[] h264_pps;
        private boolean h264_pps_changed;
        private boolean h264_sps_pps_sent;

        private byte[] aac_specific_config;

        public SrsFlv() {
            utils = new SrsUtils();

            avc = new SrsRawH264Stream();
            h264_sps = new byte[0];
            h264_sps_changed = false;
            h264_pps = new byte[0];
            h264_pps_changed = false;
            h264_sps_pps_sent = false;

            aac_specific_config = null;
        }

        /**
         * set the handler to send message to work thread.
         * @param h the handler to send the message.
         */
        public void setHandler(Handler h) {
            handler = h;
        }

        public void writeAudioSample(final ByteBuffer bb, MediaCodec.BufferInfo bi) throws Exception {
        	bb.clear();
            int pts = (int)(bi.presentationTimeUs / 1000);
            int dts = (int)pts;

            byte[] frame = new byte[bi.size + 2];
            byte aac_packet_type = 1; // 1 = AAC raw
            if (aac_specific_config == null) {
                frame = new byte[4];

                // @see aac-mp4a-format-ISO_IEC_14496-3+2001.pdf
                // AudioSpecificConfig (), page 33
                // 1.6.2.1 AudioSpecificConfig
                // audioObjectType; 5 bslbf
                byte ch = (byte)(bb.get(0) & 0xf8);
                // 3bits left.

                // samplingFrequencyIndex; 4 bslbf
                byte samplingFrequencyIndex = 0x04;
                if (asample_rate == SrsCodecAudioSampleRate.R22050) {
                    samplingFrequencyIndex = 0x07;
                } else if (asample_rate == SrsCodecAudioSampleRate.R11025) {
                    samplingFrequencyIndex = 0x0a;
                }
                ch |= (samplingFrequencyIndex >> 1) & 0x07;
                frame[2] = ch;

                ch = (byte)((samplingFrequencyIndex << 7) & 0x80);
                // 7bits left.

                // channelConfiguration; 4 bslbf
                byte channelConfiguration = 1;
                if (achannel == 2) {
                    channelConfiguration = 2;
                }
                ch |= (channelConfiguration << 3) & 0x78;
                // 3bits left.

                // GASpecificConfig(), page 451
                // 4.4.1 Decoder configuration (GASpecificConfig)
                // frameLengthFlag; 1 bslbf
                // dependsOnCoreCoder; 1 bslbf
                // extensionFlag; 1 bslbf
                frame[3] = ch;

                aac_specific_config = frame;
                aac_packet_type = 0; // 0 = AAC sequence header
                Log.v("rtmp", "aac_specific_config");
            } else {
            	Log.v("rtmp", frame.length + " " + bb.capacity());
                bb.get(frame, 2, frame.length - 2);
            }

            byte sound_format = 10; // AAC
            byte sound_type = 0; // 0 = Mono sound
            if (achannel == 2) {
                sound_type = 1; // 1 = Stereo sound
            }
            byte sound_size = 1; // 1 = 16-bit samples
            byte sound_rate = 3; // 44100, 22050, 11025
            if (asample_rate == 22050) {
                sound_rate = 2;
            } else if (asample_rate == 11025) {
                sound_rate = 1;
            }

            // for audio frame, there is 1 or 2 bytes header:
            //      1bytes, SoundFormat|SoundRate|SoundSize|SoundType
            //      1bytes, AACPacketType for SoundFormat == 10, 0 is sequence header.
            byte audio_header = (byte)(sound_type & 0x01);
            audio_header |= (sound_size << 1) & 0x02;
            audio_header |= (sound_rate << 2) & 0x0c;
            audio_header |= (sound_format << 4) & 0xf0;

            frame[0] = audio_header;
            frame[1] = aac_packet_type;

            SrsFlvFrameBytes tag = new SrsFlvFrameBytes();
            tag.frame = ByteBuffer.wrap(frame);
            tag.size = frame.length;

            int timestamp = dts;
            rtmp_write_packet(SrsCodecFlvTag.Audio, timestamp, 0, aac_packet_type, tag);
        }

        public void writeVideoSample(final ByteBuffer bb, MediaCodec.BufferInfo bi) throws Exception {
        	bb.clear();
            int pts = (int)(bi.presentationTimeUs / 1000);
            int dts = (int)pts;

            ArrayList<SrsFlvFrameBytes> ibps = new ArrayList<SrsFlvFrameBytes>();
            int frame_type = SrsCodecVideoAVCFrame.InterFrame;
            //Log.i(TAG, String.format("video %d/%d bytes, offset=%d, position=%d, pts=%d", bb.remaining(), bi.size, bi.offset, bb.position(), pts));

            // send each frame.
            while (bb.position() < bi.size) {
                SrsFlvFrameBytes frame = avc.annexb_demux(bb, bi);

                // 5bits, 7.3.1 NAL unit syntax,
                // H.264-AVC-ISO_IEC_14496-10.pdf, page 44.
                //  7: SPS, 8: PPS, 5: I Frame, 1: P Frame
                int nal_unit_type = (int)(frame.frame.get(0) & 0x1f);
                if (nal_unit_type == SrsAvcNaluType.SPS || nal_unit_type == SrsAvcNaluType.PPS) {
                    Log.i(TAG, String.format("annexb demux %dB, pts=%d, frame=%dB, nalu=%d", bi.size, pts, frame.size, nal_unit_type));
                }

                // for IDR frame, the frame is keyframe.
                if (nal_unit_type == SrsAvcNaluType.IDR) {
                    frame_type = SrsCodecVideoAVCFrame.KeyFrame;
                }

                // ignore the nalu type aud(9)
                if (nal_unit_type == SrsAvcNaluType.AccessUnitDelimiter) {
                    continue;
                }

                // for sps
                if (avc.is_sps(frame)) {
                    byte[] sps = new byte[frame.size];
                    frame.frame.get(sps);

                    if (utils.srs_bytes_equals(h264_sps, sps)) {
                        continue;
                    }
                    h264_sps_changed = true;
                    h264_sps = sps;
                    continue;
                }

                // for pps
                if (avc.is_pps(frame)) {
                    byte[] pps = new byte[frame.size];
                    frame.frame.get(pps);

                    if (utils.srs_bytes_equals(h264_pps, pps)) {
                        continue;
                    }
                    h264_pps_changed = true;
                    h264_pps = pps;
                    continue;
                }

                // ibp frame.
                SrsFlvFrameBytes nalu_header = avc.mux_ibp_frame(frame);
                ibps.add(nalu_header);
                ibps.add(frame);
            }

            write_h264_sps_pps(dts, pts);

            write_h264_ipb_frame(ibps, frame_type, dts, pts);
        }

        private void write_h264_sps_pps(int dts, int pts) {
            // when sps or pps changed, update the sequence header,
            // for the pps maybe not changed while sps changed.
            // so, we must check when each video ts message frame parsed.
            if (h264_sps_pps_sent && !h264_sps_changed && !h264_pps_changed) {
                return;
            }

            // when not got sps/pps, wait.
            if (h264_pps.length <= 0 || h264_sps.length <= 0) {
                return;
            }

            // h264 raw to h264 packet.
            ArrayList<SrsFlvFrameBytes> frames = new ArrayList<SrsFlvFrameBytes>();
            avc.mux_sequence_header(h264_sps, h264_pps, dts, pts, frames);

            // h264 packet to flv packet.
            int frame_type = SrsCodecVideoAVCFrame.KeyFrame;
            int avc_packet_type = SrsCodecVideoAVCType.SequenceHeader;
            SrsFlvFrameBytes flv_tag = avc.mux_avc2flv(frames, frame_type, avc_packet_type, dts, pts);

            // the timestamp in rtmp message header is dts.
            int timestamp = dts;
            rtmp_write_packet(SrsCodecFlvTag.Video, timestamp, frame_type, avc_packet_type, flv_tag);

            // reset sps and pps.
            h264_sps_changed = false;
            h264_pps_changed = false;
            h264_sps_pps_sent = true;
            Log.i(TAG, String.format("flv: h264 sps/pps sent, sps=%dB, pps=%dB", h264_sps.length, h264_pps.length));
        }

        private void write_h264_ipb_frame(ArrayList<SrsFlvFrameBytes> ibps, int frame_type, int dts, int pts) {
            // when sps or pps not sent, ignore the packet.
            // @see https://github.com/simple-rtmp-server/srs/issues/203
            if (!h264_sps_pps_sent) {
                return;
            }

            int avc_packet_type = SrsCodecVideoAVCType.NALU;
            SrsFlvFrameBytes flv_tag = avc.mux_avc2flv(ibps, frame_type, avc_packet_type, dts, pts);

            if (frame_type == SrsCodecVideoAVCFrame.KeyFrame) {
                //Log.i(TAG, String.format("flv: keyframe %dB, dts=%d", flv_tag.size, dts));
            }

            // the timestamp in rtmp message header is dts.
            int timestamp = dts;
            rtmp_write_packet(SrsCodecFlvTag.Video, timestamp, frame_type, avc_packet_type, flv_tag);
        }

        private void rtmp_write_packet(int type, int dts, int frame_type, int avc_aac_type, SrsFlvFrameBytes tag) {
            SrsFlvFrame frame = new SrsFlvFrame();
            frame.tag = tag;
            frame.type = type;
            frame.dts = dts;
            frame.frame_type = frame_type;
            frame.avc_aac_type = avc_aac_type;

            // use handler to send the message.
            // TODO: FIXME: we must wait for the handler to ready, for the sps/pps cannot be dropped.
            if (handler == null) {
                Log.w(TAG, "flv: drop frame for handler not ready.");
                return;
            }

            Message msg = Message.obtain();
            msg.what = SrsMessageType.FLV;
            msg.obj = frame;
            handler.sendMessage(msg);
            //Log.i(TAG, String.format("flv: enqueue frame type=%d, dts=%d, size=%dB", frame.type, frame.dts, frame.tag.size));
        }
    }
}