/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.reader.binary;

import static studio.core.v1.Constants.BINARY_ENRICHED_METADATA_ACTION_NODE_ALIGNMENT;
import static studio.core.v1.Constants.BINARY_ENRICHED_METADATA_ACTION_NODE_ALIGNMENT_PADDING;
import static studio.core.v1.Constants.BINARY_ENRICHED_METADATA_DESCRIPTION_TRUNCATE;
import static studio.core.v1.Constants.BINARY_ENRICHED_METADATA_NODE_NAME_TRUNCATE;
import static studio.core.v1.Constants.BINARY_ENRICHED_METADATA_SECTOR_1_ALIGNMENT_PADDING;
import static studio.core.v1.Constants.BINARY_ENRICHED_METADATA_STAGE_NODE_ALIGNMENT_PADDING;
import static studio.core.v1.Constants.BINARY_ENRICHED_METADATA_TITLE_TRUNCATE;
import static studio.core.v1.Constants.SECTOR_SIZE;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import studio.core.v1.model.ActionNode;
import studio.core.v1.model.ControlSettings;
import studio.core.v1.model.StageNode;
import studio.core.v1.model.StoryPack;
import studio.core.v1.model.Transition;
import studio.core.v1.model.asset.AudioAsset;
import studio.core.v1.model.asset.AudioType;
import studio.core.v1.model.asset.ImageAsset;
import studio.core.v1.model.asset.ImageType;
import studio.core.v1.model.enriched.EnrichedNodeMetadata;
import studio.core.v1.model.enriched.EnrichedNodePosition;
import studio.core.v1.model.enriched.EnrichedNodeType;
import studio.core.v1.model.enriched.EnrichedPackMetadata;
import studio.core.v1.model.metadata.StoryPackMetadata;
import studio.core.v1.reader.StoryPackReader;
import studio.core.v1.reader.binary.AssetAddr.AssetType;
import studio.core.v1.utils.PackFormat;

public class BinaryStoryPackReader implements StoryPackReader {

    private static final Logger LOGGER = LogManager.getLogger(BinaryStoryPackReader.class);

    public StoryPackMetadata readMetadata(Path path) throws IOException {
        try(DataInputStream dis = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))){
            // Pack metadata model
            StoryPackMetadata metadata = new StoryPackMetadata(PackFormat.RAW);

            // Read sector 1
            dis.skipBytes(3);   // Skip to version
            metadata.setVersion(dis.readShort());

            // Read (optional) enriched pack metadata
            dis.skipBytes(BINARY_ENRICHED_METADATA_SECTOR_1_ALIGNMENT_PADDING);
            Optional<String> maybeTitle = readString(dis, BINARY_ENRICHED_METADATA_TITLE_TRUNCATE);
            metadata.setTitle(maybeTitle.orElse(null));
            Optional<String> maybeDescription = readString(dis, BINARY_ENRICHED_METADATA_DESCRIPTION_TRUNCATE);
            metadata.setDescription(maybeDescription.orElse(null));
            // TODO Thumbnail?

            dis.skipBytes(SECTOR_SIZE - 5
                    - BINARY_ENRICHED_METADATA_SECTOR_1_ALIGNMENT_PADDING
                    - BINARY_ENRICHED_METADATA_TITLE_TRUNCATE*2
                    - BINARY_ENRICHED_METADATA_DESCRIPTION_TRUNCATE*2); // Skip to end of sector

            // Read main stage node
            long uuidLowBytes = dis.readLong();
            long uuidHighBytes = dis.readLong();
            String uuid = (new UUID(uuidLowBytes, uuidHighBytes)).toString();
            metadata.setUuid(uuid);
            return metadata;
        }
    }

    public StoryPack read(Path path) throws IOException {
        try(DataInputStream dis = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {

            // Read sector 1
            short stages = dis.readShort();
            boolean factoryDisabled = dis.readByte() == 1;
            short version = dis.readShort();

            // Read (optional) enriched pack metadata
            EnrichedPackMetadata enrichedPack = null;
            dis.skipBytes(BINARY_ENRICHED_METADATA_SECTOR_1_ALIGNMENT_PADDING);
            Optional<String> maybeTitle = readString(dis, BINARY_ENRICHED_METADATA_TITLE_TRUNCATE);
            Optional<String> maybeDescription = readString(dis, BINARY_ENRICHED_METADATA_DESCRIPTION_TRUNCATE);
            // TODO Thumbnail?
            if (maybeTitle.or(() -> maybeDescription).isPresent() ) {
                enrichedPack = new EnrichedPackMetadata(maybeTitle.orElse(null), maybeDescription.orElse(null));
            }

            dis.skipBytes(SECTOR_SIZE - 5
                    - BINARY_ENRICHED_METADATA_SECTOR_1_ALIGNMENT_PADDING
                    - BINARY_ENRICHED_METADATA_TITLE_TRUNCATE*2
                    - BINARY_ENRICHED_METADATA_DESCRIPTION_TRUNCATE*2); // Skip to end of sector

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
                    imageAssetAddr = new AssetAddr(AssetType.IMAGE, imageOffset, imageSize);
                    assetAddrsToVisit.add(imageAssetAddr);
                }

                // Audio asset
                int audioOffset = dis.readInt();
                int audioSize = dis.readInt();
                AssetAddr audioAssetAddr = null;
                if (audioOffset != -1) {
                    // Asset must be visited
                    audioAssetAddr = new AssetAddr(AssetType.AUDIO, audioOffset, audioSize);
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
                Transition okTransition = Optional.ofNullable(okActionNodeAddr)
                        .map(h -> new Transition(null, okTransitionIndex)).orElse(null);

                short homeTransitionOffset = dis.readShort();
                short homeTransitionCount = dis.readShort();
                short homeTransitionIndex = dis.readShort();
                SectorAddr homeActionNodeAddr = null;
                if (homeTransitionOffset != -1) {
                    // Action node must be visited
                    homeActionNodeAddr = new SectorAddr(homeTransitionOffset);
                    actionNodesToVisit.add(homeActionNodeAddr);
                }
                Transition homeTransition = Optional.ofNullable(homeActionNodeAddr)
                        .map(h -> new Transition(null, homeTransitionIndex)).orElse(null);

                LOGGER.trace("Transitions : {} ok, {} home", okTransitionCount, homeTransitionCount);

                // Control settings
                boolean wheelEnabled = dis.readShort() == 1;
                boolean okEnabled = dis.readShort() == 1;
                boolean homeEnabled = dis.readShort() == 1;
                boolean pauseEnabled = dis.readShort() == 1;
                boolean autoJumpEnabled = dis.readShort() == 1;
                ControlSettings ctrl = new ControlSettings(wheelEnabled, okEnabled, homeEnabled, pauseEnabled, autoJumpEnabled); 

                // Read (optional) enriched node metadata
                dis.skipBytes(BINARY_ENRICHED_METADATA_STAGE_NODE_ALIGNMENT_PADDING);
                EnrichedNodeMetadata enrichedNode = readEnrichedNodeMetadata(dis);

                // Build stage node
                StageNode stageNode = new StageNode(uuid, null, null, okTransition, homeTransition, ctrl, enrichedNode);
                stageNodes.put(new SectorAddr(i), stageNode);

                // Assets will be updated when they are read
                Optional.ofNullable(imageAssetAddr).ifPresent(adr -> {
                    List<StageNode> swi = stagesWithImage.getOrDefault(adr, new ArrayList<>());
                    swi.add(stageNode);
                    stagesWithImage.put(adr, swi);
                });
                Optional.ofNullable(audioAssetAddr).ifPresent(adr -> {
                    List<StageNode> swa = stagesWithAudio.getOrDefault(adr, new ArrayList<>());
                    swa.add(stageNode);
                    stagesWithAudio.put(adr, swa);
                });
                // Action nodes will be updated when they are read
                Optional.ofNullable(okActionNodeAddr).ifPresent(adr -> {
                    List<Transition> twa = transitionsWithAction.getOrDefault(adr, new ArrayList<>());
                    twa.add(okTransition);
                    transitionsWithAction.put(adr, twa);
                });
                Optional.ofNullable(homeActionNodeAddr).ifPresent(adr -> {
                    List<Transition> twa = transitionsWithAction.getOrDefault(adr, new ArrayList<>());
                    twa.add(homeTransition);
                    transitionsWithAction.put(adr, twa);
                });

                // Skip to end of sector
                dis.skipBytes(SECTOR_SIZE - 54
                        - BINARY_ENRICHED_METADATA_STAGE_NODE_ALIGNMENT_PADDING
                        - BINARY_ENRICHED_METADATA_NODE_NAME_TRUNCATE*2 - 16 - 1 - 4);
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
                int alignmentOverflow = 2*(options.size()) % BINARY_ENRICHED_METADATA_ACTION_NODE_ALIGNMENT;
                int alignmentPadding = BINARY_ENRICHED_METADATA_ACTION_NODE_ALIGNMENT_PADDING + (alignmentOverflow > 0 ? BINARY_ENRICHED_METADATA_ACTION_NODE_ALIGNMENT - alignmentOverflow : 0);
                dis.skipBytes(alignmentPadding - 2);    // No need to skip the last 2 bytes that were read in the previous loop
                EnrichedNodeMetadata enrichedNodeMetadata = readEnrichedNodeMetadata(dis);

                // Update action on transitions referencing this sector
                ActionNode actionNode = new ActionNode(enrichedNodeMetadata, options);
                transitionsWithAction.get(actionNodeAddr).forEach(transition -> transition.setActionNode(actionNode));

                // Skip to end of sector
                dis.skipBytes(SECTOR_SIZE - (2*(options.size()+1))
                        - (alignmentPadding - 2)
                        - BINARY_ENRICHED_METADATA_NODE_NAME_TRUNCATE*2 - 16 - 1 - 4);
                currentOffset++;
            }

            // Read assets
            Iterator<AssetAddr> assetAddrsIter = assetAddrsToVisit.iterator();
            while (assetAddrsIter.hasNext()) {
                // First sector to read
                AssetAddr assetAddr = assetAddrsIter.next();
                // Skip to the beginning of the sector, if needed
                while (assetAddr.getOffset() > currentOffset) {
                    dis.skipBytes(SECTOR_SIZE);
                    currentOffset++;
                }
                // Read all bytes
                byte[] assetBytes = new byte[SECTOR_SIZE * assetAddr.getSize()];
                dis.read(assetBytes, 0, assetBytes.length);

                // Update asset on stage nodes referencing this sector
                if(assetAddr.getType() == AssetType.AUDIO) {
                    AudioAsset audioAsset = new AudioAsset(AudioType.WAV, assetBytes);
                    stagesWithAudio.get(assetAddr).forEach(stageNode -> stageNode.setAudio(audioAsset));
                }
                if(assetAddr.getType() == AssetType.IMAGE) {
                    ImageAsset imageAsset = new ImageAsset(ImageType.BMP, assetBytes);
                    stagesWithImage.get(assetAddr).forEach(stageNode -> stageNode.setImage(imageAsset));
                }
                currentOffset += assetAddr.getSize();
            }
            // Create storypack
            StoryPack sp = new StoryPack();
            sp.setUuid(stageNodes.get(new SectorAddr(0)).getUuid());
            sp.setFactoryDisabled(factoryDisabled);
            sp.setVersion(version);
            sp.setStageNodes(List.copyOf(stageNodes.values()));
            sp.setEnriched(enrichedPack);
            sp.setNightModeAvailable(false);
            return sp;
        }
    }

    /** Read UTF16 String from stream. */
    private Optional<String> readString(InputStream dis, int maxChars) throws IOException {
        byte[] bytes = dis.readNBytes(maxChars*2);
        String str = new String(bytes, StandardCharsets.UTF_16);
        int firstNullChar = str.indexOf("\u0000");
        if(firstNullChar == 0) {
            return Optional.empty();
        }
        if(firstNullChar == -1) {
            return Optional.of(str);
        }
        return Optional.of(str.substring(0, firstNullChar));
    }

    private EnrichedNodeMetadata readEnrichedNodeMetadata(DataInputStream dis) throws IOException {
        Optional<String> maybeName = readString(dis, BINARY_ENRICHED_METADATA_NODE_NAME_TRUNCATE);
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
