package us.abstracta.wiresham;

import java.io.FileNotFoundException;

public abstract class SimpleFlow {

  public static final String SERVER_WELCOME_MESSAGE = "Hello";
  public static final String CLIENT_REQUEST = "Hello, I'm John";
  public static final String SERVER_RESPONSE = "Hello John";
  public static final String UNEXPECTED_MESSAGE = "What's up!";

  private SimpleFlow() {
  }

  public static Flow getFlow() throws FileNotFoundException {
    return Flow.fromYml(TestResource.getResourceFile("/simple.yaml"));
  }

}
