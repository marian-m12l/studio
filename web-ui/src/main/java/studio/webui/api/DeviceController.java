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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.driver.model.DeviceInfosDTO;
import studio.driver.model.MetaPackDTO;
import studio.webui.model.DeviceDTOs.TransferDTO;
import studio.webui.model.DeviceDTOs.OutputDTO;
import studio.webui.model.DeviceDTOs.UuidDTO;
import studio.webui.model.DeviceDTOs.UuidsDTO;
import studio.webui.model.LibraryDTOs.SuccessDTO;
import studio.webui.service.IStoryTellerService;

@Path("/api/device")
public class DeviceController {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeviceController.class);

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

    /** Add pack from library to device : return id to watch async copy. */
    @POST
    @Path("addFromLibrary")
    public TransferDTO copyToDevice(UuidDTO uuidDTO) {
        LOGGER.info("addFromLibrary : {}", uuidDTO);
        return storyTellerService.addPack(uuidDTO.getUuid(), uuidDTO.getPath());
    }

    /** Extract pack from device to library : return id to watch async copy. */
    @POST
    @Path("addToLibrary")
    public TransferDTO extractFromDevice(UuidDTO uuidDTO) {
        LOGGER.info("addToLibrary : {}", uuidDTO);
        return storyTellerService.extractPack(uuidDTO.getUuid());
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
