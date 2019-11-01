@echo off
java -jar ./target/shadowsocks-client-2.0.0.jar -l 1081 -s 127.0.0.1 -P 8080 -m aes-128-cfb -p 123456
