# mqhub

An IoT device monitor based on MQTT, written in Clojure.

## Installation

Download your copy or clone the github repo.  Then compile:

    $ lein uberjar

To install the jar and the shell script:

	$ cp target/mqhub<yourversion>.jar ~/bin/mqhub.jar
	$ cp mqhub.sh ~/bin/mqhub
	$ chmod u+x ~/bin/mqhub

## Configuration

Before actually running the program, you need to write your own
configuration file ~/.mqhub.  Something along these lines:

``` clojure
;; -*- Clojure -*-

{:mqtt {:broker "tcp://mybroker:1883"}
 :smtp {:server {:host "smtp.gmail.com"
                 :user "myself@gmail.com"
                 ;; application password
                 :pass "secret"
                 :ssl true}
        :message {:from "mqhub@localhost"
                  :to "myself@mydomain.me"}}
 :devices {"home" {"ss01" {:telemetry [:energy :power]
                           :threshold 20 ; Watts
                           ;; avoid switching back and forth the
                           ;; on/off state
                           :hysteresis 0.3
                           ;; with a telemetry every minute
                           :avg-samples 10
                           :trigger :on-to-off
                           :action {:type :mail
                                    :message {:subject "Washing Machine"
                                              :body "The washing is ready to hang!"}}}
                   "ss02" {
				   ;; configuration of another device
				   }}
           "office" {"ss01" {
		       ;; ... and so on, and on
		   }}}}
```

The configuration above is meant to monitor topics like
"tele/home/ss01/SENSOR" (the default structure).  If your sensors
produce something else, you should change the `:mqtt
:topic-structure`.  By default it is `[:type :place :device :what]`.
If for instance you sensors were to produce topics like
"home/ssh01/tele/SENSOR", your configuration will have something like

``` clojure
{:mqtt {:topic-structure [:place :device :type :what]}}
```

## Usage

If you have installed the mqhub shell script as indicated above, you
can simply:

	$ mqhub
	
Otherwise you can run the jar as usual:

    $ java -cp target/mqhub-0.1.0-standalone.jar mqhub.core


## Options

So far, none.


### Bugs

Likely.


## License

Copyright © 2020 Walter C. Pelissero <walter@pelissero.de>

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
