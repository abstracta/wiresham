<p align="center">
  <img width="300" src="https://raw.githubusercontent.com/abstracta/wiresham/master/logo.svg?sanitize=true"/>
</p>
<br/>

Simple TCP mocking tool for replaying [tcpdump](http://www.tcpdump.org/) or [Wireshark](https://www.wireshark.org/) captured service or client traffic.

If you like this project, **please give it a star :star:!** This helps the project be more visible, gain relevance and encourages us to invest more effort in new features.

## Description

This project is inspired in other tools like [WireMock](http://wiremock.org/), [mountebank](http://www.mbtest.org/) and [MockTCPServer](https://github.com/CloudRacer/MockTCPServer), but provides following features that are partially supported by listed tools:
  * TCP mocking support, with async messages sent (i.e: allows sending welcome messages which are not supported by mountebank).
  * Load mocking specification from tcpdump `.pcap` or Wireshark `.json` dump files and provides a reduced `.yaml` format for easy versioning.
  * Allows to easily run the mock embedded in Java projects for easy testing
  * Allows both mocking servers and clients.

Take into consideration that this tool is very simple, and only replays TCP traffic that has previously been recorded, so if user (or server) interacts with the tool in unexpected ways, then the mock will not answer until next expected packet is received. For more complex scenarios consider using one of previously mentioned tools.

## Usage

This tool (as previously listed ones) is particularly useful to implement integration tests without the hassle of flaky connections, or complex environment setup or restrictions (VPN, quotas, etc).

**Note:** If you use `.pcap`, since Wiresham uses [pcap4j](https://www.pcap4j.org/) for `.pcap` files support, you need to install libpcap or winpcap as detailed in [pcap4j website](https://www.pcap4j.org/).  
 
The general use case for the tool takes following steps:
  1. User captures traffic with tcpdump (with something like `tcpdump port 23 -w ~/traffic.pcap`) or Wireshark between a client application and a service.
  1. If traffic has been captured with Wireshark then store the captured traffic, filtering with proper condition for service port, in a `.json` file (File -> Export Packet Dissections -> As JSON...)
  1. At this point user might follow three potential courses:
      1. Start Wiresham in standalone mode with stored `.pcap` or `.json` and connect to it with the client application to reproduce previously stored traffic. 
          
          E.g.: `java -jar wiresham-standalone.jar -p 2324 -a 0.0.0.0 wireshark-dump.json`
          
          > Latest version of wiresham-standalone.jar can be downloaded from [maven central](https://search.maven.org/).
          
          A similar example for a tcpdump traffic:
          
          E.g.: `java -jar wiresham-standalone.jar -p 2324 -a 0.0.0.0 traffic.pcap`
          
          > Run `java -jar wiresham-standalone.jar -h` to get usage instructions and help.
      1. Same as previous one but start Wiresham in standalong mode to emulate a client application (instead of a service application):
    
        E.g.: `java -jar wiresham-standalone.jar -t 0.0.0.0:23 -a 0.0.0.0 wireshark-dump.json`

        > Note that the only difference with previous example is the use of `-t` to specify target server address instead of the `-p` option to specify the local port. 
          
      1. Convert the tcpdump or Wireshark dump to a reduced `.yaml` file (an example file can be found in [simple.yaml](src/test/resources/simple.yaml)), optionally manually tune it (response times or binary packets), add it to the project repository and implement tests using [VirtualTcpService class](src/main/java/us/abstracta/wiresham/VirtualTcpService.java) or [VirtualTcpClient class](src/main/java/us/abstracta/wiresham/VirtualTcpClient.java).
          
          To convert a script run something like `java -jar wiresham-standalone.jar -d reduced-dump.yml -a 0.0.0.0 wireshark-dump.json`.
          
          To add Wiresham as dependency in maven project include in `pom.xml` the dependency:
          
          ```xml
          <dependency>
           <groupId>us.abstracta</groupId>
           <artifactId>wiresham</artifactId>
           <version>0.1</version>
          </dependency>
          ```
          
          > Check what is the latest version in [releases](https://github.com/abstracta/wiresham/releases)
          
          > Check [VirtualTcpServiceTest](src/test/java/us/abstracta/wiresham/VirtualTcpServiceTest.java) and [VirtualTcpClientTest](src/test/java/us/abstracta/wiresham/VirtualTcpClientTest.java) for simple and raw examples on how to use the classes.
          
## Build

In case you want to build this project from scratch, it is required [JDK8+](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html) and [maven](https://maven.apache.org/) 3.3+.

Then just run `mvn clean install` and the library (and standalone version) will be built and installed in the local maven repository.

## Release

To release the project, define the version to be released by checking included changes since last release and following [semantic versioning](https://semver.org/). 
Then, create a [release](https://github.com/abstracta/wiresham/releases) (including `v` as prefix of the version, e.g. `v0.1`), this will trigger a GitHub Actions workflow which will publish the jars to maven central repository (and make it general available to be used as maven dependency projects) in around 10 mins and can be found in [maven central search](https://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22us.abstracta%22%20AND%20a%3A%22wiresham%22) after up to 2 hours.
