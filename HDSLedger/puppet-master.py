#!/usr/bin/env python

import os
import json
import sys
import signal
import subprocess

# Terminal Emulator used to spawn the processes
terminal = "kitty"

# Blockchain node configuration file name
if len(sys.argv) <= 1:
    if conf := os.environ.get('CONF_PATH'):
        system_config = conf
    else:
        system_config = "Service/src/main/resources/regular_config.json"
else:
    system_config = sys.argv[1]
    os.environ["CONF_PATH"] = system_config



def quit_handler(*args):
    os.system(f"pkill -i {terminal}")
    sys.exit()

# Generate Key pairs for nodes
subprocess.call(f"sh -c \"cd KeyGenerator; mvn compile exec:java\"", shell=True)

# Compile classes
os.system("mvn clean install -Dmaven.test.skip=true")

# Spawn blockchain nodes
with open(system_config) as f:
    data = json.load(f)
    for key in data["nodes"]:
        pid = os.fork()
        if pid == 0:
            os.system(
                f"{terminal} sh -c \"cd Service; mvn exec:java -Dexec.args='{key['id']}' ; sleep 500\"")
            sys.exit()
    for key in data["clients"]:
        pid = os.fork()
        if pid == 0:
            os.system(
                f"{terminal} sh -c \"cd Client; mvn exec:java -Dexec.args='{key['id']}' ; sleep 500\"")
            sys.exit()

signal.signal(signal.SIGINT, quit_handler)

if "CONF_PATH" in os.environ:
    print("$CONF_PATH =", os.environ["CONF_PATH"])

if "KEY_DIR" in os.environ:
    print("$KEY_DIR =", os.environ["KEY_DIR"])


print("Type quit to quit")
while True:
    command = input(">> ")
    if command.strip() == "quit":
        quit_handler()
