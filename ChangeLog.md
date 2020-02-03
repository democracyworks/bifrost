# Change Log

## Changes between 0.2.0 and 0.3.0

* **[Breaking]**: removed the lower-level `bifrost.core/async-interceptor`
  function.
* Added a new `bifrost.core/handler` function that works like
  `bifrost.core/interceptor`, but assocs an error response if the channel is
  already closed.

## Changes between 0.1.5 and 0.2.0

* `bifrost.core/interceptor` is no longer a macro.
* The first argument `bifrost.core/interceptor`, instead of being an
  async channel, may be a function that will called with the bifrost
  request and returns a core async channel that the result will
  eventually be placed on.

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
