# Redis Master-Slave & Sentinel Configuration (WSL Environment)

This directory contains the configuration files used to deploy a highly available Redis cluster with read-write separation in a local WSL environment.

## Architecture
- **Master**: 127.0.0.1:7001
- **Slaves**: 127.0.0.1:7002, 127.0.0.1:7003
- **Sentinels**: 127.0.0.1:27001, 127.0.0.1:27002, 127.0.0.1:27003

## 1. Master-Slave Configs

### `7001.conf` (Master)
```conf
port 7001
daemonize yes
dir ./7001
dbfilename dump-7001.rdb
logfile "7001.log"
```

### `7002.conf` (Slave)
```conf
port 7002
daemonize yes
dir ./7002
dbfilename dump-7002.rdb
logfile "7002.log"
replicaof 127.0.0.1 7001
```

### `7003.conf` (Slave)
```conf
port 7003
daemonize yes
dir ./7003
dbfilename dump-7003.rdb
logfile "7003.log"
replicaof 127.0.0.1 7001
```

## 2. Sentinel Configs

### `sentinel-27001.conf`
```conf
port 27001
sentinel monitor mymaster 127.0.0.1 7001 2
sentinel down-after-milliseconds mymaster 5000
sentinel failover-timeout mymaster 60000
dir "./s1"
```
*(sentinel-27002 and sentinel-27003 are identical except for the `port` and `dir`)*

## Validation & Chaos Testing
- Successfully verified Read-Write separation: Slaves correctly throw `(error) READONLY` on write attempts.
- Successfully verified Failover: Manually killed Master (`kill -9 <pid>`). Sentinels detected the failure, entered standard election (`+vote-for-leader`), and automatically promoted `7002` to the new Master.
