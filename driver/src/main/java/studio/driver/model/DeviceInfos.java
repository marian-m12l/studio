package studio.driver.model;

import lombok.Data;

@Data
public abstract class DeviceInfos {
    private short firmwareMajor;
    private short firmwareMinor;
    private String serialNumber;
}
