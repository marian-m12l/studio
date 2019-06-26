/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.writer.binary;

import org.apache.commons.codec.digest.DigestUtils;
import studio.core.v1.Constants;
import studio.core.v1.model.*;
import studio.core.v1.reader.binary.AssetAddr;
import studio.core.v1.reader.binary.SectorAddr;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public class BinaryStoryPackWriter {

    public void write(StoryPack pack, OutputStream outputStream) throws IOException {
        DataOutputStream dos = new DataOutputStream(outputStream);

        // Write sector 1
        dos.writeShort(pack.getStageNodes().size());
        dos.writeByte(pack.isFactoryDisabled() ? 1 : 0);
        dos.writeShort(pack.getVersion());
        writePadding(dos, Constants.SECTOR_SIZE - 5);   // Skip to end of sector

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
                String assetHash = DigestUtils.sha1Hex(imageData);
                if (!assetsHashes.containsKey(assetHash)) {
                    if (!"image/bmp".equals(image.getMimeType())) {
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
                String assetHash = DigestUtils.sha1Hex(audioData);
                if (!assetsHashes.containsKey(assetHash)) {
                    if (!"audio/x-wav".equals(audio.getMimeType())) {
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
                String assetHash = DigestUtils.sha1Hex(image.getRawData());
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
                String assetHash = DigestUtils.sha1Hex(audio.getRawData());
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

            // Skip to end of sector
            writePadding(dos, Constants.SECTOR_SIZE - 54);
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

            // Skip to end of sector
            writePadding(dos, Constants.SECTOR_SIZE - 2*(actionNode.getOptions().size()));
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

        // Because of bug Luniistore's error-checker, we need at least 100000 sectors (i.e. 51 200 000 bytes) *after* the last action node
        int requiredPadding = 100000 - (currentOffset - actionNodesMap.lastKey().getOffset());
        if (requiredPadding > 0) {
            writePadding(dos, Constants.SECTOR_SIZE * requiredPadding);
        }

        // Write check bytes
        dos.write(Constants.CHECK_BYTES, 0, Constants.CHECK_BYTES.length);
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
