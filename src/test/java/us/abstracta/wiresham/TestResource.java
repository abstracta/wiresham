package us.abstracta.wiresham;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Paths;

public class TestResource {

  public static File getResourceFile(String resourcePath) {
    try {
      return Paths.get(TestResource.class.getResource(resourcePath).toURI()).toFile();
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException(e);
    }
  }

}
