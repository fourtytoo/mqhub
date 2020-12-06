# mqhub

An IoT device monitor based on MQTT, written in Clojure.  Upon the
occurrence of a specific event on a given sensor, an action is
triggered.  Currently, the only action implemented is email, but there
is no reason you couldn't write your own actions.  The example below
gives you some clues about the motivation behind this project.


## Installation

Download your copy or clone the github repo.  Then compile:

    $ lein uberjar

To install the jar and the shell script:

	$ cp target/mqhub<version_number>-standalone.jar ~/bin/mqhub.jar
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
 :devices {"tele/home/ss01/SENSOR"
                          {:telemetry [:energy :power]
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
           "tele/home/ss02/SENSOR" {
				   ;; configuration of another device
				   }}}
```


## Usage

If you have installed the mqhub shell script as indicated above, you
can simply:

	$ mqhub
	
Otherwise you can run the jar as usual:

    $ java -cp target/mqhub-<version_number>-standalone.jar mqhub.core


## Logging

With the default configuration the logging is simply done to the
console (stdout).  If anything more sophisticated is required you need
to specify your logging configuration in the `:logging` map entry.

Example:

``` clojure
{:mqtt { ... }
 :smtp { ... }
 :devices { ... }
 :logging {:level :debug
		   :console false
		   :files ["/var/log/standard.log"
				   {:file "/var/log/standard-json.log" :encoder :json}]
		   :file {:file "/var/log/file.log" :encoder :json}
		   :appenders [{:appender :rolling-file
						:rolling-policy :fixed-window
						:triggering-policy :size-based
						:encoder  :pattern
						:pattern  "%p [%d] %t - %c %m%n"
						:file     "/var/log/rolling-pattern.log"}]}}
```

The above configuration would not make much sense, but it should give
an idea of what you can do.  See https://github.com/pyr/unilog for
details.


## Options

So far, the configuration file should be all you need.


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
