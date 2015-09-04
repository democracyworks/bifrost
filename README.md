# bifrost

> Be warned, I shall uphold my sacred oath to protect this realm as its
> gatekeeper. If your return threatens the safety of Asgard, my gate will
> remain shut and you will be left to perish on the cold waste of Jotunheim.
>   \- Heimdall

A Clojure library for building HTTP API gateways using Pedestal and core.async.

## Usage

Add the library as a dependency in your project.clj:

`[democracyworks/bifrost "0.1.0"]`

Require the core namespace in your pedestal service namespace (no need to alias
it or refer anything):

```
(ns my-gateway.service
  (:require [bifrost.core]))
```

Then just put core.async channels in your routes map where interceptors would
normally go. It's up to you to have something take from these channels, but the
intent is that whatever service you're gatewaying is on the other end.

Bifrost will do the following with your core.async channels:

* Wrap them in an `:enter` interceptor that, on each HTTP request does the
  following:
    * On GET and DELETE
        * Merges `:bifrost-params` -> `:path-params` -> `:query-params` (so
        `:bifrost-params` clobbers `:path-params` which clobbers
        `:query-params`) and puts the resulting map into a vector like:
        `[response-channel params-map]`. (The `response-channel` is created
        for you.)
    * On POST, PUT, and PATCH
        * Merges `:bifrost-params` -> `:path-params` -> `:body-params` ->
        `:json-params` -> `:transit-params` -> `:edn-params` -> `:form-params`
        -> `:query-params` and puts the resulting map into a vector like:
        `[response-channel params-map]`. (The `response-channel` is created for
        you.)
    * The vector is then put on the core.async channel from your route map.
    Whatever takes it on the other end is expected to process the `params-map`
    and put a response on the `response-channel`.
    * Responses should be EDN maps that look like the following:
    ```
    {:status :ok} ; plus any other keys and values you'd like to add
    ```
        * `:status` can be any of:
            * `:ok` - HTTP 200 response
            * `:created` - HTTP 201 response
            * `:error`
                * With `:error`, you can add an optional `:type` key whose value
                can be any of:
                    * `:semantic` - HTTP 400 response
                    * `:validation` - HTTP 400 response
                    * `:not-found` - HTTP 404 response
                    * `:server` - HTTP 500 response
                    * if `:type` is ommitted - HTTP 500 response
                    * (more to come)

If you want to add anything else to the `params-map`, then just put an
interceptor in front of your core.async interceptor that adds keys & values to
the `[:request :bifrost-params]` key path in the context.
Bifrost will then merge the `:bifrost-params` value into everything
else so those keys will clobber the others in the final `params-map`.

### Update Interceptors

The `bifrost.interceptors` namespace includes some handy interceptors for
transforming your requests and responses before and after bifrost relays
them over the core.async channels in your routes.

Specifically:

* `update-in-request`
    * 2-arity version takes a key path (like `get-in`) and a function
    * 3+-arity version takes a source key path, a target key path, a function,
    and any additional args to that function
    * It returns a before interceptor that gets the value at (source-)key-path
    in the request, calls the function argument on it as the first arg, and then
    assoc's the return value into the request at (target-)key-path.
    * If the target-key-path is different than the source-key-path, the last key
    in source-key-path will be dissoc'd from the request data structure.
* `update-in-response`
    * Just like `update-in-request` but returns an after interceptor that
    operates on the response instead of the request.

Use of these interceptors is optional.

## License

Copyright © 2015 Democracy Works, Inc.

Distributed under the Mozilla Public License either version 2.0 or (at
your option) any later version.
