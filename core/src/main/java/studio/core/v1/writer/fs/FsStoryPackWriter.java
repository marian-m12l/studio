/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.writer.fs;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import studio.core.v1.model.*;
import studio.core.v1.utils.AudioConversion;
import studio.core.v1.utils.ID3Tags;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.*;

/*
Writer for the new binary format coming with firmware 2.4
Assets must be prepared to match the expected format : 4-bits depth / RLE encoding BMP for images, and mono 44100Hz MP3 for sounds.
Assets and pack files are kept as cleartext in the library, with cipher operations happening during transfer to/from device.
 */
public class FsStoryPackWriter {

    private static final String NODE_INDEX_FILENAME = "ni";
    private static final String LIST_INDEX_FILENAME = "li";
    private static final String IMAGE_INDEX_FILENAME = "ri";
    private static final String IMAGE_FOLDER = "rf" + File.separator;
    private static final String SOUND_INDEX_FILENAME = "si";
    private static final String SOUND_FOLDER = "sf" + File.separator;
    private static final String BOOT_FILENAME = "bt";
    private static final String NIGHT_MODE_FILENAME = "nm";
    private static final String CLEARTEXT_FILENAME = ".cleartext";

    private static final String BLANK_MP3_FILE = "FFFB90C4000000000000000000000000000000000058696E670000000F0000001500000C670030303030393939393943434343434D4D4D4D4D5959595963636363636C6C6C6C6C7676767676808080808C8C8C8C8C9898989898A1A1A1A1A1ADADADADB9B9B9B9B9C3C3C3C3C3CDCDCDCDCDD9D9D9D9E3E3E3E3E3ECECECECECF6F6F6F6F6FFFFFFFF000000504C414D45332E31303004B90000000000000000152024061E410001E000000C671265A6CC000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000FFFBA0C40003C00001FE00000020493B6380000AF9FFFFFFEBA7E8AC7EF7D568E9E3D95C239EE695FFFFFFFFFFFFFFFFFFFFFFFFD3D6D720F16373C928A70D7102A78344C89D9FFAFFEBFFFFFFFFFEBF6676676D88B556B331C81CA43B89746742C862B0CE2C480791DE95000F0FFFFC64FCDFF91B0D4A77CE8D5D48FFFFFFFFFFFFDCDF10F2A4CFF6DE3FD3CB80AA2A0B21881254620E0FFFFD6301A951123577CCB653D653CFFFFFFFFFFFFEEF92C893762B8428166ADE50E2D320A34590902CC314037500090FFFFE7E73B670E7FFD7D57DFFFFFFFFFD752FFF0A3686120BE56443344336E34C7ACB269F791D6DA4BA1212CFFFFF997BDAFCE537D5F32FFFFFFFFFFFDF9EE5F37C663723B113D0FAEB1ACC35BABAE2A7058942000C0B6C7B22EB5A25D7FFFFFFFFFFFFFFFFA529D52849E744BD8CCE8CBD42C7422314A2873032E1220F783121FFFFCAFA9B2A908E8CA5FC80BFAFFFFFFFFEBBF309AB92489E11C60808BA32DAF9D72BB8A3B7DDFCCE3A48A50B67B148C900060FFFFFFFF73E7FA22B2C78DD97FFFFFFFFE472A999CF326651C29B423DDF6159BAAA20D0631315A392071C2331B02B8C07F576AA4B4774A3FFFFFFFFFFFFFFFFFE9D28F4ABF679DDDC416554BEC8A323B34E562B8BA9874615023FFFFCD675CB3FFFFFFFFFFFFFFF9FF7E9C8F3AB650310D4CD084052E93AEB515C7BB7594B1418B2243FFFFCF987E8D39EB2EDF9FFFFFB10C4F1038388011C00000000983A62C4000A79FFFFFFFFFFEFF9EB08FBC74F9FA7FA9976EEEF5CCC6C66BD134817D67955000E0951C92E6A2D5665323D133AEE8F7B687EFFFFFFFFFFDFD776DBAE32D9533AB95912538306EC4391E58B1CE626205808406B7FFFFB10C4F9038569CF1A40047F08B93D2308008FE0FE9F7DA8D3CBA497BBB32B042AADD11D880DD0E71590857126CE033FFFFFC3FAEE5939445CF6DD755FFFFFFFFE5DEBDBCF19D1953B266810D10CC5358186CCD6912312990AE4FFFFE5BD6C93A52BB4446B3864FFFB10C4F58385BDE5164004DF089F3C6304008FE2D35E9FFFFFFFFAFCCE67A316D11854CC9B327F50FD68CB3F927C5776FBB886A9E5295E818C336A033FFFF6EFA7DF5332BE53A5F6AEDFD57FFFFFFE9D726CAF4454523BDDA8B06DA2355B2B9974C8A50AE2E930FFFB20C4F4038575D918400051C8C93CE288009BE1EA7DA6EC79A200E0BD07FFFFE5917CF5EEC18EC827BFFFFFFFFFFFFFAEF836339B848470647CF376A087370AB0401B5394610740E2068A55002E0FFC81A5AC8E438A89DB8E49AFDF3FFFFFFFFFFE5ED665E65886368F303A9B1DE26E383020D4BC1A1D5642A1432B08155B67FFFFFB10C4FB838629E51640047F00A43CA308000A39FF7C3FF2F79E5E3DF9C4CFFFFFFFFFEB9DF722C76B8C4E30B999C8E60C3892915C766EC20AC0AB8B257008A2000F0FFFFFDF2EB9AB0A973E5D5FE7FFFFFFFFFFEFB978A989230CCAE79F0F553BF259827E2186FFFB10C4F7838521E31620047F18AA3C62C8009BE10EA6A2ADC1FFFC8A4D849622F2390C33F992B3F1BF33FFFFFFFFFFEC66E891D18C9A2CCB67162B66F28A8E6215C4B876DB0E21F1C885002B0FFE2D7D99FFECB3433199453323912223AFFFFFFFFFFCE67A5323FFFB10C4F703C685E716400051C86B3C2300000AF982CD0641308F91F6266DF970C7977454277B62ECAD4BF98137B3FFFE74E5AF9B9C065E7304C5610F9EFFFFFFFFF3F61A2EF5B0CD08E13306FFF18F39E5B295B306DACE28D9A42BD0D424CC032FFA37DBA6B657FFFB10C4F903C581E51620047F18CD3D6280009BE2C9E9D3EFFFFFFFFFFDFDDA9B1CCD54D5501564712AA2495550C08C7294E254714C54168222CC25E7383FFFEF003B0E6D1886888F45327ACE8FFFFFFFFFFFF599E50894D9EB320C39D14AB659F6F0AAEB4A36BFFFFB20C4F283C6C5E31220091F08B43CE2C0008FE34112A8DE29CC4919D5033FFFFFD7B9F33C27398B3F5FEBFFFFFFFFCFFE6726C483A95504B276E9DB114D2D0050E28E361AD561AE258E0FFFFEC7466EC98D0502D987CF5A527CB9FFFFFFFFFE7738F79E4163772916B3C6F6DACB9D76769E6265422C952348033298000E0FFFF8FFFB20C4F7038661E71640047F08B6BCE2C4008FE348466C20D1F9B993348B9565CD14FFFFFFFFFFFFDBA3CDC91332416B23D1404C18E6085EF453999BF0998118054739E1FFFFFFFFFFFFD6DA549DAE61473CF9198A3FFFFF637D5819C307A8310228E83E1A28B38B3880885D00002303FFFE619A26411F58463302F4B64EE843E9FFFB10C4FD038555E91A40047F00C83CA2C8008FE14FFFFFFFFFFF9CCACCF9558CC990233FDE079E855096C6DCA613184BC27C0F5F7B97D73DEB2FFFFFFFFFFFFFFFFFFFF14E525AAE644A239AD30AEE099877762CB51000033FFFFFF66AB917E77DF979B6BFFFFFFFFB20C4F80386C5E5164004DF00C7BD2284009BE2FFFFFC34220B648C2CE64CB24712D71486826909A6E0E9E1C61C7561D01E4425BFFFF4BACE775F912F996795D77FFFFFFFFFFF90F47626DC0C1219428E095E24EAB12EA33F068A78A0E0D60900011B03C826FDC8DD4B250DFF6591E8828CFD7ACFFFFFFFFFFFE4FCCD106D1A06FFFB20C4FA838601E516200051D8CD3CA2C8009BE1CC8CCF9F65ED6DDAB38A5FDD05AAE43FDEE7DEA9557543AA2AE935BB569FFFFFFFFFFFEBF92C945A1D4D54443A4EC523E150E625EE821995EB5500001C03A7EDBA2D6D2D51433D190D3D1E9774637FFFFFFFFFFF44E67B22B2D5EF296831794C9446A321CF39D84B38E5027FFFFFFB10C4FF038589E91620047F10CB3CA2C8009BE1FFFFFFFFFFFFFFFFFFFFA8F7113C441A47921160D3C44581AC444C414D45332E313030AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAFFFB10C4F9038675E11640047F08A4802348000000AAAAAAAAAAAAAAAAAAAAAAAAAA4C414D45332E313030AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAFFFB20C4F4038649DD1860047F0896BCA348008BE1AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAFFFB10C4FE83C609E91620047F10AF3C22C0008FE3AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAFFFB10C4FA03C609E11A60047F08A7BAE300000A39AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAFFFB10C4F603C629E11A600051C86E806280000000AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAFFFB10C4D603C00001FE0000002000003480000004AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    // TODO Enriched metadata in a dedicated file (pack's title, description and thumbnail, nodes' name, group, type and position)

    public Path write(StoryPack pack, Path outputFolder) throws Exception {
        // Create pack folder: last 8 digits of uuid
        File packFolder = new File(outputFolder.toFile(), transformUuid(UUID.fromString(pack.getUuid())));
        packFolder.mkdirs();

        // Write night mode
        if (pack.isNightModeAvailable()) {
            new File(packFolder, NIGHT_MODE_FILENAME).createNewFile();
        }

        // Indicate that files are cleartext
        new File(packFolder, CLEARTEXT_FILENAME).createNewFile();

        // Store assets bytes
        TreeMap<String, byte[]> assets = new TreeMap<>();

        // Add nodes index file: ni
        FileOutputStream niFos = new FileOutputStream(new File(packFolder, NODE_INDEX_FILENAME));
        DataOutputStream niDos = new DataOutputStream(niFos);
        ByteBuffer bb = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN);

        // Nodes index file format version (1)
        bb.putShort((short) 1);
        // Story pack version (1)
        bb.putShort(pack.getVersion());
        // Start of actual nodes list in this file (0x200 / 512)
        bb.putInt(512);
        // Size of a stage node in this file (0x2C / 44)
        bb.putInt(44);
        // Number of stage nodes in this file
        bb.putInt(pack.getStageNodes().size());
        // Number of images (in RI file and rf/ folder)
        bb.putInt((int) pack.getStageNodes().stream()
                .map(StageNode::getImage)
                .filter(Objects::nonNull)
                .map(ImageAsset::getRawData)
                .map(DigestUtils::sha1Hex)
                .distinct()
                .count());
        // Number of sounds (in SI file and sf/ folder)
        bb.putInt((int) pack.getStageNodes().stream()
                .map(StageNode::getAudio)
                .filter(Objects::nonNull)
                .map(AudioAsset::getRawData)
                .map(DigestUtils::sha1Hex)
                .distinct()
                .count());
        // Is factory pack (boolean) set to true to avoid pack inspection by official Luniistore application
        bb.put((byte) 1);

        // Jump to address 0x200 for actual list of nodes
        bb.put(new byte[512-25]);
        niDos.write(bb.array());
        bb.clear();

        // Write stage nodes and keep track of action nodes and assets
        List<ActionNode> actionNodesOrdered = new ArrayList<>();
        Map<ActionNode, Integer> actionNodesIndexes = new HashMap<>();
        int nextActionNodeIndex = 0;
        List<String> imageHashOrdered = new ArrayList<>();
        List<String> audioHashOrdered = new ArrayList<>();
        for (int i = 0; i < pack.getStageNodes().size(); i++) {
            StageNode node = pack.getStageNodes().get(i);

            int imageIndex = -1;
            ImageAsset image = node.getImage();
            if (image != null) {
                byte[] imageData = image.getRawData();
                String imageHash = DigestUtils.sha1Hex(imageData);
                if (!imageHashOrdered.contains(imageHash)) {
                    if (!"image/bmp".equals(image.getMimeType())) {
                        throw new IllegalArgumentException("FS pack file requires image assets to be BMP.");
                    }
                    ByteBuffer bmpBuffer = ByteBuffer.wrap(imageData);
                    bmpBuffer.order(ByteOrder.LITTLE_ENDIAN);
                    // Make sure the BMP file is RLE-compressed / 4-bits depth
                    if (bmpBuffer.getShort(28) != 0x0004 || bmpBuffer.getInt(30) != 0x00000002) {
                        throw new IllegalArgumentException("FS pack file requires image assets to use 4-bit depth and RLE encoding.");
                    }
                    // Check image dimensions
                    if (bmpBuffer.getInt(18) != 320 || bmpBuffer.getInt(22) != 240) {
                        throw new IllegalArgumentException("FS pack file requires image assets to be 320x240 pixels.");
                    }
                    imageIndex = imageHashOrdered.size();
                    imageHashOrdered.add(imageHash);
                    assets.putIfAbsent(imageHash, imageData);
                } else {
                    imageIndex = imageHashOrdered.indexOf(imageHash);
                }
            }
            int audioIndex = -1;
            AudioAsset audio = node.getAudio();
            // If audio is missing, add a blank audio to satisfy the device
            if (audio == null) {
                audio = new AudioAsset("audio/mp3", Hex.decodeHex(BLANK_MP3_FILE), "blank_audio_placeholder");
            }
            byte[] audioData = audio.getRawData();
            String audioHash = DigestUtils.sha1Hex(audioData);
            if (!audioHashOrdered.contains(audioHash)) {
                if (!"audio/mp3".equals(audio.getMimeType()) && !"audio/mpeg".equals(audio.getMimeType())) {
                    throw new IllegalArgumentException("FS pack file requires audio assets to be MP3.");
                } else {
                    // Check ID3 tags
                    if (ID3Tags.hasID3v1Tag(audioData) || ID3Tags.hasID3v2Tag(audioData)) {
                        throw new IllegalArgumentException("FS pack file does not support ID3 tags in MP3 files.");
                    }
                    // Check that the file is MONO / 44100Hz
                    AudioFileFormat audioFileFormat = AudioSystem.getAudioFileFormat(new ByteArrayInputStream(audioData));
                    if (audioFileFormat.getFormat().getChannels() != AudioConversion.CHANNELS
                            || audioFileFormat.getFormat().getSampleRate() != AudioConversion.MP3_SAMPLE_RATE) {
                        throw new IllegalArgumentException("FS pack file requires MP3 audio assets to be MONO / 44100Hz.");
                    }
                }
                audioIndex = audioHashOrdered.size();
                audioHashOrdered.add(audioHash);
                assets.putIfAbsent(audioHash, audioData);
            } else {
                audioIndex = audioHashOrdered.indexOf(audioHash);
            }
            Transition okTransition = node.getOkTransition();
            if (okTransition != null && !actionNodesOrdered.contains(okTransition.getActionNode())) {
                actionNodesOrdered.add(okTransition.getActionNode());
                actionNodesIndexes.put(okTransition.getActionNode(), nextActionNodeIndex);
                nextActionNodeIndex += okTransition.getActionNode().getOptions().size();
            }
            Transition homeTransition = node.getHomeTransition();
            if (homeTransition != null && !actionNodesOrdered.contains(homeTransition.getActionNode())) {
                actionNodesOrdered.add(homeTransition.getActionNode());
                actionNodesIndexes.put(homeTransition.getActionNode(), nextActionNodeIndex);
                nextActionNodeIndex += homeTransition.getActionNode().getOptions().size();
            }
            writeStageNode(
                    niDos,
                    imageIndex,  // Image index in RI file (index 0 == first image) --> rf/000/11111111
                    audioIndex,  // Sound index in SI file (index 0 == first sound) --> sf/000/11111111
                    okTransition == null ? -1 : actionNodesIndexes.get(okTransition.getActionNode()),  // OK transition: Action node index in LI file (index 0 == first action node)
                    okTransition == null ? -1 : okTransition.getActionNode().getOptions().size(),  // OK transition: Number of options available
                    okTransition == null ? -1 : okTransition.getOptionIndex(),  // OK transition: Menu option index (index 0 == first menu option)
                    homeTransition == null ? -1 : actionNodesIndexes.get(homeTransition.getActionNode()), // HOME transition: Action node index in LI file (-1 == no transition)
                    homeTransition == null ? -1 : homeTransition.getActionNode().getOptions().size(), // HOME transition: Number of options available
                    homeTransition == null ? -1 : homeTransition.getOptionIndex(), // HOME transition: Menu option index
                    node.getControlSettings().isWheelEnabled(),   // WHEEL flag
                    node.getControlSettings().isOkEnabled(),   // OK flag
                    node.getControlSettings().isHomeEnabled(),  // HOME flag
                    node.getControlSettings().isPauseEnabled(),  // PAUSE flag
                    node.getControlSettings().isAutoJumpEnabled()   // AUTOPLAY flag
            );
        }
        niDos.close();
        niFos.close();


        // Add lists index file: li
        FileOutputStream liFos = new FileOutputStream(new File(packFolder, LIST_INDEX_FILENAME));
        ByteArrayOutputStream liBaos = new ByteArrayOutputStream();
        DataOutputStream liDos = new DataOutputStream(liBaos);
        // Add action nodes
        for (ActionNode actionNode : actionNodesOrdered) {
            writeActionNode(
                    liDos,
                    actionNode.getOptions().stream().mapToInt(stage -> pack.getStageNodes().indexOf(stage)).toArray()   // Each option points to a stage node by index in Nodes Index file (ni)
            );
        }
        liDos.close();
        liBaos.close();
        byte[] liBytes = liBaos.toByteArray();
        liFos.write(liBytes);
        liFos.close();


        // Add images index file: ri
        FileOutputStream riFos = new FileOutputStream(new File(packFolder, IMAGE_INDEX_FILENAME));
        ByteArrayOutputStream riBaos = new ByteArrayOutputStream();
        DataOutputStream riDos = new DataOutputStream(riBaos);
        // For each image asset: 12-bytes relative path (e.g. 000\11111111)
        for (int i=0; i<imageHashOrdered.size(); i++) {
            // Write image path into ri file
            String imageHash = imageHashOrdered.get(i);
            String rfPath = assetPathFromIndex(i);
            riDos.write(rfPath.getBytes(Charset.forName("UTF-8")));
            // Write image data into file
            File rfFile = new File(packFolder, IMAGE_FOLDER + rfPath.replace('\\', '/'));
            rfFile.getParentFile().mkdirs();
            FileOutputStream rfFos = new FileOutputStream(rfFile);
            byte[] rfBytes = assets.get(imageHash);
            rfFos.write(rfBytes);
            rfFos.close();
        }
        riDos.close();
        riBaos.close();
        byte[] riBytes = riBaos.toByteArray();
        riFos.write(riBytes);
        riFos.close();


        // Add sound index file: si
        FileOutputStream siFos = new FileOutputStream(new File(packFolder, SOUND_INDEX_FILENAME));
        ByteArrayOutputStream siBaos = new ByteArrayOutputStream();
        DataOutputStream siDos = new DataOutputStream(siBaos);
        // For each image asset: 12-bytes relative path (e.g. 000\11111111)
        for (int i=0; i<audioHashOrdered.size(); i++) {
            // Write sound path into si file
            String audioHash = audioHashOrdered.get(i);
            String sfPath = assetPathFromIndex(i);
            siDos.write(sfPath.getBytes(Charset.forName("UTF-8")));
            // Write sound data into file
            File sfFile = new File(packFolder, SOUND_FOLDER + sfPath.replace('\\', '/'));
            sfFile.getParentFile().mkdirs();
            FileOutputStream sfFos = new FileOutputStream(sfFile);
            byte[] sfBytes = assets.get(audioHash);
            sfFos.write(sfBytes);
            sfFos.close();
        }
        siDos.close();
        siBaos.close();
        byte[] siBytes = siBaos.toByteArray();
        siFos.write(siBytes);
        siFos.close();

        return packFolder.toPath();
    }

    private static String transformUuid(UUID uuid) {
        String uuidStr = uuid.toString().replaceAll("-", "");
        return uuidStr.substring(uuidStr.length()-8).toUpperCase();
    }

    private static void writeStageNode(
            DataOutputStream niDos,
            int imageAssetIndexInRI,
            int soundAssetIndexInSI,
            int okTransitionActionNodeIndexInLI,
            int okTransitionNumberOfOptions,
            int okTransitionSelectedOptionIndex,
            int homeTransitionActionNodeIndexInLI,
            int homeTransitionNumberOfOptions,
            int homeTransitionSelectedOptionIndex,
            boolean wheel,
            boolean ok,
            boolean home,
            boolean pause,
            boolean autoplay
    ) throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        bb.putInt(imageAssetIndexInRI);
        bb.putInt(soundAssetIndexInSI);
        bb.putInt(okTransitionActionNodeIndexInLI);
        bb.putInt(okTransitionNumberOfOptions);
        bb.putInt(okTransitionSelectedOptionIndex);
        bb.putInt(homeTransitionActionNodeIndexInLI);
        bb.putInt(homeTransitionNumberOfOptions);
        bb.putInt(homeTransitionSelectedOptionIndex);
        bb.putShort(boolToShort(wheel));
        bb.putShort(boolToShort(ok));
        bb.putShort(boolToShort(home));
        bb.putShort(boolToShort(pause));
        bb.putShort(boolToShort(autoplay));
        bb.putShort((short) 0);

        niDos.write(bb.array());
        bb.clear();
    }

    private static short boolToShort(boolean b) {
        return (short) (b ? 1 : 0);
    }

    private static void writeActionNode(
            DataOutputStream liDos,
            int[] stageNodesIndexes
    ) throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(stageNodesIndexes.length*4).order(ByteOrder.LITTLE_ENDIAN);
        for (int stageNodeIndex : stageNodesIndexes) {
            bb.putInt(stageNodeIndex);
        }
        liDos.write(bb.array());
        bb.clear();
    }

    private static String assetPathFromIndex(int index) {
        return String.format("000\\%08d", index);
    }

}
