/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.core.v1.writer.archive;

import com.google.gson.stream.JsonWriter;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import studio.core.v1.model.ActionNode;
import studio.core.v1.model.StageNode;
import studio.core.v1.model.StoryPack;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ArchiveStoryPackWriter {

    public void write(StoryPack pack, OutputStream outputStream) throws IOException {

        // Zip archive contains a json file and separate assets
        ZipOutputStream zos = new ZipOutputStream(outputStream);

        // Store assets bytes
        TreeMap<String, byte[]> assets = new TreeMap<>();


        // Add story descriptor file: story.json
        ZipEntry zipEntry = new ZipEntry("story.json");
        zos.putNextEntry(zipEntry);

        // Start json document
        JsonWriter writer = new JsonWriter(new OutputStreamWriter(zos));
        writer.setIndent("    ");
        writer.beginObject();

        // Write file format metadata
        writer.name("format").value("v1");

        // Write metadata
        writer.name("title").value("MISSING_PACK_TITLE");
        writer.name("version").value(pack.getVersion());

        // Write stage nodes and keep track of action nodes and assets
        Map<ActionNode, String> actionNodeToId = new HashMap<>();
        writer.name("stageNodes");
        writer.beginArray();
        for (int i = 0; i < pack.getStageNodes().size(); i++) {
            StageNode node = pack.getStageNodes().get(i);
            writer.beginObject();
            writer.name("uuid").value(node.getUuid());
            writer.name("name").value("MISSING_NAME");
            if (i == 0) {
                // The first stage node is marked as such
                writer.name("squareOne").value(true);
            }
            writer.name("image");
            if (node.getImage() == null) {
                writer.nullValue();
            } else {
                String assetFileName = DigestUtils.sha1Hex(node.getImage().getBitmapData()) + ".bmp";
                writer.value(assetFileName);
                assets.put(assetFileName, node.getImage().getBitmapData());
            }
            writer.name("audio");
            if (node.getAudio() == null) {
                writer.nullValue();
            } else {
                String assetFileName = DigestUtils.sha1Hex(node.getAudio().getWaveData()) + ".wav";
                writer.value(assetFileName);
                assets.put(assetFileName, node.getAudio().getWaveData());
            }
            writer.name("okTransition");
            if (node.getOkTransition() == null) {
                writer.nullValue();
            } else {
                writer.beginObject();
                if (!actionNodeToId.containsKey(node.getOkTransition().getActionNode())) {
                    actionNodeToId.put(node.getOkTransition().getActionNode(), UUID.randomUUID().toString());
                }
                writer.name("actionNode").value(actionNodeToId.get(node.getOkTransition().getActionNode()));
                writer.name("optionIndex").value(node.getOkTransition().getOptionIndex());
                writer.endObject();
            }
            writer.name("homeTransition");
            if (node.getHomeTransition() == null) {
                writer.nullValue();
            } else {
                writer.beginObject();
                if (!actionNodeToId.containsKey(node.getHomeTransition().getActionNode())) {
                    actionNodeToId.put(node.getHomeTransition().getActionNode(), UUID.randomUUID().toString());
                }
                writer.name("actionNode").value(actionNodeToId.get(node.getHomeTransition().getActionNode()));
                writer.name("optionIndex").value(node.getHomeTransition().getOptionIndex());
                writer.endObject();
            }
            writer.name("controlSettings");
            writer.beginObject();
            writer.name("wheel").value(node.getControlSettings().isWheelEnabled());
            writer.name("ok").value(node.getControlSettings().isOkEnabled());
            writer.name("home").value(node.getControlSettings().isHomeEnabled());
            writer.name("pause").value(node.getControlSettings().isPauseEnabled());
            writer.name("autoplay").value(node.getControlSettings().isAutoJumpEnabled());
            writer.endObject();
            writer.endObject();
        }
        writer.endArray();

        writer.name("actionNodes");
        writer.beginArray();
        for (Map.Entry<ActionNode, String> actionNode : actionNodeToId.entrySet()) {
            writer.beginObject();
            writer.name("id").value(actionNode.getValue());
            writer.name("name").value("MISSING_NAME");
            writer.name("options");
            writer.beginArray();
            for (StageNode option : actionNode.getKey().getOptions()) {
                writer.value(option.getUuid());
            }
            writer.endArray();
            writer.endObject();
        }
        writer.endArray();

        writer.endObject();
        writer.flush();


        // Add assets in separate directory
        zipEntry = new ZipEntry("assets/");
        zos.putNextEntry(zipEntry);
        for (Map.Entry<String, byte[]> assetEntry : assets.entrySet()) {
            String assetPath = "assets/" + assetEntry.getKey();
            zipEntry = new ZipEntry(assetPath);
            zos.putNextEntry(zipEntry);
            IOUtils.write(assetEntry.getValue(), zos);
        }

        zos.flush();
        zos.close();
    }

}
