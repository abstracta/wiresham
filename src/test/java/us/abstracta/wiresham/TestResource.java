package us.abstracta.wiresham;

import java.io.File;

public class TestResource {

  public static File getResourceFile(String resourcePath) {
    return new File(TestResource.class.getResource(resourcePath).getFile());
  }

}
