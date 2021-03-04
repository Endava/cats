package com.endava.cats.args;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

@Component
@Getter
public class AuthArguments {
    private final List<CatsArg> args = new ArrayList<>();

    @Value("${sslKeystore:empty}")
    private String sslKeystore;
    @Value("${sslKeystorePwd:empty}")
    private String sslKeystorePwd;
    @Value("${sslKeyPwd:empty}")
    private String sslKeyPwd;
    @Value("${basicauth:empty}")
    private String basicAuth;

    @PostConstruct
    public void init() {
        args.add(CatsArg.builder().name("sslKeystore").value(sslKeystore).help("Location of the keystore holding certificates used when authenticating calls using one-way or two-way SSL").build());
        args.add(CatsArg.builder().name("sslKeystorePwd").value(sslKeystorePwd).help("The password of the sslKeystore").build());
        args.add(CatsArg.builder().name("sslKeyPwd").value(sslKeyPwd).help("The password of the private key from the sslKeystore").build());
        args.add(CatsArg.builder().name("basicauth").value(basicAuth).help("Supplies a `username:password` pair, in case the service uses basic auth").build());
    }

}
