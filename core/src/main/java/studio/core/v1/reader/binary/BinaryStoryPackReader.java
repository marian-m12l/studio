/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.reader.binary;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

import studio.core.v1.Constants;
import studio.core.v1.model.ActionNode;
import studio.core.v1.model.AssetType;
import studio.core.v1.model.AudioAsset;
import studio.core.v1.model.ControlSettings;
import studio.core.v1.model.ImageAsset;
import studio.core.v1.model.StageNode;
import studio.core.v1.model.StoryPack;
import studio.core.v1.model.Transition;
import studio.core.v1.model.enriched.EnrichedNodeMetadata;
import studio.core.v1.model.enriched.EnrichedNodePosition;
import studio.core.v1.model.enriched.EnrichedNodeType;
import studio.core.v1.model.enriched.EnrichedPackMetadata;
import studio.core.v1.model.metadata.StoryPackMetadata;
import studio.core.v1.model.mime.AudioType;
import studio.core.v1.model.mime.ImageType;

public class BinaryStoryPackReader {

    public StoryPackMetadata readMetadata(InputStream inputStream) throws IOException {
        try(DataInputStream dis = new DataInputStream(inputStream)){
            // Pack metadata model
            StoryPackMetadata metadata = new StoryPackMetadata(Constants.PACK_FORMAT_RAW);

            // Read sector 1
            dis.skipBytes(3);   // Skip to version
            metadata.setVersion(dis.readShort());

            // Read (optional) enriched pack metadata
            dis.skipBytes(Constants.BINARY_ENRICHED_METADATA_SECTOR_1_ALIGNMENT_PADDING);
            Optional<String> maybeTitle = readString(dis, Constants.BINARY_ENRICHED_METADATA_TITLE_TRUNCATE);
            metadata.setTitle(maybeTitle.orElse(null));
            Optional<String> maybeDescription = readString(dis, Constants.BINARY_ENRICHED_METADATA_DESCRIPTION_TRUNCATE);
            metadata.setDescription(maybeDescription.orElse(null));
            // TODO Thumbnail?

            dis.skipBytes(Constants.SECTOR_SIZE - 5
                    - Constants.BINARY_ENRICHED_METADATA_SECTOR_1_ALIGNMENT_PADDING
                    - Constants.BINARY_ENRICHED_METADATA_TITLE_TRUNCATE*2
                    - Constants.BINARY_ENRICHED_METADATA_DESCRIPTION_TRUNCATE*2); // Skip to end of sector

            // Read main stage node
            long uuidLowBytes = dis.readLong();
            long uuidHighBytes = dis.readLong();
            String uuid = (new UUID(uuidLowBytes, uuidHighBytes)).toString();
            metadata.setUuid(uuid);
            return metadata;
        }
    }

    public StoryPack read(InputStream inputStream) throws IOException {
        try(DataInputStream dis = new DataInputStream(inputStream)) {

            // Read sector 1
            short stages = dis.readShort();
            boolean factoryDisabled = dis.readByte() == 1;
            short version = dis.readShort();

            // Read (optional) enriched pack metadata
            EnrichedPackMetadata enrichedPack = null;
            dis.skipBytes(Constants.BINARY_ENRICHED_METADATA_SECTOR_1_ALIGNMENT_PADDING);
            Optional<String> maybeTitle = readString(dis, Constants.BINARY_ENRICHED_METADATA_TITLE_TRUNCATE);
            Optional<String> maybeDescription = readString(dis, Constants.BINARY_ENRICHED_METADATA_DESCRIPTION_TRUNCATE);
            // TODO Thumbnail?
            if (maybeTitle.isPresent() || maybeDescription.isPresent()) {
                enrichedPack = new EnrichedPackMetadata(maybeTitle.orElse(null), maybeDescription.orElse(null));
            }

            dis.skipBytes(Constants.SECTOR_SIZE - 5
                    - Constants.BINARY_ENRICHED_METADATA_SECTOR_1_ALIGNMENT_PADDING
                    - Constants.BINARY_ENRICHED_METADATA_TITLE_TRUNCATE*2
                    - Constants.BINARY_ENRICHED_METADATA_DESCRIPTION_TRUNCATE*2); // Skip to end of sector

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

                // Read (optional) enriched node metadata
                dis.skipBytes(Constants.BINARY_ENRICHED_METADATA_STAGE_NODE_ALIGNMENT_PADDING);
                EnrichedNodeMetadata enrichedNodeMetadata = readEnrichedNodeMetadata(dis);

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
                        new ControlSettings(wheelEnabled, okEnabled, homeEnabled, pauseEnabled, autoJumpEnabled),
                        enrichedNodeMetadata
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
                dis.skipBytes(Constants.SECTOR_SIZE - 54
                        - Constants.BINARY_ENRICHED_METADATA_STAGE_NODE_ALIGNMENT_PADDING
                        - Constants.BINARY_ENRICHED_METADATA_NODE_NAME_TRUNCATE*2 - 16 - 1 - 4);
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

                // Read (optional) enriched node metadata
                int alignmentOverflow = 2*(options.size()) % Constants.BINARY_ENRICHED_METADATA_ACTION_NODE_ALIGNMENT;
                int alignmentPadding = Constants.BINARY_ENRICHED_METADATA_ACTION_NODE_ALIGNMENT_PADDING + (alignmentOverflow > 0 ? Constants.BINARY_ENRICHED_METADATA_ACTION_NODE_ALIGNMENT - alignmentOverflow : 0);
                dis.skipBytes(alignmentPadding - 2);    // No need to skip the last 2 bytes that were read in the previous loop
                EnrichedNodeMetadata enrichedNodeMetadata = readEnrichedNodeMetadata(dis);

                // Update action on transitions referencing this sector
                ActionNode actionNode = new ActionNode(options, enrichedNodeMetadata);
                transitionsWithAction.get(actionNodeAddr).forEach(transition -> transition.setActionNode(actionNode));

                // Skip to end of sector
                dis.skipBytes(Constants.SECTOR_SIZE - (2*(options.size()+1))
                        - (alignmentPadding - 2)
                        - Constants.BINARY_ENRICHED_METADATA_NODE_NAME_TRUNCATE*2 - 16 - 1 - 4);
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
                        AudioAsset audioAsset = new AudioAsset(AudioType.WAV.getMime(), assetBytes);
                        stagesWithAudio.get(assetAddr).forEach(stageNode -> stageNode.setAudio(audioAsset));
                        break;
                    case IMAGE:
                        ImageAsset imageAsset = new ImageAsset(ImageType.BMP.getMime(), assetBytes);
                        stagesWithImage.get(assetAddr).forEach(stageNode -> stageNode.setImage(imageAsset));
                        break;
                }

                currentOffset += assetAddr.getSize();
            }
            return new StoryPack(stageNodes.get(new SectorAddr(0)).getUuid(), factoryDisabled, version, List.copyOf(stageNodes.values()), enrichedPack, false);
        }
    }

    private Optional<String> readString(DataInputStream dis, int maxChars) throws IOException {
        byte[] bytes = new byte[maxChars*2];
        dis.read(bytes);
        String str = new String(bytes, StandardCharsets.UTF_16);
        int firstNullChar = str.indexOf("\u0000");
        return firstNullChar == 0
                ? Optional.empty()
                : firstNullChar == -1
                    ? Optional.of(str)
                    : Optional.of(str.substring(0, firstNullChar));
    }

    private EnrichedNodeMetadata readEnrichedNodeMetadata(DataInputStream dis) throws IOException {
        Optional<String> maybeName = readString(dis, Constants.BINARY_ENRICHED_METADATA_NODE_NAME_TRUNCATE);
        Optional<String> maybeGroupId = Optional.empty();
        long groupIdLowBytes = dis.readLong();
        long groupIdHighBytes = dis.readLong();
        if (groupIdLowBytes != 0 || groupIdHighBytes != 0) {
            maybeGroupId = Optional.of((new UUID(groupIdLowBytes, groupIdHighBytes)).toString());
        }
        Optional<EnrichedNodeType> maybeType = Optional.empty();
        byte nodeTypeByte = dis.readByte();
        if (nodeTypeByte != 0x00) {
            maybeType = Optional.ofNullable(EnrichedNodeType.fromCode(nodeTypeByte));
        }
        Optional<EnrichedNodePosition> maybePosition = Optional.empty();
        short positionX = dis.readShort();
        short positionY = dis.readShort();
        if (positionX != 0 || positionY != 0) {
            maybePosition = Optional.of(new EnrichedNodePosition(positionX, positionY));
        }
        if (maybeName.isPresent() || maybeType.isPresent() || maybeGroupId.isPresent() || maybePosition.isPresent()) {
            return new EnrichedNodeMetadata(
                    maybeName.orElse(null),
                    maybeType.orElse(null),
                    maybeGroupId.orElse(null),
                    maybePosition.orElse(null)
            );
        }
        return null;
    }
}
