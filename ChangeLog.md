# Change Log

## Changes between 0.1.2 and 0.1.3

* Updated a test for the 0.1.2 change timeout status change.

## Changes between 0.1.1 and 0.1.2

* In 0.1.1 timeouts returned 500 statuses, but 504 is probably the more
appropriate status to return. So 0.1.2 does that.

## Changes between 0.1.0 and 0.1.1

* 0.1.0 put the entire pedestal request onto the core.async channel, but in
0.1.1 the original intent of just putting the merged params map onto the
channel has been restored.
