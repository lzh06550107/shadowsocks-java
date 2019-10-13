@echo off
java -jar ./target/shadowsocks-server-1.0.0.jar -P 8080 -m aes-128-cfb -p 123456
