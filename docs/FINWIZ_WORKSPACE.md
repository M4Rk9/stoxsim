# Finwiz workspace

Finwiz uses the application theme, a conversation area and a recent-chat sidebar. Main bottom navigation is omitted on this route; Dashboard and Learn remain available. The Learn page owns the topic map and opens suggested questions without submitting them.

History is stored in this browser's localStorage, separated by signed-in account email, with a maximum of 30 threads and 50 answers per thread. Clear history requires a second explicit click. History does not sync between devices. The existing ask API and server-side tier rules remain unchanged; past turns are displayed locally, not automatically sent as model context.

This is the UI phase of the requested redesign. The chart ranges, candle gaps, removal of pattern controls, right-side order actions, and BSE/F&O discoverability changes are still pending. Production activation remains separate from UI delivery.
