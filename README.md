# The json-to-iso using j8583

## How to Build

```
mvn clean package
```

## How to Build

```
java -Xms64m -Xmx1g -jar target/j8583-netty-server-1.0-SNAPSHOT-jar-with-dependencies.jar
```

## Notes

This non-blocking netty is really fast. Tested in Acer Nitro-5, this library can easily give 900TPS.
But, please be aware that j8583 doesn't support LLLLLLBIN at the time of writing these codes.
