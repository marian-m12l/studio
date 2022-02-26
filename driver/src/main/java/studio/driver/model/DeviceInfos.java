package studio.driver.model;

public abstract class DeviceInfos {

    private short firmwareMajor;
    private short firmwareMinor;
    private String serialNumber;

    public short getFirmwareMajor() {
        return firmwareMajor;
    }

    public void setFirmwareMajor(short firmwareMajor) {
        this.firmwareMajor = firmwareMajor;
    }

    public short getFirmwareMinor() {
        return firmwareMinor;
    }

    public void setFirmwareMinor(short firmwareMinor) {
        this.firmwareMinor = firmwareMinor;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

}
