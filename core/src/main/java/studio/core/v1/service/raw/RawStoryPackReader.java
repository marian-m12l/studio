/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.service.raw;

import static studio.core.v1.service.raw.RawStoryPackDTO.BINARY_ENRICHED_METADATA_ACTION_NODE_ALIGNMENT;
import static studio.core.v1.service.raw.RawStoryPackDTO.BINARY_ENRICHED_METADATA_ACTION_NODE_ALIGNMENT_PADDING;
import static studio.core.v1.service.raw.RawStoryPackDTO.BINARY_ENRICHED_METADATA_DESCRIPTION_TRUNCATE;
import static studio.core.v1.service.raw.RawStoryPackDTO.BINARY_ENRICHED_METADATA_NODE_NAME_TRUNCATE;
import static studio.core.v1.service.raw.RawStoryPackDTO.BINARY_ENRICHED_METADATA_SECTOR_1_ALIGNMENT_PADDING;
import static studio.core.v1.service.raw.RawStoryPackDTO.BINARY_ENRICHED_METADATA_STAGE_NODE_ALIGNMENT_PADDING;
import static studio.core.v1.service.raw.RawStoryPackDTO.BINARY_ENRICHED_METADATA_TITLE_TRUNCATE;
import static studio.core.v1.service.raw.RawStoryPackDTO.SECTOR_SIZE;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
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
import studio.core.v1.model.enriched.EnrichedNodeMetadata;
import studio.core.v1.model.enriched.EnrichedNodePosition;
import studio.core.v1.model.enriched.EnrichedNodeType;
import studio.core.v1.model.enriched.EnrichedPackMetadata;
import studio.core.v1.model.metadata.StoryPackMetadata;
import studio.core.v1.service.PackFormat;
import studio.core.v1.service.StoryPackReader;
import studio.core.v1.service.raw.RawStoryPackDTO.AssetAddr;
import studio.core.v1.service.raw.RawStoryPackDTO.AssetType;
import studio.core.v1.service.raw.RawStoryPackDTO.SectorAddr;

public class RawStoryPackReader implements StoryPackReader {

    private static final Logger LOGGER = LogManager.getLogger(RawStoryPackReader.class);

    public StoryPackMetadata readMetadata(Path path) throws IOException {
        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            // Pack metadata model
            StoryPackMetadata metadata = new StoryPackMetadata(PackFormat.RAW);

            // Read sector 1
            dis.skipBytes(3); // Skip to version
            metadata.setVersion(dis.readShort());

            // Read (optional) enriched pack metadata
            dis.skipBytes(BINARY_ENRICHED_METADATA_SECTOR_1_ALIGNMENT_PADDING);
            Optional<String> maybeTitle = readString(dis, BINARY_ENRICHED_METADATA_TITLE_TRUNCATE);
            metadata.setTitle(maybeTitle.orElse(null));
            Optional<String> maybeDescription = readString(dis, BINARY_ENRICHED_METADATA_DESCRIPTION_TRUNCATE);
            metadata.setDescription(maybeDescription.orElse(null));
            // TODO Thumbnail?

            // Skip to end of sector
            dis.skipBytes(SECTOR_SIZE - 5 //
                    - BINARY_ENRICHED_METADATA_SECTOR_1_ALIGNMENT_PADDING //
                    - BINARY_ENRICHED_METADATA_TITLE_TRUNCATE * 2 //
                    - BINARY_ENRICHED_METADATA_DESCRIPTION_TRUNCATE * 2);

            // Read main stage node UUID
            long uuidLowBytes = dis.readLong();
            long uuidHighBytes = dis.readLong();
            metadata.setUuid(new UUID(uuidLowBytes, uuidHighBytes).toString());
            return metadata;
        }
    }

    public StoryPack read(Path path) throws IOException {
        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            StoryPack sp = new StoryPack();

            // Read sector 1 : stageNode number
            short stages = dis.readShort();
            sp.setFactoryDisabled(dis.readByte() == 1);
            sp.setVersion(dis.readShort());

            // Read (optional) enriched pack metadata
            dis.skipBytes(BINARY_ENRICHED_METADATA_SECTOR_1_ALIGNMENT_PADDING);
            Optional<String> maybeTitle = readString(dis, BINARY_ENRICHED_METADATA_TITLE_TRUNCATE);
            Optional<String> maybeDescription = readString(dis, BINARY_ENRICHED_METADATA_DESCRIPTION_TRUNCATE);
            // TODO Thumbnail?
            maybeTitle.or(() -> maybeDescription).ifPresent(s -> sp.setEnriched( //
                    new EnrichedPackMetadata(maybeTitle.orElse(null), maybeDescription.orElse(null))));

            // Skip to end of sector
            dis.skipBytes(SECTOR_SIZE - 5 //
                    - BINARY_ENRICHED_METADATA_SECTOR_1_ALIGNMENT_PADDING //
                    - BINARY_ENRICHED_METADATA_TITLE_TRUNCATE * 2 //
                    - BINARY_ENRICHED_METADATA_DESCRIPTION_TRUNCATE * 2);

            // Read stage nodes (`stages` sectors, starting from sector 2)
            Map<SectorAddr, StageNode> stageNodes = new TreeMap<>();
            // StageNodes must be updated with the actual ImageAsset
            Map<AssetAddr, List<StageNode>> stagesWithImage = new TreeMap<>();
            // StageNodes must be updated with the actual AudioAsset
            Map<AssetAddr, List<StageNode>> stagesWithAudio = new TreeMap<>();
            // Transitions must be updated with the actual ActionNode
            Map<SectorAddr, List<Transition>> transitionsWithAction = new TreeMap<>();
            // Stage nodes / transitions reference action nodes, which are read after
            Set<SectorAddr> actionNodesToVisit = new TreeSet<>();
            // Stage nodes reference assets, which are read after all nodes
            Set<AssetAddr> assetAddrsToVisit = new TreeSet<>();

            for (int i = 0; i < stages; i++) {
                // Build stage node
                StageNode stageNode = new StageNode();
                stageNodes.put(new SectorAddr(i), stageNode);

                // Reading sector i+2 : StageNode UUID
                long uuidLowBytes = dis.readLong();
                long uuidHighBytes = dis.readLong();
                stageNode.setUuid(new UUID(uuidLowBytes, uuidHighBytes).toString());

                // Keep Asset addresses
                readAssetAddr(AssetType.IMAGE, dis.readInt(), dis.readInt(), assetAddrsToVisit, stagesWithImage,
                        stageNode);
                readAssetAddr(AssetType.AUDIO, dis.readInt(), dis.readInt(), assetAddrsToVisit, stagesWithAudio,
                        stageNode);

                // Transitions
                stageNode.setOkTransition(readTransition(dis.readShort(), dis.readShort(), dis.readShort(),
                        transitionsWithAction, actionNodesToVisit));
                stageNode.setHomeTransition(readTransition(dis.readShort(), dis.readShort(), dis.readShort(),
                        transitionsWithAction, actionNodesToVisit));

                // Control settings
                ControlSettings ctrl = new ControlSettings();
                ctrl.setWheelEnabled(dis.readShort() == 1);
                ctrl.setOkEnabled(dis.readShort() == 1);
                ctrl.setHomeEnabled(dis.readShort() == 1);
                ctrl.setPauseEnabled(dis.readShort() == 1);
                ctrl.setAutoJumpEnabled(dis.readShort() == 1);
                stageNode.setControlSettings(ctrl);

                // Read (optional) enriched node metadata
                dis.skipBytes(BINARY_ENRICHED_METADATA_STAGE_NODE_ALIGNMENT_PADDING);
                stageNode.setEnriched(readEnrichedNodeMetadata(dis));

                // Skip to end of sector
                dis.skipBytes(SECTOR_SIZE - 54 - BINARY_ENRICHED_METADATA_STAGE_NODE_ALIGNMENT_PADDING
                        - BINARY_ENRICHED_METADATA_NODE_NAME_TRUNCATE * 2 - 16 - 1 - 4);
            }

            // Read action nodes
            // We are positioned at the end of sector stages+1
            int currentOffset = stages;
            for (SectorAddr actionNodeAddr : actionNodesToVisit) {
                // Skip to the beginning of the sector, if needed
                while (actionNodeAddr.getOffset() > currentOffset) {
                    dis.skipBytes(SECTOR_SIZE);
                    currentOffset++;
                }

                List<StageNode> options = new ArrayList<>();
                short optionAddr = dis.readShort();
                while (optionAddr != 0) {
                    options.add(stageNodes.get(new SectorAddr(optionAddr)));
                    optionAddr = dis.readShort();
                }

                // Read (optional) enriched node metadata
                int alignmentOverflow = 2 * options.size() % BINARY_ENRICHED_METADATA_ACTION_NODE_ALIGNMENT;
                int alignmentPadding = BINARY_ENRICHED_METADATA_ACTION_NODE_ALIGNMENT_PADDING
                        + (alignmentOverflow > 0 ? BINARY_ENRICHED_METADATA_ACTION_NODE_ALIGNMENT - alignmentOverflow
                                : 0);
                // No need to skip the last 2 bytes that were read in the previous loop
                dis.skipBytes(alignmentPadding - 2);
                EnrichedNodeMetadata enrichedNodeMetadata = readEnrichedNodeMetadata(dis);

                // Update action on transitions referencing this sector
                String id = UUID.randomUUID().toString();
                ActionNode actionNode = new ActionNode(id, enrichedNodeMetadata, options);
                transitionsWithAction.get(actionNodeAddr).forEach(transition -> transition.setActionNode(actionNode));

                // Skip to end of sector
                dis.skipBytes(SECTOR_SIZE - (2 * (options.size() + 1)) - (alignmentPadding - 2)
                        - BINARY_ENRICHED_METADATA_NODE_NAME_TRUNCATE * 2 - 16 - 1 - 4);
                currentOffset++;
            }

            // Read assets
            for (AssetAddr assetAddr : assetAddrsToVisit) {
                // Skip to the beginning of the sector, if needed
                while (assetAddr.getOffset() > currentOffset) {
                    dis.skipBytes(SECTOR_SIZE);
                    currentOffset++;
                }
                // Read all bytes
                byte[] assetBytes = new byte[SECTOR_SIZE * assetAddr.getSize()];
                dis.read(assetBytes, 0, assetBytes.length);

                // Update asset on stage nodes referencing this sector
                if (assetAddr.getType() == AssetType.AUDIO) {
                    MediaAsset audioAsset = new MediaAsset(MediaAssetType.WAV, assetBytes);
                    stagesWithAudio.get(assetAddr).forEach(stageNode -> stageNode.setAudio(audioAsset));
                }
                if (assetAddr.getType() == AssetType.IMAGE) {
                    MediaAsset imageAsset = new MediaAsset(MediaAssetType.BMP, assetBytes);
                    stagesWithImage.get(assetAddr).forEach(stageNode -> stageNode.setImage(imageAsset));
                }
                currentOffset += assetAddr.getSize();
            }
            // Update storypack
            sp.setNightModeAvailable(false);
            sp.setUuid(stageNodes.get(new SectorAddr(0)).getUuid());
            sp.setStageNodes(List.copyOf(stageNodes.values()));

            // cleanup
            Stream.of(stageNodes, stagesWithImage, stagesWithAudio, transitionsWithAction).forEach(Map::clear);
            Stream.of(actionNodesToVisit, assetAddrsToVisit).forEach(Set::clear);
            return sp;
        }
    }

    /** Read UTF16 String from stream. */
    private static Optional<String> readString(InputStream dis, int maxChars) throws IOException {
        byte[] bytes = dis.readNBytes(maxChars * 2);
        String str = new String(bytes, StandardCharsets.UTF_16);
        int firstNullChar = str.indexOf("\u0000");
        if (firstNullChar == 0) {
            return Optional.empty();
        }
        if (firstNullChar == -1) {
            return Optional.of(str);
        }
        return Optional.of(str.substring(0, firstNullChar));
    }

    private static void readAssetAddr(AssetType type, int offset, int size, Set<AssetAddr> assetAddrsToVisit,
            Map<AssetAddr, List<StageNode>> stagesWithMedia, StageNode stageNode) {
        if (offset != -1) {
            // Asset must be visited
            AssetAddr audioAssetAddr = new AssetAddr(type, offset, size);
            assetAddrsToVisit.add(audioAssetAddr);
            // flag stageNode
            List<StageNode> sw = stagesWithMedia.getOrDefault(audioAssetAddr, new ArrayList<>());
            sw.add(stageNode);
            stagesWithMedia.put(audioAssetAddr, sw);
        }
    }

    private static Transition readTransition(short offset, short count, short index,
            Map<SectorAddr, List<Transition>> transitionsWithAction, Set<SectorAddr> actionNodesToVisit) {
        LOGGER.trace("Read {} transition", count);
        Transition transition = null;
        if (offset != -1) {
            // Action node must be visited
            SectorAddr actionNodeAddr = new SectorAddr(offset);
            actionNodesToVisit.add(actionNodeAddr);
            transition = new Transition(null, index);
            List<Transition> twa = transitionsWithAction.getOrDefault(actionNodeAddr, new ArrayList<>());
            twa.add(transition);
            transitionsWithAction.put(actionNodeAddr, twa);
        }
        return transition;
    }

    private static EnrichedNodeMetadata readEnrichedNodeMetadata(DataInputStream dis) throws IOException {
        Optional<String> maybeName = readString(dis, BINARY_ENRICHED_METADATA_NODE_NAME_TRUNCATE);
        Optional<String> maybeGroupId = Optional.empty();
        long groupIdLowBytes = dis.readLong();
        long groupIdHighBytes = dis.readLong();
        if (groupIdLowBytes != 0 || groupIdHighBytes != 0) {
            maybeGroupId = Optional.of(new UUID(groupIdLowBytes, groupIdHighBytes).toString());
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
            return new EnrichedNodeMetadata(maybeName.orElse(null), maybeType.orElse(null), maybeGroupId.orElse(null),
                    maybePosition.orElse(null));
        }
        return null;
    }
}
