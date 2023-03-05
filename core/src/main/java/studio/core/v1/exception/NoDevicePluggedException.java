package studio.core.v1.exception;

public class NoDevicePluggedException extends StoryTellerException {

    public NoDevicePluggedException() {
        super("No device plugged");
    }
}
