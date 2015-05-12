# smartwatch199

These are the Java files for an app that served as the complement to a smartwatch device.

We wanted to develop a working prototype as soon as possible, so we took some short cuts. For example, the ID of the bluetooth module on the Arduino device to connect to is hard coded.

Other than that, I'm pretty proud of what we made. The app has a broadcast reciever that listens for incoming text messages and can forward the body of the message to the Arduino. The code that sets up and maintains a Bluetooth socket connection is in a service class, so the connection is maintained even if the app is not at the forefront. The code that reads from the socket is in a separate thread from the code that writes to the socket to improve responsiveness. For communicating between threads, a Handler is used.
