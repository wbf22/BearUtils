# Bear Utils
This repo is a collection of single class utils you can just copy paste into any project. They're mostly a lightweight way to have functionality similar to Spring Boot. All the classes are independent of each other so they can be copied seperatly and should work as is. Each folder in this repo (except the _test folder) contains a class you can copy into your code. The folders should also contain a README to explain their usage.

Here's a summary of the different single class tools in the repo:
- RestClient
    - A rest client with standard method GET, POST, PUT, PATCH, DELETE, OPTIONS
- Server
    - A server you can extend to expose a rest endpoint. Handles recieving requests and mapping to your defined methods. Protects against standard DoS attacks with timeouts, max request sizes, and rate limiting.
- ScheduledTask
    - A class you can extend to have a scheduled task be run during your apps lifetime. Supports a fixed and initial delay. 


Feel free to use these however you like, commericially or otherwise. These are under the MIT license (free to use). If you'd like to contribute, feel free to make a PR.

