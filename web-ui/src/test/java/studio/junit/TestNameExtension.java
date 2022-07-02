package studio.junit;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class TestNameExtension implements BeforeEachCallback, AfterEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        System.out.println(">>> " + context.getRequiredTestClass().getSimpleName() + ": " + context.getDisplayName());
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        System.out.println("<<< " + context.getRequiredTestClass().getSimpleName() + ": " + context.getDisplayName());
    }

}