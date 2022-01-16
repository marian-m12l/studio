/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.writer.binary;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import studio.core.v1.Constants;
import studio.core.v1.model.ActionNode;
import studio.core.v1.model.AssetType;
import studio.core.v1.model.AudioAsset;
import studio.core.v1.model.ImageAsset;
import studio.core.v1.model.Node;
import studio.core.v1.model.StageNode;
import studio.core.v1.model.StoryPack;
import studio.core.v1.model.Transition;
import studio.core.v1.model.enriched.EnrichedNodePosition;
import studio.core.v1.model.enriched.EnrichedNodeType;
import studio.core.v1.model.mime.AudioType;
import studio.core.v1.model.mime.ImageType;
import studio.core.v1.reader.binary.AssetAddr;
import studio.core.v1.reader.binary.SectorAddr;
import studio.core.v1.utils.SecurityUtils;
import studio.core.v1.writer.StoryPackWriter;

public class BinaryStoryPackWriter implements StoryPackWriter {

    public void write(StoryPack pack, Path path, boolean enriched) throws IOException {
        try(DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path)))){

            // Write sector 1
            dos.writeShort(pack.getStageNodes().size());
            dos.writeByte(pack.isFactoryDisabled() ? 1 : 0);
            dos.writeShort(pack.getVersion());

            // Write (optional) enriched pack metadata
            int enrichedPackMetadataSize = 0;
            if (enriched && pack.getEnriched() != null) {
                writePadding(dos, Constants.BINARY_ENRICHED_METADATA_SECTOR_1_ALIGNMENT_PADDING);
                writeTruncatedString(dos, pack.getEnriched().getTitle(), Constants.BINARY_ENRICHED_METADATA_TITLE_TRUNCATE);
                writeTruncatedString(dos, pack.getEnriched().getDescription(), Constants.BINARY_ENRICHED_METADATA_DESCRIPTION_TRUNCATE);
                // TODO Thumbnail?
                enrichedPackMetadataSize = Constants.BINARY_ENRICHED_METADATA_SECTOR_1_ALIGNMENT_PADDING + Constants.BINARY_ENRICHED_METADATA_TITLE_TRUNCATE*2 + Constants.BINARY_ENRICHED_METADATA_DESCRIPTION_TRUNCATE*2;
            }
            writePadding(dos, Constants.SECTOR_SIZE - 5 - enrichedPackMetadataSize);   // Skip to end of sector
    
            // Count action nodes and assets (with sizes) and attribute a sector address (offset) to each
            TreeMap<SectorAddr, ActionNode> actionNodesMap = new TreeMap<>();
            int nextFreeOffset = pack.getStageNodes().size();
            for (StageNode stageNode : pack.getStageNodes()) {
                if (stageNode.getOkTransition() != null && !actionNodesMap.containsValue(stageNode.getOkTransition().getActionNode())) {
                    actionNodesMap.put(new SectorAddr(nextFreeOffset++), stageNode.getOkTransition().getActionNode());
                }
                if (stageNode.getHomeTransition() != null && !actionNodesMap.containsValue(stageNode.getHomeTransition().getActionNode())) {
                    actionNodesMap.put(new SectorAddr(nextFreeOffset++), stageNode.getHomeTransition().getActionNode());
                }
            }
            TreeMap<String, AssetAddr> assetsHashes = new TreeMap<>();
            TreeMap<AssetAddr, byte[]> assetsData = new TreeMap<>();
            for (StageNode stageNode : pack.getStageNodes()) {
                ImageAsset image = stageNode.getImage();
                if (image != null) {
                    byte[] imageData = image.getRawData();
                    String assetHash = SecurityUtils.sha1Hex(imageData);
                    if (!assetsHashes.containsKey(assetHash)) {
                        if (!ImageType.BMP.is(image.getMimeType())) {
                            throw new IllegalArgumentException("Cannot write binary pack file from a compressed story pack. Uncompress the pack assets first.");
                        }
                        int imageSize = imageData.length;
                        int imageSectors = (imageSize / Constants.SECTOR_SIZE);
                        if (imageSize % Constants.SECTOR_SIZE > 0) {
                            imageSectors++;
                        }
                        AssetAddr addr = new AssetAddr(nextFreeOffset, imageSectors, AssetType.IMAGE);
                        assetsHashes.put(assetHash, addr);
                        assetsData.put(addr, image.getRawData());
                        nextFreeOffset += imageSectors;
                    }
                }
            }
            for (StageNode stageNode : pack.getStageNodes()) {
                AudioAsset audio = stageNode.getAudio();
                if (audio != null) {
                    byte[] audioData = audio.getRawData();
                    String assetHash = SecurityUtils.sha1Hex(audioData);
                    if (!assetsHashes.containsKey(assetHash)) {
                        if (!AudioType.WAV.is(audio.getMimeType())) {
                            throw new IllegalArgumentException("Cannot write binary pack file from a compressed story pack. Uncompress the pack assets first.");
                        }
                        int audioSize = audioData.length;
                        int audioSectors = (audioSize / Constants.SECTOR_SIZE);
                        if (audioSize % Constants.SECTOR_SIZE > 0) {
                            audioSectors++;
                        }
                        AssetAddr addr = new AssetAddr(nextFreeOffset, audioSectors, AssetType.AUDIO);
                        assetsHashes.put(assetHash, addr);
                        assetsData.put(addr, audio.getRawData());
                        nextFreeOffset += audioSectors;
                    }
                }
            }
    
            // Write stage nodes (from sector 2)
            for (StageNode stageNode : pack.getStageNodes()) {
                // UUID
                UUID nodeUuid = UUID.fromString(stageNode.getUuid());
                dos.writeLong(nodeUuid.getMostSignificantBits());
                dos.writeLong(nodeUuid.getLeastSignificantBits());
    
                // Image asset
                ImageAsset image = stageNode.getImage();
                if (image == null) {
                    dos.writeInt(-1);
                    dos.writeInt(-1);
                } else {
                    String assetHash = SecurityUtils.sha1Hex(image.getRawData());
                    AssetAddr assetAddr = assetsHashes.get(assetHash);
                    dos.writeInt(assetAddr.getOffset());
                    dos.writeInt(assetAddr.getSize());
                }
    
                // Audio asset
                AudioAsset audio = stageNode.getAudio();
                if (audio == null) {
                    dos.writeInt(-1);
                    dos.writeInt(-1);
                } else {
                    String assetHash = SecurityUtils.sha1Hex(audio.getRawData());
                    AssetAddr assetAddr = assetsHashes.get(assetHash);
                    dos.writeInt(assetAddr.getOffset());
                    dos.writeInt(assetAddr.getSize());
                }
    
                // Transitions
                Transition okTransition = stageNode.getOkTransition();
                if (okTransition == null) {
                    dos.writeShort(-1);
                    dos.writeShort(-1);
                    dos.writeShort(-1);
                } else {
                    SectorAddr nodeAddr = getKey(actionNodesMap, okTransition.getActionNode());
                    dos.writeShort(nodeAddr.getOffset());
                    dos.writeShort(okTransition.getActionNode().getOptions().size());
                    dos.writeShort(okTransition.getOptionIndex());
                }
                Transition homeTransition = stageNode.getHomeTransition();
                if (homeTransition == null) {
                    dos.writeShort(-1);
                    dos.writeShort(-1);
                    dos.writeShort(-1);
                } else {
                    SectorAddr nodeAddr = getKey(actionNodesMap, homeTransition.getActionNode());
                    dos.writeShort(nodeAddr.getOffset());
                    dos.writeShort(homeTransition.getActionNode().getOptions().size());
                    dos.writeShort(homeTransition.getOptionIndex());
                }
    
                // Control settings
                dos.writeShort(stageNode.getControlSettings().isWheelEnabled() ? 1 : 0);
                dos.writeShort(stageNode.getControlSettings().isOkEnabled() ? 1 : 0);
                dos.writeShort(stageNode.getControlSettings().isHomeEnabled() ? 1 : 0);
                dos.writeShort(stageNode.getControlSettings().isPauseEnabled() ? 1 : 0);
                dos.writeShort(stageNode.getControlSettings().isAutoJumpEnabled() ? 1 : 0);
    
                // Write (optional) enriched node metadata
                int enrichedNodeMetadataSize = 0;
                if (enriched && stageNode.getEnriched() != null) {
                    writePadding(dos, Constants.BINARY_ENRICHED_METADATA_STAGE_NODE_ALIGNMENT_PADDING);
                    enrichedNodeMetadataSize = Constants.BINARY_ENRICHED_METADATA_STAGE_NODE_ALIGNMENT_PADDING + writeEnrichedNodeMetadata(dos, stageNode);
                }
    
                // Skip to end of sector
                writePadding(dos, Constants.SECTOR_SIZE - 54 - enrichedNodeMetadataSize);
            }
    
            // Write action sectors
            int currentOffset = pack.getStageNodes().size();
            for (Map.Entry<SectorAddr, ActionNode> actionNodeEntry : actionNodesMap.entrySet()) {
                // Sector to write
                SectorAddr actionNodeAddr = actionNodeEntry.getKey();
                // Add padding to the beginning of the sector, if needed
                while (actionNodeAddr.getOffset() > currentOffset) {
                    writePadding(dos, Constants.SECTOR_SIZE);
                    currentOffset++;
                }
    
                // Node to write
                ActionNode actionNode = actionNodeEntry.getValue();
                for (StageNode stageNode : actionNode.getOptions()) {
                    int stageNodeOffset = pack.getStageNodes().indexOf(stageNode);
                    dos.writeShort(stageNodeOffset);
                }
    
                // Write (optional) enriched node metadata
                int enrichedNodeMetadataSize = 0;
                if (enriched && actionNode.getEnriched() != null) {
                    int alignmentOverflow = 2*(actionNode.getOptions().size()) % Constants.BINARY_ENRICHED_METADATA_ACTION_NODE_ALIGNMENT;
                    int alignmentPadding = Constants.BINARY_ENRICHED_METADATA_ACTION_NODE_ALIGNMENT_PADDING + (alignmentOverflow > 0 ? Constants.BINARY_ENRICHED_METADATA_ACTION_NODE_ALIGNMENT - alignmentOverflow : 0);
                    writePadding(dos, alignmentPadding);
                    enrichedNodeMetadataSize = alignmentPadding + writeEnrichedNodeMetadata(dos, actionNode);
                }
    
                // Skip to end of sector
                writePadding(dos, Constants.SECTOR_SIZE - 2*(actionNode.getOptions().size()) - enrichedNodeMetadataSize);
                currentOffset++;
            }
    
            // Write assets (images / audio)
            for (Map.Entry<AssetAddr, byte[]> assetEntry: assetsData.entrySet()) {
                // First sector to write
                AssetAddr assetAddr = assetEntry.getKey();
                // Skip to the beginning of the sector, if needed
                while (assetAddr.getOffset() > currentOffset) {
                    writePadding(dos, Constants.SECTOR_SIZE);
                    currentOffset++;
                }
    
                // Asset to write
                byte[] assetBytes = assetEntry.getValue();
                // Write all bytes
                int overflow = 0;
                dos.write(assetBytes, 0, assetBytes.length);
                overflow = assetBytes.length % Constants.SECTOR_SIZE;
    
                // Skip to end of sector
                if (overflow > 0) {
                    writePadding(dos, Constants.SECTOR_SIZE - overflow);
                }
    
                currentOffset += assetAddr.getSize();
            }
    
            // The Luniistore's error-checker bug is no more! No need to pad the story pack to 100000 sectors after the last action node
    
            // Write check bytes
            dos.write(Constants.CHECK_BYTES, 0, Constants.CHECK_BYTES.length);
        }
    }

    private int writeEnrichedNodeMetadata(DataOutputStream dos, Node node) throws IOException {
        writeTruncatedString(dos, node.getEnriched().getName(), Constants.BINARY_ENRICHED_METADATA_NODE_NAME_TRUNCATE);
        String nodeGroupId = node.getEnriched().getGroupId();
        if (nodeGroupId != null) {
            UUID groupId = UUID.fromString(nodeGroupId);
            dos.writeLong(groupId.getMostSignificantBits());
            dos.writeLong(groupId.getLeastSignificantBits());
        } else {
            writePadding(dos, 16);
        }
        EnrichedNodeType nodeType = node.getEnriched().getType();
        if (nodeType != null) {
            dos.writeByte(nodeType.code);
        } else {
            dos.writeByte(0x00);
        }
        EnrichedNodePosition nodePosition = node.getEnriched().getPosition();
        if (nodePosition != null) {
            dos.writeShort(nodePosition.getX());
            dos.writeShort(nodePosition.getY());
        } else {
            writePadding(dos, 4);
        }
        return Constants.BINARY_ENRICHED_METADATA_NODE_NAME_TRUNCATE*2 + 16 + 1 + 4;
    }

    private void writeTruncatedString(DataOutputStream dos, String str, int maxChars) throws IOException {
        if (str != null) {
            int strLength = Math.min(str.length(), maxChars);
            dos.writeChars(str.substring(0, strLength));
            int remaining =  maxChars - strLength;
            if (remaining > 0) {
                writePadding(dos, remaining*2);
            }
        } else {
            writePadding(dos, maxChars*2);
        }
    }

    private void writePadding(DataOutputStream dos, int length) throws IOException {
        byte[] padding = new byte[length];
        dos.write(padding, 0, length);
    }

    private <K, V> K getKey(Map<K, V> map, V value) {
        return map.entrySet()
                .stream()
                .filter(entry -> value.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .findFirst().get();
    }

}
