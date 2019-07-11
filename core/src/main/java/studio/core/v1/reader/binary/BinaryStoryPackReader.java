/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.reader.binary;

import studio.core.v1.Constants;
import studio.core.v1.model.*;
import studio.core.v1.model.metadata.StoryPackMetadata;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class BinaryStoryPackReader {

    public StoryPackMetadata readMetadata(InputStream inputStream) throws IOException {
        DataInputStream dis = new DataInputStream(inputStream);

        // Pack metadata model
        StoryPackMetadata metadata = new StoryPackMetadata(Constants.PACK_FORMAT_BINARY);

        // Read sector 1
        dis.skipBytes(3);   // Skip to version
        metadata.setVersion(dis.readShort());
        dis.skipBytes(Constants.SECTOR_SIZE - 5); // Skip to end of sector

        // Read main stage node
        long uuidLowBytes = dis.readLong();
        long uuidHighBytes = dis.readLong();
        String uuid = (new UUID(uuidLowBytes, uuidHighBytes)).toString();
        metadata.setUuid(uuid);

        dis.close();

        return metadata;
    }

    public StoryPack read(InputStream inputStream) throws IOException {
        DataInputStream dis = new DataInputStream(inputStream);

        // Read sector 1
        short stages = dis.readShort();
        boolean factoryDisabled = dis.readByte() == 1;
        short version = dis.readShort();
        dis.skipBytes(Constants.SECTOR_SIZE - 5); // Skip to end of sector

        // Read stage nodes (`stages` sectors, starting from sector 2)
        TreeMap<SectorAddr, StageNode> stageNodes = new TreeMap<>();
        TreeMap<AssetAddr, List<StageNode>> stagesWithImage = new TreeMap<>();         // StageNodes must be updated with the actual ImageAsset
        TreeMap<AssetAddr, List<StageNode>> stagesWithAudio = new TreeMap<>();         // StageNodes must be updated with the actual AudioAsset
        TreeMap<SectorAddr, List<Transition>> transitionsWithAction = new TreeMap<>(); // Transitions must be updated with the actual ActionNode
        Set<SectorAddr> actionNodesToVisit = new TreeSet<>();   // Stage nodes / transitions reference action nodes, which are read after all stage nodes
        Set<AssetAddr> assetAddrsToVisit = new TreeSet<>();     // Stage nodes reference assets, which are read after all nodes
        for (int i = 0; i < stages; i++) {
            // Reading sector i+2

            // UUID
            long uuidLowBytes = dis.readLong();
            long uuidHighBytes = dis.readLong();
            String uuid = (new UUID(uuidLowBytes, uuidHighBytes)).toString();

            // Image asset
            int imageOffset = dis.readInt();
            int imageSize = dis.readInt();
            AssetAddr imageAssetAddr = null;
            if (imageOffset != -1) {
                // Asset must be visited
                imageAssetAddr = new AssetAddr(imageOffset, imageSize, AssetType.IMAGE);
                assetAddrsToVisit.add(imageAssetAddr);
            }

            // Audio asset
            int audioOffset = dis.readInt();
            int audioSize = dis.readInt();
            AssetAddr audioAssetAddr = null;
            if (audioOffset != -1) {
                // Asset must be visited
                audioAssetAddr = new AssetAddr(audioOffset, audioSize, AssetType.AUDIO);
                assetAddrsToVisit.add(audioAssetAddr);
            }

            // Transitions
            short okTransitionOffset = dis.readShort();
            short okTransitionCount = dis.readShort();
            short okTransitionIndex = dis.readShort();
            SectorAddr okActionNodeAddr = null;
            if (okTransitionOffset != -1) {
                // Action node must be visited
                okActionNodeAddr = new SectorAddr(okTransitionOffset);
                actionNodesToVisit.add(okActionNodeAddr);
            }
            short homeTransitionOffset = dis.readShort();
            short homeTransitionCount = dis.readShort();
            short homeTransitionIndex = dis.readShort();
            SectorAddr homeActionNodeAddr = null;
            if (homeTransitionOffset != -1) {
                // Action node must be visited
                homeActionNodeAddr = new SectorAddr(homeTransitionOffset);
                actionNodesToVisit.add(homeActionNodeAddr);
            }

            // Control settings
            boolean wheelEnabled = dis.readShort() == 1;
            boolean okEnabled = dis.readShort() == 1;
            boolean homeEnabled = dis.readShort() == 1;
            boolean pauseEnabled = dis.readShort() == 1;
            boolean autoJumpEnabled = dis.readShort() == 1;

            // Build stage node
            SectorAddr address = new SectorAddr(i);
            Transition okTransition = okActionNodeAddr != null ? new Transition(null, okTransitionIndex) : null;
            Transition homeTransition = homeActionNodeAddr != null ? new Transition(null, homeTransitionIndex) : null;
            StageNode stageNode = new StageNode(
                    uuid,
                    null,
                    null,
                    okTransition,
                    homeTransition,
                    new ControlSettings(wheelEnabled, okEnabled, homeEnabled, pauseEnabled, autoJumpEnabled)
            );
            stageNodes.put(address, stageNode);

            // Assets will be updated when they are read
            if (imageAssetAddr != null) {
                List<StageNode> swi = stagesWithImage.getOrDefault(imageAssetAddr, new ArrayList<>());
                swi.add(stageNode);
                stagesWithImage.put(imageAssetAddr, swi);
            }
            if (audioAssetAddr != null) {
                List<StageNode> swa = stagesWithAudio.getOrDefault(audioAssetAddr, new ArrayList<>());
                swa.add(stageNode);
                stagesWithAudio.put(audioAssetAddr, swa);
            }
            // Action nodes will be updated when they are read
            if (okActionNodeAddr != null) {
                List<Transition> twa = transitionsWithAction.getOrDefault(okActionNodeAddr, new ArrayList<>());
                twa.add(okTransition);
                transitionsWithAction.put(okActionNodeAddr, twa);
            }
            if (homeActionNodeAddr != null) {
                List<Transition> twa = transitionsWithAction.getOrDefault(homeActionNodeAddr, new ArrayList<>());
                twa.add(homeTransition);
                transitionsWithAction.put(homeActionNodeAddr, twa);
            }

            // Skip to end of sector
            dis.skipBytes(Constants.SECTOR_SIZE - 54);
        }

        // Read action nodes
        // We are positioned at the end of sector stages+1
        int currentOffset = stages;
        Iterator<SectorAddr> actionNodesIter = actionNodesToVisit.iterator();
        while (actionNodesIter.hasNext()) {
            // Sector to read
            SectorAddr actionNodeAddr = actionNodesIter.next();
            // Skip to the beginning of the sector, if needed
            while (actionNodeAddr.getOffset() > currentOffset) {
                dis.skipBytes(Constants.SECTOR_SIZE);
                currentOffset++;
            }

            List<StageNode> options = new ArrayList<>();
            short optionAddr = dis.readShort();
            while (optionAddr != 0) {
                options.add(stageNodes.get(new SectorAddr(optionAddr)));
                optionAddr = dis.readShort();
            }

            // Update action on transitions referencing this sector
            ActionNode actionNode = new ActionNode(options);
            transitionsWithAction.get(actionNodeAddr).forEach(transition -> transition.setActionNode(actionNode));

            // Skip to end of sector
            dis.skipBytes(Constants.SECTOR_SIZE - (2*(options.size()+1)));
            currentOffset++;
        }

        // Read assets
        Iterator<AssetAddr> assetAddrsIter = assetAddrsToVisit.iterator();
        while (assetAddrsIter.hasNext()) {
            // First sector to read
            AssetAddr assetAddr = assetAddrsIter.next();
            // Skip to the beginning of the sector, if needed
            while (assetAddr.getOffset() > currentOffset) {
                dis.skipBytes(Constants.SECTOR_SIZE);
                currentOffset++;
            }
            // Read all bytes
            byte[] assetBytes = new byte[Constants.SECTOR_SIZE * assetAddr.getSize()];
            dis.read(assetBytes, 0, assetBytes.length);

            // Update asset on stage nodes referencing this sector
            switch (assetAddr.getType()) {
                case AUDIO:
                    AudioAsset audioAsset = new AudioAsset("audio/x-wav", assetBytes);
                    stagesWithAudio.get(assetAddr).forEach(stageNode -> stageNode.setAudio(audioAsset));
                    break;
                case IMAGE:
                    ImageAsset imageAsset = new ImageAsset("image/bmp", assetBytes);
                    stagesWithImage.get(assetAddr).forEach(stageNode -> stageNode.setImage(imageAsset));
                    break;
            }

            currentOffset += assetAddr.getSize();
        }

        dis.close();

        return new StoryPack(factoryDisabled, version, List.copyOf(stageNodes.values()));
    }
}
