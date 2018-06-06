# Wiresham
Simple TCP service mocking tool for replaying [Wireshark](https://www.wireshark.org/) captured service traffic.

This project is inspired in other like [WireMock](http://wiremock.org/), [mountebank](http://www.mbtest.org/) and [MockTCPServer](https://github.com/CloudRacer/MockTCPServer) but provides following features that are partially supported for some tools:
  * TCP mocking support, with async messages sent (i.e: allows sending welcome messages which are not supported by mountebank).
  * Load mocking specification from Wireshark `.json` dump files and provides a reduced `.yaml` format for easy versioning.
  * Allows to easily run the mock embedded in Java projects for easy testing

Take into consideration that this tool is very simple, and only replays TCP traffic that has previously been recorded, so if user interacts with the tool in unexpected ways, then the service will not answer until next expected packet is received. For more complex scenarios consider using one of previously mentioned tools.

## Usage

This tool (as previously listed ones) is particularly useful to implement integration tests without the hassle of flaky connections, or complex environment setup or restrictions (VPN, quotas, etc).
 
The general use case for the tool takes following steps:
  1. User captures with Wireshark traffic between an client application and a service.
  1. User stores the captured traffic, filtering with proper condition for service port, a `.json` file (File -> Export Packet Dissections -> As JSON...)
  1. At this point user might follow two potential courses:
      1. Start Wiresham in standalone mode with provided `.json` and connect to it with the client application to reproduce previously stored traffic. 
          
          E.g.: `java -jar wiresham-standalone.jar -p 2324 -w 0.0.0.0 wireshark-dump.json`
          
          > Latest version of wiresham-standalone.jar can be downloaded from [maven central](https://search.maven.org/).
          
          > Run `java -jar wiresham-standalone.jar -h` to get usage instructions and help.
      1. Convert the Wireshark dump to the reduced `.yaml` file (an example file can be found in [simple.yaml](src/test/resources/simple.yaml)), optionally manually tune it (response times or binary packets), add it to the project repository and implement tests using [VirtualTcpService class](src/main/java/us/abstracta/wiresham/VirtualTcpService.java).
          
          To convert an script can run something like `java -jar wiresham-standalone.jar -d reduced-dump.yml -w 0.0.0.0 wireshark-dump.json`.
          
          Check [VirtualServiceTest](src/test/java/us/abstracta/wiresham/VirtualServiceTest.java) for simple and raw examples on how to use VirtualService class.
          
## Build

In case you want to build this project from scratch it is required [JDK8+](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html) and [maven](https://maven.apache.org/) 3.3+.

Then just run `mvn clean install` and the library will be built and installed in local maven repository.

### Release

To release the project, define the version to be released by checking included changes since last release and following [semantic versioning](https://semver.org/). Then create a release in GitHub (including `v` as prefix of the version), this will trigger Travis build which will take care of the rest and you will be able to find the artifact in [maven central](https://search.maven.org/).
