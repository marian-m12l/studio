package studio.core.v1.utils;

import de.sciss.jump3r.lowlevel.LameEncoder;

public class LameEncoderWrapper implements AutoCloseable {

    private final LameEncoder delegate;

    public LameEncoderWrapper(LameEncoder encoder) {
        delegate = encoder;
    }

    @Override
    public void close() throws Exception {
        delegate.close();
    }

}
