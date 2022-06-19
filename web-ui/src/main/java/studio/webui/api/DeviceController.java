/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.webui.api;

import java.util.List;
import java.util.concurrent.CompletionStage;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import studio.core.v1.utils.PackFormat;
import studio.webui.model.DeviceDTOs.DeviceInfosDTO;
import studio.webui.model.DeviceDTOs.OutputDTO;
import studio.webui.model.DeviceDTOs.UuidDTO;
import studio.webui.model.DeviceDTOs.UuidsDTO;
import studio.webui.model.LibraryDTOs.MetaPackDTO;
import studio.webui.model.LibraryDTOs.SuccessDTO;
import studio.webui.model.LibraryDTOs.TransferDTO;
import studio.webui.service.IStoryTellerService;

@Path("/api/device")
public class DeviceController {

    private static final Logger LOGGER = LogManager.getLogger(DeviceController.class);

    @ConfigProperty(name = "studio.library")
    java.nio.file.Path libraryPath;

    @Inject
    IStoryTellerService storyTellerService;

    /** Plugged device metadata. */
    @GET
    @Path("infos")
    public CompletionStage<DeviceInfosDTO> infos() {
        return storyTellerService.deviceInfos();
    }

    /** Plugged device packs list. */
    @GET
    @Path("packs")
    public CompletionStage<List<MetaPackDTO>> packs() {
        return storyTellerService.packs();
    }

    /** Add pack from library to device. */
    @POST
    @Path("addFromLibrary")
    public CompletionStage<TransferDTO> copyToDevice(UuidDTO uuidDTO) {
        var packFile = libraryPath.resolve(uuidDTO.getPath());
        // Return the transfer id, which is used to monitor transfer progress
        return storyTellerService.addPack(uuidDTO.getUuid(), packFile).thenApply(TransferDTO::new);
    }

    /** Extract pack from device to library. */
    @POST
    @Path("addToLibrary")
    public CompletionStage<TransferDTO> extractFromDevice(UuidDTO uuidDTO) {
        LOGGER.info("addToLibrary : {}", uuidDTO);
        // newDriver
        var packFile = libraryPath;
        // oldDriver
        if (PackFormat.RAW.getLabel().equals(uuidDTO.getDriver())) {
            packFile = libraryPath.resolve(uuidDTO.getUuid() + PackFormat.RAW.getExtension());
        }
        // Return the transfer id, which is used to monitor transfer progress
        return storyTellerService.extractPack(uuidDTO.getUuid(), packFile).thenApply(TransferDTO::new);
    }

    /** Remove pack from device. */
    @POST
    @Path("removeFromDevice")
    public CompletionStage<SuccessDTO> remove(UuidDTO uuidDTO) {
        LOGGER.info("Remove: {}", uuidDTO);
        return storyTellerService.deletePack(uuidDTO.getUuid()).thenApply(SuccessDTO::new);
    }

    /** Reorder packs on device. */
    @POST
    @Path("reorder")
    public CompletionStage<SuccessDTO> reorder(UuidsDTO uuidsDTO) {
        LOGGER.info("Reorder : {}", uuidsDTO);
        return storyTellerService.reorderPacks(uuidsDTO.getUuids()).thenApply(SuccessDTO::new);
    }

    /** Dump important sectors. */
    @POST
    @Path("dump")
    public CompletionStage<SuccessDTO> dump(OutputDTO outputDTO) {
        LOGGER.info("Dump to {}", outputDTO.getOutputPath());
        return storyTellerService.dump(outputDTO.getOutputPath()).thenApply(any -> new SuccessDTO(true));
    }
}
