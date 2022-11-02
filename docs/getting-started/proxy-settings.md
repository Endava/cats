---
sidebar_position: 13
description: Running CATS behind a proxy
---

# Running Behind Proxy
If you need to run CATS behind a proxy, you can supply the following arguments: `--proxyHost` and `--proxyPort`.

A typical run with proxy settings on `localhost:8080` will look as follows:

```shell
cats --contract=YAML_FILE --server=SERVER_URL --proxyHost=localhost --proxyPort=8080
```