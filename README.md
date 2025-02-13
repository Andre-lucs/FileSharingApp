# FileSharingApp

## Overview
FileSharingApp is a Java-based application that allows users to share and download files seamlessly. The frontend client interface is built using JavaFX, and the connection is established using sockets with a protocol defined for a college project.

## Features
- Share files with other users
- Download files from other clients
- Download files from multiple owners and combine them for faster download
- Track file transfer progress
- Intuitive JavaFX-based user interface

## Technologies Used
- Java
- JavaFX
- Maven
- Sockets
- Concurrency

## Getting Started

### Prerequisites
- Java 21 or higher
- Maven

### Installation
1. Clone the repository:
    ```sh
    git clone https://github.com/Andre-lucs/FileSharingApp.git
    cd FileSharingApp
    ```

2. Build the project using Maven:
    ```sh
    mvn clean install
    ```

3. Run the application:
    ```sh
    mvn javafx:run
    ```

## Usage

### Server
1. Run the server class. : `src/main/java/com/andrelucs/filesharingapp/communication/server/Server.java`
2. The server will start listening for client connections.

### Client
1. Launch the application.
2. Enter the server IP address to connect.
3. Use the interface to upload or download files.


## Project Structure
- `src/main/java/com/andrelucs/filesharingapp/` - Main application source code
- `src/main/java/com/andrelucs/filesharingapp/communication/client/` - Client-side code
- `src/main/java/com/andrelucs/filesharingapp/communication/server/` - Server-side code
- `src/main/resources/` - Application resources
- `pom.xml` - Maven configuration file

## Contributing
Contributions are welcome! Please fork the repository and submit a pull request.

## License
This project is licensed under the MIT License.

## Acknowledgements
- Communication protocol defined by the college
- JavaFX community
- Open source libraries used in this project
