/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.service.fs;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import studio.core.v1.model.ActionNode;
import studio.core.v1.model.ControlSettings;
import studio.core.v1.model.StageNode;
import studio.core.v1.model.StoryPack;
import studio.core.v1.model.Transition;
import studio.core.v1.model.asset.MediaAsset;
import studio.core.v1.model.asset.MediaAssetType;
import studio.core.v1.model.metadata.StoryPackMetadata;
import studio.core.v1.service.PackFormat;
import studio.core.v1.service.StoryPackReader;
import studio.core.v1.service.fs.FsStoryPackDTO.FsStoryPack;
import studio.core.v1.utils.security.XXTEACipher;
import studio.core.v1.utils.security.XXTEACipher.CipherMode;

public class FsStoryPackReader implements StoryPackReader {

    private static final Logger LOGGER = LogManager.getLogger(FsStoryPackReader.class);

    public StoryPackMetadata readMetadata(Path inputFolder) throws IOException {
        // Pack metadata model
        StoryPackMetadata metadata = new StoryPackMetadata(PackFormat.FS);
        FsStoryPack fsp = new FsStoryPack(inputFolder);
        // Open 'ni' file
        try (InputStream niDis = new BufferedInputStream(Files.newInputStream(fsp.getNodeIndex()))) {
            ByteBuffer bb = ByteBuffer.wrap(niDis.readNBytes(512)).order(ByteOrder.LITTLE_ENDIAN);
            metadata.setVersion(bb.getShort(2));
        }
        // Get uuid from folder name
        metadata.setUuid(fsp.getUuid());
        // Night mode is available if file 'nm' exists
        metadata.setNightModeAvailable(fsp.isNightModeAvailable());
        return metadata;
    }

    public StoryPack read(Path inputFolder) throws IOException {
        // Create storypack
        StoryPack sp = new StoryPack();
        sp.setEnriched(null);
        sp.setStageNodes(new ArrayList<>());

        FsStoryPack fsp = new FsStoryPack(inputFolder);
        // Keep action nodes' options count
        Map<Integer, Integer> actionOptions = new TreeMap<>();
        // Transitions must be updated with the actual ActionNode
        Map<Integer, List<Transition>> transitionsWithAction = new TreeMap<>();

        // Get uuid from folder name
        sp.setUuid(fsp.getUuid());
        // Night mode is available if file 'nm' exists
        sp.setNightModeAvailable(fsp.isNightModeAvailable());

        // Load ri, si and li files
        byte[] riContent = readCipheredFile(fsp.getImageIndex());
        byte[] siContent = readCipheredFile(fsp.getSoundIndex());
        byte[] liContent = readCipheredFile(fsp.getListIndex());

        // Open 'ni' file
        try (InputStream niDis = new BufferedInputStream(Files.newInputStream(fsp.getNodeIndex()))) {
            ByteBuffer bb = ByteBuffer.wrap(niDis.readNBytes(512)).order(ByteOrder.LITTLE_ENDIAN);
            // Nodes index file format version (1)
            bb.getShort();
            // Story pack version
            sp.setVersion(bb.getShort());
            // Start of actual nodes list in this file (0x200 / 512)
            int nodesList = bb.getInt();
            // Size of a stage node in this file (0x2C / 44)
            int nodeSize = bb.getInt();
            // Number of stage nodes in this file
            int stageNodesCount = bb.getInt();
            // Number of images (in RI file and rf/ folder)
            int imageAssetsCount = bb.getInt();
            // Number of sounds (in SI file and sf/ folder)
            int soundAssetsCount = bb.getInt();
            LOGGER.trace("NodeList start : {}, containing {} images and {} audio.", nodesList, imageAssetsCount,
                    soundAssetsCount);
            // if true, avoid pack inspection by official Luniistore application
            sp.setFactoryDisabled(bb.get() != 0x00);

            // Read stage nodes
            for (int i = 0; i < stageNodesCount; i++) {
                bb = ByteBuffer.wrap(niDis.readNBytes(nodeSize)).order(ByteOrder.LITTLE_ENDIAN);

                // Read image and audio assets
                MediaAsset image = readAsset(fsp.getImageFolder(), MediaAssetType.BMP, riContent, bb.getInt());
                MediaAsset audio = readAsset(fsp.getSoundFolder(), MediaAssetType.MP3, siContent, bb.getInt());

                // Transition will be updated later with the actual action nodes
                Transition okTransition = readTransition(bb.getInt(), bb.getInt(), bb.getInt(), actionOptions,
                        transitionsWithAction);
                Transition homeTransition = readTransition(bb.getInt(), bb.getInt(), bb.getInt(), actionOptions,
                        transitionsWithAction);

                ControlSettings ctrl = new ControlSettings();
                ctrl.setWheelEnabled(bb.getShort() != 0);
                ctrl.setOkEnabled(bb.getShort() != 0);
                ctrl.setHomeEnabled(bb.getShort() != 0);
                ctrl.setPauseEnabled(bb.getShort() != 0);
                ctrl.setAutoJumpEnabled(bb.getShort() != 0);

                // First node should have the same UUID as the story pack
                // TODO node uuids from metadata file
                String id = (i == 0) ? sp.getUuid() : UUID.randomUUID().toString();
                StageNode stageNode = new StageNode(id, image, audio, okTransition, homeTransition, ctrl, null);
                sp.getStageNodes().add(stageNode);
            }
        }

        // Read action nodes from 'li' file
        ByteBuffer liBb = ByteBuffer.wrap(liContent).order(ByteOrder.LITTLE_ENDIAN);
        for (Map.Entry<Integer, Integer> actionCount : actionOptions.entrySet()) {
            Integer offset = actionCount.getKey();
            Integer count = actionCount.getValue();
            List<StageNode> options = new ArrayList<>(count);
            liBb.position(offset * 4); // Each entry takes 4 bytes
            for (int i = 0; i < count; i++) {
                int stageNodeIndex = liBb.getInt();
                options.add(sp.getStageNodes().get(stageNodeIndex));
            }
            // Update action on transitions referencing this sector
            String id = UUID.randomUUID().toString();
            ActionNode actionNode = new ActionNode(id, null, options);
            transitionsWithAction.get(offset).forEach(transition -> transition.setActionNode(actionNode));
        }

        // cleanup
        Stream.of(actionOptions, transitionsWithAction).forEach(Map::clear);

        return sp;
    }

    private static byte[] readCipheredFile(Path path) throws IOException {
        byte[] content = Files.readAllBytes(path);
        return XXTEACipher.cipherCommonKey(CipherMode.DECIPHER, content);
    }

    private static Transition readTransition(int actionNodeIndexInLI, int numberOfOptions, int selectedOptionIndex,
            Map<Integer, Integer> actionOptions, Map<Integer, List<Transition>> transitionsWithAction) {
        Transition t = null;
        if (actionNodeIndexInLI != -1 && numberOfOptions != -1 && selectedOptionIndex != -1) {
            actionOptions.putIfAbsent(actionNodeIndexInLI, numberOfOptions);
            t = new Transition(null, (short) selectedOptionIndex);
            List<Transition> twa = transitionsWithAction.getOrDefault(actionNodeIndexInLI, new ArrayList<>());
            twa.add(t);
            transitionsWithAction.put(actionNodeIndexInLI, twa);
        }
        return t;
    }

    private static MediaAsset readAsset(Path assetFolder, MediaAssetType assetType, byte[] indexList, int index)
            throws IOException {
        if (index == -1) {
            return null;
        }
        // Read asset name, each entry takes 12 bytes
        String assetName = new String(indexList, index * 12, 12, StandardCharsets.UTF_8).replace("\\", "/");
        // Read asset file
        return new MediaAsset(assetType, readCipheredFile(assetFolder.resolve(assetName)));
    }
}
