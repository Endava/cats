---
sidebar_position: 18
description: Mutators
---

# CATS Response Codes

In order to preserve consistency, CATS has some custom HTTP response codes it uses mainly to signal communication issues.
They are all in the `9XX` range. The current defined ones are as follows:

- `952` - the server might close the connection earlier than expected; part of the response might be sent, but not all
- `953` - connection is refused; typically might indicate that the service is not available or there is a typo in the server name
- `954` - read timeout; typically when reading the response takes longer that the current set timeout; you might want to increase the `--readTimeout` argument
- `955` - write timeout; typically when writing the request takes longer that the current set timeout; you might want to increase the `--writeTimeout` argument
- `956` - connection timeout; typically when establishing a connection takes longer that the current set timeout; you might want to increase the `--connectionTimeout` argument
- `957` - protocol communication exception; typically when the service might write some malformed data into the response; might indicate an issue with the service
- `999` - when something unexpected happened which is not part of the above errors

If the entire communication is successful i.e. request was fully sent and response was fully consumed the HTTP response code will be used.