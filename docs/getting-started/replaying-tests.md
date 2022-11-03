---
sidebar_position: 7
description: How to replay specific tests from a CATS run
---

# Replaying Tests

CATS will output both an HTML file that will be linked in the final report and individual JSON files. The JSON files can be used to replay tests at later times.
When replaying a test (or a list of tests), CATS won't produce any report. The output will be solely available in the console.
This is useful when you want to see the exact behaviour of the specific test or attach it in a bug report for example.

The syntax for replaying tests is the following:

```bash
cats replay "Test1,Test233,Test15.json,dir/Test19.json"
```

Important notes:
- test names are separated by comma `,`
- if you provide a `.json` extension to a test name, that file will be searched as a path i.e. it will search for `Test15.json` in the current folder and `Test19.json` in the `dir` folder
- if you don't provide a json extension to a test name, CATS searches for that test in the `cats-report` folder i.e. `cats-report/Test1.json` and `cats-report/Test233.json`

You can also use environment variables for headers when replaying tests using `$$varible`. Make sure you replace the header's value with `$$variable` inside the JSON file.

:::tip
When replaying tests you can also supply headers using the `-H` argument. This will override any header matching the given header name.
`cats replay Test1 -H Authentication=token`
:::