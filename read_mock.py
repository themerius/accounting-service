#!/usr/bin/env python
"""
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
"""
import os
import sys
import time
import signal
import uuid
import random


# pip install stompest==2.1.6
from stompest.config import StompConfig
from stompest.sync import Stomp


user = os.getenv('APOLLO_USER') or 'admin'
password = os.getenv('APOLLO_PASSWORD') or 'password'
host = os.getenv('APOLLO_HOST') or 'ashburner'
port = int(os.getenv('APOLLO_PORT') or 61613)
destination = sys.argv[1:2] or ['/topic/billing']
destination = destination[0]

config = StompConfig('tcp://%s:%d' % (host, port), login=user, passcode=password, version='1.1')
client = Stomp(config)
client.connect(host=host)
client.subscribe(destination=destination, headers={'id': 'required-for-STOMP-1.1'})


count = 0
start = time.time()

def signal_handler(signal, frame):
    print('You pressed Ctrl+C!')
    diff = time.time() - start
    print 'Received %s frames in %f seconds' % (count, diff)
    client.disconnect(receipt='bye')
    client.receiveFrame()
    client.close()
    sys.exit(0)

signal.signal(signal.SIGINT, signal_handler)

while True:
    #time.sleep(1)  # delay in secs
    frame = client.receiveFrame()
    count = count + 1
    print "Got frame " + str(count)
