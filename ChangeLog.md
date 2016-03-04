# Change Log

## Changes between 0.1.5 and HEAD

**This is a breaking change.**

* Change `bifrost.core/interceptor` to work with functions that return
  core.async channels. This makes it less dependent on Kehaar's
  message format. To change existing interceptors, replace the
  channels passed as arguments with either functions returned by
  `kehaar.wire-up/async->fn` or
  `kehaar.power/defn-external-service`.
* Removed `bifrost.core/interceptor-xf`,
  `bifrost.core/api-response-xf`, and
  `bifrost.core/async-interceptor`.
* Converted `bifrost.core/interceptor` from a macro to a function.

## Changes between 0.1.4 and 0.1.5

* Added a new optional argument to `bifrost.core/interceptor` and
`bifrost.core/async-interceptor` called `timeout` that allows specifying how
long bifrost should wait for a RabbitMQ response before giving up and putting a
504 Gateway Timeout response into the context. Timeouts are specified in
milliseconds. If not provided, they default to 10,000 milliseconds, just like
before. This should be a non-breaking change.

## Changes between 0.1.3 and 0.1.4

* Fixed a bug in the `bifrost.interceptors/update-*` interceptors where they
assumed that the updated value would always be a map and thus could have `dissoc`
called on it. But that's a bad assumption and bifrost now checks whether or not
it's a map instead of crashing with a cryptic cast exception.

## Changes between 0.1.2 and 0.1.3

* Updated a test for the 0.1.2 change timeout status change.

## Changes between 0.1.1 and 0.1.2

* In 0.1.1 timeouts returned 500 statuses, but 504 is probably the more
appropriate status to return. So 0.1.2 does that.

## Changes between 0.1.0 and 0.1.1

* 0.1.0 put the entire pedestal request onto the core.async channel, but in
0.1.1 the original intent of just putting the merged params map onto the
channel has been restored.
