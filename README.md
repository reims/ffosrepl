# ffosrepl

[![Clojars Project](http://clojars.org/ffosrepl/latest-version.svg)](http://clojars.org/ffosrepl)

Ffosrepl uses the Firefox [Remote Debugging Protocol][rdb] to connect a
ClojureScript REPL to a [Firefox OS][ffos] device.

## Why?

The [Content Usage Policy][csp] for Firefox OS apps does not allow calling the
Javascript function `eval`. This means that ClojureScript REPL environments like
[weasel][weasel] that use `eval` to evaluate Javascript on the client do not
work in a Firefox OS app. By using the Remote Debugging Protocol, ffosrepl gets
around this. The (unintended) advantage is, that no changes to the app are
necessary for ffosrepl to work. I.e. there is no need for code in the app that
establishes the transport between the app and the REPL environment.

## Usage

Ffosrepl is intended to be used with the [piggieback][piggieback] nREPL
middleware. So you need to set it up first. After that, add ffosrepl to the
dependencies in your project.clj:

```clojure
[ffosrepl "0.2.0-SNAPSHOT"]
```

You then have to forward the debugger socket of your device to a port on your
localhost:

```
adb forward tcp:6000 localfilesystem:/data/local/debugger-socket
```

Then start `lein repl` and piggieback the ffosrepl REPL environment onto the
nREPL session. You have to specify the name of the WebApp you want to connect
to. You can also specify a host (defaults to "127.0.0.1") and a port (defaults
to 6000). They must match the host and port the debugger socket of the device
was forwared to. If the app is not yet running, ffosrepl will start it
automatically.

```clojure
user> (require 'ffosrepl.repl)
nil
user> (cemerick.piggieback/cljs-repl
        :repl-env (ffosrepl.repl/repl-env "YourAwesomeApp"
                                          :host "127.0.0.1"
                                          :port 6000))
Type `:cljs/quit` to stop the ClojureScript REPL
nil
cljs.user> 
```

## TODO

- Automate the forwarding of the debugger socket.
- Make a library out of the Remote Debugging Protocol implementation. This
  library can then be used to write a leiningen plugin to install, start and
  stop apps on the Firefox OS device.

[rdb]: <https://wiki.mozilla.org/Remote_Debugging_Protocol>
[piggieback]: <https://github.com/cemerick/piggieback>
[ffos]: <https://www.mozilla.org/en-US/firefox/os/>
[csp]: <https://developer.mozilla.org/en-US/Apps/Build/Building_apps_for_Firefox_OS/CSP>
[weasel]: <https://github.com/tomjakubowski/weasel>

